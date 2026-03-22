package de.espend.ml.llm.session.adapter.opencode

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import de.espend.ml.llm.session.util.ToolInputFormatter
import de.espend.ml.llm.session.util.ToolOutputFormatter
import kotlinx.serialization.json.*
import java.io.File
import java.sql.DriverManager
import java.time.Instant

/**
 * Standalone parser for OpenCode sessions from ~/.local/share/opencode/opencode.db.
 * Reads messages and parts from SQLite and converts to unified SessionDetail format.
 * No IntelliJ dependencies.
 */
object OpenCodeSessionParser {

    internal val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Parses an OpenCode session from the SQLite database.
     */
    fun parseSession(sessionId: String): SessionDetail? {
        val sessionInfo = OpenCodeSessionFinder.findSession(sessionId) ?: return null
        val dbFile = File(OpenCodeSessionFinder.getDbPath())
        if (!dbFile.exists()) return null

        return try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                val messages = mutableListOf<Pair<String, JsonObject>>()
                conn.prepareStatement(
                    "SELECT id, data FROM message WHERE session_id = ? ORDER BY time_created"
                ).use { stmt ->
                    stmt.setString(1, sessionId)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        try {
                            val data = JSON.parseToJsonElement(rs.getString("data")).jsonObject
                            messages.add(rs.getString("id") to data)
                        } catch (_: Exception) {}
                    }
                }

                val partsByMessage = mutableMapOf<String, MutableList<JsonObject>>()
                conn.prepareStatement(
                    "SELECT message_id, data FROM part WHERE session_id = ? ORDER BY time_created"
                ).use { stmt ->
                    stmt.setString(1, sessionId)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        try {
                            val data = JSON.parseToJsonElement(rs.getString("data")).jsonObject
                            partsByMessage.getOrPut(rs.getString("message_id")) { mutableListOf() }.add(data)
                        } catch (_: Exception) {}
                    }
                }

                buildSessionDetail(sessionInfo, messages, partsByMessage)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSessionDetail(
        sessionInfo: OpenCodeSession,
        messages: List<Pair<String, JsonObject>>,
        partsByMessage: Map<String, List<JsonObject>>
    ): SessionDetail {
        val parsedMessages = mutableListOf<ParsedMessage>()
        val modelCounts = mutableMapOf<String, Int>()

        for ((msgId, msgData) in messages) {
            val role = msgData["role"]?.jsonPrimitive?.content ?: continue
            val timestamp = msgData["time"]?.jsonObject?.get("created")?.jsonPrimitive?.longOrNull
                ?.let { Instant.ofEpochMilli(it).toString() } ?: ""
            val parts = partsByMessage[msgId] ?: emptyList()

            when (role) {
                "user" -> {
                    val textContent = parts
                        .filter { it["type"]?.jsonPrimitive?.content == "text" }
                        .mapNotNull { it["text"]?.jsonPrimitive?.content?.takeIf { t -> t.isNotEmpty() } }
                        .map { MessageContent.Text(it) }

                    if (textContent.isNotEmpty()) {
                        parsedMessages.add(ParsedMessage.User(timestamp = timestamp, content = textContent))
                    }
                }

                "assistant" -> {
                    val model = msgData["modelID"]?.jsonPrimitive?.content
                    if (model != null) modelCounts[model] = (modelCounts[model] ?: 0) + 1

                    val partMessages = mutableListOf<ParsedMessage>()
                    for (part in parts) {
                        val type = part["type"]?.jsonPrimitive?.content ?: continue
                        val partTimestamp = part["time"]?.jsonObject?.get("start")?.jsonPrimitive?.longOrNull
                            ?.let { Instant.ofEpochMilli(it).toString() } ?: timestamp

                        when (type) {
                            "text" -> {
                                val text = part["text"]?.jsonPrimitive?.content?.trim()
                                    ?.takeIf { it.isNotEmpty() } ?: continue
                                partMessages.add(ParsedMessage.AssistantText(
                                    timestamp = partTimestamp,
                                    content = listOf(MessageContent.Markdown(text))
                                ))
                            }
                            "reasoning" -> {
                                val text = part["text"]?.jsonPrimitive?.content?.trim()
                                    ?.takeIf { it.isNotEmpty() } ?: continue
                                partMessages.add(ParsedMessage.AssistantThinking(
                                    timestamp = partTimestamp,
                                    thinking = text
                                ))
                            }
                            "tool" -> {
                                val toolName = part["tool"]?.jsonPrimitive?.content ?: "tool"
                                val callId = part["callID"]?.jsonPrimitive?.content
                                val state = part["state"]?.jsonObject

                                val inputMap = ToolInputFormatter.jsonToMap(state?.get("input"))

                                val status = state?.get("status")?.jsonPrimitive?.content
                                val results = mutableListOf<ParsedMessage.ToolResult>()
                                if (status == "completed" || status == "error") {
                                    val outputEl = if (status == "error") state["error"] else state["output"]
                                    val outputBlocks = ToolOutputFormatter.formatToolOutput(outputEl)
                                    results.add(ParsedMessage.ToolResult(
                                        timestamp = partTimestamp,
                                        toolCallId = callId,
                                        output = outputBlocks,
                                        isError = status == "error"
                                    ))
                                }

                                partMessages.add(ParsedMessage.ToolUse(
                                    timestamp = partTimestamp,
                                    toolName = toolName,
                                    toolCallId = callId,
                                    input = inputMap,
                                    results = results
                                ))
                            }
                            // step-start, step-finish — skip
                        }
                    }

                    if (partMessages.isNotEmpty()) {
                        parsedMessages.addAll(partMessages)
                    } else {
                        val error = msgData["error"]?.jsonObject
                        if (error != null) {
                            val errorMsg = error["data"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                                ?: msgData.toString()
                            parsedMessages.add(ParsedMessage.Info(
                                timestamp = timestamp,
                                title = "error",
                                subtitle = error["name"]?.jsonPrimitive?.content,
                                content = MessageContent.Text(errorMsg),
                                style = ParsedMessage.InfoStyle.ERROR
                            ))
                        }
                    }
                }
            }
        }

        val models = modelCounts.entries.sortedByDescending { it.value }.map { it.key to it.value }

        return SessionDetail(
            sessionId = sessionInfo.sessionId,
            title = sessionInfo.title,
            messages = parsedMessages,
            metadata = SessionMetadata(
                cwd = sessionInfo.directory,
                models = models,
                messageCount = messages.size,
                created = Instant.ofEpochMilli(sessionInfo.created).toString(),
                modified = Instant.ofEpochMilli(sessionInfo.updated).toString()
            )
        )
    }

    /**
     * Result of parsing messages.
     */
    data class MessageParseResult(
        val messages: List<ParsedMessage>,
        val sortedModels: List<Pair<String, Int>>,
        val messageFileCount: Int
    )
}

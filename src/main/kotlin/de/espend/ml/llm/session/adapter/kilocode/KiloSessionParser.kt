package de.espend.ml.llm.session.adapter.kilocode

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import kotlinx.serialization.json.*
import java.io.File
import java.sql.DriverManager
import java.time.Instant

/**
 * Standalone parser for Kilo Code sessions from ~/.local/share/kilo/kilo.db.
 * Reads messages and parts from SQLite and converts to unified SessionDetail format.
 * No IntelliJ dependencies.
 */
object KiloSessionParser {

    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Parse a session from the SQLite database by session ID.
     */
    fun parseSession(sessionId: String): SessionDetail? {
        val dbFile = File(KiloSessionFinder.getDbPath())
        if (!dbFile.exists()) return null

        return try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                val sessionInfo = KiloSessionFinder.findSession(sessionId) ?: return null

                // Load messages ordered by creation time
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

                // Load parts ordered by creation time, grouped by message
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
        sessionInfo: KiloTaskInfo,
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

                    for (part in parts) {
                        val type = part["type"]?.jsonPrimitive?.content ?: continue
                        when (type) {
                            "text" -> {
                                val text = part["text"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: continue
                                parsedMessages.add(ParsedMessage.AssistantText(
                                    timestamp = timestamp,
                                    content = listOf(MessageContent.Markdown(text))
                                ))
                            }
                            "reasoning" -> {
                                val text = part["text"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: continue
                                parsedMessages.add(ParsedMessage.AssistantThinking(
                                    timestamp = timestamp,
                                    thinking = text
                                ))
                            }
                            "tool" -> {
                                val toolName = part["tool"]?.jsonPrimitive?.content ?: "unknown"
                                val callId = part["callID"]?.jsonPrimitive?.content
                                val state = part["state"]?.jsonObject
                                val inputObj = state?.get("input")?.jsonObject
                                val output = state?.get("output")?.jsonPrimitive?.content

                                val inputMap = mutableMapOf<String, String>()
                                inputObj?.entries?.forEach { (key, value) ->
                                    inputMap[key] = if (value is JsonPrimitive && value.isString) value.content else value.toString()
                                }

                                val results = if (output != null) listOf(
                                    ParsedMessage.ToolResult(
                                        timestamp = timestamp,
                                        toolName = toolName,
                                        toolCallId = callId,
                                        output = listOf(MessageContent.Text(output))
                                    )
                                ) else emptyList()

                                parsedMessages.add(ParsedMessage.ToolUse(
                                    timestamp = timestamp,
                                    toolName = toolName,
                                    toolCallId = callId,
                                    input = inputMap,
                                    results = results
                                ))
                            }
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
                cwd = sessionInfo.projectPath,
                models = models,
                messageCount = parsedMessages.size,
                created = Instant.ofEpochMilli(sessionInfo.timeCreated).toString(),
                modified = Instant.ofEpochMilli(sessionInfo.timeUpdated).toString()
            )
        )
    }
}

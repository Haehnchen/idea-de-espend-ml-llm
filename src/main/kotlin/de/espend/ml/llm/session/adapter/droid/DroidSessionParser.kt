package de.espend.ml.llm.session.adapter.droid

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import kotlinx.serialization.json.*
import java.io.File

/**
 * Standalone parser for Droid (Factory.ai CLI) session files.
 * Parses JSONL session files into unified SessionDetail format.
 * No IntelliJ dependencies.
 */
object DroidSessionParser {

    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Parse a JSONL session file by path.
     */
    fun parseFile(filePath: String): SessionDetail? {
        val file = File(filePath)
        if (!file.exists()) return null

        return try {
            val content = file.readText()
            parseContent(content)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse a JSONL session file.
     */
    fun parseFile(file: File): SessionDetail? {
        if (!file.exists()) return null

        return try {
            val content = file.readText()
            parseContent(content)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse session content from JSONL string.
     */
    fun parseContent(content: String): SessionDetail? {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val firstLine = try {
            JSON.parseToJsonElement(lines[0]).jsonObject
        } catch (_: Exception) {
            return null
        }

        if (firstLine["type"]?.jsonPrimitive?.content != "session_start") return null

        val sessionId = firstLine["id"]?.jsonPrimitive?.content ?: return null
        val messages = mutableListOf<ParsedMessage>()
        val toolCallMap = mutableMapOf<String, Pair<String, Map<String, String>>>() // toolCallId -> (name, input)

        for (i in 1 until lines.size) {
            try {
                val json = JSON.parseToJsonElement(lines[i]).jsonObject
                if (json["type"]?.jsonPrimitive?.content != "message") continue

                val timestamp = json["timestamp"]?.jsonPrimitive?.content ?: ""
                val messageObj = json["message"]?.jsonObject ?: continue
                val role = messageObj["role"]?.jsonPrimitive?.content ?: continue
                val contentArray = messageObj["content"]?.jsonArray ?: continue

                val parsed = parseMessage(role, contentArray, timestamp, toolCallMap)
                messages.addAll(parsed)
            } catch (_: Exception) {
                // Skip invalid lines
            }
        }

        val sessionTitle = firstLine["title"]?.jsonPrimitive?.content
        val sessionTitleAlt = firstLine["sessionTitle"]?.jsonPrimitive?.content
        val title = extractTitle(sessionTitle, sessionTitleAlt, messages)

        return SessionDetail(
            sessionId = sessionId,
            title = title,
            messages = messages,
            metadata = SessionMetadata(
                messageCount = messages.size
            )
        )
    }

    private fun parseMessage(
        role: String,
        contentArray: JsonArray,
        timestamp: String,
        toolCallMap: MutableMap<String, Pair<String, Map<String, String>>>
    ): List<ParsedMessage> {
        val messages = mutableListOf<ParsedMessage>()

        if (role == "user") {
            for (block in contentArray) {
                val obj = block.jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    "tool_result" -> {
                        val toolUseId = obj["tool_use_id"]?.jsonPrimitive?.content
                        val resultContent = obj["content"]?.jsonPrimitive?.content ?: ""
                        val toolCall = if (toolUseId != null) toolCallMap[toolUseId] else null

                        messages.add(ParsedMessage.ToolResult(
                            timestamp = timestamp,
                            toolName = toolCall?.first,
                            toolCallId = toolUseId,
                            output = parseToolResultContent(resultContent)
                        ))
                    }
                    "text" -> {
                        val text = obj["text"]?.jsonPrimitive?.content ?: ""
                        if (text.isNotEmpty()) {
                            messages.add(ParsedMessage.User(
                                timestamp = timestamp,
                                content = listOf(MessageContent.Text(text))
                            ))
                        }
                    }
                }
            }
        } else if (role == "assistant") {
            for (block in contentArray) {
                val obj = block.jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    "tool_use" -> {
                        val id = obj["id"]?.jsonPrimitive?.content ?: ""
                        val name = obj["name"]?.jsonPrimitive?.content ?: "tool"
                        val inputElement = obj["input"]?.jsonObject
                        val inputMap = mutableMapOf<String, String>()

                        inputElement?.entries?.forEach { (key, value) ->
                            inputMap[key] = when {
                                value is JsonPrimitive && value.isString -> value.content
                                else -> value.toString()
                            }
                        }

                        toolCallMap[id] = Pair(name, inputMap)

                        messages.add(ParsedMessage.ToolUse(
                            timestamp = timestamp,
                            toolName = name,
                            toolCallId = id,
                            input = inputMap,
                            results = emptyList()
                        ))
                    }
                    "text" -> {
                        val text = obj["text"]?.jsonPrimitive?.content ?: ""
                        if (text.isNotEmpty()) {
                            messages.add(ParsedMessage.AssistantText(
                                timestamp = timestamp,
                                content = listOf(MessageContent.Markdown(text))
                            ))
                        }
                    }
                }
            }
        }

        return messages
    }

    private fun parseToolResultContent(content: String): List<MessageContent> {
        // Check if it's a diff
        if (content.contains("---") && content.contains("+++")) {
            return listOf(MessageContent.Code(content, "diff"))
        }
        // Check if it's JSON
        if (content.trimStart().startsWith("{")) {
            try {
                JSON.parseToJsonElement(content)
                return listOf(MessageContent.Json(content))
            } catch (_: Exception) {
                // Not valid JSON
            }
        }
        // Default to code block
        return listOf(MessageContent.Code(content))
    }

    private fun extractTitle(
        sessionTitle: String?,
        sessionTitleAlt: String?,
        messages: List<ParsedMessage>
    ): String {
        // Use title from session_start if available and not generic
        if (!sessionTitle.isNullOrEmpty() && sessionTitle != "New Session") {
            return sessionTitle
        }

        // Find first user message
        val userMsg = messages.filterIsInstance<ParsedMessage.User>().firstOrNull()
        if (userMsg != null) {
            val text = userMsg.content.filterIsInstance<MessageContent.Text>().firstOrNull()?.text ?: ""
            if (text.isNotEmpty()) {
                return if (text.length > 100) text.take(100) + "..." else text
            }
        }

        return "Droid Session"
    }
}

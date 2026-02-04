package de.espend.ml.llm.session.adapter.amp

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Standalone parser for AMP session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 */
object AmpSessionParser {

    internal val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Parses an AMP session file and returns SessionDetail.
     */
    fun parseFile(file: Path): SessionDetail? {
        return try {
            val content = file.readText()
            parseContent(content)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses an AMP session by ID.
     */
    fun parseSession(sessionId: String): SessionDetail? {
        val file = AmpSessionFinder.findSessionFile(sessionId) ?: return null
        return parseFile(file)
    }

    /**
     * Parses AMP session JSON content and extracts messages with metadata.
     */
    fun parseContent(content: String): SessionDetail? {
        return try {
            val json = JSON.parseToJsonElement(content).jsonObject

            val sessionId = json["id"]?.jsonPrimitive?.content ?: return null
            val created = json["created"]?.jsonPrimitive?.longOrNull ?: 0L

            // Parse messages
            val messages = parseMessages(json)

            // Extract title from first user message
            val title = messages.filterIsInstance<ParsedMessage.User>().firstOrNull()?.let { userMsg ->
                val text = userMsg.content.filterIsInstance<MessageContent.Text>().firstOrNull()?.text
                    ?: userMsg.content.filterIsInstance<MessageContent.Markdown>().firstOrNull()?.markdown
                    ?: "Untitled"
                text.take(100) + if (text.length > 100) "..." else ""
            } ?: "Untitled"

            // Extract working directory from tool results (they contain absolute paths)
            val cwd = extractWorkingDirectory(json)

            // Calculate metadata
            val metadata = calculateMetadata(json, messages, created, cwd)

            SessionDetail(
                sessionId = sessionId,
                title = title,
                messages = messages,
                metadata = metadata
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses all messages from the session JSON.
     */
    private fun parseMessages(json: JsonObject): List<ParsedMessage> {
        val messages = mutableListOf<ParsedMessage>()
        val messagesArray = json["messages"]?.jsonArray ?: return emptyList()

        messagesArray.forEachIndexed { index, messageElement ->
            try {
                val message = messageElement.jsonObject
                val parsedMessages = parseMessage(index.toString(), message)
                messages.addAll(parsedMessages)
            } catch (e: Exception) {
                // Skip invalid messages
            }
        }

        return messages
    }

    /**
     * Parses a single message. Returns a list because assistant messages
     * can contain multiple tool_use blocks that should be separate messages.
     */
    private fun parseMessage(key: String, message: JsonObject): List<ParsedMessage> {
        val role = message["role"]?.jsonPrimitive?.content ?: return emptyList()

        // Extract timestamp from usage.timestamp if available, otherwise empty
        val timestamp = message["usage"]?.jsonObject?.get("timestamp")?.jsonPrimitive?.content ?: ""

        val contentArray = message["content"]?.jsonArray

        return when (role) {
            "user" -> listOfNotNull(parseUserMessage(timestamp, contentArray))
            "assistant" -> parseAssistantMessage(timestamp, contentArray)
            else -> emptyList()
        }
    }

    /**
     * Parses a user message.
     */
    private fun parseUserMessage(timestamp: String, contentArray: kotlinx.serialization.json.JsonArray?): ParsedMessage? {
        if (contentArray == null) return null

        val contentBlocks = mutableListOf<MessageContent>()

        contentArray.forEach { element ->
            val content = element.jsonObject
            val type = content["type"]?.jsonPrimitive?.content

            when (type) {
                "text" -> {
                    val text = content["text"]?.jsonPrimitive?.content
                    if (!text.isNullOrBlank()) {
                        contentBlocks.add(MessageContent.Text(text))
                    }
                }
                "tool_result" -> {
                    // Skip tool results that only contain a diff (redundant with tool_use diff view)
                    val run = content["run"]?.jsonObject
                    val result = run?.get("result")?.jsonObject
                    if (result?.containsKey("diff") == true && result.size <= 2) {
                        // Skip - this is just a diff result which we already show in tool_use
                        return@forEach
                    }

                    // Tool results in user messages are responses to tool calls
                    val toolResult = parseToolResultContent(content)
                    if (toolResult != null) {
                        contentBlocks.add(MessageContent.Code(toolResult))
                    }
                }
            }
        }

        // Return null if no meaningful content (all tool_results were skipped)
        if (contentBlocks.isEmpty()) {
            return null
        }

        return ParsedMessage.User(
            timestamp = timestamp,
            content = contentBlocks
        )
    }

    /**
     * Parses an assistant message. Returns a list because an assistant message
     * can contain multiple tool_use blocks that should be displayed separately.
     */
    private fun parseAssistantMessage(
        timestamp: String,
        contentArray: kotlinx.serialization.json.JsonArray?
    ): List<ParsedMessage> {
        if (contentArray == null) return emptyList()

        val result = mutableListOf<ParsedMessage>()
        val textBlocks = mutableListOf<MessageContent>()
        var thinkingBlock: ParsedMessage.AssistantThinking? = null

        contentArray.forEach { element ->
            val content = element.jsonObject
            val type = content["type"]?.jsonPrimitive?.content

            when (type) {
                "thinking" -> {
                    val thinking = content["thinking"]?.jsonPrimitive?.content
                    if (!thinking.isNullOrBlank()) {
                        thinkingBlock = ParsedMessage.AssistantThinking(
                            timestamp = timestamp,
                            thinking = thinking
                        )
                    }
                }
                "text" -> {
                    val text = content["text"]?.jsonPrimitive?.content
                    if (!text.isNullOrBlank()) {
                        textBlocks.add(MessageContent.Markdown(text))
                    }
                }
                "tool_use" -> {
                    // Before adding a tool_use, flush any accumulated text
                    if (textBlocks.isNotEmpty()) {
                        result.add(ParsedMessage.AssistantText(
                            timestamp = timestamp,
                            content = textBlocks.toList()
                        ))
                        textBlocks.clear()
                    }

                    val toolUse = parseToolUse(timestamp, content)
                    if (toolUse != null) {
                        result.add(toolUse)
                    }
                }
            }
        }

        // Add thinking block at the beginning if present
        if (thinkingBlock != null) {
            result.add(0, thinkingBlock!!)
        }

        // Add any remaining text blocks
        if (textBlocks.isNotEmpty()) {
            result.add(ParsedMessage.AssistantText(
                timestamp = timestamp,
                content = textBlocks
            ))
        }

        // If no content was parsed, return empty assistant text
        if (result.isEmpty()) {
            result.add(ParsedMessage.AssistantText(
                timestamp = timestamp,
                content = listOf(MessageContent.Text("[Empty assistant message]"))
            ))
        }

        return result
    }

    /**
     * Parses a tool_use content block.
     */
    private fun parseToolUse(
        timestamp: String,
        content: JsonObject
    ): ParsedMessage.ToolUse? {
        val id = content["id"]?.jsonPrimitive?.content
        val name = content["name"]?.jsonPrimitive?.content ?: "tool"
        val input = content["input"]?.jsonObject

        // Convert input to Map<String, String>
        val inputMap = input?.entries?.mapNotNull { (key, value) ->
            val strValue = when {
                value.jsonPrimitive.isString -> value.jsonPrimitive.content
                else -> value.toString()
            }
            key to strValue
        }?.toMap() ?: emptyMap()

        return ParsedMessage.ToolUse(
            timestamp = timestamp,
            toolName = name,
            toolCallId = id,
            input = inputMap,
            results = emptyList()
        )
    }

    /**
     * Parses a tool_result content block.
     */
    private fun parseToolResultContent(content: JsonObject): String? {
        val run = content["run"]?.jsonObject

        return when {
            run != null -> {
                val status = run["status"]?.jsonPrimitive?.content
                val result = run["result"]
                val error = run["error"]

                when {
                    error != null -> "Error: ${error.jsonPrimitive.content}"
                    result != null -> {
                        // Handle different result types
                        when {
                            result is JsonObject -> {
                                val content = result["content"]?.jsonPrimitive?.content
                                    ?: result.toString()
                                content
                            }
                            result.jsonPrimitive.isString -> result.jsonPrimitive.content
                            else -> result.toString()
                        }
                    }
                    else -> "[$status]"
                }
            }
            else -> null
        }
    }

    /**
     * Extracts the working directory from env.initial.trees[].uri.
     * This is the authoritative source for the session's working directory.
     */
    private fun extractWorkingDirectory(json: JsonObject): String? {
        // Navigate to env.initial.trees array
        val env = json["env"]?.jsonObject ?: return null
        val initial = env["initial"]?.jsonObject ?: return null
        val trees = initial["trees"]?.jsonArray ?: return null

        // Get first tree's URI and extract the path
        val firstTree = trees.firstOrNull()?.jsonObject ?: return null
        val uri = firstTree["uri"]?.jsonPrimitive?.content ?: return null

        // Remove "file://" prefix and return
        return uri.removePrefix("file://")
    }

    /**
     * Calculates session metadata from parsed data.
     */
    private fun calculateMetadata(
        json: JsonObject,
        messages: List<ParsedMessage>,
        created: Long,
        cwd: String?
    ): SessionMetadata {
        val modelCounts = mutableMapOf<String, Int>()

        val messagesArray = json["messages"]?.jsonArray ?: emptyList()

        messagesArray.forEach { messageElement ->
            try {
                val message = messageElement.jsonObject
                val usage = message["usage"]?.jsonObject

                // Count models
                val model = usage?.get("model")?.jsonPrimitive?.content
                if (model != null) {
                    modelCounts[model] = (modelCounts[model] ?: 0) + 1
                }
            } catch (_: Exception) {
                // Skip invalid usage data
            }
        }

        val sortedModels = modelCounts.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        return SessionMetadata(
            cwd = cwd,
            created = java.time.Instant.ofEpochMilli(created).toString(),
            modified = java.time.Instant.ofEpochMilli(created).toString(),
            messageCount = messages.size,
            models = sortedModels
        )
    }
}

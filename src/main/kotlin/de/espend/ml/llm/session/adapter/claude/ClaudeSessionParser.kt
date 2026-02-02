package de.espend.ml.llm.session.adapter.claude

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import de.espend.ml.llm.session.util.ToolInputFormatter
import de.espend.ml.llm.session.util.ToolOutputFormatter
import kotlinx.serialization.json.*
import java.io.File

/**
 * Standalone parser for Claude Code session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 */
object ClaudeSessionParser {

    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Parses a Claude session file and returns SessionDetail.
     */
    fun parseFile(file: File): SessionDetail? {
        if (!file.exists()) return null

        return try {
            val content = file.readText()
            val (messages, metadata) = parseContent(content)

            val title = messages.filterIsInstance<ParsedMessage.User>().firstOrNull()?.let { userMsg ->
                val text = userMsg.content.filterIsInstance<MessageContent.Text>().joinToString(" ") { it.text }
                    .ifEmpty { userMsg.content.filterIsInstance<MessageContent.Markdown>().joinToString(" ") { it.markdown } }
                text.take(100) + if (text.length > 100) "..." else ""
            } ?: "Untitled"

            SessionDetail(
                sessionId = file.nameWithoutExtension,
                title = title,
                messages = messages,
                metadata = metadata
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses Claude Code .jsonl content and extracts messages with metadata.
     * Connects ToolUse messages with their corresponding ToolResults in a post-processing step.
     */
    fun parseContent(content: String): Pair<List<ParsedMessage>, SessionMetadata?> {
        val rawMessages = mutableListOf<ParsedMessage>()
        var metadata: SessionMetadata? = null
        var created: String? = null
        var modified: String? = null
        var messageCount = 0
        val modelCounts = mutableMapOf<String, Int>()
        val lines = content.lines()

        // Phase 1: Parse all messages into rawMessages list
        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) continue

            try {
                val json = JSON.parseToJsonElement(trimmed).jsonObject
                val type = json["type"]?.jsonPrimitive?.content

                val timestamp = json["timestamp"]?.jsonPrimitive?.content
                    ?: json["snapshot"]?.jsonObject?.get("timestamp")?.jsonPrimitive?.content

                // Only update created/modified if we have a valid timestamp
                if (timestamp != null) {
                    if (created == null) created = timestamp
                    modified = timestamp
                }

                if (metadata == null && (type == "user" || type == "assistant")) {
                    val version = json["version"]?.jsonPrimitive?.content
                    val gitBranch = json["gitBranch"]?.jsonPrimitive?.content
                    val cwd = json["cwd"]?.jsonPrimitive?.content

                    metadata = SessionMetadata(
                        version = version,
                        gitBranch = gitBranch?.takeIf { it.isNotEmpty() },
                        cwd = cwd
                    )
                }

                val messageObj = json["message"]?.jsonObject

                val model = messageObj?.get("model")?.jsonPrimitive?.content
                if (model != null) {
                    modelCounts[model] = (modelCounts[model] ?: 0) + 1
                }

                // Skip parsing entries without a timestamp (e.g., summary records)
                val timestampStr = timestamp ?: continue
                val parsed = parseMessageContent(type, messageObj, json, trimmed, timestampStr)
                if (parsed != null) {
                    rawMessages.add(parsed)
                    messageCount++
                }
            } catch (_: Exception) {
                // Skip lines that fail to parse - don't add error messages for them
                // Continue to next line
            }
        }

        // Phase 2: Connect ToolResults to their corresponding ToolUse messages
        val finalMessages = connectToolResultsToToolUse(rawMessages)

        val sortedModels = modelCounts.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        metadata = metadata?.copy(
            created = created,
            modified = modified,
            messageCount = messageCount,
            models = sortedModels
        )

        return Pair(finalMessages, metadata)
    }

    /**
     * Post-processes raw messages to connect ToolResults to their corresponding ToolUse messages.
     * ToolResults with matching toolCallId are nested inside the ToolUse and removed from the main list.
     */
    private fun connectToolResultsToToolUse(rawMessages: List<ParsedMessage>): List<ParsedMessage> {
        // Build a map of toolCallId -> list of ToolResults
        val toolResultsByCallId = mutableMapOf<String, MutableList<ParsedMessage.ToolResult>>()
        rawMessages.forEach { msg ->
            if (msg is ParsedMessage.ToolResult && msg.toolCallId != null) {
                toolResultsByCallId.getOrPut(msg.toolCallId) { mutableListOf() }.add(msg)
            }
        }

        // Track which ToolResults have been connected
        val connectedToolResultIds = mutableSetOf<String>()

        // Build final list: update ToolUse with connected results, filter out connected ToolResults
        val result = mutableListOf<ParsedMessage>()

        for (msg in rawMessages) {
            when (msg) {
                is ParsedMessage.ToolUse -> {
                    val toolCallId = msg.toolCallId
                    if (toolCallId != null && toolResultsByCallId.containsKey(toolCallId)) {
                        // Connect results to this ToolUse
                        val results = toolResultsByCallId[toolCallId] ?: emptyList()
                        val updatedToolUse = msg.copy(results = msg.results + results)
                        result.add(updatedToolUse)
                        connectedToolResultIds.add(toolCallId)
                    } else {
                        result.add(msg)
                    }
                }
                is ParsedMessage.ToolResult -> {
                    // Only add if not connected to a ToolUse
                    val toolCallId = msg.toolCallId
                    if (toolCallId == null || !connectedToolResultIds.contains(toolCallId)) {
                        // Check if there's a matching ToolUse in the list
                        val hasMatchingToolUse = rawMessages.any { m ->
                            m is ParsedMessage.ToolUse && m.toolCallId == toolCallId
                        }
                        if (!hasMatchingToolUse) {
                            // Orphaned ToolResult - keep it as standalone
                            result.add(msg)
                        }
                        // Otherwise, skip - it will be nested inside the ToolUse
                    }
                }
                else -> {
                    result.add(msg)
                }
            }
        }

        return result
    }

    private fun parseMessageContent(
        type: String?,
        messageObj: JsonObject?,
        json: JsonObject,
        rawLine: String,
        timestamp: String
    ): ParsedMessage? {
        if (type == null) {
            return ParsedMessage.Info(
                timestamp = timestamp,
                title = "error",
                subtitle = "schema",
                content = MessageContent.Text("Schema Error: Missing 'type' field in this conversation entry.\n$rawLine"),
                style = ParsedMessage.InfoStyle.ERROR
            )
        }

        return when (type) {
            "user" -> parseUserMessage(messageObj, json, timestamp)
            "assistant" -> parseAssistantMessage(messageObj, timestamp)
            "tool_use" -> parseToolUseMessage(messageObj, timestamp)
            "tool_result" -> parseToolResultMessage(messageObj, json, timestamp)
            "thinking" -> parseThinkingMessage(messageObj, timestamp)
            "queue-operation" -> parseQueueOperation(json, timestamp)
            "system" -> parseSystemMessage(messageObj, json, rawLine, timestamp)
            "summary" -> parseSummaryMessage(json, timestamp)
            else -> null  // Skip unknown message types
        }
    }

    private fun parseUserMessage(
        messageObj: JsonObject?,
        json: JsonObject,
        timestamp: String
    ): ParsedMessage {
        val contentField = messageObj?.get("content")

        var hasText = false
        var hasToolResult = false
        var toolResultId: String? = null
        val contentBlocks = mutableListOf<MessageContent>()

        when {
            contentField is JsonArray -> {
                contentField.jsonArray.forEach { item ->
                    val obj = item.jsonObject
                    when (val itemType = obj["type"]?.jsonPrimitive?.content) {
                        "text" -> {
                            hasText = true
                            val text = obj["text"]?.jsonPrimitive?.content ?: ""
                            if (text.isNotEmpty()) {
                                contentBlocks.add(MessageContent.Text(text))
                            }
                        }
                        "tool_result" -> {
                            hasToolResult = true
                            val contentItem = obj["content"]
                            val innerContent = when {
                                contentItem is JsonPrimitive -> contentItem.content
                                contentItem is JsonArray -> {
                                    contentItem.jsonArray.mapNotNull { inner ->
                                        val innerObj = inner.jsonObject
                                        val innerType = innerObj["type"]?.jsonPrimitive?.content
                                        if (innerType == "text") innerObj["text"]?.jsonPrimitive?.content else null
                                    }.joinToString("")
                                }
                                else -> contentItem?.toString() ?: ""
                            }
                            toolResultId = obj["tool_use_id"]?.jsonPrimitive?.content
                            if (innerContent.isNotEmpty()) {
                                contentBlocks.add(MessageContent.Code(ToolOutputFormatter.truncateContent(innerContent)))
                            }
                        }
                        else -> {
                            contentBlocks.add(MessageContent.Code("[Unknown Content: $itemType] ${obj.toString().take(1000)}"))
                        }
                    }
                }
                if (contentBlocks.isEmpty()) {
                    contentBlocks.add(MessageContent.Code("[User Message - No parsable content] ${messageObj.toString().take(1000)}"))
                }
            }
            contentField is JsonPrimitive -> {
                hasText = true
                contentBlocks.add(MessageContent.Text(contentField.content))
            }
            else -> {
                contentBlocks.add(MessageContent.Code("[User Message - Unrecognized format] ${messageObj?.toString() ?: json.toString().take(1000)}"))
            }
        }

        // If only tool_result (no text), return as ToolResult
        // This allows the tool result to be connected to its ToolUse via toolCallId
        return if (hasToolResult && !hasText) {
            ParsedMessage.ToolResult(
                timestamp = timestamp,
                toolCallId = toolResultId,
                output = contentBlocks
            )
        } else {
            ParsedMessage.User(
                timestamp = timestamp,
                content = contentBlocks
            )
        }
    }

    private fun parseAssistantMessage(
        messageObj: JsonObject?,
        timestamp: String
    ): ParsedMessage {
        val contentArray = messageObj?.get("content")?.jsonArray ?: emptyList()
        var firstToolName: String? = null
        var firstToolCallId: String? = null
        var hasOnlyThinking = true
        var thinkingContent: String? = null

        var hasText = false
        var hasToolUse = false
        var hasThinking = false

        val contentBlocks = mutableListOf<MessageContent>()
        // Map to collect tool results by their tool_use_id within the same assistant message
        val toolResultsById = mutableMapOf<String, MutableList<ParsedMessage.ToolResult>>()

        contentArray.forEach { item ->
            val obj = item.jsonObject
            when (val itemType = obj["type"]?.jsonPrimitive?.content) {
                "text" -> {
                    hasOnlyThinking = false
                    hasText = true
                    val text = obj["text"]?.jsonPrimitive?.content ?: ""
                    if (text.isNotEmpty()) {
                        contentBlocks.add(MessageContent.Markdown(text))
                    }
                }
                "thinking" -> {
                    hasThinking = true
                    val thinking = obj["thinking"]?.jsonPrimitive?.content
                    if (thinking != null) {
                        thinkingContent = thinking
                        contentBlocks.add(MessageContent.Markdown(thinking))
                    }
                }
                "tool_use", "server_tool_use" -> {
                    hasOnlyThinking = false
                    hasToolUse = true
                    val name = obj["name"]?.jsonPrimitive?.content ?: "tool"
                    if (firstToolName == null) firstToolName = name
                    // Store tool_use_id for later connection with tool_result
                    val toolUseId = obj["id"]?.jsonPrimitive?.content
                    if (toolUseId != null && firstToolCallId == null) {
                        firstToolCallId = toolUseId
                    }
                }
                "tool_result" -> {
                    hasOnlyThinking = false
                    val contentField = obj["content"]
                    val resultContent = when {
                        contentField is JsonPrimitive -> contentField.content
                        contentField is JsonArray -> {
                            contentField.jsonArray.mapNotNull { inner ->
                                val innerObj = inner.jsonObject
                                val innerType = innerObj["type"]?.jsonPrimitive?.content
                                if (innerType == "text") innerObj["text"]?.jsonPrimitive?.content else null
                            }.joinToString("")
                        }
                        else -> contentField?.toString() ?: ""
                    }
                    val toolUseId = obj["tool_use_id"]?.jsonPrimitive?.content
                    val isError = obj["is_error"]?.jsonPrimitive?.content?.toBoolean() ?: false

                    // Create ToolResult and associate it with its tool_use_id
                    val toolResult = ParsedMessage.ToolResult(
                        timestamp = timestamp,
                        toolCallId = toolUseId,
                        output = if (resultContent.isNotEmpty()) listOf(MessageContent.Code(ToolOutputFormatter.truncateContent(resultContent))) else emptyList(),
                        isError = isError
                    )

                    // Store in map by tool_use_id for connection
                    if (toolUseId != null) {
                        toolResultsById.getOrPut(toolUseId) { mutableListOf() }.add(toolResult)
                    }
                }
                else -> {
                    hasOnlyThinking = false
                    contentBlocks.add(MessageContent.Code("[Unknown Content: $itemType] ${obj.toString().take(1000)}"))
                }
            }
        }

        if (contentBlocks.isEmpty()) {
            hasOnlyThinking = false
            contentBlocks.add(MessageContent.Code("[Assistant Message - No parsable content] ${messageObj.toString().take(1000)}"))
        }

        // Return appropriate message type based on content
        return when {
            hasOnlyThinking && thinkingContent != null -> {
                ParsedMessage.AssistantThinking(
                    timestamp = timestamp,
                    thinking = thinkingContent
                )
            }
            hasToolUse && firstToolName != null -> {
                // Tool use message - use ToolUse class
                // Connect any tool results that belong to this tool_use
                val connectedResults = firstToolCallId?.let { toolResultsById[it] } ?: emptyList()
                // Reconstruct input map from the tool_use object
                val inputMap = extractInputMap(contentArray, firstToolCallId)
                ParsedMessage.ToolUse(
                    timestamp = timestamp,
                    toolName = firstToolName,
                    toolCallId = firstToolCallId,
                    input = inputMap,
                    results = connectedResults
                )
            }
            else -> {
                // Pure text response
                ParsedMessage.AssistantText(
                    timestamp = timestamp,
                    content = contentBlocks
                )
            }
        }
    }

    private fun parseToolUseMessage(
        messageObj: JsonObject?,
        timestamp: String
    ): ParsedMessage {
        val contentArray = messageObj?.get("content")?.jsonArray ?: emptyList()
        var toolName: String? = null
        var toolCallId: String? = null

        contentArray.forEach { item ->
            val obj = item.jsonObject
            val itemType = obj["type"]?.jsonPrimitive?.content
            if (itemType == "tool_use") {
                val name = obj["name"]?.jsonPrimitive?.content ?: "tool"
                if (toolName == null) toolName = name
                // Store tool_use_id for later connection with tool_result
                val toolUseId = obj["id"]?.jsonPrimitive?.content
                if (toolUseId != null && toolCallId == null) {
                    toolCallId = toolUseId
                }
            }
        }

        // Extract input map from the content array
        val inputMap = extractInputMap(contentArray, toolCallId)

        return ParsedMessage.ToolUse(
            timestamp = timestamp,
            toolName = toolName ?: "tool",
            toolCallId = toolCallId,
            input = inputMap
        )
    }

    /**
     * Extracts the input map from a tool_use content array.
     * Finds the tool_use item with matching toolCallId (if provided) and returns its input as a Map.
     */
    private fun extractInputMap(contentArray: List<JsonElement>, toolCallId: String?): Map<String, String> {
        for (item in contentArray) {
            val obj = item.jsonObject
            val itemType = obj["type"]?.jsonPrimitive?.content
            if (itemType == "tool_use") {
                val itemToolCallId = obj["id"]?.jsonPrimitive?.content
                // If toolCallId is provided, match it; otherwise use first tool_use
                if (toolCallId == null || itemToolCallId == toolCallId) {
                    val inputElement = obj["input"]
                    return ToolInputFormatter.jsonToMap(inputElement)
                }
            }
        }
        return emptyMap()
    }

    private fun parseToolResultMessage(
        messageObj: JsonObject?,
        json: JsonObject,
        timestamp: String
    ): ParsedMessage {
        val contentField = json["content"] ?: messageObj?.get("content")
        val content = when {
            contentField is JsonPrimitive -> contentField.content
            contentField is JsonArray -> {
                contentField.jsonArray.mapNotNull { item ->
                    val itemObj = item.jsonObject
                    val itemType = itemObj["type"]?.jsonPrimitive?.content
                    if (itemType == "text") itemObj["text"]?.jsonPrimitive?.content else null
                }.joinToString("")
            }
            else -> messageObj?.get("result")?.jsonPrimitive?.content
                ?: messageObj?.toString()?.take(1000)
                ?: json["result"]?.jsonPrimitive?.content
                ?: json.toString().take(1000)
        }
        val toolUseId = json["tool_use_id"]?.jsonPrimitive?.content
            ?: messageObj?.get("tool_use_id")?.jsonPrimitive?.content

        return ParsedMessage.ToolResult(
            timestamp = timestamp,
            toolCallId = toolUseId,
            output = if (content.isNotEmpty()) listOf(MessageContent.Code(ToolOutputFormatter.truncateContent(content))) else emptyList()
        )
    }

    private fun parseThinkingMessage(
        messageObj: JsonObject?,
        timestamp: String
    ): ParsedMessage {
        val contentArray = messageObj?.get("content")?.jsonArray ?: emptyList()
        val thinkingParts = mutableListOf<String>()

        contentArray.forEach { item ->
            val obj = item.jsonObject
            val itemType = obj["type"]?.jsonPrimitive?.content
            if (itemType == "thinking") {
                val thinking = obj["thinking"]?.jsonPrimitive?.content
                if (thinking != null) {
                    thinkingParts.add(thinking)
                }
            }
        }

        val combinedThinking = thinkingParts.joinToString("\n\n").ifEmpty {
            "[Thinking message with no parsable content]"
        }

        return ParsedMessage.AssistantThinking(
            timestamp = timestamp,
            thinking = combinedThinking
        )
    }

    private fun parseQueueOperation(
        json: JsonObject,
        timestamp: String
    ): ParsedMessage {
        val operation = json["operation"]?.jsonPrimitive?.content ?: "unknown"
        val queueContent = json["content"]?.jsonPrimitive?.content

        return ParsedMessage.Info(
            timestamp = timestamp,
            title = "queue",
            subtitle = operation,
            content = queueContent?.takeIf { it.isNotEmpty() }?.let { MessageContent.Text(it) }
        )
    }

    private fun parseSystemMessage(
        messageObj: JsonObject?,
        json: JsonObject,
        rawLine: String,
        timestamp: String
    ): ParsedMessage {
        val subtype = json["subtype"]?.jsonPrimitive?.content

        // Handle turn_duration subtype specially
        if (subtype == "turn_duration") {
            val durationMs = json["durationMs"]?.jsonPrimitive?.longOrNull
            val content = if (durationMs != null) {
                MessageContent.Text(formatDuration(durationMs))
            } else {
                MessageContent.Json(rawLine)
            }

            return ParsedMessage.Info(
                timestamp = timestamp,
                title = "duration",
                subtitle = "turn_duration",
                content = content
            )
        }

        val messageText = messageObj?.get("content")?.jsonPrimitive?.content
            ?: json["content"]?.jsonPrimitive?.content

        val content = if (messageText != null) {
            MessageContent.Text(messageText)
        } else {
            MessageContent.Json(rawLine)
        }

        return ParsedMessage.Info(
            timestamp = timestamp,
            title = "system",
            subtitle = subtype,
            content = content
        )
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0) append("${minutes}m ")
            append("${seconds}s")
        }.trim()
    }

    private fun parseSummaryMessage(
        json: JsonObject,
        timestamp: String
    ): ParsedMessage {
        val summaryText = json["summary"]?.jsonPrimitive?.content

        return ParsedMessage.Info(
            timestamp = timestamp,
            title = "summary",
            content = MessageContent.Markdown(summaryText ?: "Session summary")
        )
    }
}

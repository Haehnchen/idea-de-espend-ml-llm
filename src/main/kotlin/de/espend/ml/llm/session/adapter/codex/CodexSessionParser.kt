package de.espend.ml.llm.session.adapter.codex

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import de.espend.ml.llm.session.util.ToolInputFormatter
import de.espend.ml.llm.session.util.ToolOutputFormatter
import kotlinx.serialization.json.*
import java.io.File

/**
 * Standalone parser for Codex session files (JetBrains AI Assistant / Codex CLI).
 * No IntelliJ dependencies - can be used from CLI or tests.
 *
 * Codex JSONL format has these main line types:
 * - session_meta: First line with session metadata (id, cwd, originator, cli_version, git info)
 * - event_msg: Contains user_message, agent_message, agent_reasoning, token_count
 * - response_item: Contains message (developer/user roles), function_call, function_call_output,
 *                  custom_tool_call, custom_tool_call_output, reasoning
 * - turn_context: Turn context with model, effort, cwd, etc.
 */
object CodexSessionParser {

    val JSON = Json { ignoreUnknownKeys = true }

    private enum class MessageSource {
        EVENT,
        RESPONSE_ITEM
    }

    private data class MessageRecord(
        val message: ParsedMessage,
        val source: MessageSource
    )

    private data class AssistantTextPresentation(
        val displayType: String,
        val style: ParsedMessage.AssistantTextStyle
    )

    /**
     * Parses a Codex session file and returns SessionDetail.
     */
    fun parseFile(file: File): SessionDetail? {
        if (!file.exists()) return null

        return try {
            val content = file.readText()
            val sessionId = CodexSessionFinder.extractSessionId(file) ?: file.nameWithoutExtension
            val (messages, metadata) = parseContent(content)

            val title = findFirstPublicUserMessage(content)?.let(::summarizeText)
                ?: messages.filterIsInstance<ParsedMessage.User>().firstOrNull()?.let { userMsg ->
                val text = userMsg.content.filterIsInstance<MessageContent.Text>().joinToString(" ") { it.text }
                    .ifEmpty { userMsg.content.filterIsInstance<MessageContent.Markdown>().joinToString(" ") { it.markdown } }
                summarizeText(text)
            } ?: "Untitled"

            SessionDetail(
                sessionId = sessionId,
                title = title,
                messages = messages,
                metadata = metadata
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses Codex .jsonl content and extracts messages with metadata.
     */
    fun parseContent(content: String): Pair<List<ParsedMessage>, SessionMetadata?> {
        val rawMessages = mutableListOf<MessageRecord>()
        var metadata: SessionMetadata? = null
        var created: String? = null
        var modified: String? = null
        val modelCounts = mutableMapOf<String, Int>()

        val lines = content.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) continue

            try {
                val json = JSON.parseToJsonElement(trimmed).jsonObject
                val type = json["type"]?.jsonPrimitive?.content
                val timestamp = json["timestamp"]?.jsonPrimitive?.content

                if (timestamp != null) {
                    if (created == null) created = timestamp
                    modified = timestamp
                }

                when (type) {
                    "session_meta" -> {
                        val payload = json["payload"]?.jsonObject
                        if (payload != null && metadata == null) {
                            val git = payload["git"]?.jsonObject
                            metadata = SessionMetadata(
                                version = payload["cli_version"]?.jsonPrimitive?.content,
                                gitBranch = git?.get("branch")?.jsonPrimitive?.content,
                                cwd = payload["cwd"]?.jsonPrimitive?.content
                            )
                        }
                    }

                    "event_msg" -> {
                        val payload = json["payload"]?.jsonObject
                        val parsed = parseEventMessage(payload, timestamp ?: "")
                        if (parsed != null) {
                            rawMessages.add(MessageRecord(parsed, MessageSource.EVENT))
                        }
                    }

                    "response_item" -> {
                        val payload = json["payload"]?.jsonObject
                        val payloadType = payload?.get("type")?.jsonPrimitive?.content
                        val parsed = parseResponseItem(payloadType, payload, timestamp ?: "")
                        if (parsed != null) {
                            rawMessages.add(MessageRecord(parsed, MessageSource.RESPONSE_ITEM))
                        }
                    }

                    "turn_context" -> {
                        val payload = json["payload"]?.jsonObject
                        val model = payload?.get("model")?.jsonPrimitive?.content
                        if (model != null) {
                            modelCounts[model] = (modelCounts[model] ?: 0) + 1
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip lines that fail to parse
            }
        }

        // Current Codex writes public user/assistant text both as event_msg and response_item.
        // LEGACY_CODEX_FORMAT: retain event-only messages from older sessions. Remove this fallback
        // once pre-response_item session files no longer need to be supported.
        val deduplicatedMessages = deduplicateEventMessages(rawMessages)
        val finalMessages = connectToolOutputsToToolCalls(deduplicatedMessages)

        val sortedModels = modelCounts.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        metadata = metadata?.copy(
            created = created,
            modified = modified,
            messageCount = finalMessages.size,
            models = sortedModels
        ) ?: SessionMetadata(
            created = created,
            modified = modified,
            messageCount = finalMessages.size,
            models = sortedModels
        )

        return Pair(finalMessages, metadata)
    }

    /**
     * Uses the public user event stream as the canonical source for user cards. Response user
     * records can also contain injected turn context, while event_msg/user_message represents
     * what the user submitted. Response user records remain as a fallback when no public user
     * events exist. Other event messages are removed only when an identical response item exists.
     */
    private fun deduplicateEventMessages(records: List<MessageRecord>): List<ParsedMessage> {
        val hasPublicUserEvents = records.any {
            it.source == MessageSource.EVENT && it.message is ParsedMessage.User
        }
        val remainingResponseCounts = records
            .asSequence()
            .filter { it.source == MessageSource.RESPONSE_ITEM }
            .mapNotNull { messageDeduplicationKey(it.message) }
            .groupingBy { it }
            .eachCount()
            .toMutableMap()

        return records.mapNotNull { record ->
            val key = messageDeduplicationKey(record.message)
            when {
                hasPublicUserEvents && record.source == MessageSource.RESPONSE_ITEM && record.message is ParsedMessage.User -> null
                record.source == MessageSource.EVENT && record.message !is ParsedMessage.User && key != null && remainingResponseCounts.getOrDefault(key, 0) > 0 -> {
                    remainingResponseCounts[key] = remainingResponseCounts.getValue(key) - 1
                    null
                }
                else -> record.message
            }
        }
    }

    private fun messageDeduplicationKey(message: ParsedMessage): String? {
        return when (message) {
            is ParsedMessage.User -> "user:${normalizeDeduplicationText(contentAsText(message.content))}"
            is ParsedMessage.AssistantText -> "assistant:${normalizeDeduplicationText(contentAsText(message.content))}"
            is ParsedMessage.AssistantThinking -> "thinking:${message.thinking.trim()}"
            else -> null
        }
    }

    private fun normalizeDeduplicationText(text: String): String {
        return text.trim()
    }

    private fun contentAsText(content: List<MessageContent>): String {
        return content.joinToString("\n") { block ->
            when (block) {
                is MessageContent.Text -> block.text
                is MessageContent.Code -> block.code
                is MessageContent.Markdown -> block.markdown
                is MessageContent.Json -> block.json
            }
        }
    }

    private fun findFirstPublicUserMessage(content: String): String? {
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) continue

            try {
                val json = JSON.parseToJsonElement(trimmed).jsonObject
                if (json["type"]?.jsonPrimitive?.contentOrNull != "event_msg") continue
                val payload = json["payload"]?.jsonObject ?: continue
                if (payload["type"]?.jsonPrimitive?.contentOrNull != "user_message") continue
                return payload["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                // Continue with the next record.
            }
        }
        return null
    }

    private fun summarizeText(text: String): String {
        return text.take(100) + if (text.length > 100) "..." else ""
    }

    /**
     * Parses public event messages as a fallback for older or partially-written sessions.
     * LEGACY_CODEX_FORMAT: current completed sessions should use response_item as the canonical text.
     */
    private fun parseEventMessage(payload: JsonObject?, timestamp: String): ParsedMessage? {
        if (payload == null) return null

        return when (payload["type"]?.jsonPrimitive?.content) {
            "user_message" -> payload["message"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let { ParsedMessage.User(timestamp, listOf(MessageContent.Text(it))) }

            "agent_message" -> payload["message"]?.jsonPrimitive?.contentOrNull
                ?.let(::stripInternalAssistantMetadata)
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    val presentation = parseAssistantTextPresentation(payload["phase"]?.jsonPrimitive?.contentOrNull)
                    ParsedMessage.AssistantText(
                        timestamp = timestamp,
                        content = listOf(MessageContent.Markdown(it)),
                        displayType = presentation.displayType,
                        style = presentation.style
                    )
                }

            // LEGACY_CODEX_FORMAT: older rollouts exposed reasoning as a clear-text event.
            // Current rollouts use response_item/reasoning with summary_text or encrypted content.
            "agent_reasoning" -> payload["text"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let { ParsedMessage.AssistantThinking(timestamp, it) }

            else -> null
        }
    }

    /**
     * Post-processes messages to connect tool outputs to their corresponding tool calls.
     */
    private fun connectToolOutputsToToolCalls(rawMessages: List<ParsedMessage>): List<ParsedMessage> {
        val toolResultsByCallId = mutableMapOf<String, MutableList<ParsedMessage.ToolResult>>()

        // Collect all tool results by call_id
        rawMessages.forEach { msg ->
            if (msg is ParsedMessage.ToolResult && msg.toolCallId != null) {
                toolResultsByCallId.getOrPut(msg.toolCallId) { mutableListOf() }.add(msg)
            }
        }

        val connectedCallIds = mutableSetOf<String>()
        val result = mutableListOf<ParsedMessage>()

        for (msg in rawMessages) {
            when (msg) {
                is ParsedMessage.ToolUse -> {
                    val callId = msg.toolCallId
                    if (callId != null && toolResultsByCallId.containsKey(callId)) {
                        val results = toolResultsByCallId[callId] ?: emptyList()
                        val updatedToolUse = msg.copy(results = msg.results + results)
                        result.add(updatedToolUse)
                        connectedCallIds.add(callId)
                    } else {
                        result.add(msg)
                    }
                }
                is ParsedMessage.ToolResult -> {
                    val callId = msg.toolCallId
                    if (callId == null || !connectedCallIds.contains(callId)) {
                        val hasMatchingToolUse = rawMessages.any { m ->
                            m is ParsedMessage.ToolUse && m.toolCallId == callId
                        }
                        if (!hasMatchingToolUse) {
                            result.add(msg)
                        }
                    }
                }
                else -> result.add(msg)
            }
        }

        return result
    }

    /**
     * Parses response_item payload types.
     */
    private fun parseResponseItem(
        payloadType: String?,
        payload: JsonObject?,
        timestamp: String
    ): ParsedMessage? {
        if (payload == null) return null

        return when (payloadType) {
            "message" -> parseMessagePayload(payload, timestamp)

            "function_call" -> {
                val name = payload["name"]?.jsonPrimitive?.content ?: "function"
                val callId = payload["call_id"]?.jsonPrimitive?.content
                val argumentsStr = payload["arguments"]?.jsonPrimitive?.content

                val input = if (argumentsStr != null) {
                    try {
                        val argsJson = JSON.parseToJsonElement(argumentsStr)
                        ToolInputFormatter.jsonToMap(argsJson)
                    } catch (_: Exception) {
                        mapOf("arguments" to argumentsStr)
                    }
                } else {
                    emptyMap()
                }

                ParsedMessage.ToolUse(
                    timestamp = timestamp,
                    toolName = name,
                    toolCallId = callId,
                    input = input
                )
            }

            "function_call_output" -> {
                val callId = payload["call_id"]?.jsonPrimitive?.content

                ParsedMessage.ToolResult(
                    timestamp = timestamp,
                    toolCallId = callId,
                    output = parseToolOutput(payload["output"])
                )
            }

            "custom_tool_call" -> {
                val name = payload["name"]?.jsonPrimitive?.content ?: "tool"
                val callId = payload["call_id"]?.jsonPrimitive?.content
                val inputStr = payload["input"]?.jsonPrimitive?.content

                val input = if (inputStr != null) {
                    mapOf("input" to inputStr.take(2000))
                } else {
                    emptyMap()
                }

                ParsedMessage.ToolUse(
                    timestamp = timestamp,
                    toolName = name,
                    toolCallId = callId,
                    input = input
                )
            }

            "custom_tool_call_output" -> {
                val callId = payload["call_id"]?.jsonPrimitive?.content

                ParsedMessage.ToolResult(
                    timestamp = timestamp,
                    toolCallId = callId,
                    output = parseToolOutput(payload["output"])
                )
            }

            "reasoning" -> {
                val summaryText = extractReasoningSummary(payload)

                if (summaryText.isNotEmpty()) {
                    ParsedMessage.AssistantThinking(
                        timestamp = timestamp,
                        thinking = summaryText
                    )
                } else {
                    null
                }
            }

            "agent_message" -> parseAgentMessagePayload(payload, timestamp)

            "tool_search_call" -> {
                ParsedMessage.ToolUse(
                    timestamp = timestamp,
                    toolName = "tool_search",
                    toolCallId = payload["call_id"]?.jsonPrimitive?.contentOrNull,
                    input = ToolInputFormatter.jsonToMap(payload["arguments"])
                )
            }

            "tool_search_output" -> {
                ParsedMessage.ToolResult(
                    timestamp = timestamp,
                    toolName = "tool_search",
                    toolCallId = payload["call_id"]?.jsonPrimitive?.contentOrNull,
                    output = summarizeToolSearchOutput(payload["tools"])
                )
            }

            "web_search_call" -> {
                ParsedMessage.ToolUse(
                    timestamp = timestamp,
                    toolName = "web_search",
                    toolCallId = payload["id"]?.jsonPrimitive?.contentOrNull,
                    input = ToolInputFormatter.jsonToMap(payload["action"])
                )
            }

            "image_generation_call" -> {
                val input = buildMap {
                    payload["status"]?.jsonPrimitive?.contentOrNull?.let { put("status", it) }
                    payload["revised_prompt"]?.jsonPrimitive?.contentOrNull?.let {
                        put("prompt", ToolOutputFormatter.truncateContent(it))
                    }
                }
                ParsedMessage.ToolUse(
                    timestamp = timestamp,
                    toolName = "image_generation",
                    toolCallId = payload["id"]?.jsonPrimitive?.contentOrNull,
                    input = input
                )
            }

            else -> null
        }
    }

    private fun extractReasoningSummary(payload: JsonObject): String {
        fun extractText(element: JsonElement?): List<String> {
            return when (element) {
                is JsonArray -> element.flatMap(::extractText)
                is JsonObject -> listOfNotNull(element["text"]?.jsonPrimitive?.contentOrNull)
                is JsonPrimitive -> listOfNotNull(element.contentOrNull)
                else -> emptyList()
            }
        }

        return (extractText(payload["summary"]) + extractText(payload["content"]))
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    /**
     * Codex 0.144+ stores tool output as either a legacy string or a Responses API
     * content array containing input_text/input_image blocks.
     */
    private fun parseToolOutput(output: JsonElement?): List<MessageContent> {
        val text = extractToolOutputText(output).trim()
        if (text.isEmpty()) return emptyList()
        return listOf(MessageContent.Code(ToolOutputFormatter.truncateContent(text)))
    }

    private fun extractToolOutputText(output: JsonElement?): String {
        return when (output) {
            null, JsonNull -> ""
            is JsonArray -> output.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    "input_text", "output_text", "text" -> obj["text"]?.jsonPrimitive?.contentOrNull
                    "input_image", "output_image" -> "[image]"
                    else -> obj["text"]?.jsonPrimitive?.contentOrNull
                }
            }.joinToString("\n")

            is JsonObject -> {
                val nested = output["output"] ?: output["content"] ?: output["result"]
                if (nested != null) extractToolOutputText(nested) else output.toString()
            }

            // LEGACY_CODEX_FORMAT: older tool results stored output as a plain string or as a
            // JSON object encoded inside a string. Current Responses-style output is a content array.
            is JsonPrimitive -> {
                val raw = output.contentOrNull ?: return ""
                try {
                    val parsed = JSON.parseToJsonElement(raw)
                    if (parsed is JsonObject || parsed is JsonArray) extractToolOutputText(parsed) else raw
                } catch (_: Exception) {
                    raw
                }
            }
        }
    }

    private fun summarizeToolSearchOutput(toolsElement: JsonElement?): List<MessageContent> {
        val tools = toolsElement as? JsonArray ?: return emptyList()
        val names = tools.mapNotNull { tool ->
            (tool as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }
        val summary = if (names.isEmpty()) {
            "Found ${tools.size} tool definitions"
        } else {
            "Found ${tools.size} tool definitions: ${names.joinToString(", ")}"
        }
        return listOf(MessageContent.Text(summary))
    }

    private fun parseAgentMessagePayload(payload: JsonObject, timestamp: String): ParsedMessage? {
        val text = extractMessageText(payload["content"])
            .let(::stripInternalAssistantMetadata)
            .takeIf { it.isNotBlank() }
            ?: return null
        val author = payload["author"]?.jsonPrimitive?.contentOrNull
        val recipient = payload["recipient"]?.jsonPrimitive?.contentOrNull
        val subtitle = listOfNotNull(author, recipient).joinToString(" → ").ifEmpty { null }

        return ParsedMessage.Info(
            timestamp = timestamp,
            title = "agent_message",
            subtitle = subtitle,
            content = MessageContent.Markdown(text)
        )
    }

    /**
     * Parses message payload (developer/user roles in response_item).
     */
    private fun parseMessagePayload(payload: JsonObject, timestamp: String): ParsedMessage? {
        val role = payload["role"]?.jsonPrimitive?.content
        val contentArray = payload["content"]?.jsonArray ?: return null

        val contentBlocks = mutableListOf<MessageContent>()

        for (item in contentArray) {
            val obj = item.jsonObject
            val itemType = obj["type"]?.jsonPrimitive?.content

            when (itemType) {
                "input_text" -> {
                    val text = obj["text"]?.jsonPrimitive?.content ?: continue
                    contentBlocks.add(MessageContent.Text(text))
                }
                "output_text" -> {
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull
                        ?.let(::stripInternalAssistantMetadata)
                        ?: continue
                    val block = if (role == "assistant") {
                        MessageContent.Markdown(text)
                    } else {
                        MessageContent.Text(text)
                    }
                    contentBlocks.add(block)
                }
                // LEGACY_CODEX_FORMAT: old message records used the generic text block type.
                "text" -> {
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull
                        ?.let(::stripInternalAssistantMetadata)
                        ?: continue
                    val block = if (role == "assistant") {
                        MessageContent.Markdown(text)
                    } else {
                        MessageContent.Text(text)
                    }
                    contentBlocks.add(block)
                }
                "input_image", "output_image" -> contentBlocks.add(MessageContent.Text("[image]"))
            }
        }

        if (contentBlocks.isEmpty()) return null

        return when (role) {
            "user" -> ParsedMessage.User(timestamp = timestamp, content = contentBlocks)
            "assistant" -> {
                val presentation = parseAssistantTextPresentation(payload["phase"]?.jsonPrimitive?.contentOrNull)
                ParsedMessage.AssistantText(
                    timestamp = timestamp,
                    content = contentBlocks,
                    displayType = presentation.displayType,
                    style = presentation.style
                )
            }
            "developer", "system" -> null
            else -> null
        }
    }

    private fun parseAssistantTextPresentation(phase: String?): AssistantTextPresentation {
        return when (phase) {
            "commentary" -> AssistantTextPresentation("commentary", ParsedMessage.AssistantTextStyle.STATUS)
            "final_answer" -> AssistantTextPresentation("final_answer", ParsedMessage.AssistantTextStyle.RESULT)
            else -> AssistantTextPresentation("text", ParsedMessage.AssistantTextStyle.DEFAULT)
        }
    }

    private fun extractMessageText(content: JsonElement?): String {
        val contentArray = content as? JsonArray ?: return ""
        return contentArray.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "input_text", "output_text", "text" -> obj["text"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }.joinToString("\n")
    }

    private fun stripInternalAssistantMetadata(text: String): String {
        return text.replace(
            Regex("""\s*<oai-mem-citation>.*?</oai-mem-citation>\s*$""", RegexOption.DOT_MATCHES_ALL),
            ""
        ).trimEnd()
    }

    /**
     * Parses JSONL file content to extract session metadata for the list view.
     * The whole file is scanned so the displayed message count uses the same current-schema,
     * legacy fallback, deduplication, and tool-result connection rules as the detail view.
     */
    fun parseJsonlMetadata(content: String): CodexSessionMetadata {
        return try {
            val lines = content.lines()
            var eventSummary: String? = null
            var responseSummary: String? = null
            var created: String? = null
            var lastTimestamp: String? = null
            var cwd: String? = null
            var originator: String? = null
            var model: String? = null
            var gitBranch: String? = null
            val rawMessages = mutableListOf<MessageRecord>()

            for (line in lines) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("{")) continue

                // Extract timestamp via simple string search
                val timestamp = extractTimestamp(trimmed)
                if (timestamp != null) {
                    if (created == null) created = timestamp
                    lastTimestamp = timestamp
                }

                try {
                    val json = JSON.parseToJsonElement(trimmed).jsonObject
                    val type = json["type"]?.jsonPrimitive?.content

                    when (type) {
                        "session_meta" -> {
                            val payload = json["payload"]?.jsonObject
                            if (payload != null) {
                                cwd = payload["cwd"]?.jsonPrimitive?.content
                                originator = payload["originator"]?.jsonPrimitive?.content
                                val git = payload["git"]?.jsonObject
                                gitBranch = git?.get("branch")?.jsonPrimitive?.content
                            }
                        }
                        "turn_context" -> {
                            val payload = json["payload"]?.jsonObject
                            if (model == null) {
                                model = payload?.get("model")?.jsonPrimitive?.content
                            }
                        }
                        "event_msg" -> {
                            val payload = json["payload"]?.jsonObject
                            if (eventSummary == null) {
                                val payloadType = payload?.get("type")?.jsonPrimitive?.content
                                if (payloadType == "user_message") {
                                    val message = payload["message"]?.jsonPrimitive?.content
                                    if (message != null && message.isNotEmpty()) {
                                        eventSummary = summarizeText(message)
                                    }
                                }
                            }
                            parseEventMessage(payload, timestamp ?: "")?.let {
                                rawMessages.add(MessageRecord(it, MessageSource.EVENT))
                            }
                        }
                        "response_item" -> {
                            val payload = json["payload"]?.jsonObject
                            val payloadType = payload?.get("type")?.jsonPrimitive?.contentOrNull
                            val parsed = parseResponseItem(payloadType, payload, timestamp ?: "")
                            if (parsed != null) {
                                rawMessages.add(MessageRecord(parsed, MessageSource.RESPONSE_ITEM))
                                if (responseSummary == null && parsed is ParsedMessage.User) {
                                    val message = normalizeDeduplicationText(contentAsText(parsed.content))
                                    if (message.isNotEmpty()) {
                                        responseSummary = summarizeText(message)
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Continue
                }
            }

            CodexSessionMetadata(
                summary = eventSummary ?: responseSummary,
                messageCount = connectToolOutputsToToolCalls(deduplicateEventMessages(rawMessages)).size,
                created = created,
                modified = lastTimestamp,
                cwd = cwd,
                originator = originator,
                model = model,
                gitBranch = gitBranch
            )
        } catch (_: Exception) {
            CodexSessionMetadata()
        }
    }

    /**
     * Extracts timestamp from a line using simple string search.
     */
    private fun extractTimestamp(line: String): String? {
        val marker = "\"timestamp\":\""
        val startIndex = line.indexOf(marker)
        if (startIndex == -1) return null

        val valueStart = startIndex + marker.length
        val endIndex = line.indexOf('"', valueStart)
        if (endIndex == -1) return null

        return line.substring(valueStart, endIndex)
    }
}

/**
 * Metadata extracted from parsing a Codex JSONL file.
 */
data class CodexSessionMetadata(
    val summary: String? = null,
    val messageCount: Int? = null,
    val created: String? = null,
    val modified: String? = null,
    val cwd: String? = null,
    val originator: String? = null,
    val model: String? = null,
    val gitBranch: String? = null
)

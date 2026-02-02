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

    /**
     * Parses a Codex session file and returns SessionDetail.
     */
    fun parseFile(file: File): SessionDetail? {
        if (!file.exists()) return null

        return try {
            val content = file.readText()
            val sessionId = CodexSessionFinder.extractSessionId(file) ?: file.nameWithoutExtension
            val (messages, metadata) = parseContent(content)

            val title = messages.filterIsInstance<ParsedMessage.User>().firstOrNull()?.let { userMsg ->
                val text = userMsg.content.filterIsInstance<MessageContent.Text>().joinToString(" ") { it.text }
                    .ifEmpty { userMsg.content.filterIsInstance<MessageContent.Markdown>().joinToString(" ") { it.markdown } }
                text.take(100) + if (text.length > 100) "..." else ""
            } ?: "Untitled"

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
     * Parses Codex .jsonl content and extracts messages with metadata.
     */
    fun parseContent(content: String): Pair<List<ParsedMessage>, SessionMetadata?> {
        val rawMessages = mutableListOf<ParsedMessage>()
        var metadata: SessionMetadata? = null
        var created: String? = null
        var modified: String? = null
        var messageCount = 0
        val modelCounts = mutableMapOf<String, Int>()

        // Map to track function_call by call_id for connecting with outputs
        val functionCallsByCallId = mutableMapOf<String, ParsedMessage.ToolUse>()

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
                        // Skip event_msg - all relevant data is in response_item
                    }

                    "response_item" -> {
                        val payload = json["payload"]?.jsonObject
                        val payloadType = payload?.get("type")?.jsonPrimitive?.content
                        val parsed = parseResponseItem(payloadType, payload, timestamp ?: "")
                        if (parsed != null) {
                            rawMessages.add(parsed)
                            messageCount++

                            // Track ToolUse for later connection with output
                            if (parsed is ParsedMessage.ToolUse && parsed.toolCallId != null) {
                                functionCallsByCallId[parsed.toolCallId] = parsed
                            }
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

        // Post-process: Connect tool outputs to their corresponding tool calls
        val finalMessages = connectToolOutputsToToolCalls(rawMessages)

        val sortedModels = modelCounts.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        metadata = metadata?.copy(
            created = created,
            modified = modified,
            messageCount = messageCount,
            models = sortedModels
        ) ?: SessionMetadata(
            created = created,
            modified = modified,
            messageCount = messageCount,
            models = sortedModels
        )

        return Pair(finalMessages, metadata)
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
                val output = payload["output"]?.jsonPrimitive?.content ?: ""

                ParsedMessage.ToolResult(
                    timestamp = timestamp,
                    toolCallId = callId,
                    output = if (output.isNotEmpty()) {
                        listOf(MessageContent.Code(ToolOutputFormatter.truncateContent(output)))
                    } else {
                        emptyList()
                    }
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
                val outputStr = payload["output"]?.jsonPrimitive?.content ?: ""

                val outputContent = if (outputStr.isNotEmpty()) {
                    try {
                        val outputJson = JSON.parseToJsonElement(outputStr).jsonObject
                        val resultOutput = outputJson["output"]?.jsonPrimitive?.content ?: outputStr
                        listOf(MessageContent.Code(ToolOutputFormatter.truncateContent(resultOutput)))
                    } catch (_: Exception) {
                        listOf(MessageContent.Code(ToolOutputFormatter.truncateContent(outputStr)))
                    }
                } else {
                    emptyList()
                }

                ParsedMessage.ToolResult(
                    timestamp = timestamp,
                    toolCallId = callId,
                    output = outputContent
                )
            }

            "reasoning" -> {
                val summary = payload["summary"]?.jsonArray
                val summaryText = summary?.mapNotNull { item ->
                    item.jsonObject["text"]?.jsonPrimitive?.content
                }?.joinToString("\n") ?: ""

                if (summaryText.isNotEmpty()) {
                    ParsedMessage.AssistantThinking(
                        timestamp = timestamp,
                        thinking = summaryText
                    )
                } else {
                    null
                }
            }

            else -> null
        }
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
                    // Skip system instructions and environment context
                    if (text.contains("<permissions instructions>") ||
                        text.contains("<environment_context>") ||
                        text.contains("# AGENTS.md instructions")) {
                        continue
                    }
                    contentBlocks.add(MessageContent.Text(text))
                }
                "text" -> {
                    val text = obj["text"]?.jsonPrimitive?.content ?: continue
                    contentBlocks.add(MessageContent.Text(text))
                }
            }
        }

        if (contentBlocks.isEmpty()) return null

        return when (role) {
            "user" -> ParsedMessage.User(timestamp = timestamp, content = contentBlocks)
            "developer" -> null  // Skip developer system messages
            else -> ParsedMessage.User(timestamp = timestamp, content = contentBlocks)
        }
    }

    /**
     * Parses JSONL file content to extract session metadata (lightweight, for list view).
     * Only reads the first line to extract metadata.
     */
    fun parseJsonlMetadata(content: String): CodexSessionMetadata {
        return try {
            val lines = content.lines()
            var summary: String? = null
            var messageCount = 0
            var created: String? = null
            var lastTimestamp: String? = null
            var cwd: String? = null
            var originator: String? = null
            var model: String? = null
            var gitBranch: String? = null

            for (line in lines) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("{")) continue

                messageCount++

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
                            if (summary == null) {
                                val payload = json["payload"]?.jsonObject
                                val payloadType = payload?.get("type")?.jsonPrimitive?.content
                                if (payloadType == "user_message") {
                                    val message = payload["message"]?.jsonPrimitive?.content
                                    if (message != null && message.isNotEmpty()) {
                                        summary = if (message.length > 100) message.take(100) + "..." else message
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
                summary = summary,
                messageCount = messageCount,
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

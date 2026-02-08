package de.espend.ml.llm.session.adapter.junie

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.nio.file.Path
import kotlin.io.path.bufferedReader

/**
 * Standalone parser for Junie session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 *
 * Performance: Lines are pre-filtered by string content before JSON parsing.
 * AgentStateUpdatedEvent (~90% of file size) is skipped entirely since it contains
 * a huge serialized blob not useful for display.
 *
 * Events come in IN_PROGRESS/COMPLETED pairs by stepId. We deduplicate and keep only COMPLETED.
 */
object JunieSessionParser {

    internal val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Event kinds that are expensive to parse but not needed for display.
     * These are skipped via cheap string check before JSON parsing.
     */
    private val SKIP_KINDS = setOf(
        "AgentStateUpdatedEvent",
        "AgentCurrentStatusUpdatedEvent",
        "AgentPatchCreatedEvent",
    )

    /**
     * Step-based event kinds that come in IN_PROGRESS/COMPLETED pairs.
     */
    private val STEP_KINDS = setOf(
        "ToolBlockUpdatedEvent",
        "TerminalBlockUpdatedEvent",
        "ViewFilesBlockUpdatedEvent",
        "FileChangesBlockUpdatedEvent",
        "ResultBlockUpdatedEvent",
    )

    private val CD_PATH_REGEX = """^cd\s+(/[^\s&;]+)""".toRegex()

    /**
     * Parses a Junie session by ID and returns SessionDetail.
     */
    fun parseSession(sessionId: String): SessionDetail? {
        val eventsFile = JunieSessionFinder.findSessionFile(sessionId) ?: return null
        return parseFile(eventsFile, sessionId)
    }

    /**
     * Parses a Junie events.jsonl file using streaming (line-by-line).
     */
    fun parseFile(file: Path, sessionId: String? = null): SessionDetail? {
        return try {
            val id = sessionId ?: file.parent.fileName.toString()
            val (messages, metadata) = parseReader(file.bufferedReader())

            val title = getSessionTitle(id)
                ?: messages.filterIsInstance<ParsedMessage.User>().firstOrNull()?.let { userMsg ->
                    val text = userMsg.content.filterIsInstance<MessageContent.Text>().firstOrNull()?.text ?: "Untitled"
                    text.take(100) + if (text.length > 100) "..." else ""
                }
                ?: "Untitled"

            SessionDetail(
                sessionId = id,
                title = title,
                messages = messages,
                metadata = metadata
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gets the session title from index.jsonl.
     */
    private fun getSessionTitle(sessionId: String): String? {
        val sessions = JunieSessionFinder.listSessions()
        return sessions.find { it.sessionId == sessionId }?.taskName
    }

    /**
     * Parses Junie events.jsonl content from a string (for tests).
     */
    fun parseContent(content: String): Pair<List<ParsedMessage>, SessionMetadata?> {
        return parseReader(content.reader().buffered())
    }

    /**
     * The agentEvent kind string appears at ~position 80 in each line.
     * We peek at the first PEEK_SIZE chars to decide whether to read the full line.
     * This avoids allocating 40-400KB strings for AgentStateUpdatedEvent lines (~90% of file).
     */
    private const val PEEK_SIZE = 512

    /**
     * Core parser: streams lines from a reader, peeking at line starts to skip expensive events.
     * Two-pass approach:
     * 1. Read lines (skipping huge ones via peek), deduplicate step events (keep COMPLETED)
     * 2. Build messages in chronological order
     */
    private fun parseReader(reader: BufferedReader): Pair<List<ParsedMessage>, SessionMetadata?> {
        val modelCounts = mutableMapOf<String, Int>()
        var cwd: String? = null

        // Parsed event: kind + json data (either full line json or just agentEvent)
        data class ParsedEvent(val kind: String, val json: JsonObject)

        // Step dedup: stepId -> latest (COMPLETED) event
        val stepEvents = mutableMapOf<String, ParsedEvent>()

        // All events in file order (non-step events inline, step events as placeholders)
        data class EventSlot(val kind: String, val json: JsonObject?, val stepId: String?)
        val slots = mutableListOf<EventSlot>()
        val seenStepIds = mutableSetOf<String>()

        reader.use { br ->
            while (true) {
                // Peek at the start of each line to decide if we need the full line
                br.mark(PEEK_SIZE)
                val peekBuf = CharArray(PEEK_SIZE)
                val peekRead = br.read(peekBuf, 0, PEEK_SIZE)
                if (peekRead <= 0) break
                br.reset()

                val peek = String(peekBuf, 0, peekRead)

                // Check if this line should be skipped entirely (don't allocate full line)
                val shouldSkip = peek.startsWith("{") && SKIP_KINDS.any { peek.contains(it) }

                if (shouldSkip) {
                    // Discard the entire line without loading it into a String
                    skipLine(br)
                    continue
                }

                // Read the full line (only for lines we actually need)
                val line = br.readLine() ?: break
                val trimmed = line.trim()
                if (!trimmed.startsWith("{")) continue

                try {
                    val json = JSON.parseToJsonElement(trimmed).jsonObject
                    val topKind = json["kind"]?.jsonPrimitive?.content

                    when (topKind) {
                        "UserPromptEvent" -> {
                            slots.add(EventSlot("UserPromptEvent", json, null))
                        }
                        "SessionA2uxEvent" -> {
                            val event = json["event"]?.jsonObject ?: continue
                            val agentEvent = event["agentEvent"]?.jsonObject ?: continue
                            val agentEventKind = agentEvent["kind"]?.jsonPrimitive?.content ?: continue
                            val stepId = agentEvent["stepId"]?.jsonPrimitive?.content

                            // Extract cwd from first terminal cd command
                            if (cwd == null && agentEventKind == "TerminalBlockUpdatedEvent") {
                                val command = agentEvent["command"]?.jsonPrimitive?.content
                                if (command != null) {
                                    cwd = CD_PATH_REGEX.find(command)?.groupValues?.get(1)
                                }
                            }

                            if (stepId != null && agentEventKind in STEP_KINDS) {
                                // Step event: dedup by keeping latest (COMPLETED overwrites IN_PROGRESS)
                                stepEvents[stepId] = ParsedEvent(agentEventKind, agentEvent)

                                // Add placeholder slot on first occurrence only
                                if (seenStepIds.add(stepId)) {
                                    slots.add(EventSlot(agentEventKind, null, stepId))
                                }
                            } else {
                                // Non-step event (e.g. LlmResponseMetadataEvent, AgentFailureEvent)
                                slots.add(EventSlot(agentEventKind, json, null))
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Skip unparseable lines
                }
            }
        }

        // Build messages from slots (resolving step placeholders to their COMPLETED version)
        val messages = mutableListOf<ParsedMessage>()

        for (slot in slots) {
            try {
                val json = if (slot.stepId != null) {
                    stepEvents[slot.stepId]?.json ?: continue
                } else {
                    slot.json ?: continue
                }
                val isDirectAgentEvent = slot.stepId != null

                when (slot.kind) {
                    "UserPromptEvent" -> {
                        val prompt = json["prompt"]?.jsonPrimitive?.content
                        if (prompt != null) {
                            messages.add(ParsedMessage.User(
                                timestamp = "",
                                content = listOf(MessageContent.Text(prompt))
                            ))
                        }
                    }
                    "LlmResponseMetadataEvent" -> {
                        val agentEvent = if (isDirectAgentEvent) json
                            else json["event"]?.jsonObject?.get("agentEvent")?.jsonObject ?: continue
                        val modelUsage = agentEvent["modelUsage"]?.jsonArray
                        modelUsage?.forEach { usage ->
                            val model = usage.jsonObject["model"]?.jsonPrimitive?.content
                            if (model != null) {
                                modelCounts[model] = (modelCounts[model] ?: 0) + 1
                            }
                        }
                    }
                    "ToolBlockUpdatedEvent" -> {
                        val agentEvent = if (isDirectAgentEvent) json
                            else json["event"]?.jsonObject?.get("agentEvent")?.jsonObject ?: continue
                        val text = agentEvent["text"]?.jsonPrimitive?.content
                        val details = agentEvent["details"]?.jsonPrimitive?.content

                        val inputMap = mutableMapOf<String, String>()
                        if (text != null) inputMap["action"] = text

                        val results = if (!details.isNullOrBlank()) {
                            listOf(ParsedMessage.ToolResult(
                                timestamp = "",
                                toolName = "tool",
                                output = listOf(MessageContent.Code(details)),
                                isError = false
                            ))
                        } else emptyList()

                        messages.add(ParsedMessage.ToolUse(
                            timestamp = "",
                            toolName = "tool",
                            input = inputMap,
                            results = results
                        ))
                    }
                    "TerminalBlockUpdatedEvent" -> {
                        val agentEvent = if (isDirectAgentEvent) json
                            else json["event"]?.jsonObject?.get("agentEvent")?.jsonObject ?: continue
                        val command = agentEvent["command"]?.jsonPrimitive?.content
                        val output = agentEvent["output"]?.jsonPrimitive?.content

                        val inputMap = mutableMapOf<String, String>()
                        if (command != null) inputMap["command"] = command

                        val results = if (!output.isNullOrBlank()) {
                            listOf(ParsedMessage.ToolResult(
                                timestamp = "",
                                toolName = "terminal",
                                output = listOf(MessageContent.Code(output)),
                                isError = false
                            ))
                        } else emptyList()

                        messages.add(ParsedMessage.ToolUse(
                            timestamp = "",
                            toolName = "terminal",
                            input = inputMap,
                            results = results
                        ))
                    }
                    "ViewFilesBlockUpdatedEvent" -> {
                        val agentEvent = if (isDirectAgentEvent) json
                            else json["event"]?.jsonObject?.get("agentEvent")?.jsonObject ?: continue
                        val files = agentEvent["files"]?.jsonArray
                        val filePaths = files?.mapNotNull { f ->
                            f.jsonObject["relativePath"]?.jsonPrimitive?.content
                        }?.joinToString(", ")

                        messages.add(ParsedMessage.Info(
                            timestamp = "",
                            title = "Opened file",
                            content = if (filePaths != null) MessageContent.Text(filePaths) else null
                        ))
                    }
                    "FileChangesBlockUpdatedEvent" -> {
                        val agentEvent = if (isDirectAgentEvent) json
                            else json["event"]?.jsonObject?.get("agentEvent")?.jsonObject ?: continue
                        val changes = agentEvent["changes"]?.jsonArray ?: continue

                        changes.forEach { change ->
                            val changeObj = change.jsonObject
                            val filePath = changeObj["afterRelativePath"]?.jsonPrimitive?.content
                                ?: changeObj["beforeRelativePath"]?.jsonPrimitive?.content
                                ?: return@forEach

                            messages.add(ParsedMessage.Info(
                                timestamp = "",
                                title = "Edited file",
                                content = MessageContent.Text(filePath)
                            ))
                        }
                    }
                    "ResultBlockUpdatedEvent" -> {
                        val agentEvent = if (isDirectAgentEvent) json
                            else json["event"]?.jsonObject?.get("agentEvent")?.jsonObject ?: continue
                        val cancelled = agentEvent["cancelled"]?.jsonPrimitive?.content == "true"
                        val result = agentEvent["result"]?.jsonPrimitive?.content

                        if (!cancelled && !result.isNullOrBlank() && result != "Empty") {
                            messages.add(ParsedMessage.AssistantText(
                                timestamp = "",
                                content = listOf(MessageContent.Markdown(result))
                            ))
                        }
                    }
                    "AgentFailureEvent" -> {
                        val agentEvent = if (isDirectAgentEvent) json
                            else json["event"]?.jsonObject?.get("agentEvent")?.jsonObject ?: continue
                        val message = agentEvent["message"]?.jsonPrimitive?.content

                        if (!message.isNullOrBlank()) {
                            messages.add(ParsedMessage.Info(
                                timestamp = "",
                                title = "Error",
                                content = MessageContent.Text(message),
                                style = ParsedMessage.InfoStyle.ERROR
                            ))
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip
            }
        }

        val metadata = SessionMetadata(
            cwd = cwd,
            messageCount = messages.size,
            models = modelCounts.entries.sortedByDescending { it.value }.map { it.key to it.value }
        )

        return Pair(messages, metadata)
    }

    /**
     * Skips the rest of the current line without allocating a String.
     * Reads and discards chars until newline or EOF.
     */
    private fun skipLine(reader: BufferedReader) {
        while (true) {
            val ch = reader.read()
            if (ch == -1 || ch == '\n'.code) break
        }
    }
}

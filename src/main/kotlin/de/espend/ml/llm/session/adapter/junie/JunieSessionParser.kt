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
import java.io.PushbackReader
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.bufferedReader

/**
 * Standalone parser for Junie session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 *
 * Performance: A short line prefix is classified before the complete line is read.
 * Only event kinds used by this parser are materialized and parsed as JSON. This is
 * important because ignored AgentStateUpdatedEvent lines can exceed one megabyte.
 *
 * Events come in IN_PROGRESS/COMPLETED pairs by stepId. We deduplicate and keep only COMPLETED.
 */
object JunieSessionParser {

    internal val JSON = Json { ignoreUnknownKeys = true }

    private val RELEVANT_TOP_LEVEL_KINDS = setOf(
        "UserPromptEvent",
        "SystemMessageEvent",
    )

    /**
     * Allowlist of agent events that contribute messages or session metadata.
     * Every other agent event is discarded after reading only its short prefix.
     */
    private val RELEVANT_AGENT_EVENT_KINDS = setOf(
        "LlmResponseMetadataEvent",
        "EnvironmentVariablesUpdatedEvent",
        "AgentThoughtBlockUpdatedEvent",
        "AgentPlanUpdatedEvent",
        "ToolBlockUpdatedEvent",
        "TerminalBlockUpdatedEvent",
        "ViewFilesBlockUpdatedEvent",
        "FileChangesBlockUpdatedEvent",
        "ResultBlockUpdatedEvent",
        "AgentFailureEvent",
    )

    private const val TOP_LEVEL_KIND_PREFIX = "{\"kind\":\""
    private const val SESSION_EVENT_KIND = "SessionA2uxEvent"
    private const val AGENT_EVENT_KIND_PREFIX = "\"agentEvent\":{\"kind\":\""

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

    private val JUNIE_VERSION_PATH_MARKERS = listOf(
        "/junie/versions/",
        "\\junie\\versions\\",
    )

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
            val (messages, parsedMetadata) = parseReader(file.bufferedReader())
            val session = JunieSessionFinder.listSessions().find { it.sessionId == id }
            val metadata = (parsedMetadata ?: SessionMetadata()).copy(
                created = session?.createdAt
                    ?.takeIf { it > 0 }
                    ?.let { Instant.ofEpochMilli(it).toString() },
                modified = session?.updatedAt
                    ?.takeIf { it > 0 }
                    ?.let { Instant.ofEpochMilli(it).toString() }
            )

            val title = session?.taskName
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
     * Parses Junie events.jsonl content from a string (for tests).
     */
    fun parseContent(content: String): Pair<List<ParsedMessage>, SessionMetadata?> {
        return parseReader(content.reader().buffered())
    }

    /**
     * Junie's compact JSON writer puts the agentEvent kind near the start of each line.
     * We peek at the first PEEK_SIZE chars to decide whether to read the full line.
     * This avoids allocating strings of up to several megabytes for ignored state events.
     */
    private const val PEEK_SIZE = 512
    private const val READ_BUFFER_SIZE = 16 * 1024

    /**
     * Core parser: streams lines from a reader, peeking at line starts to skip expensive events.
     * Two-pass approach:
     * 1. Read lines (skipping huge ones via peek), deduplicate step events (keep COMPLETED)
     * 2. Build messages in chronological order
     */
    private fun parseReader(reader: BufferedReader): Pair<List<ParsedMessage>, SessionMetadata?> {
        val modelCounts = mutableMapOf<String, Int>()
        var version: String? = null

        data class StepEventKey(val kind: String, val stepId: String)

        // A single Junie step can emit multiple block kinds with the same stepId.
        val stepEvents = mutableMapOf<StepEventKey, JsonObject>()

        // All events in file order (non-step events inline, step events as placeholders)
        data class EventSlot(val kind: String, val json: JsonObject?, val stepKey: StepEventKey?)
        val slots = mutableListOf<EventSlot>()
        val seenStepKeys = mutableSetOf<StepEventKey>()

        PushbackReader(reader, READ_BUFFER_SIZE).use { input ->
            val readBuffer = CharArray(READ_BUFFER_SIZE)
            while (true) {
                val line = readNextRelevantLine(input, readBuffer) ?: break

                try {
                    val json = JSON.parseToJsonElement(line).jsonObject
                    val topKind = json["kind"]?.jsonPrimitive?.content

                    when (topKind) {
                        "UserPromptEvent" -> {
                            slots.add(EventSlot("UserPromptEvent", json, null))
                        }
                        "SystemMessageEvent" -> {
                            slots.add(EventSlot("SystemMessageEvent", json, null))
                        }
                        "SessionA2uxEvent" -> {
                            val event = json["event"]?.jsonObject ?: continue
                            val agentEvent = event["agentEvent"]?.jsonObject ?: continue
                            val agentEventKind = agentEvent["kind"]?.jsonPrimitive?.content ?: continue
                            val stepId = agentEvent["stepId"]?.jsonPrimitive?.content

                            if (agentEventKind == "EnvironmentVariablesUpdatedEvent") {
                                if (version == null) {
                                    version = agentEvent["env"]?.jsonArray
                                        ?.firstNotNullOfOrNull { entry ->
                                            val value = entry.jsonObject["value"]?.jsonPrimitive?.content
                                                ?: return@firstNotNullOfOrNull null
                                            extractJunieVersion(value)
                                        }
                                }
                                continue
                            }

                            if (agentEventKind == "LlmResponseMetadataEvent") {
                                agentEvent["modelUsage"]?.jsonArray?.forEach { usage ->
                                    val model = usage.jsonObject["model"]?.jsonPrimitive?.content
                                    if (model != null) {
                                        modelCounts[model] = (modelCounts[model] ?: 0) + 1
                                    }
                                }
                                continue
                            }

                            if (stepId != null && agentEventKind in STEP_KINDS) {
                                val stepKey = StepEventKey(agentEventKind, stepId)
                                // Step event: dedup by keeping latest (COMPLETED overwrites IN_PROGRESS)
                                stepEvents[stepKey] = agentEvent

                                // Add placeholder slot on first occurrence only
                                if (seenStepKeys.add(stepKey)) {
                                    slots.add(EventSlot(agentEventKind, null, stepKey))
                                }
                            } else {
                                slots.add(EventSlot(agentEventKind, agentEvent, null))
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
                val json = if (slot.stepKey != null) {
                    stepEvents[slot.stepKey] ?: continue
                } else {
                    slot.json ?: continue
                }

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
                    "SystemMessageEvent" -> {
                        val text = json["text"]?.jsonPrimitive?.content
                        val level = json["level"]?.jsonPrimitive?.content
                        if (!text.isNullOrBlank()) {
                            messages.add(ParsedMessage.Info(
                                timestamp = "",
                                title = "system",
                                subtitle = level?.lowercase(),
                                content = MessageContent.Text(text),
                                style = if (level == "ERROR") {
                                    ParsedMessage.InfoStyle.ERROR
                                } else {
                                    ParsedMessage.InfoStyle.DEFAULT
                                }
                            ))
                        }
                    }
                    "AgentThoughtBlockUpdatedEvent" -> {
                        val agentEvent = json
                        val text = agentEvent["text"]?.jsonPrimitive?.content
                        if (!text.isNullOrBlank()) {
                            messages.add(ParsedMessage.AssistantThinking(
                                timestamp = "",
                                thinking = text
                            ))
                        }
                    }
                    "AgentPlanUpdatedEvent" -> {
                        val agentEvent = json
                        val plan = agentEvent["items"]?.jsonArray
                            ?.mapNotNull { item ->
                                val itemObject = item.jsonObject
                                val description = itemObject["description"]?.jsonPrimitive?.content
                                    ?.takeIf { it.isNotBlank() }
                                    ?: return@mapNotNull null
                                val prefix = when (itemObject["status"]?.jsonPrimitive?.content) {
                                    "DONE" -> "- [x] "
                                    "IN_PROGRESS" -> "- [ ] **In progress:** "
                                    else -> "- [ ] "
                                }
                                prefix + description
                            }
                            ?.joinToString("\n")

                        if (!plan.isNullOrBlank()) {
                            messages.add(ParsedMessage.AssistantText(
                                timestamp = "",
                                content = listOf(MessageContent.Markdown(plan)),
                                displayType = "plan",
                                style = ParsedMessage.AssistantTextStyle.STATUS
                            ))
                        }
                    }
                    "ToolBlockUpdatedEvent" -> {
                        val agentEvent = json
                        val text = agentEvent["text"]?.jsonPrimitive?.content
                        val details = agentEvent["details"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: agentEvent["output"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

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
                        val agentEvent = json
                        val command = agentEvent["command"]?.jsonPrimitive?.content
                        val output = agentEvent["presentableOutput"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: agentEvent["output"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: agentEvent["details"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

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
                        val agentEvent = json
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
                        val agentEvent = json
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
                        val agentEvent = json
                        val cancelled = agentEvent["cancelled"]?.jsonPrimitive?.content == "true"
                        val result = agentEvent["result"]?.jsonPrimitive?.content

                        if (!cancelled && !result.isNullOrBlank() && result != "Empty") {
                            messages.add(ParsedMessage.AssistantText(
                                timestamp = "",
                                content = listOf(MessageContent.Markdown(result)),
                                displayType = "final_answer",
                                style = ParsedMessage.AssistantTextStyle.RESULT
                            ))
                        }
                    }
                    "AgentFailureEvent" -> {
                        val agentEvent = json
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
            version = version,
            messageCount = messages.size,
            models = modelCounts.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key to it.value }
        )

        return Pair(messages, metadata)
    }

    /**
     * Reads the next supported event line. Ignored lines are consumed in fixed-size chunks,
     * without ever being assembled into a complete String.
     */
    private fun readNextRelevantLine(reader: PushbackReader, readBuffer: CharArray): String? {
        while (true) {
            val prefix = StringBuilder(PEEK_SIZE)
            var lineEnded = false
            var endOfFile = false

            while (prefix.length < PEEK_SIZE) {
                val ch = reader.read()
                if (ch == -1) {
                    endOfFile = true
                    break
                }
                if (ch == '\n'.code) {
                    lineEnded = true
                    break
                }
                prefix.append(ch.toChar())
            }

            if (prefix.isEmpty() && endOfFile) return null

            if (!isRelevantLinePrefix(prefix.toString())) {
                if (!lineEnded && !endOfFile) {
                    consumeLineRemainder(reader, readBuffer, null)
                }
                continue
            }

            if (lineEnded || endOfFile) return prefix.toString()

            consumeLineRemainder(reader, readBuffer, prefix)
            return prefix.toString()
        }
    }

    /**
     * Junie writes compact JSONL with both kind fields near the beginning. Plain string
     * markers keep this hot-path classification cheap and prevent message text from being
     * mistaken for an event kind.
     */
    internal fun isRelevantLinePrefix(prefix: String): Boolean {
        val topLevelKind = extractKind(prefix, TOP_LEVEL_KIND_PREFIX, requireAtStart = true)
            ?: return false

        if (topLevelKind in RELEVANT_TOP_LEVEL_KINDS) return true
        if (topLevelKind != SESSION_EVENT_KIND) return false

        val agentEventKind = extractKind(prefix, AGENT_EVENT_KIND_PREFIX)
            ?: return false
        return agentEventKind in RELEVANT_AGENT_EVENT_KINDS
    }

    private fun extractKind(value: String, marker: String, requireAtStart: Boolean = false): String? {
        val markerIndex = if (requireAtStart) {
            if (value.startsWith(marker)) 0 else return null
        } else {
            value.indexOf(marker).takeIf { it >= 0 } ?: return null
        }
        val valueStart = markerIndex + marker.length
        val valueEnd = value.indexOf('\"', valueStart)
        if (valueEnd < 0) return null
        return value.substring(valueStart, valueEnd)
    }

    private fun consumeLineRemainder(
        reader: PushbackReader,
        readBuffer: CharArray,
        target: StringBuilder?
    ) {
        while (true) {
            val read = reader.read(readBuffer)
            if (read < 0) return

            var newlineIndex = 0
            while (newlineIndex < read && readBuffer[newlineIndex] != '\n') {
                newlineIndex++
            }

            target?.append(readBuffer, 0, newlineIndex)
            if (newlineIndex == read) continue

            val remainderStart = newlineIndex + 1
            val remainderLength = read - remainderStart
            if (remainderLength > 0) {
                reader.unread(readBuffer, remainderStart, remainderLength)
            }
            return
        }
    }

    private fun extractJunieVersion(value: String): String? {
        val markerMatch = JUNIE_VERSION_PATH_MARKERS.firstNotNullOfOrNull { marker ->
            value.indexOf(marker)
                .takeIf { it >= 0 }
                ?.let { it to marker }
        } ?: return null

        val versionStart = markerMatch.first + markerMatch.second.length
        var versionEnd = versionStart
        while (versionEnd < value.length && !isVersionSeparator(value[versionEnd])) {
            versionEnd++
        }
        return value.substring(versionStart, versionEnd).takeIf { it.isNotBlank() }
    }

    private fun isVersionSeparator(char: Char): Boolean {
        return char == '/' || char == '\\' || char == ':' || char == ';'
    }
}

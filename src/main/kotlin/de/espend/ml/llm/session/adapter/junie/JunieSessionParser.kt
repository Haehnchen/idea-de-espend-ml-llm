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
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Standalone parser for Junie session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 *
 * Event structure (fields are directly on agentEvent, not nested):
 * - ToolBlockUpdatedEvent: stepId, text, status, details
 * - TerminalBlockUpdatedEvent: stepId, status, command, output, presentableOutput, details
 * - ViewFilesBlockUpdatedEvent: stepId, status, files[{relativePath}]
 * - FileChangesBlockUpdatedEvent: stepId, status, changes[{beforeRelativePath, afterRelativePath, beforeContent, afterContent}]
 * - ResultBlockUpdatedEvent: stepId, cancelled, result, changes[], errorCode
 * - LlmResponseMetadataEvent: modelUsage[{model, cost, inputTokens, outputTokens}]
 * - AgentStateUpdatedEvent: blob (serialized JSON string - not useful for display)
 *
 * Events come in IN_PROGRESS/COMPLETED pairs by stepId. We deduplicate and keep only COMPLETED.
 */
object JunieSessionParser {

    internal val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Parses a Junie session by ID and returns SessionDetail.
     */
    fun parseSession(sessionId: String): SessionDetail? {
        val eventsFile = JunieSessionFinder.findSessionFile(sessionId) ?: return null
        return parseFile(eventsFile, sessionId)
    }

    /**
     * Parses a Junie events.jsonl file.
     */
    fun parseFile(file: Path, sessionId: String? = null): SessionDetail? {
        return try {
            val content = file.readText()
            val id = sessionId ?: file.parent.fileName.toString()
            val (messages, metadata) = parseContent(content)

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
     * Parses Junie events.jsonl content.
     */
    fun parseContent(content: String): Pair<List<ParsedMessage>, SessionMetadata?> {
        val lines = content.lines()
        val modelCounts = mutableMapOf<String, Int>()

        // First pass: collect events, deduplicating by stepId (keep COMPLETED over IN_PROGRESS)
        data class EventEntry(val agentEvent: JsonObject, val kind: String, val stepId: String?, val status: String?)
        val stepEvents = mutableMapOf<String, EventEntry>()  // stepId -> latest event
        val orderedStepIds = mutableListOf<String>()          // preserve insertion order
        val nonStepEvents = mutableListOf<Pair<String, JsonObject>>()  // kind -> full json for events without stepId

        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) continue

            try {
                val json = JSON.parseToJsonElement(trimmed).jsonObject
                val topKind = json["kind"]?.jsonPrimitive?.content

                when (topKind) {
                    "UserPromptEvent" -> {
                        nonStepEvents.add("UserPromptEvent" to json)
                    }
                    "SessionA2uxEvent" -> {
                        val event = json["event"]?.jsonObject ?: continue
                        val agentEvent = event["agentEvent"]?.jsonObject ?: continue
                        val agentEventKind = agentEvent["kind"]?.jsonPrimitive?.content ?: continue
                        val stepId = agentEvent["stepId"]?.jsonPrimitive?.content
                        val status = agentEvent["status"]?.jsonPrimitive?.content
                            ?: event["state"]?.jsonPrimitive?.content

                        if (stepId != null && agentEventKind in setOf(
                                "ToolBlockUpdatedEvent", "TerminalBlockUpdatedEvent",
                                "ViewFilesBlockUpdatedEvent", "FileChangesBlockUpdatedEvent",
                                "ResultBlockUpdatedEvent"
                            )) {
                            val entry = EventEntry(agentEvent, agentEventKind, stepId, status)
                            if (!stepEvents.containsKey(stepId)) {
                                orderedStepIds.add(stepId)
                            }
                            // Always overwrite: COMPLETED comes after IN_PROGRESS
                            stepEvents[stepId] = entry
                        } else {
                            nonStepEvents.add(agentEventKind to json)
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip unparseable lines
            }
        }

        // Second pass: build messages in order
        val messages = mutableListOf<ParsedMessage>()
        var stepIndex = 0
        var nonStepIndex = 0

        // Interleave: process nonStepEvents, inserting step events at proper positions
        // Since events are ordered chronologically in the file, we process them in file order.
        // Rebuild in file order by replaying all lines
        val allEvents = mutableListOf<Pair<String, JsonObject>>()
        val seenSteps = mutableSetOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) continue

            try {
                val json = JSON.parseToJsonElement(trimmed).jsonObject
                val topKind = json["kind"]?.jsonPrimitive?.content

                when (topKind) {
                    "UserPromptEvent" -> {
                        allEvents.add("UserPromptEvent" to json)
                    }
                    "SessionA2uxEvent" -> {
                        val event = json["event"]?.jsonObject ?: continue
                        val agentEvent = event["agentEvent"]?.jsonObject ?: continue
                        val agentEventKind = agentEvent["kind"]?.jsonPrimitive?.content ?: continue
                        val stepId = agentEvent["stepId"]?.jsonPrimitive?.content

                        if (stepId != null && stepId in stepEvents) {
                            if (stepId !in seenSteps) {
                                // First occurrence of this stepId - will emit the COMPLETED version
                                seenSteps.add(stepId)
                                val completed = stepEvents[stepId]!!
                                allEvents.add(completed.kind to wrapAsSessionEvent(completed.agentEvent))
                            }
                            // Skip duplicate occurrences
                        } else {
                            allEvents.add(agentEventKind to json)
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip
            }
        }

        // Now process all events in order
        for ((kind, json) in allEvents) {
            try {
                when (kind) {
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
                        val event = json["event"]?.jsonObject
                        val agentEvent = event?.get("agentEvent")?.jsonObject ?: continue
                        val modelUsage = agentEvent["modelUsage"]?.jsonArray
                        modelUsage?.forEach { usage ->
                            val model = usage.jsonObject["model"]?.jsonPrimitive?.content
                            if (model != null) {
                                modelCounts[model] = (modelCounts[model] ?: 0) + 1
                            }
                        }
                    }
                    "ToolBlockUpdatedEvent" -> {
                        val agentEvent = extractAgentEvent(json) ?: continue
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
                        val agentEvent = extractAgentEvent(json) ?: continue
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
                        val agentEvent = extractAgentEvent(json) ?: continue
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
                        val agentEvent = extractAgentEvent(json) ?: continue
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
                        val agentEvent = extractAgentEvent(json) ?: continue
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
                        val event = json["event"]?.jsonObject
                        val agentEvent = event?.get("agentEvent")?.jsonObject ?: continue
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
            messageCount = messages.size,
            models = modelCounts.entries.sortedByDescending { it.value }.map { it.key to it.value }
        )

        return Pair(messages, metadata)
    }

    /**
     * Extracts agentEvent from a json object.
     * Handles both direct agentEvent objects and wrapped SessionA2uxEvent.
     */
    private fun extractAgentEvent(json: JsonObject): JsonObject? {
        // If this is already the agentEvent (from our dedup wrapper)
        if (json.containsKey("kind") && !json.containsKey("event") && !json.containsKey("prompt")) {
            return json
        }
        // Standard SessionA2uxEvent wrapper
        return json["event"]?.jsonObject?.get("agentEvent")?.jsonObject
    }

    /**
     * Wraps an agentEvent JsonObject back into a pseudo-SessionA2uxEvent for uniform processing.
     */
    private fun wrapAsSessionEvent(agentEvent: JsonObject): JsonObject {
        // Return the agentEvent directly - extractAgentEvent handles both formats
        return agentEvent
    }
}

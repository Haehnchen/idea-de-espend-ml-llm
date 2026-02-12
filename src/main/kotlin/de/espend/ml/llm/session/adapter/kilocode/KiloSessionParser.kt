package de.espend.ml.llm.session.adapter.kilocode

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import kotlinx.serialization.json.*
import java.io.File

/**
 * Standalone parser for Kilo Code CLI session files.
 * Parses ui_messages.json and api_conversation_history.json into unified SessionDetail format.
 * No IntelliJ dependencies.
 */
object KiloSessionParser {

    private val JSON = Json { ignoreUnknownKeys = true }
    private val MODEL_TAG_REGEX = Regex("<model>([^<]+)</model>")

    /**
     * Parse a Kilo CLI session from task directory.
     */
    fun parseSession(taskPath: String, sessionId: String?): SessionDetail? {
        val taskDir = File(taskPath)
        val uiMessagesFile = File(taskDir, "ui_messages.json")
        if (!uiMessagesFile.exists()) return null

        return try {
            val uiMessages = JSON.parseToJsonElement(uiMessagesFile.readText()).jsonArray
            val apiHistoryFile = File(taskDir, "api_conversation_history.json")
            val apiHistory = if (apiHistoryFile.exists()) {
                JSON.parseToJsonElement(apiHistoryFile.readText()).jsonArray
            } else {
                JsonArray(emptyList())
            }
            val metadataFile = File(taskDir, "task_metadata.json")
            val metadata = if (metadataFile.exists()) {
                JSON.parseToJsonElement(metadataFile.readText()).jsonObject
            } else {
                JsonObject(emptyMap())
            }

            val taskId = taskDir.name
            val finalSessionId = sessionId ?: taskId

            val (messages, sessionMetadata) = parseContent(uiMessages, apiHistory, metadata, taskPath)
            val title = extractTitle(uiMessages, apiHistory) ?: "Kilo Session ${finalSessionId.take(8)}"

            SessionDetail(
                sessionId = finalSessionId,
                title = title,
                messages = messages,
                metadata = sessionMetadata
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse content from UI messages and API history.
     */
    fun parseContent(
        uiMessages: JsonArray,
        apiHistory: JsonArray,
        metadata: JsonObject,
        taskPath: String
    ): Pair<List<ParsedMessage>, SessionMetadata> {
        val messages = mutableListOf<ParsedMessage>()
        var firstTimestamp: Long? = null
        var lastTimestamp: Long? = null

        // Track tool uses from API history to avoid duplicates
        val apiToolCallIds = mutableSetOf<String>()

        // Process API history first for tool uses
        for (apiMsgElement in apiHistory) {
            val apiMsg = apiMsgElement.jsonObject
            val role = apiMsg["role"]?.jsonPrimitive?.content ?: continue

            if (role == "assistant") {
                val contentField = apiMsg["content"]
                val contentArray = when {
                    contentField is JsonArray -> contentField
                    else -> continue
                }

                for (item in contentArray) {
                    val itemObj = item.jsonObject
                    if (itemObj["type"]?.jsonPrimitive?.content == "tool_use") {
                        val id = itemObj["id"]?.jsonPrimitive?.content ?: continue
                        val name = itemObj["name"]?.jsonPrimitive?.content ?: "unknown"
                        val inputElement = itemObj["input"]?.jsonObject
                        val inputMap = mutableMapOf<String, String>()

                        inputElement?.entries?.forEach { (key, value) ->
                            inputMap[key] = when {
                                value is JsonPrimitive && value.isString -> value.content
                                else -> value.toString()
                            }
                        }

                        messages.add(ParsedMessage.ToolUse(
                            timestamp = "",
                            toolName = name,
                            toolCallId = id,
                            input = inputMap,
                            results = emptyList()
                        ))
                        apiToolCallIds.add(id)
                    }
                }
            }
        }

        // Process UI messages
        for (uiMsgElement in uiMessages) {
            val uiMsg = uiMsgElement.jsonObject
            val ts = uiMsg["ts"]?.jsonPrimitive?.longOrNull

            if (ts != null) {
                if (firstTimestamp == null) firstTimestamp = ts
                lastTimestamp = ts
            }

            val parsed = parseUiMessage(uiMsg)
            if (parsed != null) {
                // Skip UI tool uses that we already have from API history
                if (parsed is ParsedMessage.ToolUse && parsed.toolCallId != null && apiToolCallIds.contains(parsed.toolCallId)) {
                    continue
                }
                messages.add(parsed)
            }
        }

        // Extract model from API history
        val model = extractModelFromApiHistory(apiHistory)

        val sessionMetadata = SessionMetadata(
            cwd = extractWorkspace(metadata),
            models = if (model != null) listOf(model to 1) else emptyList(),
            messageCount = messages.size,
            created = firstTimestamp?.let { java.time.Instant.ofEpochMilli(it).toString() },
            modified = lastTimestamp?.let { java.time.Instant.ofEpochMilli(it).toString() }
        )

        return Pair(messages, sessionMetadata)
    }

    private fun parseUiMessage(uiMsg: JsonObject): ParsedMessage? {
        val ts = uiMsg["ts"]?.jsonPrimitive?.longOrNull
        val timestamp = if (ts != null) java.time.Instant.ofEpochMilli(ts).toString() else ""
        val type = uiMsg["type"]?.jsonPrimitive?.content ?: return null

        return when (type) {
            "say" -> parseSayMessage(uiMsg, timestamp)
            "ask" -> parseAskMessage(uiMsg, timestamp)
            else -> null
        }
    }

    private fun parseSayMessage(uiMsg: JsonObject, timestamp: String): ParsedMessage? {
        val say = uiMsg["say"]?.jsonPrimitive?.content ?: return null
        val text = uiMsg["text"]?.jsonPrimitive?.content ?: ""

        return when (say) {
            "text" -> ParsedMessage.User(
                timestamp = timestamp,
                content = listOf(MessageContent.Text(text))
            )
            "reasoning" -> ParsedMessage.AssistantThinking(
                timestamp = timestamp,
                thinking = text
            )
            "error" -> ParsedMessage.Info(
                timestamp = timestamp,
                title = "error",
                content = MessageContent.Text(text.ifEmpty { "Unknown error" }),
                style = ParsedMessage.InfoStyle.ERROR
            )
            "checkpoint_saved", "api_req_started", "api_req_finished" -> null
            else -> null
        }
    }

    private fun parseAskMessage(uiMsg: JsonObject, timestamp: String): ParsedMessage? {
        val ask = uiMsg["ask"]?.jsonPrimitive?.content ?: return null
        val text = uiMsg["text"]?.jsonPrimitive?.content ?: ""

        return when (ask) {
            "tool" -> {
                try {
                    val toolData = JSON.parseToJsonElement(text).jsonObject
                    val toolName = toolData["tool"]?.jsonPrimitive?.content ?: "unknown"
                    val inputMap = mutableMapOf<String, String>()

                    for ((key, value) in toolData.entries) {
                        if (key != "tool") {
                            inputMap[key] = when {
                                value is JsonPrimitive && value.isString -> value.content
                                else -> value.toString()
                            }
                        }
                    }

                    ParsedMessage.ToolUse(
                        timestamp = timestamp,
                        toolName = toolName,
                        input = inputMap,
                        results = emptyList()
                    )
                } catch (_: Exception) {
                    ParsedMessage.Info(
                        timestamp = timestamp,
                        title = "tool_error",
                        content = MessageContent.Text("Failed to parse tool: $text"),
                        style = ParsedMessage.InfoStyle.ERROR
                    )
                }
            }
            "followup" -> ParsedMessage.Info(
                timestamp = timestamp,
                title = "followup",
                subtitle = "question",
                content = MessageContent.Text(text),
                style = ParsedMessage.InfoStyle.DEFAULT
            )
            "command" -> ParsedMessage.Info(
                timestamp = timestamp,
                title = "command",
                content = MessageContent.Text(text),
                style = ParsedMessage.InfoStyle.DEFAULT
            )
            else -> null
        }
    }

    private fun extractModelFromApiHistory(apiHistory: JsonArray): String? {
        for (msgElement in apiHistory) {
            val msg = msgElement.jsonObject
            if (msg["role"]?.jsonPrimitive?.content != "user") continue

            val contentField = msg["content"]
            when {
                contentField is JsonArray -> {
                    for (item in contentField) {
                        val itemObj = item.jsonObject
                        if (itemObj["type"]?.jsonPrimitive?.content == "text") {
                            val text = itemObj["text"]?.jsonPrimitive?.content ?: continue
                            if (text.contains("<environment_details>")) {
                                val match = MODEL_TAG_REGEX.find(text)
                                if (match != null) return match.groupValues[1]
                            }
                        }
                    }
                }
                contentField is JsonPrimitive -> {
                    val text = contentField.content
                    if (text.contains("<environment_details>")) {
                        val match = MODEL_TAG_REGEX.find(text)
                        if (match != null) return match.groupValues[1]
                    }
                }
            }
        }
        return null
    }

    private fun extractWorkspace(metadata: JsonObject): String? {
        val cwd = metadata["cwd"]?.jsonPrimitive?.content
        if (cwd != null) return cwd

        val filesInContext = metadata["files_in_context"]?.jsonArray
        if (filesInContext != null && filesInContext.isNotEmpty()) {
            val firstFile = filesInContext[0].jsonObject
            val path = firstFile["path"]?.jsonPrimitive?.content
            if (path != null) {
                return File(path).parent
            }
        }

        return null
    }

    /**
     * Extract title from first user message.
     */
    fun extractTitle(uiMessages: JsonArray, apiHistory: JsonArray): String? {
        // Try UI messages first
        for (msgElement in uiMessages) {
            val msg = msgElement.jsonObject
            if (msg["type"]?.jsonPrimitive?.content == "say" &&
                msg["say"]?.jsonPrimitive?.content == "text") {
                val text = msg["text"]?.jsonPrimitive?.content?.trim() ?: continue
                if (text.isNotEmpty()) {
                    return if (text.length > 100) text.take(100) + "..." else text
                }
            }
        }

        // Fallback to API history
        for (msgElement in apiHistory) {
            val msg = msgElement.jsonObject
            if (msg["role"]?.jsonPrimitive?.content != "user") continue

            val contentField = msg["content"]
            val text = when {
                contentField is JsonArray -> {
                    contentField.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                        ?.jsonObject?.get("text")?.jsonPrimitive?.content
                }
                contentField is JsonPrimitive -> contentField.content
                else -> null
            }?.trim() ?: continue

            if (text.isNotEmpty()) {
                return if (text.length > 100) text.take(100) + "..." else text
            }
        }

        return null
    }
}

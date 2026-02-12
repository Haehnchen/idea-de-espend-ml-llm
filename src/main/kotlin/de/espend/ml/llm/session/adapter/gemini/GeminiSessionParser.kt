package de.espend.ml.llm.session.adapter.gemini

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import kotlinx.serialization.json.*
import java.io.File

/**
 * Standalone parser for Gemini CLI session files.
 * Parses session JSON files into unified SessionDetail format.
 * No IntelliJ dependencies.
 */
object GeminiSessionParser {

    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Parse a session file by path.
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
     * Parse a session file.
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
     * Parse session content from JSON string.
     */
    fun parseContent(content: String): SessionDetail {
        val json = JSON.parseToJsonElement(content).jsonObject
        val sessionId = json["sessionId"]?.jsonPrimitive?.content ?: ""
        val startTime = json["startTime"]?.jsonPrimitive?.content
        val lastUpdated = json["lastUpdated"]?.jsonPrimitive?.content
        val messagesArray = json["messages"]?.jsonArray ?: JsonArray(emptyList())

        val messages = mutableListOf<ParsedMessage>()
        val modelCounts = mutableMapOf<String, Int>()

        for (msgElement in messagesArray) {
            val msg = msgElement.jsonObject
            val model = msg["model"]?.jsonPrimitive?.content
            if (model != null) {
                modelCounts[model] = (modelCounts[model] ?: 0) + 1
            }

            val parsed = parseMessage(msg)
            messages.addAll(parsed)
        }

        val sortedModels = modelCounts.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        val title = extractTitle(messagesArray)

        return SessionDetail(
            sessionId = sessionId,
            title = title,
            messages = messages,
            metadata = SessionMetadata(
                models = sortedModels,
                messageCount = messages.size,
                created = startTime,
                modified = lastUpdated
            )
        )
    }

    private fun parseMessage(msg: JsonObject): List<ParsedMessage> {
        val messages = mutableListOf<ParsedMessage>()
        val timestamp = msg["timestamp"]?.jsonPrimitive?.content ?: ""
        val type = msg["type"]?.jsonPrimitive?.content ?: return emptyList()

        when (type) {
            "user" -> {
                val content = extractContent(msg["content"])
                messages.add(ParsedMessage.User(
                    timestamp = timestamp,
                    content = listOf(MessageContent.Text(content))
                ))
            }

            "gemini" -> {
                // Add thoughts as thinking messages
                val thoughts = msg["thoughts"]?.jsonArray
                if (thoughts != null && thoughts.isNotEmpty()) {
                    for (thought in thoughts) {
                        val thoughtObj = thought.jsonObject
                        val subject = thoughtObj["subject"]?.jsonPrimitive?.content ?: ""
                        val description = thoughtObj["description"]?.jsonPrimitive?.content ?: ""
                        val thoughtTimestamp = thoughtObj["timestamp"]?.jsonPrimitive?.content ?: timestamp

                        messages.add(ParsedMessage.AssistantThinking(
                            timestamp = thoughtTimestamp,
                            thinking = "[$subject]\n$description"
                        ))
                    }
                }

                // Add tool calls
                val toolCalls = msg["toolCalls"]?.jsonArray
                if (toolCalls != null && toolCalls.isNotEmpty()) {
                    for (toolCallElement in toolCalls) {
                        val toolCall = toolCallElement.jsonObject
                        messages.addAll(parseToolCall(toolCall, timestamp))
                    }
                }

                // Add text content
                val content = extractContent(msg["content"])
                if (content.isNotBlank()) {
                    messages.add(ParsedMessage.AssistantText(
                        timestamp = timestamp,
                        content = listOf(MessageContent.Markdown(content))
                    ))
                }
            }

            "error" -> {
                val content = extractContent(msg["content"])
                messages.add(ParsedMessage.Info(
                    timestamp = timestamp,
                    title = "error",
                    content = MessageContent.Text(content),
                    style = ParsedMessage.InfoStyle.ERROR
                ))
            }

            "info" -> {
                val content = extractContent(msg["content"])
                messages.add(ParsedMessage.Info(
                    timestamp = timestamp,
                    title = "info",
                    content = MessageContent.Text(content),
                    style = ParsedMessage.InfoStyle.DEFAULT
                ))
            }
        }

        return messages
    }

    private fun parseToolCall(toolCall: JsonObject, timestamp: String): List<ParsedMessage> {
        val messages = mutableListOf<ParsedMessage>()
        val toolCallId = toolCall["id"]?.jsonPrimitive?.content ?: ""
        val toolName = toolCall["displayName"]?.jsonPrimitive?.content
            ?: toolCall["name"]?.jsonPrimitive?.content
            ?: "tool"

        // Parse input args
        val inputMap = mutableMapOf<String, String>()
        val args = toolCall["args"]?.jsonObject
        args?.entries?.forEach { (key, value) ->
            inputMap[key] = when {
                value is JsonPrimitive && value.isString -> value.content
                else -> value.toString()
            }
        }

        // Parse tool results
        val results = mutableListOf<ParsedMessage.ToolResult>()

        val resultArray = toolCall["result"]?.jsonArray
        if (resultArray != null && resultArray.isNotEmpty()) {
            for (result in resultArray) {
                val resultObj = result.jsonObject
                val funcResponse = resultObj["functionResponse"]?.jsonObject
                val response = funcResponse?.get("response")?.jsonObject
                val output = response?.get("output")

                if (output != null) {
                    val outputStr = when {
                        output is JsonPrimitive && output.isString -> output.content
                        else -> output.toString()
                    }

                    val isError = toolCall["status"]?.jsonPrimitive?.content == "error"
                    val outputContent = parseToolResultOutput(outputStr)

                    results.add(ParsedMessage.ToolResult(
                        timestamp = timestamp,
                        toolName = toolName,
                        toolCallId = toolCallId,
                        output = outputContent,
                        isError = isError
                    ))
                }
            }
        }

        // Check for file diff in resultDisplay (can be a string or object)
        val resultDisplayElement = toolCall["resultDisplay"]
        val resultDisplay = try { resultDisplayElement?.jsonObject } catch (_: Exception) { null }
        val fileDiff = resultDisplay?.get("fileDiff")?.jsonPrimitive?.content
        if (!fileDiff.isNullOrEmpty()) {
            results.add(ParsedMessage.ToolResult(
                timestamp = timestamp,
                toolName = toolName,
                toolCallId = toolCallId,
                output = listOf(MessageContent.Code(fileDiff, "diff"))
            ))
        }

        messages.add(ParsedMessage.ToolUse(
            timestamp = timestamp,
            toolName = toolName,
            toolCallId = toolCallId,
            input = inputMap,
            results = results
        ))

        return messages
    }

    private fun parseToolResultOutput(output: String): List<MessageContent> {
        if (output.contains("---") && output.contains("+++")) {
            return listOf(MessageContent.Code(output, "diff"))
        }
        return listOf(MessageContent.Code(output))
    }

    /**
     * Extract text content from message content field.
     * Content can be a string or an array of {text: string}.
     */
    private fun extractContent(content: JsonElement?): String {
        if (content == null) return ""

        return when {
            content is JsonPrimitive -> content.content
            content is JsonArray -> {
                content.mapNotNull { item ->
                    item.jsonObject["text"]?.jsonPrimitive?.content
                }.joinToString("\n")
            }
            else -> ""
        }
    }

    private fun extractTitle(messages: JsonArray): String {
        for (msg in messages) {
            val msgObj = msg.jsonObject
            if (msgObj["type"]?.jsonPrimitive?.content == "user") {
                val content = extractContent(msgObj["content"])
                if (content.isNotBlank()) {
                    return if (content.length > 100) content.take(100) + "..." else content
                }
            }
        }
        return "Gemini Session"
    }
}

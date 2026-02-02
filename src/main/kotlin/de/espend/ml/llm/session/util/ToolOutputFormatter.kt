package de.espend.ml.llm.session.util

import de.espend.ml.llm.session.model.MessageContent
import kotlinx.serialization.json.*

/**
 * Utility for formatting tool output JSON into MessageContent.
 * Extracts common output patterns and formats them as code blocks.
 */
object ToolOutputFormatter {

    private const val MAX_LENGTH = 500

    /**
     * Truncates content to MAX_LENGTH characters.
     * If content is too long, shows the beginning and end with "[...]" in the middle.
     *
     * @param content The content to truncate
     * @return Truncated content with ellipsis in the middle if needed
     */
    fun truncateContent(content: String): String {
        if (content.length <= MAX_LENGTH) return content

        val ellipsis = " [...] "
        val remainingLength = MAX_LENGTH - ellipsis.length
        val firstHalfLength = remainingLength / 2
        val secondHalfLength = remainingLength - firstHalfLength

        val beginning = content.take(firstHalfLength)
        val end = content.takeLast(secondHalfLength)
        return "$beginning$ellipsis$end"
    }

    /**
     * Formats a JsonElement output into a list of MessageContent.
     * Tool output content is automatically truncated to MAX_LENGTH characters.
     *
     * @param output The JSON output to format
     * @return List of MessageContent representing the formatted output
     */
    fun formatToolOutput(output: JsonElement?): List<MessageContent> {
        if (output == null) return listOf(MessageContent.Code("{}"))

        // Handle primitive values directly - always as code block
        if (output is JsonPrimitive) {
            val content = output.contentOrNull ?: output.toString()
            return listOf(MessageContent.Code(truncateContent(content)))
        }

        // Handle arrays
        if (output is JsonArray) {
            return listOf(MessageContent.Code(truncateContent(output.toString())))
        }

        val outputObj = output as? JsonObject ?: return listOf(MessageContent.Code(truncateContent(output.toString())))

        // Try to extract common output patterns
        // Check for content field (common in read results)
        val content = outputObj["content"]?.jsonPrimitive?.contentOrNull
        if (content != null) {
            return listOf(MessageContent.Code(truncateContent(content)))
        }

        // Check for result field
        val result = outputObj["result"]?.jsonPrimitive?.contentOrNull
        if (result != null) {
            return listOf(MessageContent.Code(truncateContent(result)))
        }

        // Check for output field
        val outputField = outputObj["output"]?.jsonPrimitive?.contentOrNull
        if (outputField != null) {
            return listOf(MessageContent.Code(truncateContent(outputField)))
        }

        // Check for error/message fields
        val error = outputObj["error"]?.jsonPrimitive?.contentOrNull
        val message = outputObj["message"]?.jsonPrimitive?.contentOrNull
        if (error != null || message != null) {
            return listOf(MessageContent.Code(truncateContent(error ?: message ?: outputObj.toString())))
        }

        // Default: return the whole object as code
        return listOf(MessageContent.Code(truncateContent(outputObj.toString())))
    }
}

package de.espend.ml.llm.session.util

import kotlinx.serialization.json.*

/**
 * Utility for formatting tool input parameters into HTML.
 * Parameters are displayed as key-value pairs with values in code blocks.
 */
object ToolInputFormatter {

    /**
     * Converts a JsonElement to a Map<String, String> for storage in ToolUse.input.
     * This is used by parsers to store raw tool parameters as simple key-value pairs.
     *
     * @param input The JSON input to convert
     * @return Map of parameter names to string values
     */
    fun jsonToMap(input: JsonElement?): Map<String, String> {
        if (input == null) return emptyMap()
        if (input !is JsonObject) return emptyMap()

        return input.entries.associate { (key, value) ->
            val stringValue = when (value) {
                is JsonPrimitive -> value.contentOrNull ?: value.toString()
                else -> value.toString()
            }
            key to stringValue
        }
    }

    /**
     * Formats a Map<String, String> input as HTML.
     * Returns key-value pairs with values wrapped in <code> tags.
     *
     * @param input The map of parameters to format
     * @return HTML string representing the formatted input
     */
    fun formatToolInput(input: Map<String, String>): String {
        if (input.isEmpty()) return "<code>{}</code>"

        return buildString {
            for ((index, entry) in input.entries.withIndex()) {
                val (key, value) = entry
                append("$key: <code>${HtmlBuilder.escapeHtml(value)}</code>")
                if (index < input.entries.size - 1) {
                    append("<br>")
                }
            }
        }
    }
}

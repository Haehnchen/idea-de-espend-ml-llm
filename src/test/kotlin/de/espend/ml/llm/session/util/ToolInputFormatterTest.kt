package de.espend.ml.llm.session.util

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class ToolInputFormatterTest {

    @Test
    fun `formatToolInput should format parameters as HTML with code tags`() {
        val input: Map<String, String> = mapOf(
            "command" to "ls -la",
            "description" to "List files in directory"
        )

        val result = ToolInputFormatter.formatToolInput(input)

        assertTrue(result.contains("command: <code>ls -la</code>"))
        assertTrue(result.contains("description: <code>List files in directory</code>"))
    }

    @Test
    fun `formatToolInput should format Edit tool parameters`() {
        val input: Map<String, String> = mapOf(
            "file_path" to "/path/to/file.kt",
            "old_string" to "old code here",
            "new_string" to "new code here"
        )

        val result = ToolInputFormatter.formatToolInput(input)

        // Edit tools are formatted generically like any other tool
        // Diff rendering is handled in the view layer
        assertTrue(result.contains("file_path: <code>/path/to/file.kt</code>"))
        assertTrue(result.contains("old_string: <code>old code here</code>"))
        assertTrue(result.contains("new_string: <code>new code here</code>"))
    }

    @Test
    fun `formatToolInput should handle empty map`() {
        val input: Map<String, String> = emptyMap()

        val result = ToolInputFormatter.formatToolInput(input)

        assertEquals("<code>{}</code>", result)
    }

    @Test
    fun `formatToolInput should format boolean and numeric values as strings`() {
        val input: Map<String, String> = mapOf(
            "enabled" to "true",
            "count" to "42"
        )

        val result = ToolInputFormatter.formatToolInput(input)

        assertTrue(result.contains("enabled: <code>true</code>"))
        assertTrue(result.contains("count: <code>42</code>"))
    }

    @Test
    fun `formatToolInput should preserve multiline content`() {
        val multilineContent = """
            |line 1
            |line 2
            |line 3
        """.trimMargin()

        val input: Map<String, String> = mapOf(
            "content" to multilineContent
        )

        val result = ToolInputFormatter.formatToolInput(input)

        assertTrue(result.contains("line 1"))
        assertTrue(result.contains("line 2"))
        assertTrue(result.contains("line 3"))
    }

    @Test
    fun `formatToolInput should HTML encode special characters`() {
        val input: Map<String, String> = mapOf(
            "xml" to "<tag>value</tag>",
            "quote" to "hello \"world\""
        )

        val result = ToolInputFormatter.formatToolInput(input)

        assertTrue(result.contains("xml: <code>&lt;tag&gt;value&lt;/tag&gt;</code>"))
        assertTrue(result.contains("quote: <code>hello &quot;world&quot;</code>"))
    }

    @Test
    fun `jsonToMap should convert JsonObject to Map`() {
        val jsonElement = kotlinx.serialization.json.buildJsonObject {
            put("command", "ls -la")
            put("enabled", true)
            put("count", 42)
        }

        val result = ToolInputFormatter.jsonToMap(jsonElement)

        assertEquals(3, result.size)
        assertEquals("ls -la", result["command"])
        assertEquals("true", result["enabled"])
        assertEquals("42", result["count"])
    }

    @Test
    fun `jsonToMap should handle nested objects`() {
        val jsonElement = kotlinx.serialization.json.buildJsonObject {
            put("outer", "value")
            put("nested", kotlinx.serialization.json.buildJsonObject {
                put("inner", "inner_value")
            })
        }

        val result = ToolInputFormatter.jsonToMap(jsonElement)

        assertEquals(2, result.size)
        assertEquals("value", result["outer"])
        // Nested objects are converted to string representation
        assertNotNull(result["nested"])
    }

    @Test
    fun `jsonToMap should return empty map for null input`() {
        val result = ToolInputFormatter.jsonToMap(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `jsonToMap should return empty map for non-JsonObject input`() {
        val jsonElement = kotlinx.serialization.json.JsonPrimitive("just a string")
        val result = ToolInputFormatter.jsonToMap(jsonElement)
        assertTrue(result.isEmpty())
    }
}

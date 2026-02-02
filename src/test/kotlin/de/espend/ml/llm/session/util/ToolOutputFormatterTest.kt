package de.espend.ml.llm.session.util

import de.espend.ml.llm.session.model.MessageContent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class ToolOutputFormatterTest {

    // ===== truncateContent Tests =====

    @Test
    fun `truncateContent should return content unchanged if under limit`() {
        val short = "This is a short string"
        val result = ToolOutputFormatter.truncateContent(short)
        assertEquals(short, result)
    }

    @Test
    fun `truncateContent should return content unchanged if exactly at limit`() {
        val exact = "a".repeat(500)
        val result = ToolOutputFormatter.truncateContent(exact)
        assertEquals(500, result.length)
        assertEquals(exact, result)
    }

    @Test
    fun `truncateContent should split long content with ellipsis in the middle`() {
        val long = "a".repeat(600)
        val result = ToolOutputFormatter.truncateContent(long)

        // Result should be exactly 500 chars
        assertEquals(500, result.length)

        // Should contain the ellipsis placeholder with spaces
        assertTrue("Should contain [...] with spaces", result.contains(" [...] "))

        // Should start with the beginning
        assertTrue("Should start with 'a'", result.startsWith("a"))

        // Should end with the end
        assertTrue("Should end with 'a'", result.endsWith("a"))
    }

    @Test
    fun `truncateContent should preserve beginning and end for long content`() {
        val beginning = "START_"
        val end = "_END"
        val middle = "x".repeat(600)
        val long = beginning + middle + end

        val result = ToolOutputFormatter.truncateContent(long)

        // Result should be exactly 500 chars
        assertEquals(500, result.length)

        // Should preserve beginning
        assertTrue("Should start with '$beginning'", result.startsWith(beginning))

        // Should preserve end
        assertTrue("Should end with '$end'", result.endsWith(end))
    }

    // ===== formatToolOutput Tests =====

    @Test
    fun `formatToolOutput should return empty code block for null output`() {
        val result = ToolOutputFormatter.formatToolOutput(null)
        assertEquals(1, result.size)
        assertEquals("{}", (result[0] as MessageContent.Code).code)
    }

    @Test
    fun `formatToolOutput should format primitive string as code block`() {
        val primitive = JsonPrimitive("test output")
        val result = ToolOutputFormatter.formatToolOutput(primitive)

        assertEquals(1, result.size)
        val code = result[0] as MessageContent.Code
        assertEquals("test output", code.code)
    }

    @Test
    fun `formatToolOutput should truncate long primitive content`() {
        val longContent = "a".repeat(1000)
        val primitive = JsonPrimitive(longContent)
        val result = ToolOutputFormatter.formatToolOutput(primitive)

        assertEquals(1, result.size)
        val code = result[0] as MessageContent.Code
        assertEquals(500, code.code.length)
        assertTrue("Should contain [...] with spaces", code.code.contains(" [...] "))
    }

    @Test
    fun `formatToolOutput should format object with content field`() {
        val obj = buildJsonObject {
            put("content", "file content here")
        }

        val result = ToolOutputFormatter.formatToolOutput(obj)

        assertEquals(1, result.size)
        val code = result[0] as MessageContent.Code
        assertEquals("file content here", code.code)
    }

    @Test
    fun `formatToolOutput should truncate content field when too long`() {
        val longContent = "x".repeat(800)
        val obj = buildJsonObject {
            put("content", longContent)
        }

        val result = ToolOutputFormatter.formatToolOutput(obj)

        assertEquals(1, result.size)
        val code = result[0] as MessageContent.Code
        assertEquals(500, code.code.length)
        assertTrue("Should contain [...] with spaces", code.code.contains(" [...] "))
    }

    @Test
    fun `formatToolOutput should format object with result field`() {
        val obj = buildJsonObject {
            put("result", "operation result")
        }

        val result = ToolOutputFormatter.formatToolOutput(obj)

        assertEquals(1, result.size)
        val code = result[0] as MessageContent.Code
        assertEquals("operation result", code.code)
    }

    @Test
    fun `formatToolOutput should format object with error field`() {
        val obj = buildJsonObject {
            put("error", "something went wrong")
        }

        val result = ToolOutputFormatter.formatToolOutput(obj)

        assertEquals(1, result.size)
        val code = result[0] as MessageContent.Code
        assertEquals("something went wrong", code.code)
    }

    @Test
    fun `formatToolOutput should fallback to full object for unknown structure`() {
        val obj = buildJsonObject {
            put("unknown_field", "some value")
            put("another_field", 42)
        }

        val result = ToolOutputFormatter.formatToolOutput(obj)

        assertEquals(1, result.size)
        val code = result[0] as MessageContent.Code
        assertTrue("Should contain object as string", code.code.contains("unknown_field"))
        // Small JSON object should NOT be truncated
        assertTrue("Should be less than 500 chars", code.code.length < 500)
    }

    @Test
    fun `formatToolOutput should format arrays`() {
        val array = kotlinx.serialization.json.buildJsonArray {
            add(JsonPrimitive("item1"))
            add(JsonPrimitive("item2"))
            add(JsonPrimitive("item3"))
        }

        val result = ToolOutputFormatter.formatToolOutput(array)

        assertEquals(1, result.size)
        val code = result[0] as MessageContent.Code
        assertTrue("Should be valid JSON array", code.code.startsWith("["))
    }

    @Test
    fun `formatToolOutput should handle boolean primitive`() {
        val primitive = JsonPrimitive(true)
        val result = ToolOutputFormatter.formatToolOutput(primitive)

        assertEquals(1, result.size)
        val code = result[0] as MessageContent.Code
        assertEquals("true", code.code)
    }

    @Test
    fun `formatToolOutput should handle numeric primitive`() {
        val primitive = JsonPrimitive(12345)
        val result = ToolOutputFormatter.formatToolOutput(primitive)

        assertEquals(1, result.size)
        val code = result[0] as MessageContent.Code
        assertEquals("12345", code.code)
    }
}

package de.espend.ml.llm.session.adapter.amp

import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths

class AmpSessionParserTest {

    private val fixturesDir = Paths.get("src/test/kotlin/de/espend/ml/llm/session/adapter/fixtures/amp")

    @Test
    fun `parseContent should parse valid session JSON`() {
        val fixtureFile = fixturesDir.resolve("T-019c2505-6bed-73de-8e27-51899656b47b.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        assertEquals("T-019c2505-6bed-73de-8e27-51899656b47b", result!!.sessionId)
        assertTrue("Messages should not be empty", result.messages.isNotEmpty())
    }

    @Test
    fun `parseContent should parse second fixture`() {
        val fixtureFile = fixturesDir.resolve("T-array-format-test.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        // Session ID comes from JSON id field in parser
        assertEquals("T-different-id-in-json", result!!.sessionId)
        assertTrue("Messages should not be empty", result.messages.isNotEmpty())
    }

    @Test
    fun `parseContent should extract title from first user message`() {
        val fixtureFile = fixturesDir.resolve("T-019c2505-6bed-73de-8e27-51899656b47b.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        assertTrue("Title should not be empty", result!!.title.isNotEmpty())
        assertTrue("Title should contain expected text",
            result.title.contains("twilwind") || result.title.contains("theme"))
    }

    @Test
    fun `parseContent should handle missing file gracefully`() {
        val result = AmpSessionParser.parseSession("T-nonexistent-0000-0000-0000-000000000000")

        assertNull(result)
    }

    @Test
    fun `parseContent should parse user messages correctly`() {
        val fixtureFile = fixturesDir.resolve("T-019c2505-6bed-73de-8e27-51899656b47b.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        val userMessages = result!!.messages.filterIsInstance<ParsedMessage.User>()
        assertTrue("Should have user messages", userMessages.isNotEmpty())

        val firstUser = userMessages.first()
        val textContent = firstUser.content.filterIsInstance<MessageContent.Text>()
        assertTrue("User message should have text content", textContent.isNotEmpty())
    }

    @Test
    fun `parseContent should parse assistant messages with thinking`() {
        val fixtureFile = fixturesDir.resolve("T-019c2505-6bed-73de-8e27-51899656b47b.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        val thinkingMessages = result!!.messages.filterIsInstance<ParsedMessage.AssistantThinking>()
        assertTrue("Should have thinking messages", thinkingMessages.isNotEmpty())
    }

    @Test
    fun `parseContent should parse tool use messages`() {
        val fixtureFile = fixturesDir.resolve("T-019c2505-6bed-73de-8e27-51899656b47b.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        val toolUseMessages = result!!.messages.filterIsInstance<ParsedMessage.ToolUse>()
        assertTrue("Should have tool use messages", toolUseMessages.isNotEmpty())

        val toolUse = toolUseMessages.first()
        assertEquals("Read", toolUse.toolName)
        assertNotNull(toolUse.toolCallId)
    }

    @Test
    fun `parseContent should return multiple tool_use blocks from single assistant message`() {
        val fixtureFile = fixturesDir.resolve("T-array-format-test.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        val toolUseMessages = result!!.messages.filterIsInstance<ParsedMessage.ToolUse>()

        // The fixture has: Read, glob, and edit_file tool calls
        assertTrue("Should have at least 3 tool use messages", toolUseMessages.size >= 3)

        val toolNames = toolUseMessages.map { it.toolName }
        assertTrue("Should have Read tool", toolNames.contains("Read"))
        assertTrue("Should have glob tool", toolNames.contains("glob"))
        assertTrue("Should have edit_file tool", toolNames.contains("edit_file"))
    }

    @Test
    fun `parseContent should extract timestamp from usage field`() {
        val fixtureFile = fixturesDir.resolve("T-array-format-test.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        val toolUseMessages = result!!.messages.filterIsInstance<ParsedMessage.ToolUse>()
        assertTrue("Should have tool use messages", toolUseMessages.isNotEmpty())

        // Check that timestamp is ISO format, not a number
        val firstToolUse = toolUseMessages.first()
        assertTrue("Timestamp should be ISO format",
            firstToolUse.timestamp.contains("2026") || firstToolUse.timestamp.isEmpty())
        assertFalse("Timestamp should not be just a number",
            firstToolUse.timestamp.matches(Regex("^\\d+$")))
    }

    @Test
    fun `parseContent should parse edit_file tool with old_str and new_str`() {
        val fixtureFile = fixturesDir.resolve("T-array-format-test.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        val toolUseMessages = result!!.messages.filterIsInstance<ParsedMessage.ToolUse>()
        val editTool = toolUseMessages.find { it.toolName == "edit_file" }

        assertNotNull("Should have edit_file tool", editTool)
        assertTrue("Should have old_str input", editTool!!.input.containsKey("old_str"))
        assertTrue("Should have new_str input", editTool.input.containsKey("new_str"))
        assertEquals("println(\"Hello\")", editTool.input["old_str"])
        assertEquals("println(\"Hello World\")", editTool.input["new_str"])
    }

    @Test
    fun `parseContent should skip tool_result with only diff field`() {
        val fixtureFile = fixturesDir.resolve("T-array-format-test.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        val userMessages = result!!.messages.filterIsInstance<ParsedMessage.User>()

        // Should only have the initial user message, not the tool_result messages
        // The fixture has 1 user text message and tool_results that should be filtered
        assertEquals("Should have only 1 user message (tool results with diff skipped)", 1, userMessages.size)

        val firstUser = userMessages.first()
        val textContent = firstUser.content.filterIsInstance<MessageContent.Text>()
        assertTrue("User message should contain 'edit'",
            textContent.any { it.text.contains("edit") })
    }

    @Test
    fun `parseContent should extract metadata correctly`() {
        val fixtureFile = fixturesDir.resolve("T-019c2505-6bed-73de-8e27-51899656b47b.json")

        if (!fixtureFile.toFile().exists()) {
            return
        }

        val result = AmpSessionParser.parseFile(fixtureFile)

        assertNotNull(result)
        assertNotNull(result!!.metadata)

        val metadata = result.metadata!!
        assertTrue("Message count should be > 0", metadata.messageCount > 0)
        assertNotNull(metadata.created)
    }

    @Test
    fun `parseContent should handle invalid JSON gracefully`() {
        val result = AmpSessionParser.parseContent("invalid json")

        assertNull(result)
    }

    @Test
    fun `parseContent should handle empty JSON gracefully`() {
        val result = AmpSessionParser.parseContent("{}")

        assertNull(result)
    }

    @Test
    fun `parseContent should parse inline JSON`() {
        val json = """
        {
          "id": "T-test-inline",
          "created": 1770147638256,
          "messages": [
            {"role": "user", "messageId": 0, "content": [{"type": "text", "text": "Hello"}]},
            {"role": "assistant", "messageId": 1, "content": [{"type": "text", "text": "Hi there"}], "usage": {"timestamp": "2026-02-03T10:00:00.000Z"}}
          ]
        }
        """.trimIndent()

        val result = AmpSessionParser.parseContent(json)

        assertNotNull(result)
        assertEquals("T-test-inline", result!!.sessionId)
        assertTrue("Should have messages", result.messages.isNotEmpty())

        val userMessages = result.messages.filterIsInstance<ParsedMessage.User>()
        assertEquals("Should have 1 user message", 1, userMessages.size)

        val assistantMessages = result.messages.filterIsInstance<ParsedMessage.AssistantText>()
        assertEquals("Should have 1 assistant message", 1, assistantMessages.size)
    }
}

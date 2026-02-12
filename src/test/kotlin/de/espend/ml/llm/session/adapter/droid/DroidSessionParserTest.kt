package de.espend.ml.llm.session.adapter.droid

import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths

class DroidSessionParserTest {

    private val fixturesDir = Paths.get("src/test/kotlin/de/espend/ml/llm/session/adapter/fixtures/droid")

    @Test
    fun `parseContent should parse session_start line`() {
        val content = """{"type":"session_start","id":"1535cefc-1c55-4956-a51b-abadba8e0e92","title":"test session","sessionTitle":"New Session","owner":"daniel","version":2,"cwd":"/home/daniel/plugins"}"""

        val result = DroidSessionParser.parseContent(content)

        assertNotNull(result)
        assertEquals("1535cefc-1c55-4956-a51b-abadba8e0e92", result!!.sessionId)
        assertEquals("test session", result.title)
    }

    @Test
    fun `parseContent should parse user message`() {
        val content = listOf(
            """{"type":"session_start","id":"test-id","title":"test","sessionTitle":"New Session","owner":"daniel","version":2,"cwd":"/home/daniel/test"}""",
            """{"type":"message","id":"msg-1","timestamp":"2026-01-24T16:49:08.867Z","message":{"role":"user","content":[{"type":"text","text":"Hello, world!"}]}}"""
        ).joinToString("\n")

        val result = DroidSessionParser.parseContent(content)

        assertNotNull(result)
        assertEquals(1, result!!.messages.size)
        val msg = result.messages[0]
        assertTrue(msg is ParsedMessage.User)
        val user = msg as ParsedMessage.User
        assertEquals(1, user.content.size)
        assertTrue(user.content[0] is MessageContent.Text)
        assertEquals("Hello, world!", (user.content[0] as MessageContent.Text).text)
    }

    @Test
    fun `parseContent should parse assistant text message`() {
        val content = listOf(
            """{"type":"session_start","id":"test-id","title":"test","sessionTitle":"New Session","owner":"daniel","version":2,"cwd":"/home/daniel/test"}""",
            """{"type":"message","id":"msg-1","timestamp":"2026-01-24T16:49:10.867Z","message":{"role":"assistant","content":[{"type":"text","text":"Hi! How can I help you?"}]}}"""
        ).joinToString("\n")

        val result = DroidSessionParser.parseContent(content)

        assertNotNull(result)
        assertEquals(1, result!!.messages.size)
        val msg = result.messages[0]
        assertTrue(msg is ParsedMessage.AssistantText)
        val assistant = msg as ParsedMessage.AssistantText
        assertTrue(assistant.content[0] is MessageContent.Markdown)
        assertEquals("Hi! How can I help you?", (assistant.content[0] as MessageContent.Markdown).markdown)
    }

    @Test
    fun `parseContent should parse tool_use message`() {
        val content = listOf(
            """{"type":"session_start","id":"test-id","title":"test","sessionTitle":"New Session","owner":"daniel","version":2,"cwd":"/home/daniel/test"}""",
            """{"type":"message","id":"msg-1","timestamp":"2026-01-24T16:49:10.867Z","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_123abc","name":"Read","input":{"file_path":"/path/to/file.txt"}}]}}"""
        ).joinToString("\n")

        val result = DroidSessionParser.parseContent(content)

        assertNotNull(result)
        assertEquals(1, result!!.messages.size)
        val msg = result.messages[0]
        assertTrue(msg is ParsedMessage.ToolUse)
        val toolUse = msg as ParsedMessage.ToolUse
        assertEquals("Read", toolUse.toolName)
        assertEquals("toolu_123abc", toolUse.toolCallId)
        assertEquals("/path/to/file.txt", toolUse.input["file_path"])
    }

    @Test
    fun `parseContent should parse tool_result message`() {
        val content = listOf(
            """{"type":"session_start","id":"test-id","title":"test","sessionTitle":"New Session","owner":"daniel","version":2,"cwd":"/home/daniel/test"}""",
            """{"type":"message","id":"msg-1","timestamp":"2026-01-24T16:49:10.867Z","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_123abc","name":"Read","input":{"file_path":"/path/to/file.txt"}}]}}""",
            """{"type":"message","id":"msg-2","timestamp":"2026-01-24T16:49:11.867Z","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_123abc","content":"File content here"}]}}"""
        ).joinToString("\n")

        val result = DroidSessionParser.parseContent(content)

        assertNotNull(result)
        assertEquals(2, result!!.messages.size)
        val toolResult = result.messages.filterIsInstance<ParsedMessage.ToolResult>().firstOrNull()
        assertNotNull(toolResult)
        assertEquals("Read", toolResult!!.toolName)
        assertEquals("toolu_123abc", toolResult.toolCallId)
    }

    @Test
    fun `parseContent should extract title from first user message when session_start title is generic`() {
        val content = listOf(
            """{"type":"session_start","id":"test-id","title":"New Session","sessionTitle":"New Session","owner":"daniel","version":2,"cwd":"/home/daniel/test"}""",
            """{"type":"message","id":"msg-1","timestamp":"2026-01-24T16:49:08.867Z","message":{"role":"user","content":[{"type":"text","text":"Implement feature X"}]}}"""
        ).joinToString("\n")

        val result = DroidSessionParser.parseContent(content)

        assertNotNull(result)
        assertEquals("Implement feature X", result!!.title)
    }

    @Test
    fun `parseContent should handle empty content gracefully`() {
        val result = DroidSessionParser.parseContent("")
        assertNull(result)
    }

    @Test
    fun `parseContent should handle non-session_start first line`() {
        val content = """{"type":"message","id":"msg-1"}"""
        val result = DroidSessionParser.parseContent(content)
        assertNull(result)
    }

    @Test
    fun `parseContent should skip invalid message lines`() {
        val content = listOf(
            """{"type":"session_start","id":"test-id","title":"test","sessionTitle":"New Session","owner":"daniel","version":2,"cwd":"/home/daniel/test"}""",
            "invalid json line",
            """{"type":"message","id":"msg-1","timestamp":"2026-01-24T16:49:08.867Z","message":{"role":"user","content":[{"type":"text","text":"Hello"}]}}"""
        ).joinToString("\n")

        val result = DroidSessionParser.parseContent(content)

        assertNotNull(result)
        assertEquals(1, result!!.messages.size)
    }

    @Test
    fun `parseFile should parse simple session fixture`() {
        val fixtureFile = fixturesDir.resolve("session_simple.jsonl")
        if (!fixtureFile.toFile().exists()) return

        val result = DroidSessionParser.parseFile(fixtureFile.toFile())

        assertNotNull(result)
        assertEquals("test-session-001", result!!.sessionId)
        assertEquals("Simple test", result.title)
        assertTrue(result.messages.isNotEmpty())

        val userMessages = result.messages.filterIsInstance<ParsedMessage.User>()
        assertTrue(userMessages.isNotEmpty())

        val assistantMessages = result.messages.filterIsInstance<ParsedMessage.AssistantText>()
        assertTrue(assistantMessages.isNotEmpty())
    }

    @Test
    fun `parseFile should parse tool use fixture`() {
        val fixtureFile = fixturesDir.resolve("tool_use.jsonl")
        if (!fixtureFile.toFile().exists()) return

        val result = DroidSessionParser.parseFile(fixtureFile.toFile())

        assertNotNull(result)
        assertEquals("test-session-002", result!!.sessionId)

        val toolUseMessages = result.messages.filterIsInstance<ParsedMessage.ToolUse>()
        assertTrue(toolUseMessages.isNotEmpty())
        assertEquals("Read", toolUseMessages[0].toolName)

        val toolResults = result.messages.filterIsInstance<ParsedMessage.ToolResult>()
        assertTrue(toolResults.isNotEmpty())
    }

    @Test
    fun `parseFile should parse multi-tool fixture`() {
        val fixtureFile = fixturesDir.resolve("multi_tool.jsonl")
        if (!fixtureFile.toFile().exists()) return

        val result = DroidSessionParser.parseFile(fixtureFile.toFile())

        assertNotNull(result)
        assertEquals("test-session-003", result!!.sessionId)

        val toolUseMessages = result.messages.filterIsInstance<ParsedMessage.ToolUse>()
        assertTrue("Should have at least 2 tool uses", toolUseMessages.size >= 2)

        val toolNames = toolUseMessages.map { it.toolName }
        assertTrue(toolNames.contains("Read"))
        assertTrue(toolNames.contains("LS"))
    }

    @Test
    fun `parseFile should return null for non-existent file`() {
        val result = DroidSessionParser.parseFile("/non/existent/path/file.jsonl")
        assertNull(result)
    }
}

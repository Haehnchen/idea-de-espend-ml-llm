package de.espend.ml.llm.session.adapter.gemini

import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths

class GeminiSessionParserTest {

    private val fixturesDir = Paths.get("src/test/kotlin/de/espend/ml/llm/session/adapter/fixtures/gemini")

    @Test
    fun `parseContent should parse user message`() {
        val fixtureFile = fixturesDir.resolve("user_message.json")
        if (!fixtureFile.toFile().exists()) return

        val content = fixtureFile.toFile().readText()
        val session = GeminiSessionParser.parseContent(content)

        assertNotNull(session)
        assertEquals("Hello, this is a simple user message", session.title)
        assertEquals(1, session.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.User)

        val userMsg = session.messages[0] as ParsedMessage.User
        assertTrue(userMsg.content[0] is MessageContent.Text)
        assertEquals("Hello, this is a simple user message", (userMsg.content[0] as MessageContent.Text).text)
    }

    @Test
    fun `parseContent should parse error message`() {
        val fixtureFile = fixturesDir.resolve("error_message.json")
        if (!fixtureFile.toFile().exists()) return

        val content = fixtureFile.toFile().readText()
        val session = GeminiSessionParser.parseContent(content)

        assertNotNull(session)
        assertEquals(1, session.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.Info)

        val errorMsg = session.messages[0] as ParsedMessage.Info
        assertEquals("error", errorMsg.title)
        assertEquals(ParsedMessage.InfoStyle.ERROR, errorMsg.style)
        assertTrue(errorMsg.content is MessageContent.Text)
        assertEquals("An error occurred while processing your request", (errorMsg.content as MessageContent.Text).text)
    }

    @Test
    fun `parseContent should parse info message`() {
        val fixtureFile = fixturesDir.resolve("info_message.json")
        if (!fixtureFile.toFile().exists()) return

        val content = fixtureFile.toFile().readText()
        val session = GeminiSessionParser.parseContent(content)

        assertNotNull(session)
        assertEquals(1, session.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.Info)

        val infoMsg = session.messages[0] as ParsedMessage.Info
        assertEquals("info", infoMsg.title)
        assertTrue((infoMsg.content as MessageContent.Text).text.contains("Gemini CLI update"))
    }

    @Test
    fun `parseContent should parse simple assistant message`() {
        val fixtureFile = fixturesDir.resolve("assistant_simple.json")
        if (!fixtureFile.toFile().exists()) return

        val content = fixtureFile.toFile().readText()
        val session = GeminiSessionParser.parseContent(content)

        assertNotNull(session)
        assertEquals(1, session.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.AssistantText)

        val assistantMsg = session.messages[0] as ParsedMessage.AssistantText
        assertTrue(assistantMsg.content[0] is MessageContent.Markdown)
        assertEquals("This is a simple assistant response without thoughts or tool calls.",
            (assistantMsg.content[0] as MessageContent.Markdown).markdown)
    }

    @Test
    fun `parseContent should parse mixed conversation with thoughts and tool calls`() {
        val fixtureFile = fixturesDir.resolve("mixed_conversation.json")
        if (!fixtureFile.toFile().exists()) return

        val content = fixtureFile.toFile().readText()
        val session = GeminiSessionParser.parseContent(content)

        assertNotNull(session)
        assertTrue(session.messages.isNotEmpty())

        // Should have metadata with model
        assertNotNull(session.metadata)
        assertTrue(session.metadata!!.models.isNotEmpty())
        assertEquals("gemini-3-flash-preview", session.metadata!!.models[0].first)

        // Should have user messages
        val userMessages = session.messages.filterIsInstance<ParsedMessage.User>()
        assertTrue(userMessages.isNotEmpty())

        // Should have thinking messages (from thoughts)
        val thinkingMessages = session.messages.filterIsInstance<ParsedMessage.AssistantThinking>()
        assertTrue(thinkingMessages.isNotEmpty())
        assertTrue(thinkingMessages[0].thinking.contains("Determining Project Identity"))

        // Should have tool_use messages
        val toolUseMessages = session.messages.filterIsInstance<ParsedMessage.ToolUse>()
        assertTrue(toolUseMessages.isNotEmpty())

        // Should have assistant_text messages
        val assistantMessages = session.messages.filterIsInstance<ParsedMessage.AssistantText>()
        assertTrue(assistantMessages.isNotEmpty())
    }

    @Test
    fun `parseContent should parse file with file diff in tool results`() {
        val fixtureFile = fixturesDir.resolve("with_file_diff.json")
        if (!fixtureFile.toFile().exists()) return

        val content = fixtureFile.toFile().readText()
        val session = GeminiSessionParser.parseContent(content)

        assertNotNull(session)
        assertTrue(session.messages.isNotEmpty())

        val toolUseMessages = session.messages.filterIsInstance<ParsedMessage.ToolUse>()
        assertTrue(toolUseMessages.isNotEmpty())

        // Find a tool_use with results
        val toolWithResults = toolUseMessages.find { it.results.isNotEmpty() }
        assertNotNull(toolWithResults)
    }

    @Test
    fun `parseContent should handle content as string vs array`() {
        val fixtureFile = fixturesDir.resolve("with_file_diff.json")
        if (!fixtureFile.toFile().exists()) return

        val content = fixtureFile.toFile().readText()
        val session = GeminiSessionParser.parseContent(content)

        val userMessages = session.messages.filterIsInstance<ParsedMessage.User>()
        assertTrue(userMessages.isNotEmpty())

        val firstUserMsg = userMessages[0]
        assertTrue(firstUserMsg.content.isNotEmpty())
        assertTrue(firstUserMsg.content[0] is MessageContent.Text)
        assertTrue((firstUserMsg.content[0] as MessageContent.Text).text.contains("hello world"))
    }

    @Test
    fun `parseContent should extract timestamps`() {
        val fixtureFile = fixturesDir.resolve("user_message.json")
        if (!fixtureFile.toFile().exists()) return

        val content = fixtureFile.toFile().readText()
        val session = GeminiSessionParser.parseContent(content)

        assertNotNull(session.metadata)
        assertEquals("2026-02-08T17:00:00.000Z", session.metadata!!.created)
        assertEquals("2026-02-08T17:01:00.000Z", session.metadata!!.modified)
    }

    @Test
    fun `parseContent should truncate long titles`() {
        val longMessage = """
        {
            "sessionId": "test-long-title",
            "projectHash": "abc123",
            "startTime": "2026-02-08T17:00:00.000Z",
            "lastUpdated": "2026-02-08T17:00:00.000Z",
            "messages": [
                {
                    "id": "msg-001",
                    "timestamp": "2026-02-08T17:00:00.000Z",
                    "type": "user",
                    "content": "${"a".repeat(150)}"
                }
            ]
        }
        """.trimIndent()

        val session = GeminiSessionParser.parseContent(longMessage)
        assertTrue(session.title.length <= 103)
        assertTrue(session.title.endsWith("..."))
    }

    @Test
    fun `parseContent should use default title when no user message exists`() {
        val noUserMessage = """
        {
            "sessionId": "test-no-user",
            "projectHash": "abc123",
            "startTime": "2026-02-08T17:00:00.000Z",
            "lastUpdated": "2026-02-08T17:00:00.000Z",
            "messages": [
                {
                    "id": "msg-001",
                    "timestamp": "2026-02-08T17:00:00.000Z",
                    "type": "gemini",
                    "content": "Just an assistant message"
                }
            ]
        }
        """.trimIndent()

        val session = GeminiSessionParser.parseContent(noUserMessage)
        assertEquals("Gemini Session", session.title)
    }

    @Test
    fun `parseContent should handle empty thoughts array`() {
        val fixtureFile = fixturesDir.resolve("assistant_simple.json")
        if (!fixtureFile.toFile().exists()) return

        val content = fixtureFile.toFile().readText()
        val session = GeminiSessionParser.parseContent(content)

        val thinkingMessages = session.messages.filterIsInstance<ParsedMessage.AssistantThinking>()
        assertEquals(0, thinkingMessages.size)
    }

    @Test
    fun `parseContent should parse tool calls with input arguments`() {
        val fixtureFile = fixturesDir.resolve("mixed_conversation.json")
        if (!fixtureFile.toFile().exists()) return

        val content = fixtureFile.toFile().readText()
        val session = GeminiSessionParser.parseContent(content)

        val toolUseMessages = session.messages.filterIsInstance<ParsedMessage.ToolUse>()
        assertTrue(toolUseMessages.isNotEmpty())

        val readFileTool = toolUseMessages.find { it.toolName == "ReadFile" }
        assertNotNull(readFileTool)
        assertEquals("package.json", readFileTool!!.input["file_path"])
    }

    @Test
    fun `parseFile should return null for non-existent file`() {
        val result = GeminiSessionParser.parseFile("/nonexistent/path/session.json")
        assertNull(result)
    }
}

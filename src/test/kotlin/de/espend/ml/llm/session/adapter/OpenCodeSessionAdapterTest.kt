package de.espend.ml.llm.session.adapter

import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import de.espend.ml.llm.session.util.ToolInputFormatter
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths

class OpenCodeSessionAdapterTest {

    private val fixturesDir = Paths.get("src/test/kotlin/de/espend/ml/llm/session/adapter/fixtures")

    private fun contentToString(content: List<MessageContent>): String {
        return content.joinToString("\n") { block ->
            when (block) {
                is MessageContent.Text -> block.text
                is MessageContent.Code -> block.code
                is MessageContent.Markdown -> block.markdown
                is MessageContent.Json -> block.json
            }
        }
    }

    private fun contentToString(input: Map<String, String>, toolName: String): String {
        return ToolInputFormatter.formatToolInput(input)
    }

    private fun containsContent(content: List<MessageContent>, key: String, value: String? = null): Boolean {
        val fullText = contentToString(content)
        return fullText.contains(key) && (value == null || fullText.contains(value))
    }

    private fun containsContent(input: Map<String, String>, toolName: String, key: String, value: String? = null): Boolean {
        val fullText = contentToString(input, toolName)
        return fullText.contains(key) && (value == null || fullText.contains(value))
    }

    @Test
    fun `loadRawMessages should load messages and parts from fixtures`() {
        val sessionDir = fixturesDir.resolve("opencode/3f71d252cffezp6k0JstQT7lWe")

        val messages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertTrue("Should load messages", messages.isNotEmpty())
        assertEquals("Should have 7 messages", 7, messages.size)

        val userMessages = messages.filter { it.messageData?.role == "user" }
        val assistantMessages = messages.filter { it.messageData?.role == "assistant" }

        assertTrue("Should have user messages", userMessages.isNotEmpty())
        assertTrue("Should have assistant messages", assistantMessages.isNotEmpty())

        val messagesWithParts = messages.filter { it.parts.isNotEmpty() }
        assertTrue("Some messages should have parts", messagesWithParts.isNotEmpty())

        messages.forEach { msg ->
            assertNotNull("Each message should have rawContent", msg.rawContent)
            assertNotNull("Each message should have filePath", msg.filePath)
        }
    }

    @Test
    fun `loadRawMessages should return empty list for non-existent session`() {
        val sessionDir = fixturesDir.resolve("opencode/non_existent_session")

        val messages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertTrue("Should return empty list", messages.isEmpty())
    }

    @Test
    fun `loadRawMessages should sort messages by creation time`() {
        val sessionDir = fixturesDir.resolve("opencode/3f71d252cffezp6k0JstQT7lWe")

        val messages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        val creationTimes = messages.mapNotNull { it.messageData?.time?.created }
        val sortedTimes = creationTimes.sorted()

        assertEquals("Messages should be sorted by creation time", sortedTimes, creationTimes)
    }

    @Test
    fun `parseRawMessage should parse user message with text content`() {
        val sessionDir = fixturesDir.resolve("opencode/user_message")
        val rawMessages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertEquals("Should have 1 raw message", 1, rawMessages.size)

        val parsed = OpenCodeSessionAdapter.parseRawMessage(rawMessages[0])

        assertEquals("Should produce 1 message", 1, parsed.size)
        assertTrue("Message should be User type", parsed[0] is ParsedMessage.User)
        val userMsg = parsed[0] as ParsedMessage.User
        val content = contentToString(userMsg.content)
        assertEquals("Message content should be text", "Hello, this is a user message", content)
    }

    @Test
    fun `parseRawMessage should handle unknown role`() {
        val sessionDir = fixturesDir.resolve("opencode/unknown_role")
        val rawMessages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertEquals("Should have 1 raw message", 1, rawMessages.size)

        val parsed = OpenCodeSessionAdapter.parseRawMessage(rawMessages[0])

        assertEquals("Should produce 1 message", 1, parsed.size)
        assertTrue("Message should be Info type", parsed[0] is ParsedMessage.Info)
        val infoMsg = parsed[0] as ParsedMessage.Info
        assertEquals("Title should be the role", "system", infoMsg.title)
        assertTrue("Content should contain unknown role text", (infoMsg.content as? MessageContent.Json)?.json?.contains("role") == true)
    }

    @Test
    fun `parseRawMessage should handle parse error with null messageData`() {
        val sessionDir = fixturesDir.resolve("opencode/parse_error")
        val rawMessages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertEquals("Should have 1 raw message", 1, rawMessages.size)
        assertNull("messageData should be null for broken JSON", rawMessages[0].messageData)
        assertNotNull("parseError should be set", rawMessages[0].parseError)

        val parsed = OpenCodeSessionAdapter.parseRawMessage(rawMessages[0])

        assertEquals("Should produce 1 message", 1, parsed.size)
        assertTrue("Message should be Info type with ERROR style", parsed[0] is ParsedMessage.Info)
        val errorMsg = parsed[0] as ParsedMessage.Info
        assertEquals("Style should be ERROR", ParsedMessage.InfoStyle.ERROR, errorMsg.style)
        assertTrue("Content should contain failure text", (errorMsg.content as? MessageContent.Text)?.text?.contains("Failed to parse message file") == true)
    }

    @Test
    fun `parseRawMessage should parse assistant with text and reasoning parts`() {
        val sessionDir = fixturesDir.resolve("opencode/assistant_text_reasoning")
        val rawMessages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertEquals("Should have 1 raw message", 1, rawMessages.size)

        val parsed = OpenCodeSessionAdapter.parseRawMessage(rawMessages[0])
        assertEquals("Should produce 2 messages", 2, parsed.size)

        val textMessages = parsed.filterIsInstance<ParsedMessage.AssistantText>()
        assertEquals("Should have 1 text message", 1, textMessages.size)
        val textMsg = textMessages[0]
        val textContent = contentToString(textMsg.content)
        assertTrue("Text content should contain the response", textContent.contains("Here is my response to your question."))

        val reasoningMessages = parsed.filterIsInstance<ParsedMessage.AssistantThinking>()
        assertEquals("Should have 1 reasoning message", 1, reasoningMessages.size)
        val reasoningMsg = reasoningMessages[0]
        assertEquals("Reasoning content should match", "Let me think about this problem step by step...", reasoningMsg.thinking)
    }

    @Test
    fun `parseRawMessage should format Edit tool with structured parameters`() {
        val sessionDir = fixturesDir.resolve("opencode/assistant_tool_edit")
        val rawMessages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertEquals("Should have 1 raw message", 1, rawMessages.size)

        val parsed = OpenCodeSessionAdapter.parseRawMessage(rawMessages[0])

        val toolUseMessages = parsed.filterIsInstance<ParsedMessage.ToolUse>()
        assertEquals("Should have 1 tool_use message", 1, toolUseMessages.size)

        val toolUse = toolUseMessages[0]
        assertEquals("Tool name should be Edit", "Edit", toolUse.toolName)

        // Edit tool now stores parameters as Map
        assertTrue("Input should contain old_string key", toolUse.input.containsKey("old_string"))
        assertTrue("Input should contain new_string key", toolUse.input.containsKey("new_string"))
        assertNotNull("oldString should not be null", toolUse.input["old_string"])
        assertNotNull("newString should not be null", toolUse.input["new_string"])
    }

    @Test
    fun `parseRawMessage should format Bash tool with description and command`() {
        val sessionDir = fixturesDir.resolve("opencode/assistant_tool_bash")
        val rawMessages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertEquals("Should have 1 raw message", 1, rawMessages.size)

        val parsed = OpenCodeSessionAdapter.parseRawMessage(rawMessages[0])

        val toolUseMessages = parsed.filterIsInstance<ParsedMessage.ToolUse>()
        assertEquals("Should have 1 tool_use message", 1, toolUseMessages.size)

        val toolUse = toolUseMessages[0]
        assertEquals("Tool name should be Bash", "Bash", toolUse.toolName)

        assertTrue("Should contain description", containsContent(toolUse.input, toolUse.toolName, "description", "List files in directory"))
        assertTrue("Should contain command", containsContent(toolUse.input, toolUse.toolName, "command", "ls -la"))
    }

    @Test
    fun `parseRawMessage should nest ToolResult directly inside ToolUse`() {
        val sessionDir = fixturesDir.resolve("opencode/assistant_tool_bash")
        val rawMessages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        val parsed = OpenCodeSessionAdapter.parseRawMessage(rawMessages[0])

        // ToolResult should be nested directly inside ToolUse (no separate messages)
        val toolResultMessages = parsed.filterIsInstance<ParsedMessage.ToolResult>()
        assertEquals("Should have 0 standalone tool_result messages", 0, toolResultMessages.size)

        val toolUseMessages = parsed.filterIsInstance<ParsedMessage.ToolUse>()
        assertEquals("Should have 1 tool_use message", 1, toolUseMessages.size)

        val toolUse = toolUseMessages[0]
        assertTrue("ToolUse should have results", toolUse.hasResults())
        assertEquals("ToolUse should have 1 result", 1, toolUse.results.size)

        val toolResult = toolUse.results[0]
        val content = contentToString(toolResult.output)
        assertTrue("Should contain directory listing", content.contains("drwxr-xr-x"))
        assertTrue("Should contain file1.txt", content.contains("file1.txt"))
    }

    @Test
    fun `parseRawMessage should handle assistant message with error and 0 parts`() {
        val sessionDir = fixturesDir.resolve("opencode/assistant_error")
        val rawMessages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertEquals("Should have 1 raw message", 1, rawMessages.size)
        assertEquals("Should have 0 parts", 0, rawMessages[0].parts.size)
        assertNotNull("Should have error data", rawMessages[0].messageData?.error)

        val parsed = OpenCodeSessionAdapter.parseRawMessage(rawMessages[0])

        assertEquals("Should produce 1 message", 1, parsed.size)
        assertTrue("Message should be Info type with ERROR style", parsed[0] is ParsedMessage.Info)
        val errorMsg = parsed[0] as ParsedMessage.Info
        assertEquals("Style should be ERROR", ParsedMessage.InfoStyle.ERROR, errorMsg.style)
        assertEquals("Subtitle should be APIError", "APIError", errorMsg.subtitle)
        assertTrue("Content should be error message", (errorMsg.content as? MessageContent.Text)?.text?.contains("Key limit exceeded") == true)
    }

    @Test
    fun `parseRawMessage should format Edit tool with camelCase parameters`() {
        val sessionDir = fixturesDir.resolve("opencode/assistant_tool_edit_camelcase")
        val rawMessages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertEquals("Should have 1 raw message", 1, rawMessages.size)

        val parsed = OpenCodeSessionAdapter.parseRawMessage(rawMessages[0])

        val toolUseMessages = parsed.filterIsInstance<ParsedMessage.ToolUse>()
        assertEquals("Should have 1 tool_use message", 1, toolUseMessages.size)

        val toolUse = toolUseMessages[0]
        assertEquals("Tool name should be Edit", "Edit", toolUse.toolName)

        // Edit tool with camelCase parameters should still be parsed correctly
        assertTrue("Input should contain filePath key (camelCase)", toolUse.input.containsKey("filePath"))
        assertTrue("Input should contain oldString key (camelCase)", toolUse.input.containsKey("oldString"))
        assertTrue("Input should contain newString key (camelCase)", toolUse.input.containsKey("newString"))
        assertNotNull("filePath should not be null", toolUse.input["filePath"])
        assertNotNull("oldString should not be null", toolUse.input["oldString"])
        assertNotNull("newString should not be null", toolUse.input["newString"])
    }

    @Test
    fun `parseRawMessage should nest tool results for file edit session`() {
        val sessionDir = fixturesDir.resolve("opencode/ses_3e2c36a5affeiKvkpFK5CEOZ1W")
        val rawMessages = OpenCodeSessionAdapter.loadRawMessages(sessionDir)

        assertTrue("Should have messages", rawMessages.isNotEmpty())

        // Parse all messages - ToolResults should be nested directly
        val allParsed = rawMessages.flatMap { OpenCodeSessionAdapter.parseRawMessage(it) }

        // ToolResults should be nested inside ToolUse, not standalone
        val standaloneToolResults = allParsed.filterIsInstance<ParsedMessage.ToolResult>()
        assertEquals("Should have 0 standalone tool_result messages", 0, standaloneToolResults.size)

        // Find completed tool calls (those with results)
        val toolUseMessages = allParsed.filterIsInstance<ParsedMessage.ToolUse>()
        val completedTools = toolUseMessages.filter { it.hasResults() }
        assertTrue("Should have completed tool calls with results", completedTools.isNotEmpty())

        // Check edit tool specifically
        val editTools = completedTools.filter { it.toolName == "edit" }
        assertTrue("Should have edit tool calls", editTools.isNotEmpty())

        // Verify edit tool has result with output
        val editTool = editTools.first()
        assertTrue("Edit tool should have results", editTool.results.isNotEmpty())
        val editResult = editTool.results.first()
        assertTrue("Edit result should have output", editResult.output.isNotEmpty())
    }
}

package de.espend.ml.llm.session.adapter

import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import de.espend.ml.llm.session.util.ToolInputFormatter
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.readText

class ClaudeSessionAdapterTest {

    private val fixturesDir = Paths.get("src/test/kotlin/de/espend/ml/llm/session/adapter/fixtures/claude")

    private fun loadFixture(name: String): String {
        return fixturesDir.resolve("$name.jsonl").readText()
    }

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
    fun `parseSessionFile should parse user message`() {
        val content = loadFixture("user_message")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be User type", messages[0] is ParsedMessage.User)
        val msg = messages[0] as ParsedMessage.User
        val msgContent = contentToString(msg.content)
        assertEquals("Message content should match", "Hello, this is a user message", msgContent)
        assertEquals("Timestamp should match", "2024-01-15T10:00:00.000Z", msg.timestamp)
    }

    @Test
    fun `parseSessionFile should parse assistant text message`() {
        val content = loadFixture("assistant_text")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be AssistantText type", messages[0] is ParsedMessage.AssistantText)
        val msg = messages[0] as ParsedMessage.AssistantText
        val msgContent = contentToString(msg.content)
        assertTrue("Content should contain the response", msgContent.contains("Here is my response to your question."))
    }

    @Test
    fun `parseSessionFile should parse assistant tool_use message`() {
        val content = loadFixture("assistant_tool_use")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolUse type", messages[0] is ParsedMessage.ToolUse)
        val msg = messages[0] as ParsedMessage.ToolUse
        assertEquals("Tool name should be Bash", "Bash", msg.toolName)
        assertTrue("Content should contain the command", containsContent(msg.input, msg.toolName, "command", "ls -la"))
        assertTrue("Content should contain the description", containsContent(msg.input, msg.toolName, "description", "List files in directory"))
    }

    @Test
    fun `parseSessionFile should parse tool_result in user message with correct display type`() {
        val content = loadFixture("tool_result_in_user")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolResult type", messages[0] is ParsedMessage.ToolResult)
        val msg = messages[0] as ParsedMessage.ToolResult
        assertEquals("ToolCallId should contain tool_use_id", "toolu_01ABC", msg.toolCallId)
        val msgContent = contentToString(msg.output)
        assertTrue("Content should contain directory listing", msgContent.contains("drwxr-xr-x"))
    }

    @Test
    fun `parseSessionFile should parse assistant thinking message`() {
        val content = loadFixture("assistant_thinking")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be AssistantThinking type", messages[0] is ParsedMessage.AssistantThinking)
        val msg = messages[0] as ParsedMessage.AssistantThinking
        assertTrue("Content should contain thinking text", msg.thinking.contains("Let me analyze this step by step"))
    }

    @Test
    fun `parseSessionFile should parse mixed conversation`() {
        val content = loadFixture("mixed_conversation")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 3 messages", 3, messages.size)

        assertTrue("First message should be User type", messages[0] is ParsedMessage.User)
        val firstContent = contentToString((messages[0] as ParsedMessage.User).content)
        assertTrue("First message should ask about files", firstContent.contains("List the files"))

        assertTrue("Second message should be ToolUse type", messages[1] is ParsedMessage.ToolUse)
        val toolUse = messages[1] as ParsedMessage.ToolUse
        // ToolUse input contains the command and description, not the assistant text
        assertTrue("Second message should contain command", containsContent(toolUse.input, toolUse.toolName, "command", "ls -la"))
        assertEquals("Second message tool name should be Bash", "Bash", toolUse.toolName)
        assertEquals("Should have toolCallId", "toolu_01XYZ", toolUse.toolCallId)

        assertTrue("ToolUse should have connected results", toolUse.hasResults())
        assertEquals("ToolUse should have 1 result", 1, toolUse.results.size)
        val toolResult = toolUse.results[0]
        assertEquals("ToolResult should have matching toolCallId", "toolu_01XYZ", toolResult.toolCallId)
        val resultContent = contentToString(toolResult.output)
        assertTrue("Tool result should contain file list", resultContent.contains("file1.txt"))

        assertTrue("Third message should be AssistantText type", messages[2] is ParsedMessage.AssistantText)
        val thirdContent = contentToString((messages[2] as ParsedMessage.AssistantText).content)
        assertTrue("Third message should contain file list", thirdContent.contains("file1.txt"))
    }

    @Test
    fun `parseSessionFile should connect tool_use with tool_result when tool_result comes first`() {
        val content = loadFixture("mixed_conversation_with_tool_connection")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 2 messages when tool_result is connected to ToolUse", 2, messages.size)

        val toolUse = messages.find { it is ParsedMessage.ToolUse } as? ParsedMessage.ToolUse
        assertNotNull("Should have a ToolUse message", toolUse)
        assertEquals("Tool name should be Bash", "Bash", toolUse!!.toolName)
        assertEquals("Should have toolCallId", "toolu_01XYZ", toolUse.toolCallId)

        assertTrue("ToolUse should have connected results", toolUse.hasResults())
        assertEquals("ToolUse should have 1 result", 1, toolUse.results.size)
        val toolResult = toolUse.results[0]
        val resultContent = contentToString(toolResult.output)
        assertTrue("Tool result should contain file list", resultContent.contains("file1.txt"))
        assertEquals("Tool result should have matching toolCallId", "toolu_01XYZ", toolResult.toolCallId)

        val assistantText = messages.find { it is ParsedMessage.AssistantText }
        assertNotNull("Should have an AssistantText message", assistantText)
    }

    @Test
    fun `parseSessionFile should connect tool_use with tool_result in same assistant message`() {
        val content = """
            {"type":"user","timestamp":"2024-01-15T10:00:00.000Z","message":{"content":[{"type":"text","text":"List the files"}]},"uuid":"uuid-001"}
            {"type":"assistant","timestamp":"2024-01-15T10:01:00.000Z","message":{"content":[{"type":"text","text":"I'll list the files."},{"type":"tool_use","id":"toolu_01ABC","name":"Bash","input":{"command":"ls -la"}},{"type":"tool_result","tool_use_id":"toolu_01ABC","content":"file1.txt\\nfile2.txt"}]},"uuid":"uuid-002"}
        """.trimIndent()

        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 2 messages", 2, messages.size)

        assertTrue("First message should be User type", messages[0] is ParsedMessage.User)

        assertTrue("Second message should be ToolUse type", messages[1] is ParsedMessage.ToolUse)
        val toolUse = messages[1] as ParsedMessage.ToolUse
        assertEquals("Tool name should be Bash", "Bash", toolUse.toolName)
        assertEquals("Should have toolCallId", "toolu_01ABC", toolUse.toolCallId)

        assertTrue("ToolUse should have connected results", toolUse.hasResults())
        assertEquals("ToolUse should have 1 result", 1, toolUse.results.size)
        val toolResult = toolUse.results[0]
        val resultContent = contentToString(toolResult.output)
        assertTrue("Tool result should contain file list", resultContent.contains("file1.txt"))
        assertEquals("Tool result should have matching toolCallId", "toolu_01ABC", toolResult.toolCallId)
    }

    @Test
    fun `parseSessionFile should extract metadata`() {
        val content = loadFixture("mixed_conversation")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertNotNull("Metadata should not be null", metadata)
        assertEquals("Git branch should be main", "main", metadata?.gitBranch)
        assertEquals("CWD should match", "/home/user/project", metadata?.cwd)
        assertEquals("Message count should be 4", 4, metadata?.messageCount)
    }

    @Test
    fun `parseSessionFile should track model usage`() {
        val content = loadFixture("mixed_conversation")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertNotNull("Metadata should not be null", metadata)
        val models = metadata?.models
        assertNotNull("Models should not be null", models)
        assertTrue("Should have at least one model", models!!.isNotEmpty())
        assertEquals("Model should be claude-3-opus", "claude-3-opus-20240229", models[0].first)
    }

    @Test
    fun `parseSessionFile should have metadata`() {
        val content = loadFixture("mixed_conversation")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertNotNull("Metadata should not be null", metadata)
        assertEquals("Message count should be correct", 4, metadata?.messageCount)
    }

    @Test
    fun `parseSessionFile should keep user type when text and tool_result are mixed`() {
        val content = loadFixture("user_with_text_and_tool_result")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be User type", messages[0] is ParsedMessage.User)
        val msg = messages[0] as ParsedMessage.User
        val msgContent = contentToString(msg.content)
        assertTrue("Content should contain text", msgContent.contains("Here is the result"))
    }

    @Test
    fun `parseSessionFile should handle empty content`() {
        val content = ""
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertTrue("Messages should be empty", messages.isEmpty())
    }

    @Test
    fun `parseSessionFile should handle invalid JSON gracefully`() {
        val content = "not valid json\n{\"type\":\"user\",\"timestamp\":\"2024-01-15T10:00:00Z\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"valid\"}]}}"
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message from valid line", 1, messages.size)
        assertTrue("Message should be User type", messages[0] is ParsedMessage.User)
    }

    @Test
    fun `parseSessionFile should format Edit tool with structured parameters`() {
        val content = loadFixture("assistant_tool_use_edit")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolUse type", messages[0] is ParsedMessage.ToolUse)
        val msg = messages[0] as ParsedMessage.ToolUse
        assertEquals("Tool name should be Edit", "Edit", msg.toolName)

        // Edit tool now stores parameters as Map
        assertTrue("Input should contain old_string key", msg.input.containsKey("old_string"))
        assertTrue("Input should contain new_string key", msg.input.containsKey("new_string"))
        assertTrue("oldString should contain oldFunction", msg.input["old_string"]?.contains("oldFunction") == true)
        assertTrue("newString should contain newFunction", msg.input["new_string"]?.contains("newFunction") == true)
    }

    @Test
    fun `parseSessionFile should format Read tool with structured parameters`() {
        val content = loadFixture("assistant_tool_use_read")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolUse type", messages[0] is ParsedMessage.ToolUse)
        val msg = messages[0] as ParsedMessage.ToolUse
        assertEquals("Tool name should be Read", "Read", msg.toolName)

        assertTrue("Should contain file_path", containsContent(msg.input, msg.toolName, "file_path"))
        assertTrue("Should contain offset", containsContent(msg.input, msg.toolName, "offset", "0"))
        assertTrue("Should contain limit", containsContent(msg.input, msg.toolName, "limit", "100"))
    }

    @Test
    fun `parseSessionFile should format Write tool with structured parameters`() {
        val content = loadFixture("assistant_tool_use_write")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolUse type", messages[0] is ParsedMessage.ToolUse)
        val msg = messages[0] as ParsedMessage.ToolUse
        assertEquals("Tool name should be Write", "Write", msg.toolName)

        assertTrue("Should contain file_path", containsContent(msg.input, msg.toolName, "file_path"))
        assertTrue("Should contain content", containsContent(msg.input, msg.toolName, "content"))
    }

    @Test
    fun `parseSessionFile should format Glob tool with structured parameters`() {
        val content = loadFixture("assistant_tool_use_glob")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolUse type", messages[0] is ParsedMessage.ToolUse)
        val msg = messages[0] as ParsedMessage.ToolUse
        assertEquals("Tool name should be Glob", "Glob", msg.toolName)

        assertTrue("Should contain pattern", containsContent(msg.input, msg.toolName, "pattern"))
        assertTrue("Should contain path", containsContent(msg.input, msg.toolName, "path"))
    }

    @Test
    fun `parseSessionFile should format Bash tool with description and command`() {
        val content = loadFixture("assistant_tool_use")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolUse type", messages[0] is ParsedMessage.ToolUse)
        val msg = messages[0] as ParsedMessage.ToolUse
        assertEquals("Tool name should be Bash", "Bash", msg.toolName)

        assertTrue("Should contain description", containsContent(msg.input, msg.toolName, "description", "List files in directory"))
        assertTrue("Should contain command", containsContent(msg.input, msg.toolName, "command", "ls -la"))
    }

    @Test
    fun `parseSessionFile should parse system turn_duration subtype with formatted duration`() {
        val content = loadFixture("system_turn_duration")
        val (messages, metadata) = ClaudeSessionAdapter.parseSessionFile(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be Info type", messages[0] is ParsedMessage.Info)
        val msg = messages[0] as ParsedMessage.Info
        assertEquals("Title should be duration", "duration", msg.title)
        assertEquals("Subtitle should be turn_duration", "turn_duration", msg.subtitle)

        // 190362ms = 190 seconds = 3m 10s
        val msgContent = msg.content
        assertTrue("Content should be Text", msgContent is MessageContent.Text)
        assertEquals("Duration should be formatted as 3m 10s", "3m 10s", (msgContent as MessageContent.Text).text)
    }

    // ============ parseJsonlMetadata tests ============

    @Test
    fun `parseJsonlMetadata should extract summary from string content format`() {
        val content = """
            {"type":"user","timestamp":"2026-01-15T10:00:00.000Z","message":{"role":"user","content":"Hello world, this is a string content"}}
        """.trimIndent()

        val metadata = ClaudeSessionAdapter.parseJsonlMetadata(content)

        assertEquals("Should extract summary", "Hello world, this is a string content", metadata.summary)
        assertEquals("Should have 1 message", 1, metadata.messageCount)
        assertEquals("Should extract created timestamp", "2026-01-15T10:00:00.000Z", metadata.created)
    }

    @Test
    fun `parseJsonlMetadata should extract summary from array content format`() {
        val content = """
            {"type":"user","timestamp":"2026-01-15T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"Hello from array format"}]}}
        """.trimIndent()

        val metadata = ClaudeSessionAdapter.parseJsonlMetadata(content)

        assertEquals("Should extract summary from array", "Hello from array format", metadata.summary)
    }

    @Test
    fun `parseJsonlMetadata should truncate long summary`() {
        val longText = "A".repeat(150)
        val content = """
            {"type":"user","timestamp":"2026-01-15T10:00:00.000Z","message":{"role":"user","content":"$longText"}}
        """.trimIndent()

        val metadata = ClaudeSessionAdapter.parseJsonlMetadata(content)

        assertEquals("Summary should be truncated to 100 chars + ...", "A".repeat(100) + "...", metadata.summary)
    }

    @Test
    fun `parseJsonlMetadata should extract timestamps from all lines`() {
        val content = """
            {"type":"user","timestamp":"2026-01-15T10:00:00.000Z","message":{"content":"First"}}
            {"type":"assistant","timestamp":"2026-01-15T10:01:00.000Z","message":{"content":"Response"}}
            {"type":"user","timestamp":"2026-01-15T10:02:00.000Z","message":{"content":"Second"}}
        """.trimIndent()

        val metadata = ClaudeSessionAdapter.parseJsonlMetadata(content)

        assertEquals("Should extract created timestamp", "2026-01-15T10:00:00.000Z", metadata.created)
        assertEquals("Should extract modified timestamp (last)", "2026-01-15T10:02:00.000Z", metadata.modified)
        assertEquals("Should count 3 messages", 3, metadata.messageCount)
    }

    @Test
    fun `parseJsonlMetadata should only parse first 10 lines for summary`() {
        // Create content with user message after 10 lines
        val lines = (1..15).map { i ->
            if (i == 12) {
                """{"type":"user","timestamp":"2026-01-15T10:${i}:00.000Z","message":{"content":"User message at line 12"}}"""
            } else {
                """{"type":"assistant","timestamp":"2026-01-15T10:${i}:00.000Z","message":{"content":"Message $i"}}"""
            }
        }
        val content = lines.joinToString("\n")

        val metadata = ClaudeSessionAdapter.parseJsonlMetadata(content)

        // summary should be null because user message is beyond first 10 lines
        assertNull("summary should be null (user message beyond first 10 lines)", metadata.summary)
        assertEquals("Should count all 15 messages", 15, metadata.messageCount)
    }

    @Test
    fun `parseJsonlMetadata should handle empty content`() {
        val metadata = ClaudeSessionAdapter.parseJsonlMetadata("")

        assertNull("summary should be null", metadata.summary)
        assertEquals("messageCount should be 0", 0, metadata.messageCount)
    }

    @Test
    fun `parseJsonlMetadata should skip non-user messages for summary`() {
        val content = """
            {"type":"assistant","timestamp":"2026-01-15T10:00:00.000Z","message":{"content":"Assistant first"}}
            {"type":"user","timestamp":"2026-01-15T10:01:00.000Z","message":{"content":"User message"}}
        """.trimIndent()

        val metadata = ClaudeSessionAdapter.parseJsonlMetadata(content)

        assertEquals("Should extract user message as summary", "User message", metadata.summary)
    }

    @Test
    fun `extractTimestamp should extract timestamp from JSON line`() {
        val line = """{"type":"user","timestamp":"2026-01-15T10:00:00.000Z","message":{"content":"Test"}}"""

        val timestamp = ClaudeSessionAdapter.extractTimestamp(line)

        assertEquals("Should extract timestamp", "2026-01-15T10:00:00.000Z", timestamp)
    }

    @Test
    fun `extractTimestamp should return null for line without timestamp`() {
        val line = """{"type":"user","message":{"content":"Test"}}"""

        val timestamp = ClaudeSessionAdapter.extractTimestamp(line)

        assertNull("Should return null for missing timestamp", timestamp)
    }

    @Test
    fun `parseJsonlMetadata should skip command messages for summary`() {
        val content = """
            {"type":"user","timestamp":"2026-01-15T10:00:00.000Z","message":{"content":"<command-name>/clear</command-name>\n<command-message>clear</command-message>"}}
            {"type":"user","timestamp":"2026-01-15T10:01:00.000Z","message":{"content":"My actual question"}}
        """.trimIndent()

        val metadata = ClaudeSessionAdapter.parseJsonlMetadata(content)

        assertEquals("Should skip command and use real user message", "My actual question", metadata.summary)
    }

    @Test
    fun `parseJsonlMetadata should skip local-command-stdout for summary`() {
        val content = """
            {"type":"user","timestamp":"2026-01-15T10:00:00.000Z","message":{"content":"<local-command-stdout></local-command-stdout>"}}
            {"type":"user","timestamp":"2026-01-15T10:01:00.000Z","message":{"content":"Real user message"}}
        """.trimIndent()

        val metadata = ClaudeSessionAdapter.parseJsonlMetadata(content)

        assertEquals("Should skip local-command-stdout and use real user message", "Real user message", metadata.summary)
    }

    @Test
    fun `parseJsonlMetadata should skip isMeta messages for summary`() {
        val content = """
            {"type":"user","isMeta":true,"timestamp":"2026-01-15T10:00:00.000Z","message":{"content":"<local-command-caveat>Some caveat</local-command-caveat>"}}
            {"type":"user","timestamp":"2026-01-15T10:01:00.000Z","message":{"content":"User question after meta"}}
        """.trimIndent()

        val metadata = ClaudeSessionAdapter.parseJsonlMetadata(content)

        assertEquals("Should skip isMeta and use real user message", "User question after meta", metadata.summary)
    }

    @Test
    fun `parseJsonlMetadata should skip all command-related messages for summary`() {
        val content = """
            {"type":"user","isMeta":true,"timestamp":"2026-01-15T10:00:00.000Z","message":{"content":"<local-command-caveat>Caveat</local-command-caveat>"}}
            {"type":"user","timestamp":"2026-01-15T10:00:01.000Z","message":{"content":"<command-name>/clear</command-name>"}}
            {"type":"user","timestamp":"2026-01-15T10:00:02.000Z","message":{"content":"<local-command-stdout></local-command-stdout>"}}
            {"type":"user","timestamp":"2026-01-15T10:01:00.000Z","message":{"content":"This is the real question"}}
        """.trimIndent()

        val metadata = ClaudeSessionAdapter.parseJsonlMetadata(content)

        assertEquals("Should skip all command-related messages", "This is the real question", metadata.summary)
        assertEquals("Should count all 4 messages", 4, metadata.messageCount)
    }
}

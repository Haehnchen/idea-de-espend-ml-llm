package de.espend.ml.llm.session.adapter

import de.espend.ml.llm.session.adapter.codex.CodexSessionFinder
import de.espend.ml.llm.session.adapter.codex.CodexSessionParser
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.readText

class CodexSessionAdapterTest {

    private val fixturesDir = Paths.get("src/test/kotlin/de/espend/ml/llm/session/adapter/fixtures/codex")

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

    // ============ Session Meta Tests ============

    @Test
    fun `parseContent should extract session metadata`() {
        val content = loadFixture("session_meta")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertNotNull("Metadata should not be null", metadata)
        assertEquals("CWD should match", "/home/user/project", metadata?.cwd)
        assertEquals("Git branch should be main", "main", metadata?.gitBranch)
        assertEquals("Version should match", "1.0.0", metadata?.version)
    }

    // ============ Event Message Tests ============

    @Test
    fun `parseContent should skip user_message event (duplicate of response_item)`() {
        val content = loadFixture("user_message")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        // user_message from event_msg is skipped because response_item/message contains the same data
        assertEquals("Should have 0 messages (user_message is skipped)", 0, messages.size)
    }

    @Test
    fun `parseContent should skip agent_message event (duplicate of response_item)`() {
        val content = loadFixture("agent_message")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        // agent_message from event_msg is skipped because response_item/message contains the same data
        assertEquals("Should have 0 messages (agent_message is skipped)", 0, messages.size)
    }

    @Test
    fun `parseContent should skip agent_reasoning event (duplicate of response_item)`() {
        val content = loadFixture("agent_reasoning")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        // agent_reasoning from event_msg is skipped because response_item/reasoning contains the same data
        assertEquals("Should have 0 messages (agent_reasoning is skipped)", 0, messages.size)
    }

    // ============ Response Item Tests ============

    @Test
    fun `parseContent should parse function_call response`() {
        val content = loadFixture("function_call")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolUse type", messages[0] is ParsedMessage.ToolUse)

        val msg = messages[0] as ParsedMessage.ToolUse
        assertEquals("Tool name should be shell", "shell", msg.toolName)
        assertEquals("Call ID should match", "call_ABC123", msg.toolCallId)
        assertTrue("Input should contain command", msg.input.containsKey("command"))
        assertEquals("Command should be ls -la", "ls -la", msg.input["command"])
    }

    @Test
    fun `parseContent should parse function_call_output response`() {
        val content = loadFixture("function_call_output")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolResult type", messages[0] is ParsedMessage.ToolResult)

        val msg = messages[0] as ParsedMessage.ToolResult
        assertEquals("Call ID should match", "call_ABC123", msg.toolCallId)
        val outputContent = contentToString(msg.output)
        assertTrue("Output should contain file listing", outputContent.contains("file1.txt"))
    }

    @Test
    fun `parseContent should parse custom_tool_call response`() {
        val content = loadFixture("custom_tool_call")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolUse type", messages[0] is ParsedMessage.ToolUse)

        val msg = messages[0] as ParsedMessage.ToolUse
        assertEquals("Tool name should be create_file", "create_file", msg.toolName)
        assertEquals("Call ID should match", "call_XYZ789", msg.toolCallId)
    }

    @Test
    fun `parseContent should parse custom_tool_call_output response`() {
        val content = loadFixture("custom_tool_call_output")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be ToolResult type", messages[0] is ParsedMessage.ToolResult)

        val msg = messages[0] as ParsedMessage.ToolResult
        assertEquals("Call ID should match", "call_XYZ789", msg.toolCallId)
        val outputContent = contentToString(msg.output)
        assertTrue("Output should contain success message", outputContent.contains("Success"))
    }

    @Test
    fun `parseContent should parse reasoning response`() {
        val content = loadFixture("reasoning")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals("Should have 1 message", 1, messages.size)
        assertTrue("Message should be AssistantThinking type", messages[0] is ParsedMessage.AssistantThinking)

        val msg = messages[0] as ParsedMessage.AssistantThinking
        assertTrue("Content should contain reasoning text", msg.thinking.contains("Analyzing the code structure"))
        assertTrue("Content should contain second reasoning", msg.thinking.contains("Identifying potential issues"))
    }

    // ============ Tool Connection Tests ============

    @Test
    fun `parseContent should connect function_call with function_call_output`() {
        val content = """
            {"timestamp":"2024-01-15T10:00:00.000Z","type":"response_item","payload":{"type":"function_call","name":"shell","call_id":"call_CONNECT","arguments":"{\"command\":\"echo hello\"}"}}
            {"timestamp":"2024-01-15T10:01:00.000Z","type":"response_item","payload":{"type":"function_call_output","call_id":"call_CONNECT","output":"hello"}}
        """.trimIndent()

        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals("Should have 1 message (connected)", 1, messages.size)
        assertTrue("Message should be ToolUse type", messages[0] is ParsedMessage.ToolUse)

        val toolUse = messages[0] as ParsedMessage.ToolUse
        assertTrue("ToolUse should have results", toolUse.hasResults())
        assertEquals("ToolUse should have 1 result", 1, toolUse.results.size)

        val result = toolUse.results[0]
        val resultContent = contentToString(result.output)
        assertTrue("Result should contain output", resultContent.contains("hello"))
    }

    // ============ Mixed Conversation Tests ============

    @Test
    fun `parseContent should parse mixed conversation`() {
        val content = loadFixture("mixed_conversation")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        // Should have: agent_reasoning, function_call (connected with output)
        // Note: user_message, agent_message, and agent_reasoning from event_msg are skipped
        assertTrue("Should have at least 1 message", messages.size >= 1)

        // Check metadata
        assertNotNull("Metadata should not be null", metadata)
        assertEquals("CWD should match", "/home/user/project", metadata?.cwd)
        assertEquals("Git branch should be main", "main", metadata?.gitBranch)

        // Check model tracking
        assertTrue("Should have model usage", metadata?.models?.isNotEmpty() == true)
        assertEquals("Model should be gpt-4o", "gpt-4o", metadata?.models?.firstOrNull()?.first)

        // Check tool use
        val toolUse = messages.filterIsInstance<ParsedMessage.ToolUse>().firstOrNull()
        assertNotNull("Should have tool use", toolUse)
        assertEquals("Tool should be shell", "shell", toolUse!!.toolName)
        assertTrue("ToolUse should have connected results", toolUse.hasResults())
    }

    // ============ Metadata Parsing Tests ============

    @Test
    fun `parseJsonlMetadata should extract summary from user_message`() {
        val content = loadFixture("user_message")
        val metadata = CodexSessionParser.parseJsonlMetadata(content)

        assertEquals("Summary should match", "Hello, please help me with my code", metadata.summary)
        assertEquals("Should have 1 message", 1, metadata.messageCount)
    }

    @Test
    fun `parseJsonlMetadata should extract session metadata`() {
        val content = loadFixture("mixed_conversation")
        val metadata = CodexSessionParser.parseJsonlMetadata(content)

        assertEquals("CWD should match", "/home/user/project", metadata.cwd)
        assertEquals("Git branch should be main", "main", metadata.gitBranch)
        assertNotNull("Created timestamp should not be null", metadata.created)
        assertNotNull("Modified timestamp should not be null", metadata.modified)
    }

    @Test
    fun `parseJsonlMetadata should handle empty content`() {
        val metadata = CodexSessionParser.parseJsonlMetadata("")

        assertNull("Summary should be null", metadata.summary)
        assertEquals("Message count should be 0", 0, metadata.messageCount)
    }

    // ============ Session ID Extraction Tests ============

    @Test
    fun `extractSessionId should extract UUID from filename`() {
        val testFile = java.io.File("/tmp/rollout-2026-01-26T12-27-20-019bfa0e-ec98-70e0-8575-5132b767abff.jsonl")

        val sessionId = CodexSessionFinder.extractSessionId(testFile)

        assertEquals("Session ID should be extracted", "019bfa0e-ec98-70e0-8575-5132b767abff", sessionId)
    }

    @Test
    fun `extractSessionId should return null for invalid filename`() {
        val testFile = java.io.File("/tmp/invalid-file.jsonl")

        val sessionId = CodexSessionFinder.extractSessionId(testFile)

        assertNull("Session ID should be null for invalid filename", sessionId)
    }

    // ============ Edge Cases ============

    @Test
    fun `parseContent should handle invalid JSON gracefully`() {
        val content = """
            not valid json
            {"timestamp":"2024-01-15T10:00:00.000Z","type":"response_item","payload":{"type":"reasoning","summary":[{"text":"Let me think about this step by step"}]}}
        """.trimIndent()

        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals("Should have 1 message from valid line", 1, messages.size)
        assertTrue("Message should be AssistantThinking type", messages[0] is ParsedMessage.AssistantThinking)
    }

    @Test
    fun `parseContent should skip token_count events`() {
        val content = """
            {"timestamp":"2024-01-15T10:00:00.000Z","type":"event_msg","payload":{"type":"token_count","input_tokens":100,"output_tokens":200}}
        """.trimIndent()

        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertTrue("Messages should be empty (token_count skipped)", messages.isEmpty())
    }
}

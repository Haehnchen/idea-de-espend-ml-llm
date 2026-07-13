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
    fun `parseContent should use user_message event as fallback`() {
        val content = loadFixture("user_message")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals("Should retain event-only user messages", 1, messages.size)
        assertTrue(messages[0] is ParsedMessage.User)
    }

    @Test
    fun `parseContent should use agent_message event as fallback`() {
        val content = loadFixture("agent_message")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals("Should retain event-only assistant messages", 1, messages.size)
        assertTrue(messages[0] is ParsedMessage.AssistantText)
    }

    @Test
    fun `parseContent should use agent_reasoning event as fallback`() {
        val content = loadFixture("agent_reasoning")
        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals("Should retain event-only reasoning summaries", 1, messages.size)
        assertTrue(messages[0] is ParsedMessage.AssistantThinking)
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

    @Test
    fun `parseContent should parse current Codex messages and array tool output`() {
        val content = """
            {"timestamp":"2026-07-13T15:48:00.000Z","type":"session_meta","payload":{"id":"current-session","cwd":"/home/user/project","cli_version":"0.144.3","git":{"branch":"main"}}}
            {"timestamp":"2026-07-13T15:48:01.000Z","type":"turn_context","payload":{"model":"gpt-5.6-sol","summary":"auto"}}
            {"timestamp":"2026-07-13T15:48:02.000Z","type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"Please shorten the reset label"}]}}
            {"timestamp":"2026-07-13T15:48:02.100Z","type":"event_msg","payload":{"type":"user_message","message":"Please shorten the reset label"}}
            {"timestamp":"2026-07-13T15:48:03.000Z","type":"event_msg","payload":{"type":"agent_message","message":"I will inspect the usage panel."}}
            {"timestamp":"2026-07-13T15:48:03.100Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[{"type":"output_text","text":"I will inspect the usage panel."}],"phase":"commentary"}}
            {"timestamp":"2026-07-13T15:48:04.000Z","type":"response_item","payload":{"type":"custom_tool_call","name":"exec","call_id":"call_1","input":"const r = await tools.exec_command({cmd: 'rg reset'});"}}
            {"timestamp":"2026-07-13T15:48:04.100Z","type":"response_item","payload":{"type":"custom_tool_call_output","call_id":"call_1","output":[{"type":"input_text","text":"Script completed\nOutput:\n"},{"type":"input_text","text":"CodexUsageProvider.kt:447"}]}}
            {"timestamp":"2026-07-13T15:48:05.000Z","type":"response_item","payload":{"type":"reasoning","summary":[{"type":"summary_text","text":"Checking the existing wording"}],"encrypted_content":"encrypted"}}
            {"timestamp":"2026-07-13T15:48:06.000Z","type":"event_msg","payload":{"type":"agent_message","message":"The label is shorter."}}
            {"timestamp":"2026-07-13T15:48:06.100Z","type":"response_item","payload":{"type":"message","role":"assistant","content":[{"type":"output_text","text":"The label is shorter.\n\n<oai-mem-citation>internal</oai-mem-citation>"}],"phase":"final_answer"}}
        """.trimIndent()

        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals(5, messages.size)
        assertTrue(messages[0] is ParsedMessage.User)
        assertTrue(messages[1] is ParsedMessage.AssistantText)
        assertTrue(messages[2] is ParsedMessage.ToolUse)
        assertTrue(messages[3] is ParsedMessage.AssistantThinking)
        assertTrue(messages[4] is ParsedMessage.AssistantText)

        val commentary = messages[1] as ParsedMessage.AssistantText
        assertEquals("commentary", commentary.displayType)
        assertEquals(ParsedMessage.AssistantTextStyle.STATUS, commentary.style)
        assertEquals("Please shorten the reset label", contentToString((messages[0] as ParsedMessage.User).content))

        val toolUse = messages[2] as ParsedMessage.ToolUse
        assertEquals(1, toolUse.results.size)
        val toolOutput = contentToString(toolUse.results.single().output)
        assertTrue(toolOutput.contains("Script completed"))
        assertTrue(toolOutput.contains("CodexUsageProvider.kt:447"))

        val finalAnswer = messages[4] as ParsedMessage.AssistantText
        assertEquals("final_answer", finalAnswer.displayType)
        assertEquals(ParsedMessage.AssistantTextStyle.RESULT, finalAnswer.style)
        val finalText = contentToString(finalAnswer.content)
        assertEquals("The label is shorter.", finalText)
        assertEquals(5, metadata?.messageCount)
        assertEquals("gpt-5.6-sol", metadata?.models?.single()?.first)

        val listMetadata = CodexSessionParser.parseJsonlMetadata(content)
        assertEquals("Please shorten the reset label", listMetadata.summary)
        assertEquals(5, listMetadata.messageCount)
    }

    @Test
    fun `parseContent should use public user events instead of response turn context`() {
        val content = """
            {"timestamp":"2026-07-13T15:48:02.000Z","type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"Internal-looking but unclassified context"},{"type":"input_image","image_url":"data:image/png;base64,omitted"}]}}
            {"timestamp":"2026-07-13T15:48:02.100Z","type":"event_msg","payload":{"type":"user_message","message":"Public user text"}}
        """.trimIndent()

        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals(1, messages.size)
        assertEquals("Public user text", contentToString((messages.single() as ParsedMessage.User).content))
        assertEquals(1, metadata?.messageCount)
    }

    @Test
    fun `parseContent should preserve response user content when public events are unavailable`() {
        val content = """
            {"timestamp":"2026-07-13T15:48:02.000Z","type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"Unclassified response-only content"},{"type":"input_image","image_url":"data:image/png;base64,omitted"}]}}
        """.trimIndent()

        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals(1, messages.size)
        assertEquals("Unclassified response-only content\n[image]", contentToString((messages.single() as ParsedMessage.User).content))
        assertEquals(1, metadata?.messageCount)
    }

    @Test
    fun `parseJsonlMetadata should prefer the public user event for the session summary`() {
        val content = """
            {"timestamp":"2026-07-13T15:48:01.000Z","type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"Internal turn context"}]}}
            {"timestamp":"2026-07-13T15:48:02.000Z","type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"Public user text"}]}}
            {"timestamp":"2026-07-13T15:48:02.100Z","type":"event_msg","payload":{"type":"user_message","message":"Public user text"}}
        """.trimIndent()

        val metadata = CodexSessionParser.parseJsonlMetadata(content)

        assertEquals("Public user text", metadata.summary)
        assertEquals(1, metadata.messageCount)
    }

    @Test
    fun `parseContent should parse array function output`() {
        val content = """
            {"timestamp":"2026-07-13T15:48:00.000Z","type":"response_item","payload":{"type":"function_call","name":"exec_command","call_id":"call_array","arguments":"{\"cmd\":\"pwd\"}"}}
            {"timestamp":"2026-07-13T15:48:01.000Z","type":"response_item","payload":{"type":"function_call_output","call_id":"call_array","output":[{"type":"input_text","text":"Command completed\n"},{"type":"input_text","text":"/home/user/project"}]}}
        """.trimIndent()

        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals(1, messages.size)
        val toolUse = messages.single() as ParsedMessage.ToolUse
        assertEquals("exec_command", toolUse.toolName)
        assertEquals(1, toolUse.results.size)
        assertTrue(contentToString(toolUse.results.single().output).contains("/home/user/project"))
        assertEquals(1, metadata?.messageCount)
    }

    @Test
    fun `parseContent should parse current auxiliary response items`() {
        val content = """
            {"timestamp":"2026-07-13T15:48:00.000Z","type":"response_item","payload":{"type":"tool_search_call","call_id":"search_1","arguments":{"query":"session parser","limit":8}}}
            {"timestamp":"2026-07-13T15:48:00.100Z","type":"response_item","payload":{"type":"tool_search_output","call_id":"search_1","tools":[{"type":"namespace","name":"github"}]}}
            {"timestamp":"2026-07-13T15:48:01.000Z","type":"response_item","payload":{"type":"web_search_call","id":"web_1","status":"completed","action":{"type":"search","query":"Codex JSONL"}}}
            {"timestamp":"2026-07-13T15:48:02.000Z","type":"response_item","payload":{"type":"image_generation_call","id":"image_1","status":"completed","revised_prompt":"A compact session browser","result":"omitted"}}
            {"timestamp":"2026-07-13T15:48:03.000Z","type":"response_item","payload":{"type":"agent_message","author":"/root/reviewer","recipient":"/root","content":[{"type":"input_text","text":"Message Type: FINAL_ANSWER\nParser review complete."}]}}
        """.trimIndent()

        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertEquals(4, messages.size)
        val toolSearch = messages[0] as ParsedMessage.ToolUse
        assertEquals("tool_search", toolSearch.toolName)
        assertTrue(toolSearch.hasResults())
        assertEquals("web_search", (messages[1] as ParsedMessage.ToolUse).toolName)
        assertEquals("image_generation", (messages[2] as ParsedMessage.ToolUse).toolName)
        assertTrue(messages[3] is ParsedMessage.Info)
        assertEquals(4, metadata?.messageCount)
    }

    @Test
    fun `parseContent should ignore encrypted reasoning without a summary`() {
        val content = """
            {"timestamp":"2026-07-13T15:48:00.000Z","type":"response_item","payload":{"type":"reasoning","summary":[],"encrypted_content":"encrypted"}}
        """.trimIndent()

        val (messages, metadata) = CodexSessionParser.parseContent(content)

        assertTrue(messages.isEmpty())
        assertEquals(0, metadata?.messageCount)
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

        // Event-only text remains visible and the tool result is attached to its call.
        assertEquals("Should have user, reasoning, tool, and assistant messages", 4, messages.size)

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

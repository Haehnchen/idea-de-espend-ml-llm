package de.espend.ml.llm.session.adapter.junie

import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import org.junit.Assert.*
import org.junit.Test

class JunieSessionParserTest {

    @Test
    fun `parseContent should parse UserPromptEvent`() {
        val content = """
            {"kind":"UserPromptEvent","prompt":"Hello, can you help me?"}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val userMessages = messages.filterIsInstance<ParsedMessage.User>()
        assertEquals("Should have 1 user message", 1, userMessages.size)
        val textContent = userMessages.first().content.filterIsInstance<MessageContent.Text>()
        assertEquals("Hello, can you help me?", textContent.first().text)
    }

    @Test
    fun `parseContent should parse LlmResponseMetadataEvent for model counts`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"LlmResponseMetadataEvent","modelUsage":[{"model":"claude-haiku-4-5-20251001","cost":0.002861,"inputTokens":2126,"outputTokens":147}]}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"LlmResponseMetadataEvent","modelUsage":[{"model":"claude-haiku-4-5-20251001","cost":0.001,"inputTokens":100,"outputTokens":50}]}}}
        """.trimIndent()

        val (_, metadata) = JunieSessionParser.parseContent(content)

        assertNotNull(metadata)
        assertTrue("Should have model counts", metadata!!.models.isNotEmpty())
        assertEquals("claude-haiku-4-5-20251001", metadata.models.first().first)
        assertEquals(2, metadata.models.first().second)
    }

    @Test
    fun `parseContent should parse ToolBlockUpdatedEvent with text and details`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ToolBlockUpdatedEvent","stepId":"step-1","text":"Found \"**/Main.kt\"","status":"IN_PROGRESS"}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ToolBlockUpdatedEvent","stepId":"step-1","text":"Found \"**/Main.kt\"","status":"COMPLETED","details":"src/main/kotlin/Main.kt\n"}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val toolUseMessages = messages.filterIsInstance<ParsedMessage.ToolUse>()
        assertEquals("Should have 1 tool use (deduplicated)", 1, toolUseMessages.size)
        assertEquals("tool", toolUseMessages.first().toolName)
        assertEquals("Found \"**/Main.kt\"", toolUseMessages.first().input["action"])
        assertTrue("Should have results with details", toolUseMessages.first().hasResults())
    }

    @Test
    fun `parseContent should parse TerminalBlockUpdatedEvent with command and output`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"TerminalBlockUpdatedEvent","stepId":"step-2","status":"IN_PROGRESS","command":"ls -la"}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"TerminalBlockUpdatedEvent","stepId":"step-2","status":"COMPLETED","command":"ls -la","output":"total 0\ndrwxr-xr-x 2 user user 40 Feb 6 15:00 ."}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val toolUseMessages = messages.filterIsInstance<ParsedMessage.ToolUse>()
        assertEquals("Should have 1 terminal message (deduplicated)", 1, toolUseMessages.size)
        assertEquals("terminal", toolUseMessages.first().toolName)
        assertEquals("ls -la", toolUseMessages.first().input["command"])
        assertTrue("Should have results with output", toolUseMessages.first().hasResults())
    }

    @Test
    fun `parseContent should parse ViewFilesBlockUpdatedEvent with file paths`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ViewFilesBlockUpdatedEvent","stepId":"step-3","status":"IN_PROGRESS","files":[{"relativePath":"src/main/Main.kt"}]}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val infoMessages = messages.filterIsInstance<ParsedMessage.Info>()
        assertEquals("Should have 1 info message", 1, infoMessages.size)
        assertEquals("Opened file", infoMessages.first().title)
        assertEquals("src/main/Main.kt", (infoMessages.first().content as MessageContent.Text).text)
    }

    @Test
    fun `parseContent should parse FileChangesBlockUpdatedEvent with file paths`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"FileChangesBlockUpdatedEvent","stepId":"step-4","status":"COMPLETED","changes":[{"beforeRelativePath":"src/Main.kt","afterRelativePath":"src/Main.kt","beforeContent":{"kind":"TextFileContent","text":"old"},"afterContent":{"kind":"TextFileContent","text":"new"}}]}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val infoMessages = messages.filterIsInstance<ParsedMessage.Info>()
        assertEquals("Should have 1 info message", 1, infoMessages.size)
        assertEquals("Edited file", infoMessages.first().title)
        assertEquals("src/Main.kt", (infoMessages.first().content as MessageContent.Text).text)
    }

    @Test
    fun `parseContent should parse ResultBlockUpdatedEvent as assistant text`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ResultBlockUpdatedEvent","stepId":"step-5","cancelled":false,"result":"### Summary\nThe fields were removed successfully.","changes":[]}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val assistantMessages = messages.filterIsInstance<ParsedMessage.AssistantText>()
        assertEquals("Should have 1 assistant message", 1, assistantMessages.size)
        val markdown = assistantMessages.first().content.filterIsInstance<MessageContent.Markdown>()
        assertTrue("Should contain markdown result", markdown.first().markdown.contains("Summary"))
    }

    @Test
    fun `parseContent should skip cancelled ResultBlockUpdatedEvent`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"CANCELED","agentEvent":{"kind":"ResultBlockUpdatedEvent","stepId":"step-6","cancelled":true,"result":"","changes":[],"errorCode":"ExitEarly"}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val assistantMessages = messages.filterIsInstance<ParsedMessage.AssistantText>()
        assertTrue("Should have no assistant messages for cancelled result", assistantMessages.isEmpty())
    }

    @Test
    fun `parseContent should parse AgentFailureEvent as error info`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"FAILED","agentEvent":{"kind":"AgentFailureEvent","message":"Insufficient Account Balance."}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val infoMessages = messages.filterIsInstance<ParsedMessage.Info>()
        assertEquals("Should have 1 error message", 1, infoMessages.size)
        assertEquals("Error", infoMessages.first().title)
        assertEquals(ParsedMessage.InfoStyle.ERROR, infoMessages.first().style)
    }

    @Test
    fun `parseContent should handle empty content`() {
        val (messages, metadata) = JunieSessionParser.parseContent("")

        assertTrue("Should have no messages", messages.isEmpty())
        assertNotNull(metadata)
        assertEquals(0, metadata!!.messageCount)
    }

    @Test
    fun `parseContent should skip unparseable lines`() {
        val content = """
            not valid json
            {"kind":"UserPromptEvent","prompt":"Hello"}
            also not json
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val userMessages = messages.filterIsInstance<ParsedMessage.User>()
        assertEquals("Should have 1 user message", 1, userMessages.size)
    }

    @Test
    fun `parseSession should return null for nonexistent session`() {
        val result = JunieSessionParser.parseSession("nonexistent-session-id")
        assertNull(result)
    }

    @Test
    fun `parseContent should deduplicate step events and keep COMPLETED`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ToolBlockUpdatedEvent","stepId":"step-x","text":"Searching...","status":"IN_PROGRESS"}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ToolBlockUpdatedEvent","stepId":"step-x","text":"Found file","status":"COMPLETED","details":"src/Foo.kt"}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val toolUseMessages = messages.filterIsInstance<ParsedMessage.ToolUse>()
        assertEquals("Should deduplicate to 1 message", 1, toolUseMessages.size)
        assertEquals("Found file", toolUseMessages.first().input["action"])
        assertTrue("Should have details from COMPLETED event", toolUseMessages.first().hasResults())
    }
}

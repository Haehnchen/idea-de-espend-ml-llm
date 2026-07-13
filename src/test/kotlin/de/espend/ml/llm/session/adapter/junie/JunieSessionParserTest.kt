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
    fun `parseContent should expose the three most-used models`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"LlmResponseMetadataEvent","modelUsage":[{"model":"primary"},{"model":"secondary"},{"model":"tertiary"},{"model":"delegated-once"}]}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"LlmResponseMetadataEvent","modelUsage":[{"model":"primary"},{"model":"secondary"},{"model":"tertiary"}]}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"LlmResponseMetadataEvent","modelUsage":[{"model":"primary"},{"model":"secondary"}]}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"LlmResponseMetadataEvent","modelUsage":[{"model":"primary"}]}}}
        """.trimIndent()

        val (_, metadata) = JunieSessionParser.parseContent(content)

        assertNotNull(metadata)
        assertEquals(3, metadata!!.models.size)
        assertEquals(listOf("primary", "secondary", "tertiary"), metadata.models.map { it.first })
        assertEquals(listOf(4, 3, 2), metadata.models.map { it.second })
    }

    @Test
    fun `parseContent should extract Junie version from runtime path`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"EnvironmentVariablesUpdatedEvent","env":[{"key":"LD_LIBRARY_PATH","value":"/home/user/.local/share/junie/versions/1336.1/junie-app/lib/app"}]}}}
        """.trimIndent()

        val (_, metadata) = JunieSessionParser.parseContent(content)

        assertEquals("1336.1", metadata?.version)
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
        val result = assistantMessages.first()
        assertEquals("final_answer", result.displayType)
        assertEquals(ParsedMessage.AssistantTextStyle.RESULT, result.style)
        val markdown = result.content.filterIsInstance<MessageContent.Markdown>()
        assertTrue("Should contain markdown result", markdown.first().markdown.contains("Summary"))
    }

    @Test
    fun `parseContent should parse agent thought as thinking`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"AgentThoughtBlockUpdatedEvent","stepId":"thought-1","text":"Inspecting the existing toolsets."}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val thinking = messages.single() as ParsedMessage.AssistantThinking
        assertEquals("Inspecting the existing toolsets.", thinking.thinking)
    }

    @Test
    fun `parseContent should parse plan as status card`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"AgentPlanUpdatedEvent","items":[{"status":"DONE","description":"Inspect toolsets"},{"status":"IN_PROGRESS","description":"Create collector"}]}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val plan = messages.single() as ParsedMessage.AssistantText
        assertEquals("plan", plan.displayType)
        assertEquals(ParsedMessage.AssistantTextStyle.STATUS, plan.style)
        val markdown = (plan.content.single() as MessageContent.Markdown).markdown
        assertTrue(markdown.contains("- [x] Inspect toolsets"))
        assertTrue(markdown.contains("- [ ] **In progress:** Create collector"))
    }

    @Test
    fun `parseContent should parse system error`() {
        val content = """
            {"kind":"SystemMessageEvent","text":"Task was interrupted","level":"ERROR","symbol":"✕"}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val error = messages.single() as ParsedMessage.Info
        assertEquals("system", error.title)
        assertEquals("error", error.subtitle)
        assertEquals(ParsedMessage.InfoStyle.ERROR, error.style)
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
    fun `parseContent should render prompt-too-long failure as error`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"FAILED","agentEvent":{"kind":"AgentFailureEvent","message":"Junie: prompt is too long: 200762 tokens > 200000 maximum","errorCode":"ExitContext"}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val infoMessages = messages.filterIsInstance<ParsedMessage.Info>()
        assertEquals("Should have 1 error message", 1, infoMessages.size)
        assertEquals("Error", infoMessages.first().title)
        assertEquals(ParsedMessage.InfoStyle.ERROR, infoMessages.first().style)
        assertEquals(
            "Junie: prompt is too long: 200762 tokens > 200000 maximum",
            (infoMessages.first().content as MessageContent.Text).text
        )
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

    @Test
    fun `parseContent should not classify a line using the following skipped event`() {
        val content = """
            {"kind":"UserPromptEvent","prompt":"First prompt"}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"AgentCurrentStatusUpdatedEvent","status":"Sending LLM request"}}}
            {"kind":"UserPromptEvent","prompt":"Second prompt"}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        val prompts = messages.filterIsInstance<ParsedMessage.User>()
        assertEquals(2, prompts.size)
    }

    @Test
    fun `parseContent should not skip user text naming a skipped event kind`() {
        val content = """
            {"kind":"UserPromptEvent","prompt":"Please inspect AgentCurrentStatusUpdatedEvent handling"}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        assertEquals(1, messages.filterIsInstance<ParsedMessage.User>().size)
    }

    @Test
    fun `prefix filter should allow only supported event kinds without regex matching message text`() {
        assertTrue(JunieSessionParser.isRelevantLinePrefix(
            """{"kind":"UserPromptEvent","prompt":"Please inspect AgentStateUpdatedEvent"}"""
        ))
        assertTrue(JunieSessionParser.isRelevantLinePrefix(
            """{"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ResultBlockUpdatedEvent""""
        ))
        assertFalse(JunieSessionParser.isRelevantLinePrefix(
            """{"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"AgentStateUpdatedEvent""""
        ))
        assertFalse(JunieSessionParser.isRelevantLinePrefix(
            """{"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"UnknownFutureEvent""""
        ))
    }

    @Test
    fun `parseContent should skip a very long ignored line and preserve the next message`() {
        val ignoredState = "x".repeat(2_000_000)
        val content = buildString {
            append("""{"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"AgentStateUpdatedEvent","blob":""")
            append(ignoredState)
            append(""""}}}""")
            append('\n')
            append("""{"kind":"UserPromptEvent","prompt":"Still here"}""")
        }

        val (messages, _) = JunieSessionParser.parseContent(content)

        val user = messages.single() as ParsedMessage.User
        assertEquals("Still here", (user.content.single() as MessageContent.Text).text)
    }

    @Test
    fun `parseContent should preserve different event kinds sharing a step id`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ToolBlockUpdatedEvent","stepId":"shared-step","text":"Opening file","status":"IN_PROGRESS"}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ViewFilesBlockUpdatedEvent","stepId":"shared-step","status":"IN_PROGRESS","files":[{"relativePath":"src/Main.kt"}]}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ToolBlockUpdatedEvent","stepId":"shared-step","text":"Opened file","status":"COMPLETED","details":"src/Main.kt"}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ViewFilesBlockUpdatedEvent","stepId":"shared-step","status":"COMPLETED","files":[{"relativePath":"src/Main.kt"}]}}}
        """.trimIndent()

        val (messages, _) = JunieSessionParser.parseContent(content)

        assertEquals(1, messages.filterIsInstance<ParsedMessage.ToolUse>().size)
        val openedFiles = messages.filterIsInstance<ParsedMessage.Info>()
            .filter { it.title == "Opened file" }
        assertEquals(1, openedFiles.size)
    }
}

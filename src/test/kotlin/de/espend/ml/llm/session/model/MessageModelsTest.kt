package de.espend.ml.llm.session.model

import org.junit.Assert.*
import org.junit.Test

class MessageModelsTest {

    // ===== MessageContent Tests =====

    @Test
    fun `MessageContent Text should hold text content`() {
        val block = MessageContent.Text("Hello, world!")
        assertEquals("Hello, world!", block.text)
    }

    @Test
    fun `MessageContent Code should hold code and optional language`() {
        val block = MessageContent.Code("println(\"Hello\")", "kotlin")
        assertEquals("println(\"Hello\")", block.code)
        assertEquals("kotlin", block.language)
    }

    @Test
    fun `MessageContent Code should allow null language`() {
        val block = MessageContent.Code("ls -la")
        assertEquals("ls -la", block.code)
        assertNull(block.language)
    }

    @Test
    fun `MessageContent Markdown should hold markdown content`() {
        val block = MessageContent.Markdown("# Title\n\nSome **bold** text")
        assertTrue(block.markdown.contains("# Title"))
    }

    @Test
    fun `MessageContent Json should hold json content`() {
        val block = MessageContent.Json("{\"key\": \"value\"}")
        assertTrue(block.json.contains("key"))
    }

    // ===== ParsedMessage Tests =====

    @Test
    fun `ParsedMessage User should hold content`() {
        val content = listOf(MessageContent.Text("Hello"))
        val msg = ParsedMessage.User(
            timestamp = "2024-01-15T10:00:00Z",
            content = content
        )
        assertEquals("2024-01-15T10:00:00Z", msg.timestamp)
        assertEquals(1, msg.content.size)
    }

    @Test
    fun `ParsedMessage AssistantText should hold content`() {
        val content = listOf(MessageContent.Markdown("Here is my response"))
        val msg = ParsedMessage.AssistantText(
            timestamp = "2024-01-15T10:00:00Z",
            content = content
        )
        assertEquals(1, msg.content.size)
    }

    @Test
    fun `ParsedMessage AssistantThinking should hold thinking content`() {
        val msg = ParsedMessage.AssistantThinking(
            timestamp = "2024-01-15T10:00:00Z",
            thinking = "Let me think about this..."
        )
        assertTrue(msg.thinking.contains("think"))
    }

    @Test
    fun `ParsedMessage ToolUse should hold tool name and input`() {
        val input: Map<String, String> = mapOf(
            "command" to "ls -la",
            "description" to "List files"
        )
        val msg = ParsedMessage.ToolUse(
            timestamp = "2024-01-15T10:00:00Z",
            toolName = "Bash",
            toolCallId = "toolu_001",
            input = input
        )
        assertEquals("Bash", msg.toolName)
        assertEquals("toolu_001", msg.toolCallId)
        assertEquals("ls -la", msg.input["command"])
    }

    @Test
    fun `ParsedMessage ToolResult should hold output and error flag`() {
        val output = listOf(MessageContent.Code("file1.txt\nfile2.txt"))
        val msg = ParsedMessage.ToolResult(
            timestamp = "2024-01-15T10:00:00Z",
            toolCallId = "toolu_001",
            output = output,
            isError = false
        )

        assertFalse(msg.isError)
    }

    @Test
    fun `ParsedMessage Info should hold error details with ERROR style`() {
        val msg = ParsedMessage.Info(
            timestamp = "2024-01-15T10:00:00Z",
            title = "error",
            subtitle = "APIError",
            content = MessageContent.Text("Rate limit exceeded"),
            style = ParsedMessage.InfoStyle.ERROR
        )
        assertEquals("error", msg.title)
        assertEquals("APIError", msg.subtitle)
        assertEquals(ParsedMessage.InfoStyle.ERROR, msg.style)
        assertEquals("Rate limit exceeded", (msg.content as MessageContent.Text).text)
    }

    @Test
    fun `ParsedMessage Info should hold original type and raw content`() {
        val msg = ParsedMessage.Info(
            timestamp = "2024-01-15T10:00:00Z",
            title = "custom-type",
            content = MessageContent.Json("{\"type\":\"custom-type\"}")
        )
        assertEquals("custom-type", msg.title)
        val data = msg.content as MessageContent.Json
        assertTrue(data.json.contains("custom-type"))
    }

    // ===== Sealed Class Exhaustiveness Tests =====

    @Test
    fun `ParsedMessage sealed class should support when expression`() {
        val messages = listOf<ParsedMessage>(
            ParsedMessage.User("ts", content = emptyList()),
            ParsedMessage.AssistantText("ts", content = emptyList()),
            ParsedMessage.AssistantThinking("ts", thinking = ""),
            ParsedMessage.ToolUse("ts", toolName = "test", input = emptyMap()),
            ParsedMessage.ToolResult("ts", output = emptyList()),
            ParsedMessage.Info("ts", title = "info")
        )

        messages.forEach { msg ->
            val typeName = when (msg) {
                is ParsedMessage.User -> "user"
                is ParsedMessage.AssistantText -> "assistant-text"
                is ParsedMessage.AssistantThinking -> "assistant-thinking"
                is ParsedMessage.ToolUse -> "tool-use"
                is ParsedMessage.ToolResult -> "tool-result"
                is ParsedMessage.Info -> "info"
            }
            assertNotNull(typeName)
        }
    }

    @Test
    fun `MessageContent sealed class should support when expression`() {
        val blocks = listOf<MessageContent>(
            MessageContent.Text(""),
            MessageContent.Code(""),
            MessageContent.Markdown(""),
            MessageContent.Json("")
        )

        blocks.forEach { block ->
            val typeName = when (block) {
                is MessageContent.Text -> "text"
                is MessageContent.Code -> "code"
                is MessageContent.Markdown -> "markdown"
                is MessageContent.Json -> "json"
            }
            assertNotNull(typeName)
        }
    }
}

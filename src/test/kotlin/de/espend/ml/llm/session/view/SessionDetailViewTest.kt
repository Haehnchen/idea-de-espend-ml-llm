package de.espend.ml.llm.session.view

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDetailViewTest {

    @Test
    fun `two consecutive tool calls should render as collapsed group`() {
        val messages = listOf(
            toolCall("exec", "one"),
            toolCall("wait", "two"),
            ParsedMessage.AssistantText("2026-07-13T18:00:03Z", listOf(MessageContent.Text("Done"))),
            toolCall("read", "four")
        )

        val html = render(messages)

        assertEquals(1, html.countOccurrences("class=\"message-group collapsed\""))
        assertTrue(html.contains("<span class=\"message-group-count\">2 Tool calls</span>"))
        assertTrue(html.contains("<span class=\"message-group-details\">exec, wait</span>"))
        assertEquals(3, html.countOccurrences("class=\"message tool-use\""))
        assertTrue(html.contains("onclick=\"toggleMessageGroup(this)\""))
        assertTrue(html.contains("aria-expanded=\"false\""))
    }

    @Test
    fun `tool group names should be unique and limited to three`() {
        val messages = listOf(
            toolCall("exec", "one"),
            toolCall("wait", "two"),
            toolCall("EXEC", "three"),
            toolCall("read", "four"),
            toolCall("web_search", "five")
        )

        val html = render(messages)

        assertTrue(html.contains("<span class=\"message-group-count\">5 Tool calls</span>"))
        assertTrue(html.contains("<span class=\"message-group-details\">exec, wait, read, …</span>"))
        assertFalse(html.contains("exec, wait, EXEC"))
    }

    @Test
    fun `tool calls should not group across another message type`() {
        val messages = listOf(
            toolCall("exec", "one"),
            ParsedMessage.AssistantText("2026-07-13T18:00:02Z", listOf(MessageContent.Text("Continue"))),
            toolCall("read", "three")
        )

        val html = render(messages)

        assertFalse(html.contains("class=\"message-group collapsed\""))
        assertEquals(2, html.countOccurrences("class=\"message tool-use\""))
    }

    @Test
    fun `two consecutive info messages with same title should render as collapsed group`() {
        val messages = listOf(
            info("Edited file", "src/First.kt"),
            info("Edited file", "src/Second.kt"),
            info("Edited file", "src/Third.kt")
        )

        val html = render(messages)

        assertEquals(1, html.countOccurrences("class=\"message-group collapsed\""))
        assertTrue(html.contains("<span class=\"message-group-count\">3 Edited files</span>"))
        assertEquals(3, html.countOccurrences("class=\"message info\""))
    }

    @Test
    fun `info messages with different titles should not share a group`() {
        val messages = listOf(
            info("Opened file", "src/First.kt"),
            info("Edited file", "src/First.kt")
        )

        val html = render(messages)

        assertFalse(html.contains("class=\"message-group collapsed\""))
    }

    @Test
    fun `all consecutive rendered message types should be grouped`() {
        val messages = listOf(
            ParsedMessage.User("2026-07-13T18:00:00Z", listOf(MessageContent.Text("one"))),
            ParsedMessage.User("2026-07-13T18:00:01Z", listOf(MessageContent.Text("two"))),
            ParsedMessage.AssistantText(
                "2026-07-13T18:00:02Z",
                listOf(MessageContent.Text("one")),
                displayType = "commentary",
                style = ParsedMessage.AssistantTextStyle.STATUS
            ),
            ParsedMessage.AssistantText(
                "2026-07-13T18:00:03Z",
                listOf(MessageContent.Text("two")),
                displayType = "commentary",
                style = ParsedMessage.AssistantTextStyle.STATUS
            ),
            ParsedMessage.AssistantThinking("2026-07-13T18:00:04Z", "one"),
            ParsedMessage.AssistantThinking("2026-07-13T18:00:05Z", "two"),
            toolResult("one"),
            toolResult("two")
        )

        val html = render(messages)

        assertEquals(4, html.countOccurrences("class=\"message-group collapsed\""))
        assertTrue(html.contains("<span class=\"message-group-count\">2 User messages</span>"))
        assertTrue(html.contains("<span class=\"message-group-count\">2 commentary messages</span>"))
        assertTrue(html.contains("<span class=\"message-group-count\">2 Thinking blocks</span>"))
        assertTrue(html.contains("<span class=\"message-group-count\">2 Tool results</span>"))
    }

    private fun render(messages: List<ParsedMessage>): String {
        val detail = SessionDetail(
            sessionId = "session-1",
            title = "Tool grouping",
            messages = messages
        )
        return SessionDetailView().generateSessionDetail(detail.sessionId, detail)
    }

    private fun toolCall(toolName: String, value: String): ParsedMessage.ToolUse {
        return ParsedMessage.ToolUse(
            timestamp = "2026-07-13T18:00:00Z",
            toolName = toolName,
            input = mapOf("input" to value)
        )
    }

    private fun toolResult(value: String): ParsedMessage.ToolResult {
        return ParsedMessage.ToolResult(
            timestamp = "2026-07-13T18:00:00Z",
            toolName = "exec",
            output = listOf(MessageContent.Text(value))
        )
    }

    private fun info(title: String, value: String): ParsedMessage.Info {
        return ParsedMessage.Info(
            timestamp = "2026-07-13T18:00:00Z",
            title = title,
            content = MessageContent.Text(value)
        )
    }

    private fun String.countOccurrences(needle: String): Int {
        return windowed(needle.length).count { it == needle }
    }
}

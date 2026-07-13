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

        assertEquals(1, html.countOccurrences("class=\"tool-call-group collapsed\""))
        assertTrue(html.contains("<span class=\"tool-call-group-count\">2 Tool calls</span>"))
        assertTrue(html.contains("<span class=\"tool-call-group-names\">exec, wait</span>"))
        assertEquals(3, html.countOccurrences("class=\"message tool-use\""))
        assertTrue(html.contains("onclick=\"toggleToolCallGroup(this)\""))
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

        assertTrue(html.contains("<span class=\"tool-call-group-count\">5 Tool calls</span>"))
        assertTrue(html.contains("<span class=\"tool-call-group-names\">exec, wait, read, …</span>"))
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

        assertFalse(html.contains("class=\"tool-call-group collapsed\""))
        assertEquals(2, html.countOccurrences("class=\"message tool-use\""))
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

    private fun String.countOccurrences(needle: String): Int {
        return windowed(needle.length).count { it == needle }
    }
}

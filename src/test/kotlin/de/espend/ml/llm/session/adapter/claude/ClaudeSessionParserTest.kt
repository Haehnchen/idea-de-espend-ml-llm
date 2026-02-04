package de.espend.ml.llm.session.adapter.claude

import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import org.junit.Assert.*
import org.junit.Test

class ClaudeSessionParserTest {

    @Test
    fun `parseContent should parse command message`() {
        val jsonl = """
            {"type":"user","message":{"role":"user","content":"<command-name>/clear</command-name>\n            <command-message>clear</command-message>\n            <command-args></command-args>"},"timestamp":"2026-02-03T19:30:29.273Z","uuid":"test-uuid"}
        """.trimIndent()

        val (messages, _) = ClaudeSessionParser.parseContent(jsonl)

        assertEquals(1, messages.size)
        val msg = messages[0]
        assertTrue("Should be Info message", msg is ParsedMessage.Info)
        val info = msg as ParsedMessage.Info
        assertEquals("command", info.title)
        assertNotNull(info.content)
        assertTrue(info.content is MessageContent.Text)
        assertEquals("clear", (info.content as MessageContent.Text).text)
    }

    @Test
    fun `parseContent should parse compact command message`() {
        val jsonl = """
            {"type":"user","message":{"role":"user","content":"<command-name>/compact</command-name>\n            <command-message>compact</command-message>"},"timestamp":"2026-02-03T19:30:29.273Z","uuid":"test-uuid"}
        """.trimIndent()

        val (messages, _) = ClaudeSessionParser.parseContent(jsonl)

        assertEquals(1, messages.size)
        val msg = messages[0]
        assertTrue("Should be Info message", msg is ParsedMessage.Info)
        val info = msg as ParsedMessage.Info
        assertEquals("command", info.title)
        assertEquals("compact", (info.content as MessageContent.Text).text)
    }

    @Test
    fun `parseContent should skip local-command-stdout messages`() {
        val jsonl = """
            {"type":"user","message":{"role":"user","content":"<local-command-stdout></local-command-stdout>"},"timestamp":"2026-02-03T19:30:29.274Z","uuid":"test-uuid"}
        """.trimIndent()

        val (messages, _) = ClaudeSessionParser.parseContent(jsonl)

        assertEquals("local-command-stdout should be skipped", 0, messages.size)
    }

    @Test
    fun `parseContent should skip isMeta messages`() {
        val jsonl = """
            {"type":"user","message":{"role":"user","content":"<local-command-caveat>Some caveat text</local-command-caveat>"},"isMeta":true,"timestamp":"2026-02-03T19:30:29.274Z","uuid":"test-uuid"}
        """.trimIndent()

        val (messages, _) = ClaudeSessionParser.parseContent(jsonl)

        assertEquals("isMeta messages should be skipped", 0, messages.size)
    }

    @Test
    fun `parseContent should extract title from first real user message skipping commands`() {
        val jsonl = """
            {"type":"user","message":{"role":"user","content":"<local-command-caveat>Caveat</local-command-caveat>"},"isMeta":true,"timestamp":"2026-02-03T19:30:29.274Z","uuid":"uuid1"}
            {"type":"user","message":{"role":"user","content":"<command-name>/clear</command-name>"},"timestamp":"2026-02-03T19:30:29.275Z","uuid":"uuid2"}
            {"type":"user","message":{"role":"user","content":"<local-command-stdout></local-command-stdout>"},"timestamp":"2026-02-03T19:30:29.276Z","uuid":"uuid3"}
            {"type":"user","message":{"role":"user","content":"This is my actual question about the code"},"timestamp":"2026-02-03T19:30:53.895Z","uuid":"uuid4"}
        """.trimIndent()

        val (messages, _) = ClaudeSessionParser.parseContent(jsonl)

        // Should have: command info + real user message (meta and stdout are skipped)
        assertEquals(2, messages.size)

        // First should be the command
        assertTrue(messages[0] is ParsedMessage.Info)
        assertEquals("command", (messages[0] as ParsedMessage.Info).title)

        // Second should be the real user message
        assertTrue(messages[1] is ParsedMessage.User)
        val userMsg = messages[1] as ParsedMessage.User
        val text = userMsg.content.filterIsInstance<MessageContent.Text>().joinToString { it.text }
        assertEquals("This is my actual question about the code", text)
    }

    @Test
    fun `parseFile should use first real user message for title`() {
        // Create a temp file with command followed by real message
        val tempFile = kotlin.io.path.createTempFile(suffix = ".jsonl").toFile()
        try {
            tempFile.writeText("""
                {"type":"user","message":{"role":"user","content":"<command-name>/clear</command-name>"},"timestamp":"2026-02-03T19:30:29.275Z","uuid":"uuid1"}
                {"type":"user","message":{"role":"user","content":"<local-command-stdout></local-command-stdout>"},"timestamp":"2026-02-03T19:30:29.276Z","uuid":"uuid2"}
                {"type":"user","message":{"role":"user","content":"My real question here"},"timestamp":"2026-02-03T19:30:53.895Z","uuid":"uuid3"}
            """.trimIndent())

            val sessionDetail = ClaudeSessionParser.parseFile(tempFile)

            assertNotNull(sessionDetail)
            assertEquals("Title should be from first real user message", "My real question here", sessionDetail!!.title)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `parseContent should handle local-command-stdout with content`() {
        val jsonl = """
            {"type":"user","message":{"role":"user","content":"<local-command-stdout>Some output here</local-command-stdout>"},"timestamp":"2026-02-03T19:30:29.274Z","uuid":"test-uuid"}
        """.trimIndent()

        val (messages, _) = ClaudeSessionParser.parseContent(jsonl)

        // Non-empty stdout should also be skipped (it's command output)
        assertEquals("local-command-stdout with content should be skipped", 0, messages.size)
    }
}

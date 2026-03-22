package de.espend.ml.llm.session.adapter.opencode

import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class OpenCodeSessionTest {

    private lateinit var dbFile: File
    private lateinit var dbUrl: String

    @Before
    fun setUp() {
        dbFile = File.createTempFile("opencode-test-", ".db")
        dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        OpenCodeSessionFinder.dbPathOverride = dbFile.absolutePath

        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection(dbUrl).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("""
                    CREATE TABLE session (
                        id TEXT PRIMARY KEY, project_id TEXT NOT NULL, parent_id TEXT,
                        slug TEXT NOT NULL, directory TEXT NOT NULL, title TEXT NOT NULL,
                        version TEXT NOT NULL, time_created INTEGER NOT NULL,
                        time_updated INTEGER NOT NULL, time_archived INTEGER
                    )
                """)
                stmt.executeUpdate("""
                    CREATE TABLE message (
                        id TEXT PRIMARY KEY, session_id TEXT NOT NULL,
                        time_created INTEGER NOT NULL, time_updated INTEGER NOT NULL,
                        data TEXT NOT NULL
                    )
                """)
                stmt.executeUpdate("""
                    CREATE TABLE part (
                        id TEXT PRIMARY KEY, message_id TEXT NOT NULL, session_id TEXT NOT NULL,
                        time_created INTEGER NOT NULL, time_updated INTEGER NOT NULL,
                        data TEXT NOT NULL
                    )
                """)
            }
        }
    }

    @After
    fun tearDown() {
        OpenCodeSessionFinder.dbPathOverride = null
        dbFile.delete()
    }

    // --- helpers ---

    private fun insertSession(
        sessionId: String,
        title: String,
        directory: String = "/home/user/project",
        archived: Boolean = false,
        createdMs: Long = 1700000000000L,
        updatedMs: Long = 1700000100000L
    ) {
        DriverManager.getConnection(dbUrl).use { conn ->
            conn.prepareStatement(
                "INSERT INTO session (id, project_id, slug, directory, title, version, time_created, time_updated, time_archived) VALUES (?,?,?,?,?,?,?,?,?)"
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setString(2, "proj1")
                stmt.setString(3, "slug")
                stmt.setString(4, directory)
                stmt.setString(5, title)
                stmt.setString(6, "1")
                stmt.setLong(7, createdMs)
                stmt.setLong(8, updatedMs)
                stmt.setObject(9, if (archived) updatedMs else null)
                stmt.executeUpdate()
            }
        }
    }

    private fun insertMessage(
        msgId: String, sessionId: String, role: String,
        model: String? = null, createdMs: Long = 1700000000000L,
        error: String? = null
    ) {
        val modelPart = if (model != null) ""","modelID":"$model"""" else ""
        val errorPart = if (error != null) ""","error":$error""" else ""
        val data = """{"role":"$role","time":{"created":$createdMs}$modelPart$errorPart}"""
        DriverManager.getConnection(dbUrl).use { conn ->
            conn.prepareStatement(
                "INSERT INTO message (id, session_id, time_created, time_updated, data) VALUES (?,?,?,?,?)"
            ).use { stmt ->
                stmt.setString(1, msgId)
                stmt.setString(2, sessionId)
                stmt.setLong(3, createdMs)
                stmt.setLong(4, createdMs)
                stmt.setString(5, data)
                stmt.executeUpdate()
            }
        }
    }

    private fun insertPart(
        partId: String, msgId: String, sessionId: String,
        data: String, createdMs: Long = 1700000000000L
    ) {
        DriverManager.getConnection(dbUrl).use { conn ->
            conn.prepareStatement(
                "INSERT INTO part (id, message_id, session_id, time_created, time_updated, data) VALUES (?,?,?,?,?,?)"
            ).use { stmt ->
                stmt.setString(1, partId)
                stmt.setString(2, msgId)
                stmt.setString(3, sessionId)
                stmt.setLong(4, createdMs)
                stmt.setLong(5, createdMs)
                stmt.setString(6, data)
                stmt.executeUpdate()
            }
        }
    }

    // --- OpenCodeSessionFinder tests ---

    @Test
    fun `listSessions should return empty list when DB does not exist`() {
        OpenCodeSessionFinder.dbPathOverride = "/nonexistent/opencode.db"
        assertTrue(OpenCodeSessionFinder.listSessions().isEmpty())
    }

    @Test
    fun `listSessions should return all non-archived sessions`() {
        insertSession("ses_a", "Session A", "/proj/a")
        insertSession("ses_b", "Session B", "/proj/b")
        insertSession("ses_c", "Archived", "/proj/c", archived = true)

        val sessions = OpenCodeSessionFinder.listSessions()

        assertEquals(2, sessions.size)
        assertTrue(sessions.any { it.sessionId == "ses_a" && it.title == "Session A" })
        assertTrue(sessions.any { it.sessionId == "ses_b" && it.title == "Session B" })
        assertFalse(sessions.any { it.sessionId == "ses_c" })
    }

    @Test
    fun `listSessions should include message count`() {
        insertSession("ses_x", "With messages")
        insertMessage("msg_1", "ses_x", "user")
        insertMessage("msg_2", "ses_x", "assistant")

        val session = OpenCodeSessionFinder.listSessions().first { it.sessionId == "ses_x" }

        assertEquals(2, session.messageCount)
    }

    @Test
    fun `listSessions should order by time_updated descending`() {
        insertSession("ses_old", "Old", updatedMs = 1700000000000L)
        insertSession("ses_new", "New", updatedMs = 1700000200000L)

        val sessions = OpenCodeSessionFinder.listSessions()

        assertEquals("ses_new", sessions[0].sessionId)
        assertEquals("ses_old", sessions[1].sessionId)
    }

    @Test
    fun `findSession should return session info`() {
        insertSession("ses_find", "Find me", "/home/user/proj")

        val session = OpenCodeSessionFinder.findSession("ses_find")

        assertNotNull(session)
        assertEquals("ses_find", session!!.sessionId)
        assertEquals("Find me", session.title)
        assertEquals("/home/user/proj", session.directory)
    }

    @Test
    fun `findSession should return null for unknown id`() {
        assertNull(OpenCodeSessionFinder.findSession("nonexistent"))
    }

    @Test
    fun `findSessionFile should return DB path when session exists`() {
        insertSession("ses_exists", "Exists")

        val path = OpenCodeSessionFinder.findSessionFile("ses_exists")

        assertNotNull(path)
        assertEquals(dbFile.absolutePath, path!!.toString())
    }

    @Test
    fun `findSessionFile should return null when session does not exist`() {
        assertNull(OpenCodeSessionFinder.findSessionFile("missing-session"))
    }

    // --- OpenCodeSessionParser tests ---

    @Test
    fun `parseSession should return null when DB does not exist`() {
        OpenCodeSessionFinder.dbPathOverride = "/nonexistent/opencode.db"
        assertNull(OpenCodeSessionParser.parseSession("any-id"))
    }

    @Test
    fun `parseSession should return null for unknown session id`() {
        assertNull(OpenCodeSessionParser.parseSession("unknown-session"))
    }

    @Test
    fun `parseSession should parse user text message`() {
        insertSession("ses_001", "Test session")
        insertMessage("msg_001", "ses_001", "user")
        insertPart("prt_001", "msg_001", "ses_001", """{"type":"text","text":"Hello, what is this project?"}""")

        val session = OpenCodeSessionParser.parseSession("ses_001")

        assertNotNull(session)
        assertEquals("ses_001", session!!.sessionId)
        assertEquals("Test session", session.title)
        assertEquals(1, session.messages.size)
        val user = session.messages[0] as ParsedMessage.User
        assertEquals("Hello, what is this project?", (user.content[0] as MessageContent.Text).text)
    }

    @Test
    fun `parseSession should parse assistant text message`() {
        insertSession("ses_002", "Test session")
        insertMessage("msg_001", "ses_002", "assistant", model = "claude-sonnet-4-6")
        insertPart("prt_001", "msg_001", "ses_002", """{"type":"text","text":"This is my answer."}""")

        val session = OpenCodeSessionParser.parseSession("ses_002")

        assertNotNull(session)
        assertEquals(1, session!!.messages.size)
        val msg = session.messages[0] as ParsedMessage.AssistantText
        assertEquals("This is my answer.", (msg.content[0] as MessageContent.Markdown).markdown)
    }

    @Test
    fun `parseSession should parse reasoning part`() {
        insertSession("ses_003", "Test session")
        insertMessage("msg_001", "ses_003", "assistant")
        insertPart("prt_001", "msg_001", "ses_003", """{"type":"reasoning","text":"Let me think step by step..."}""")

        val session = OpenCodeSessionParser.parseSession("ses_003")

        assertNotNull(session)
        assertEquals(1, session!!.messages.size)
        val msg = session.messages[0] as ParsedMessage.AssistantThinking
        assertEquals("Let me think step by step...", msg.thinking)
    }

    @Test
    fun `parseSession should parse completed tool with output`() {
        insertSession("ses_004", "Test session")
        insertMessage("msg_001", "ses_004", "assistant")
        insertPart("prt_001", "msg_001", "ses_004", """
            {"type":"tool","callID":"call_abc","tool":"read","state":{"status":"completed","input":{"filePath":"/src/main.kt"},"output":"file contents here"}}
        """.trimIndent())

        val session = OpenCodeSessionParser.parseSession("ses_004")

        assertNotNull(session)
        assertEquals(1, session!!.messages.size)
        val tool = session.messages[0] as ParsedMessage.ToolUse
        assertEquals("read", tool.toolName)
        assertEquals("call_abc", tool.toolCallId)
        assertEquals("/src/main.kt", tool.input["filePath"])
        assertTrue(tool.hasResults())
        assertFalse(tool.results[0].isError)
    }

    @Test
    fun `parseSession should parse tool with error status`() {
        insertSession("ses_005", "Test session")
        insertMessage("msg_001", "ses_005", "assistant")
        insertPart("prt_001", "msg_001", "ses_005", """
            {"type":"tool","callID":"call_err","tool":"bash","state":{"status":"error","input":{"command":"rm -rf /"},"error":"Permission denied"}}
        """.trimIndent())

        val session = OpenCodeSessionParser.parseSession("ses_005")

        assertNotNull(session)
        val tool = session!!.messages[0] as ParsedMessage.ToolUse
        assertEquals("bash", tool.toolName)
        assertTrue(tool.hasResults())
        assertTrue(tool.results[0].isError)
    }

    @Test
    fun `parseSession should skip step-start and step-finish parts`() {
        insertSession("ses_006", "Test session")
        insertMessage("msg_001", "ses_006", "assistant")
        insertPart("prt_001", "msg_001", "ses_006", """{"type":"step-start","snapshot":"abc123"}""")
        insertPart("prt_002", "msg_001", "ses_006", """{"type":"text","text":"The answer is 42."}""")
        insertPart("prt_003", "msg_001", "ses_006", """{"type":"step-finish","reason":"stop","cost":0}""")

        val session = OpenCodeSessionParser.parseSession("ses_006")

        assertNotNull(session)
        assertEquals(1, session!!.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.AssistantText)
    }

    @Test
    fun `parseSession should handle message-level error with no parts`() {
        val errorJson = """{"name":"APIError","data":{"message":"Key limit exceeded","statusCode":429}}"""
        insertSession("ses_007", "Test session")
        insertMessage("msg_001", "ses_007", "assistant", error = errorJson)

        val session = OpenCodeSessionParser.parseSession("ses_007")

        assertNotNull(session)
        assertEquals(1, session!!.messages.size)
        val info = session.messages[0] as ParsedMessage.Info
        assertEquals(ParsedMessage.InfoStyle.ERROR, info.style)
        assertEquals("APIError", info.subtitle)
        assertTrue((info.content as MessageContent.Text).text.contains("Key limit exceeded"))
    }

    @Test
    fun `parseSession should track model usage in metadata`() {
        insertSession("ses_008", "Test session")
        insertMessage("msg_001", "ses_008", "assistant", model = "minimax-m2.5-free")
        insertPart("prt_001", "msg_001", "ses_008", """{"type":"text","text":"Answer"}""")
        insertMessage("msg_002", "ses_008", "assistant", model = "minimax-m2.5-free", createdMs = 1700000010000L)
        insertPart("prt_002", "msg_002", "ses_008", """{"type":"text","text":"Answer 2"}""")

        val session = OpenCodeSessionParser.parseSession("ses_008")

        assertNotNull(session)
        assertEquals(1, session!!.metadata?.models?.size)
        assertEquals("minimax-m2.5-free", session.metadata?.models?.get(0)?.first)
        assertEquals(2, session.metadata?.models?.get(0)?.second)
    }

    @Test
    fun `parseSession should populate metadata with cwd and timestamps`() {
        insertSession("ses_009", "My session", directory = "/home/user/myproject")

        val session = OpenCodeSessionParser.parseSession("ses_009")

        assertNotNull(session)
        assertEquals("/home/user/myproject", session!!.metadata?.cwd)
        assertNotNull(session.metadata?.created)
        assertNotNull(session.metadata?.modified)
    }

    @Test
    fun `parseSession should order messages by time_created`() {
        insertSession("ses_010", "Test session")
        insertMessage("msg_b", "ses_010", "assistant", model = "m1", createdMs = 1700000020000L)
        insertPart("prt_b", "msg_b", "ses_010", """{"type":"text","text":"Second"}""", createdMs = 1700000020000L)
        insertMessage("msg_a", "ses_010", "user", createdMs = 1700000010000L)
        insertPart("prt_a", "msg_a", "ses_010", """{"type":"text","text":"First"}""", createdMs = 1700000010000L)

        val session = OpenCodeSessionParser.parseSession("ses_010")

        assertNotNull(session)
        assertEquals(2, session!!.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.User)
        assertTrue(session.messages[1] is ParsedMessage.AssistantText)
    }

    @Test
    fun `parseSession should set message count from DB rows`() {
        insertSession("ses_011", "Test session")
        insertMessage("msg_1", "ses_011", "user")
        insertPart("prt_1", "msg_1", "ses_011", """{"type":"text","text":"Hi"}""")
        insertMessage("msg_2", "ses_011", "assistant", model = "m1", createdMs = 1700000010000L)
        insertPart("prt_2", "msg_2", "ses_011", """{"type":"text","text":"Hi back"}""", createdMs = 1700000010000L)

        val session = OpenCodeSessionParser.parseSession("ses_011")

        assertNotNull(session)
        assertEquals(2, session!!.metadata?.messageCount)
    }
}

package de.espend.ml.llm.session.adapter.kilocode

import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class KiloSessionParserTest {

    private lateinit var dbFile: File
    private lateinit var dbUrl: String

    @Before
    fun setUp() {
        dbFile = File.createTempFile("kilo-test-", ".db")
        dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        KiloSessionFinder.dbPathOverride = dbFile.absolutePath

        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection(dbUrl).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("""
                    CREATE TABLE project (
                        id TEXT PRIMARY KEY, worktree TEXT NOT NULL, name TEXT,
                        time_created INTEGER NOT NULL, time_updated INTEGER NOT NULL, sandboxes TEXT NOT NULL
                    )
                """)
                stmt.executeUpdate("""
                    CREATE TABLE session (
                        id TEXT PRIMARY KEY, project_id TEXT NOT NULL, parent_id TEXT, slug TEXT NOT NULL,
                        directory TEXT NOT NULL, title TEXT NOT NULL, version TEXT NOT NULL,
                        time_created INTEGER NOT NULL, time_updated INTEGER NOT NULL, time_archived INTEGER
                    )
                """)
                stmt.executeUpdate("""
                    CREATE TABLE message (
                        id TEXT PRIMARY KEY, session_id TEXT NOT NULL,
                        time_created INTEGER NOT NULL, time_updated INTEGER NOT NULL, data TEXT NOT NULL
                    )
                """)
                stmt.executeUpdate("""
                    CREATE TABLE part (
                        id TEXT PRIMARY KEY, message_id TEXT NOT NULL, session_id TEXT NOT NULL,
                        time_created INTEGER NOT NULL, time_updated INTEGER NOT NULL, data TEXT NOT NULL
                    )
                """)
            }
        }
    }

    @After
    fun tearDown() {
        KiloSessionFinder.dbPathOverride = null
        dbFile.delete()
    }

    private fun insertSession(sessionId: String, title: String, directory: String = "/home/user/project") {
        DriverManager.getConnection(dbUrl).use { conn ->
            conn.prepareStatement(
                "INSERT INTO session (id, project_id, slug, directory, title, version, time_created, time_updated) VALUES (?,?,?,?,?,?,?,?)"
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setString(2, "proj1")
                stmt.setString(3, "slug")
                stmt.setString(4, directory)
                stmt.setString(5, title)
                stmt.setString(6, "1")
                stmt.setLong(7, 1700000000000L)
                stmt.setLong(8, 1700000100000L)
                stmt.executeUpdate()
            }
        }
    }

    private fun insertMessage(msgId: String, sessionId: String, role: String, model: String? = null, createdMs: Long = 1700000000000L) {
        val modelPart = if (model != null) ""","modelID":"$model"""" else ""
        val data = """{"role":"$role","time":{"created":$createdMs}$modelPart}"""
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

    private fun insertPart(partId: String, msgId: String, sessionId: String, data: String, createdMs: Long = 1700000000000L) {
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

    @Test
    fun `parseSession should return null for missing DB`() {
        KiloSessionFinder.dbPathOverride = "/nonexistent/path/kilo.db"
        assertNull(KiloSessionParser.parseSession("any-id"))
    }

    @Test
    fun `parseSession should return null for unknown session id`() {
        assertNull(KiloSessionParser.parseSession("unknown-session"))
    }

    @Test
    fun `parseSession should parse user text message`() {
        insertSession("ses_001", "Test session")
        insertMessage("msg_001", "ses_001", "user")
        insertPart("prt_001", "msg_001", "ses_001", """{"type":"text","text":"Hello, what is this project?"}""")

        val session = KiloSessionParser.parseSession("ses_001")

        assertNotNull(session)
        assertEquals("ses_001", session!!.sessionId)
        assertEquals("Test session", session.title)
        assertEquals(1, session.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.User)
        val user = session.messages[0] as ParsedMessage.User
        assertTrue(user.content[0] is MessageContent.Text)
        assertEquals("Hello, what is this project?", (user.content[0] as MessageContent.Text).text)
    }

    @Test
    fun `parseSession should parse assistant text message`() {
        insertSession("ses_002", "Test session")
        insertMessage("msg_001", "ses_002", "assistant", model = "claude-sonnet-4-5")
        insertPart("prt_001", "msg_001", "ses_002", """{"type":"text","text":"This is my answer."}""")

        val session = KiloSessionParser.parseSession("ses_002")

        assertNotNull(session)
        assertEquals(1, session!!.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.AssistantText)
    }

    @Test
    fun `parseSession should parse reasoning message`() {
        insertSession("ses_003", "Test session")
        insertMessage("msg_001", "ses_003", "assistant")
        insertPart("prt_001", "msg_001", "ses_003", """{"type":"reasoning","text":"Let me think about this..."}""")

        val session = KiloSessionParser.parseSession("ses_003")

        assertNotNull(session)
        assertEquals(1, session!!.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.AssistantThinking)
        assertEquals("Let me think about this...", (session.messages[0] as ParsedMessage.AssistantThinking).thinking)
    }

    @Test
    fun `parseSession should parse tool use with output`() {
        insertSession("ses_004", "Test session")
        insertMessage("msg_001", "ses_004", "assistant")
        insertPart("prt_001", "msg_001", "ses_004", """
            {"type":"tool","callID":"call_abc","tool":"read","state":{"status":"completed","input":{"filePath":"/home/user/file.txt"},"output":"file contents here"}}
        """.trimIndent())

        val session = KiloSessionParser.parseSession("ses_004")

        assertNotNull(session)
        assertEquals(1, session!!.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.ToolUse)
        val tool = session.messages[0] as ParsedMessage.ToolUse
        assertEquals("read", tool.toolName)
        assertEquals("call_abc", tool.toolCallId)
        assertEquals("/home/user/file.txt", tool.input["filePath"])
        assertTrue(tool.hasResults())
    }

    @Test
    fun `parseSession should skip step-start and step-finish parts`() {
        insertSession("ses_005", "Test session")
        insertMessage("msg_001", "ses_005", "assistant")
        insertPart("prt_001", "msg_001", "ses_005", """{"type":"step-start","snapshot":"abc123"}""")
        insertPart("prt_002", "msg_001", "ses_005", """{"type":"text","text":"The answer is 42."}""")
        insertPart("prt_003", "msg_001", "ses_005", """{"type":"step-finish","reason":"stop","cost":0}""")

        val session = KiloSessionParser.parseSession("ses_005")

        assertNotNull(session)
        assertEquals(1, session!!.messages.size)
        assertTrue(session.messages[0] is ParsedMessage.AssistantText)
    }

    @Test
    fun `parseSession should extract model from assistant messages`() {
        insertSession("ses_006", "Test session")
        insertMessage("msg_001", "ses_006", "assistant", model = "xiaomi/mimo-v2-pro:free")
        insertPart("prt_001", "msg_001", "ses_006", """{"type":"text","text":"Answer"}""")

        val session = KiloSessionParser.parseSession("ses_006")

        assertNotNull(session)
        assertEquals(1, session!!.metadata!!.models.size)
        assertEquals("xiaomi/mimo-v2-pro:free", session.metadata?.models?.get(0)?.first)
    }

    @Test
    fun `parseSession should populate metadata with directory and timestamps`() {
        insertSession("ses_007", "My session", directory = "/home/user/myproject")

        val session = KiloSessionParser.parseSession("ses_007")

        assertNotNull(session)
        assertEquals("/home/user/myproject", session!!.metadata?.cwd)
        assertNotNull(session.metadata?.created)
        assertNotNull(session.metadata?.modified)
    }

    @Test
    fun `listSessionFiles should return sessions from DB`() {
        insertSession("ses_a", "Session A", "/home/user/project-a")
        insertSession("ses_b", "Session B", "/home/user/project-b")

        val sessions = KiloSessionFinder.listSessionFiles()

        assertEquals(2, sessions.size)
        assertTrue(sessions.any { it.sessionId == "ses_a" && it.title == "Session A" })
        assertTrue(sessions.any { it.sessionId == "ses_b" && it.title == "Session B" })
    }

    @Test
    fun `findSession should return correct session info`() {
        insertSession("ses_find", "Find me", "/home/user/proj")

        val info = KiloSessionFinder.findSession("ses_find")

        assertNotNull(info)
        assertEquals("ses_find", info!!.sessionId)
        assertEquals("Find me", info.title)
        assertEquals("/home/user/proj", info.projectPath)
    }

    @Test
    fun `findSession should return null for missing session`() {
        assertNull(KiloSessionFinder.findSession("nonexistent"))
    }
}

package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.adapter.opencode.OpenCodeSessionFinder
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.sql.DriverManager

class OpenCodeSessionAdapterTest {

    private lateinit var dbFile: File
    private lateinit var dbUrl: String
    private lateinit var project: Project

    @Before
    fun setUp() {
        dbFile = File.createTempFile("opencode-adapter-test-", ".db")
        dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        OpenCodeSessionFinder.dbPathOverride = dbFile.absolutePath

        project = mock(Project::class.java)
        `when`(project.basePath).thenReturn("/home/user/myproject")

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

    private fun insertSession(sessionId: String, title: String, directory: String, updatedMs: Long = 1700000100000L) {
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
                stmt.setLong(8, updatedMs)
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

    // --- findSessions() ---

    @Test
    fun `findSessions should return only sessions matching project directory`() {
        insertSession("ses_match", "Match", "/home/user/myproject")
        insertSession("ses_other", "Other", "/home/user/otherproject")

        val adapter = OpenCodeSessionAdapter(project)
        val sessions = adapter.findSessions()

        assertEquals(1, sessions.size)
        assertEquals("ses_match", sessions[0].sessionId)
        assertEquals("Match", sessions[0].title)
    }

    @Test
    fun `findSessions should return empty list when no sessions match project`() {
        insertSession("ses_x", "Unrelated", "/home/user/unrelated")

        val sessions = OpenCodeSessionAdapter(project).findSessions()

        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `findSessions should return empty list when project basePath is null`() {
        `when`(project.basePath).thenReturn(null)
        insertSession("ses_a", "Session", "/home/user/myproject")

        val sessions = OpenCodeSessionAdapter(project).findSessions()

        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `findSessions should include message count`() {
        insertSession("ses_a", "With messages", "/home/user/myproject")
        insertMessage("msg_1", "ses_a", "user")
        insertMessage("msg_2", "ses_a", "assistant")

        val sessions = OpenCodeSessionAdapter(project).findSessions()

        assertEquals(1, sessions.size)
        assertEquals(2, sessions[0].messageCount)
    }

    @Test
    fun `findSessions should return multiple matching sessions`() {
        insertSession("ses_1", "First", "/home/user/myproject", updatedMs = 1700000200000L)
        insertSession("ses_2", "Second", "/home/user/myproject", updatedMs = 1700000100000L)
        insertSession("ses_3", "Other", "/home/user/other")

        val sessions = OpenCodeSessionAdapter(project).findSessions()

        assertEquals(2, sessions.size)
        assertTrue(sessions.any { it.sessionId == "ses_1" })
        assertTrue(sessions.any { it.sessionId == "ses_2" })
    }

    // --- getSessionDetail() ---

    @Test
    fun `getSessionDetail should return null for unknown session`() {
        val detail = OpenCodeSessionAdapter(project).getSessionDetail("nonexistent")
        assertNull(detail)
    }

    @Test
    fun `getSessionDetail should return parsed session detail`() {
        insertSession("ses_detail", "Detail session", "/home/user/myproject")
        insertMessage("msg_1", "ses_detail", "user")
        insertPart("prt_1", "msg_1", "ses_detail", """{"type":"text","text":"What does this do?"}""")
        insertMessage("msg_2", "ses_detail", "assistant", model = "claude-sonnet-4-6", createdMs = 1700000010000L)
        insertPart("prt_2", "msg_2", "ses_detail", """{"type":"text","text":"This is an IntelliJ plugin."}""", createdMs = 1700000010000L)

        val detail = OpenCodeSessionAdapter(project).getSessionDetail("ses_detail")

        assertNotNull(detail)
        assertEquals("ses_detail", detail!!.sessionId)
        assertEquals("Detail session", detail.title)
        assertEquals(2, detail.messages.size)
        assertTrue(detail.messages[0] is ParsedMessage.User)
        assertTrue(detail.messages[1] is ParsedMessage.AssistantText)
        val text = (detail.messages[1] as ParsedMessage.AssistantText).content[0] as MessageContent.Markdown
        assertEquals("This is an IntelliJ plugin.", text.markdown)
    }

    @Test
    fun `getSessionDetail should populate metadata with project directory`() {
        insertSession("ses_meta", "Meta session", "/home/user/myproject")

        val detail = OpenCodeSessionAdapter(project).getSessionDetail("ses_meta")

        assertNotNull(detail)
        assertEquals("/home/user/myproject", detail!!.metadata?.cwd)
    }
}

package de.espend.ml.llm.session.adapter.opencode

import java.io.File
import java.nio.file.Path
import java.sql.DriverManager

/**
 * Represents an OpenCode session.
 */
data class OpenCodeSession(
    val sessionId: String,
    val slug: String,
    val title: String,
    val directory: String,
    val created: Long,
    val updated: Long,
    val messageCount: Int = 0
)

/**
 * Standalone finder for OpenCode sessions stored in ~/.local/share/opencode/opencode.db.
 * No IntelliJ dependencies.
 */
object OpenCodeSessionFinder {

    /** Overrides the default DB path; intended for tests only. */
    internal var dbPathOverride: String? = null

    private fun getDbFile(): File {
        dbPathOverride?.let { return File(it) }
        return File(System.getProperty("user.home"), ".local/share/opencode/opencode.db")
    }

    fun getDbPath(): String = getDbFile().absolutePath

    /**
     * Lists all non-archived sessions from the SQLite database.
     */
    fun listSessions(): List<OpenCodeSession> {
        val dbFile = getDbFile()
        if (!dbFile.exists()) return emptyList()

        return try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("""
                        SELECT s.id, s.slug, s.title, s.directory, s.time_created, s.time_updated,
                               COUNT(m.id) as message_count
                        FROM session s
                        LEFT JOIN message m ON m.session_id = s.id
                        WHERE s.time_archived IS NULL
                        GROUP BY s.id
                        ORDER BY s.time_updated DESC
                    """.trimIndent())

                    val sessions = mutableListOf<OpenCodeSession>()
                    while (rs.next()) {
                        sessions.add(OpenCodeSession(
                            sessionId = rs.getString("id"),
                            slug = rs.getString("slug") ?: "",
                            title = rs.getString("title") ?: "",
                            directory = rs.getString("directory") ?: "",
                            created = rs.getLong("time_created"),
                            updated = rs.getLong("time_updated"),
                            messageCount = rs.getInt("message_count")
                        ))
                    }
                    sessions
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Finds a specific session by ID.
     */
    fun findSession(sessionId: String): OpenCodeSession? {
        val dbFile = getDbFile()
        if (!dbFile.exists()) return null

        return try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                conn.prepareStatement(
                    "SELECT id, slug, title, directory, time_created, time_updated FROM session WHERE id = ?"
                ).use { stmt ->
                    stmt.setString(1, sessionId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        OpenCodeSession(
                            sessionId = rs.getString("id"),
                            slug = rs.getString("slug") ?: "",
                            title = rs.getString("title") ?: "",
                            directory = rs.getString("directory") ?: "",
                            created = rs.getLong("time_created"),
                            updated = rs.getLong("time_updated")
                        )
                    } else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the DB file path if the session exists, or null if not found.
     * Used by the CLI for session existence checks; actual parsing is done via SQLite.
     */
    fun findSessionFile(sessionId: String): Path? {
        return if (findSession(sessionId) != null) getDbFile().toPath() else null
    }
}

package de.espend.ml.llm.session.adapter.kilocode

import java.io.File
import java.sql.DriverManager

/**
 * Session info for a Kilo Code session.
 */
data class KiloTaskInfo(
    val sessionId: String,
    val title: String,
    val projectPath: String,
    val timeCreated: Long,
    val timeUpdated: Long
)

/**
 * Standalone finder for Kilo Code sessions stored in ~/.local/share/kilo/kilo.db.
 * No IntelliJ dependencies.
 */
object KiloSessionFinder {

    /** Overrides the default DB path; intended for tests only. */
    internal var dbPathOverride: String? = null

    private fun getDbFile(): File {
        dbPathOverride?.let { return File(it) }
        val homeDir = System.getProperty("user.home")
        return File(homeDir, ".local/share/kilo/kilo.db")
    }

    fun getDbPath(): String = getDbFile().absolutePath

    /**
     * Lists all non-archived sessions from the SQLite database.
     */
    fun listSessionFiles(): List<KiloTaskInfo> {
        val dbFile = getDbFile()
        if (!dbFile.exists()) return emptyList()

        return try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("""
                        SELECT id, title, directory, time_created, time_updated
                        FROM session
                        WHERE time_archived IS NULL
                        ORDER BY time_updated DESC
                    """.trimIndent())

                    val sessions = mutableListOf<KiloTaskInfo>()
                    while (rs.next()) {
                        sessions.add(KiloTaskInfo(
                            sessionId = rs.getString("id"),
                            title = rs.getString("title") ?: "",
                            projectPath = rs.getString("directory") ?: "",
                            timeCreated = rs.getLong("time_created"),
                            timeUpdated = rs.getLong("time_updated")
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
     * Find a specific session by ID.
     */
    fun findSession(sessionId: String): KiloTaskInfo? {
        val dbFile = getDbFile()
        if (!dbFile.exists()) return null

        return try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                conn.prepareStatement(
                    "SELECT id, title, directory, time_created, time_updated FROM session WHERE id = ?"
                ).use { stmt ->
                    stmt.setString(1, sessionId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        KiloTaskInfo(
                            sessionId = rs.getString("id"),
                            title = rs.getString("title") ?: "",
                            projectPath = rs.getString("directory") ?: "",
                            timeCreated = rs.getLong("time_created"),
                            timeUpdated = rs.getLong("time_updated")
                        )
                    } else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

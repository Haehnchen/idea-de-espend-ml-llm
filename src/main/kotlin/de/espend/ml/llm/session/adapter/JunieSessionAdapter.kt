package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.junie.JunieSessionFinder
import de.espend.ml.llm.session.adapter.junie.JunieSessionParser

/**
 * Adapter for reading and parsing Junie CLI session files.
 * Uses JunieSessionParser and JunieSessionFinder for the actual work.
 * This class adds IntelliJ Project integration.
 */
class JunieSessionAdapter(private val project: Project) {

    /**
     * Finds all Junie sessions.
     * Note: Junie doesn't store project-specific sessions,
     * so we return all sessions.
     */
    fun findSessions(): List<SessionListItem> {
        return try {
            val sessions = JunieSessionFinder.listSessions()
            if (sessions.isEmpty()) return emptyList()

            sessions.map { session ->
                SessionListItem(
                    sessionId = session.sessionId,
                    title = session.taskName ?: "Untitled",
                    provider = SessionProvider.JUNIE,
                    updated = session.updatedAt,
                    created = session.createdAt,
                    messageCount = 0
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Gets detailed session information for a specific session ID.
     */
    fun getSessionDetail(sessionId: String): SessionDetail? {
        return JunieSessionParser.parseSession(sessionId)
    }
}

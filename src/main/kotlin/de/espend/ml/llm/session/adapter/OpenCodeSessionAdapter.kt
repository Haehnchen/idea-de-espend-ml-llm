package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.opencode.OpenCodeSessionFinder
import de.espend.ml.llm.session.adapter.opencode.OpenCodeSessionParser

/**
 * Adapter for reading OpenCode sessions from the SQLite database.
 */
class OpenCodeSessionAdapter(private val project: Project) {

    fun findSessions(): List<SessionListItem> {
        val projectPath = project.basePath ?: return emptyList()

        return OpenCodeSessionFinder.listSessions()
            .filter { it.directory == projectPath }
            .map { session ->
                SessionListItem(
                    sessionId = session.sessionId,
                    title = session.title,
                    provider = SessionProvider.OPENCODE,
                    updated = session.updated,
                    created = session.created,
                    messageCount = if (session.messageCount > 0) session.messageCount else null
                )
            }
    }

    fun getSessionDetail(sessionId: String): SessionDetail? {
        return OpenCodeSessionParser.parseSession(sessionId)
    }
}

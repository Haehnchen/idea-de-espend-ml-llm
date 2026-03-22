package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.kilocode.KiloSessionFinder
import de.espend.ml.llm.session.adapter.kilocode.KiloSessionParser

/**
 * Adapter for reading and parsing Kilo Code session files.
 * This class adds IntelliJ Project integration.
 */
class KiloSessionAdapter(private val project: Project) {

    fun findSessions(): List<SessionListItem> {
        val projectPath = project.basePath ?: return emptyList()

        return try {
            KiloSessionFinder.listSessionFiles()
                .filter { it.projectPath == projectPath }
                .map { info ->
                    SessionListItem(
                        sessionId = info.sessionId,
                        title = info.title,
                        provider = SessionProvider.KILO_CODE,
                        updated = info.timeUpdated,
                        created = info.timeCreated,
                        messageCount = 0
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSessionDetail(sessionId: String): SessionDetail? {
        return KiloSessionParser.parseSession(sessionId)
    }
}

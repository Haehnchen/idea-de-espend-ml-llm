package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.droid.DroidSessionFinder
import de.espend.ml.llm.session.adapter.droid.DroidSessionParser
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Adapter for reading and parsing Droid (Factory.ai CLI) session files.
 * This class adds IntelliJ Project integration.
 */
class DroidSessionAdapter(private val project: Project) {

    fun findSessions(): List<SessionListItem> {
        val projectPath = project.basePath ?: return emptyList()

        return try {
            val sessions = DroidSessionFinder.listSessions()
                .filter { it.projectPath == projectPath }

            if (sessions.isEmpty()) return emptyList()

            val executor = Executors.newFixedThreadPool(4)
            try {
                val tasks = sessions.map { info ->
                    Callable {
                        try {
                            val updated = try {
                                Instant.parse(info.updated).toEpochMilli()
                            } catch (_: Exception) { 0L }

                            SessionListItem(
                                sessionId = info.sessionId,
                                title = info.title,
                                provider = SessionProvider.DROID,
                                updated = updated,
                                created = updated,
                                messageCount = info.messageCount
                            )
                        } catch (_: Exception) { null }
                    }
                }

                executor.invokeAll(tasks).mapNotNull { future ->
                    try { future.get() } catch (_: Exception) { null }
                }
            } finally {
                executor.shutdown()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSessionDetail(sessionId: String): SessionDetail? {
        val file = DroidSessionFinder.findSessionFile(sessionId) ?: return null
        return DroidSessionParser.parseFile(file)
    }
}

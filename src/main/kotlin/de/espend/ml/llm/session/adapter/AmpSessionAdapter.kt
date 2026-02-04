package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.amp.AmpSessionFinder
import de.espend.ml.llm.session.adapter.amp.AmpSessionParser
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Adapter for reading and parsing AMP session files.
 * Uses AmpSessionParser and AmpSessionFinder for the actual work.
 * This class adds IntelliJ Project integration.
 */
class AmpSessionAdapter(private val project: Project) {

    /**
     * Finds all AMP sessions.
     * Note: AMP doesn't store project-specific sessions like Claude,
     * so we return all sessions and filter by matching working directory if possible.
     */
    fun findSessions(): List<SessionListItem> {
        val projectPath = project.basePath ?: return emptyList()

        return try {
            val sessions = AmpSessionFinder.listSessions()
            if (sessions.isEmpty()) return emptyList()

            // Process sessions in parallel with 4 threads
            val executor = Executors.newFixedThreadPool(4)
            try {
                val tasks = sessions.map { session ->
                    Callable {
                        try {
                            // Filter by matching project path if cwd is available
                            if (session.cwd != null && !session.cwd.startsWith(projectPath)) {
                                return@Callable null
                            }

                            SessionListItem(
                                sessionId = session.sessionId,
                                title = session.firstPrompt ?: "Untitled",
                                provider = SessionProvider.AMP,
                                updated = session.created,
                                created = session.created,
                                messageCount = session.messageCount
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                }

                executor.invokeAll(tasks).mapNotNull { future ->
                    try {
                        future.get()
                    } catch (_: Exception) {
                        null
                    }
                }
            } finally {
                executor.shutdown()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Gets detailed session information for a specific session ID.
     */
    fun getSessionDetail(sessionId: String): SessionDetail? {
        return AmpSessionParser.parseSession(sessionId)
    }
}

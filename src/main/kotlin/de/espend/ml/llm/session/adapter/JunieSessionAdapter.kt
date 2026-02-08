package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.junie.JunieSessionFinder
import de.espend.ml.llm.session.adapter.junie.JunieSessionParser
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Adapter for reading and parsing Junie CLI session files.
 * Uses JunieSessionParser and JunieSessionFinder for the actual work.
 * This class adds IntelliJ Project integration.
 */
class JunieSessionAdapter(private val project: Project) {

    /**
     * Finds Junie sessions matching the current project.
     * Extracts the working directory from each session's first terminal cd command
     * and filters by matching project path.
     */
    fun findSessions(): List<SessionListItem> {
        val projectPath = project.basePath ?: return emptyList()

        return try {
            val sessions = JunieSessionFinder.listSessions()
            if (sessions.isEmpty()) return emptyList()

            // Extract cwd in parallel (lightweight: reads only until first cd command)
            val executor = Executors.newFixedThreadPool(4)
            try {
                val tasks = sessions.map { session ->
                    Callable {
                        try {
                            val cwd = JunieSessionFinder.extractCwd(session.sessionId)
                            if (cwd == null || cwd != projectPath) {
                                return@Callable null
                            }

                            SessionListItem(
                                sessionId = session.sessionId,
                                title = session.taskName ?: "Untitled",
                                provider = SessionProvider.JUNIE,
                                updated = session.updatedAt,
                                created = session.createdAt,
                                messageCount = 0
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
        return JunieSessionParser.parseSession(sessionId)
    }
}

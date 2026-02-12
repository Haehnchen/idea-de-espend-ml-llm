package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.gemini.GeminiSessionFinder
import de.espend.ml.llm.session.adapter.gemini.GeminiSessionParser
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Adapter for reading and parsing Gemini CLI session files.
 * This class adds IntelliJ Project integration.
 */
class GeminiSessionAdapter(private val project: Project) {

    fun findSessions(): List<SessionListItem> {
        val projectPath = project.basePath ?: return emptyList()

        return try {
            val sessions = GeminiSessionFinder.listSessions()
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
                            val created = try {
                                Instant.parse(info.created).toEpochMilli()
                            } catch (_: Exception) { 0L }

                            SessionListItem(
                                sessionId = info.sessionId,
                                title = info.sessionId.take(8),
                                provider = SessionProvider.GEMINI,
                                updated = updated,
                                created = created,
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
        val file = GeminiSessionFinder.findSessionFile(sessionId) ?: return null
        return GeminiSessionParser.parseFile(file)
    }
}

package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.codex.CodexSessionFinder
import de.espend.ml.llm.session.adapter.codex.CodexSessionParser
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Adapter for reading and parsing Codex session files (JetBrains AI Assistant / Codex CLI).
 * Uses CodexSessionParser and CodexSessionFinder for the actual work.
 * This class adds IntelliJ Project integration.
 */
class CodexSessionAdapter(private val project: Project) {

    /**
     * Finds all Codex sessions for the current project.
     * Uses 4 threads for parallel file processing.
     */
    fun findSessions(): List<SessionListItem> {
        val projectPath = project.basePath ?: return emptyList()

        return try {
            val files = CodexSessionFinder.listSessionsForProject(projectPath)
            if (files.isEmpty()) return emptyList()

            val executor = Executors.newFixedThreadPool(4)
            try {
                val tasks = files.map { file ->
                    Callable {
                        try {
                            val sessionId = CodexSessionFinder.extractSessionId(file) ?: file.nameWithoutExtension
                            val metadata = CodexSessionParser.parseJsonlMetadata(file.readText())
                            val created = metadata.created ?: java.time.Instant.now().toString()
                            val modified = metadata.modified ?: java.time.Instant.ofEpochMilli(file.lastModified()).toString()

                            SessionListItem(
                                sessionId = sessionId,
                                title = metadata.summary ?: "Untitled",
                                provider = SessionProvider.CODEX,
                                updated = parseTimestamp(modified),
                                created = parseTimestamp(created),
                                messageCount = metadata.messageCount ?: 0
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
     * Parses an ISO timestamp string to epoch millis.
     */
    private fun parseTimestamp(timestamp: String): Long {
        return try {
            java.time.Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Gets detailed session information for a specific session ID.
     */
    fun getSessionDetail(sessionId: String): SessionDetail? {
        val file = CodexSessionFinder.findSessionFile(sessionId) ?: return null
        return CodexSessionParser.parseFile(file)
    }
}

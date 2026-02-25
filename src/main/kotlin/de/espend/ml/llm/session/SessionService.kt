package de.espend.ml.llm.session

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.adapter.AmpSessionAdapter
import de.espend.ml.llm.session.adapter.ClaudeSessionAdapter
import de.espend.ml.llm.session.adapter.CodexSessionAdapter
import de.espend.ml.llm.session.adapter.DroidSessionAdapter
import de.espend.ml.llm.session.adapter.GeminiSessionAdapter
import de.espend.ml.llm.session.adapter.JunieSessionAdapter
import de.espend.ml.llm.session.adapter.KiloSessionAdapter
import de.espend.ml.llm.session.adapter.OpenCodeSessionAdapter
import de.espend.ml.llm.session.model.ParsedMessage
import java.util.concurrent.Callable
import java.util.concurrent.Executors

object SessionService {

    fun getSessionDetail(project: Project, sessionId: String, provider: SessionProvider): SessionDetail? {
        return when (provider) {
            SessionProvider.CLAUDE_CODE -> ClaudeSessionAdapter(project).getSessionDetail(sessionId)
            SessionProvider.OPENCODE -> OpenCodeSessionAdapter(project).getSessionDetail(sessionId)
            SessionProvider.CODEX -> CodexSessionAdapter(project).getSessionDetail(sessionId)
            SessionProvider.AMP -> AmpSessionAdapter(project).getSessionDetail(sessionId)
            SessionProvider.JUNIE -> JunieSessionAdapter(project).getSessionDetail(sessionId)
            SessionProvider.DROID -> DroidSessionAdapter(project).getSessionDetail(sessionId)
            SessionProvider.GEMINI -> GeminiSessionAdapter(project).getSessionDetail(sessionId)
            SessionProvider.KILO_CODE -> KiloSessionAdapter(project).getSessionDetail(sessionId)
        }
    }

    /**
     * Returns all sessions from all providers, merged and sorted by date (newest first).
     * Uses 8 threads for parallel provider queries.
     */
    fun getAllSessions(project: Project): List<SessionListItem> {
        val executor = Executors.newFixedThreadPool(8)
        return try {
            val tasks = listOf(
                Callable { ClaudeSessionAdapter(project).findSessions() },
                Callable { OpenCodeSessionAdapter(project).findSessions() },
                Callable { CodexSessionAdapter(project).findSessions() },
                Callable { AmpSessionAdapter(project).findSessions() },
                Callable { JunieSessionAdapter(project).findSessions() },
                Callable { DroidSessionAdapter(project).findSessions() },
                Callable { GeminiSessionAdapter(project).findSessions() },
                Callable { KiloSessionAdapter(project).findSessions() }
            )

            executor.invokeAll(tasks)
                .flatMap { future ->
                    try {
                        future.get()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                .sortedByDescending { it.sortTimestamp }
        } catch (_: Exception) {
            emptyList()
        } finally {
            executor.shutdown()
        }
    }
}

data class SessionDetail(
    val sessionId: String,
    val title: String,
    val messages: List<ParsedMessage>,
    val metadata: SessionMetadata? = null
)

data class SessionMetadata(
    val version: String? = null,
    val gitBranch: String? = null,
    val cwd: String? = null,
    val models: List<Pair<String, Int>> = emptyList(),  // Model name to usage count, sorted by count desc
    val created: String? = null,
    val modified: String? = null,
    val messageCount: Int = 0
)

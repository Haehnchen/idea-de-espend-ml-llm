package de.espend.ml.llm.session

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.adapter.ClaudeSessionAdapter
import de.espend.ml.llm.session.adapter.CodexSessionAdapter
import de.espend.ml.llm.session.adapter.OpenCodeSessionAdapter
import de.espend.ml.llm.session.model.ParsedMessage
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) {

    private val claudeAdapter = ClaudeSessionAdapter(project)
    private val openCodeAdapter = OpenCodeSessionAdapter(project)
    private val codexAdapter = CodexSessionAdapter(project)

    companion object {
        fun getInstance(project: Project): SessionService = project.service()
    }

    /**
     * Returns all sessions from all providers, merged and sorted by date (newest first).
     * Uses 4 threads for parallel provider queries.
     */
    fun getAllSessions(): List<SessionListItem> {
        val executor = Executors.newFixedThreadPool(4)
        return try {
            val tasks = listOf(
                Callable { claudeAdapter.findSessions() },
                Callable { openCodeAdapter.findSessions() },
                Callable { codexAdapter.findSessions() }
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


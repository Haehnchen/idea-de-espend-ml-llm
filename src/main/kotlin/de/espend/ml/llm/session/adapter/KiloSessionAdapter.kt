package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.kilocode.KiloSessionFinder
import de.espend.ml.llm.session.adapter.kilocode.KiloSessionParser
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Adapter for reading and parsing Kilo Code CLI session files.
 * This class adds IntelliJ Project integration.
 */
class KiloSessionAdapter(private val project: Project) {

    fun findSessions(): List<SessionListItem> {
        val projectPath = project.basePath ?: return emptyList()

        return try {
            val taskInfos = KiloSessionFinder.listSessionFiles()
                .filter { it.projectPath == projectPath }

            if (taskInfos.isEmpty()) return emptyList()

            val executor = Executors.newFixedThreadPool(4)
            try {
                val tasks = taskInfos.map { info ->
                    Callable {
                        try {
                            val session = KiloSessionParser.parseSession(info.taskPath, info.sessionId)
                                ?: return@Callable null

                            val taskDir = File(info.taskPath)
                            val created = taskDir.lastModified()
                            val updated = taskDir.lastModified()

                            SessionListItem(
                                sessionId = info.sessionId,
                                title = session.title,
                                provider = SessionProvider.KILO_CODE,
                                updated = updated,
                                created = created,
                                messageCount = session.messages.size
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
        val taskDir = KiloSessionFinder.findSessionFile(sessionId) ?: return null
        return KiloSessionParser.parseSession(taskDir.absolutePath, sessionId)
    }
}

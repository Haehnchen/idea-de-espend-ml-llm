package de.espend.ml.llm.session.adapter.junie

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * Represents a Junie session from the index.jsonl file.
 */
data class JunieSession(
    val sessionId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val taskName: String?,
    val status: String?,
    val projectDir: String? = null
)

/**
 * Standalone utility to find Junie session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 */
object JunieSessionFinder {

    private const val JUNIE_DIR = ".junie"
    private const val SESSIONS_DIR = "sessions"
    private const val INDEX_FILE = "index.jsonl"
    private const val PROJECT_DIR_MARKER = "Project root directory:"
    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Gets the Junie sessions directory.
     */
    fun getJunieSessionsDir(): Path? {
        val homeDir = System.getProperty("user.home")
        val sessionsDir = Paths.get(homeDir, JUNIE_DIR, SESSIONS_DIR)
        return if (sessionsDir.exists()) sessionsDir else null
    }

    /**
     * Finds the events.jsonl file for a session.
     */
    fun findSessionFile(sessionId: String): Path? {
        val sessionsDir = getJunieSessionsDir() ?: return null
        val sessionDir = sessionsDir.resolve(sessionId)
        if (!sessionDir.exists()) return null
        val eventsFile = sessionDir.resolve("events.jsonl")
        return if (eventsFile.exists()) eventsFile else null
    }

    /**
     * Lists all sessions from the index.jsonl file.
     */
    fun listSessions(): List<JunieSession> {
        val sessionsDir = getJunieSessionsDir() ?: return emptyList()
        val indexFile = sessionsDir.resolve(INDEX_FILE)
        if (!indexFile.exists()) return emptyList()

        return try {
            indexFile.toFile().bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }
                    .mapNotNull { line -> parseIndexLine(line) }
                    .toList()
            }
                .sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parses a single line from index.jsonl.
     */
    internal fun parseIndexLine(line: String): JunieSession? {
        val json = try {
            JSON.parseToJsonElement(line).jsonObject
        } catch (_: Exception) {
            return null
        }
        val sessionId = json["sessionId"]?.jsonPrimitive?.contentOrNull ?: return null
        val createdAt = json["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L
        val updatedAt = json["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
        val taskName = json["taskName"]?.jsonPrimitive?.contentOrNull
        val status = json["status"]?.jsonPrimitive?.contentOrNull
        val projectDir = json["projectDir"]?.jsonPrimitive?.contentOrNull

        return JunieSession(
            sessionId = sessionId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            taskName = taskName,
            status = status,
            projectDir = projectDir
        )
    }

    /**
     * Extracts the project directory from the standalone project worker for
     * sessions whose index entry predates the projectDir field.
     */
    fun extractProjectDir(sessionId: String): String? {
        val sessionsDir = getJunieSessionsDir() ?: return null
        val sessionDir = sessionsDir.resolve(sessionId)
        if (!sessionDir.exists()) return null

        val projectDirFile = findProjectWorkerFile(sessionDir.toFile())
        return projectDirFile?.let { extractProjectDirFromFile(it) }
    }

    /**
     * Finds the issue.standalone_project_str_worker file in a session directory.
     */
    internal fun findProjectWorkerFile(sessionDir: File): File? {
        // The file is located at: session-{id}/task-{id}/.matterhorn/representations/issue_md/issue.standalone_project_str_worker
        val taskDirs = sessionDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("task-") } ?: emptyList()
        for (taskDir in taskDirs) {
            val workerFile = File(taskDir, ".matterhorn/representations/issue_md/issue.standalone_project_str_worker")
            if (workerFile.exists()) return workerFile
        }
        return null
    }

    /**
     * Extracts project directory from issue.standalone_project_str_worker file.
     */
    internal fun extractProjectDirFromFile(file: File): String? {
        return try {
            file.bufferedReader().use { reader ->
                reader.useLines { lines ->
                    lines.firstNotNullOfOrNull { line ->
                        val markerIndex = line.indexOf(PROJECT_DIR_MARKER)
                        if (markerIndex < 0) return@firstNotNullOfOrNull null
                        line.substring(markerIndex + PROJECT_DIR_MARKER.length)
                            .trim()
                            .takeIf { it.isNotEmpty() }
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

}

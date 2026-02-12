package de.espend.ml.llm.session.adapter.kilocode

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Session file info for a Kilo Code CLI task.
 */
data class KiloTaskInfo(
    val taskPath: String,
    val taskId: String,
    val sessionId: String,
    val projectPath: String
)

/**
 * Standalone finder for Kilo Code CLI session files.
 * Locates sessions in ~/.kilocode/cli/ directory structure.
 * No IntelliJ dependencies.
 */
object KiloSessionFinder {

    private val JSON = Json { ignoreUnknownKeys = true }

    private fun getBaseDir(): File {
        val homeDir = System.getProperty("user.home")
        return File(homeDir, ".kilocode/cli")
    }

    /**
     * Lists all Kilo CLI sessions by iterating through workspace projects.
     */
    fun listSessionFiles(): List<KiloTaskInfo> {
        val baseDir = getBaseDir()
        val sessions = mutableListOf<KiloTaskInfo>()

        val workspaceMap = loadWorkspaceMap(baseDir) ?: return sessions
        val tasksDir = File(baseDir, "global/tasks")

        for ((projectPath, workspaceDir) in workspaceMap) {
            val sessionFile = File(baseDir, "workspaces/$workspaceDir/session.json")
            if (!sessionFile.exists()) continue

            try {
                val content = sessionFile.readText()
                val data = JSON.parseToJsonElement(content).jsonObject
                val taskSessionMap = data["taskSessionMap"]?.jsonObject ?: continue

                for ((taskId, sessionIdElement) in taskSessionMap) {
                    val sessionId = sessionIdElement.jsonPrimitive.content
                    val taskPath = File(tasksDir, taskId)

                    if (taskPath.exists()) {
                        sessions.add(KiloTaskInfo(
                            taskPath = taskPath.absolutePath,
                            taskId = taskId,
                            sessionId = sessionId,
                            projectPath = projectPath
                        ))
                    }
                }
            } catch (_: Exception) {
                // Skip this workspace on error
            }
        }

        return sessions
    }

    /**
     * Find a specific task directory by session ID.
     */
    fun findSessionFile(sessionId: String): File? {
        val allSessions = listSessionFiles()
        val info = allSessions.find { it.sessionId == sessionId || it.taskId == sessionId }
        return if (info != null) File(info.taskPath) else null
    }

    /**
     * Load workspace-map.json which maps project paths to workspace directories.
     */
    private fun loadWorkspaceMap(baseDir: File): Map<String, String>? {
        val workspaceMapFile = File(baseDir, "workspaces/workspace-map.json")
        if (!workspaceMapFile.exists()) return null

        return try {
            val content = workspaceMapFile.readText()
            val json = JSON.parseToJsonElement(content).jsonObject
            json.entries.associate { (key, value) -> key to value.jsonPrimitive.content }
        } catch (_: Exception) {
            null
        }
    }
}

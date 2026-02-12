package de.espend.ml.llm.session.adapter.droid

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Session metadata for a Droid (Factory.ai CLI) session file.
 */
data class DroidSessionInfo(
    val sessionId: String,
    val filePath: String,
    val projectPath: String,
    val projectName: String,
    val title: String,
    val created: String,
    val updated: String,
    val messageCount: Int
)

/**
 * Standalone finder for Droid (Factory.ai CLI) session files.
 * Locates session files in ~/.factory/sessions/{sanitized-project-path}/
 * No IntelliJ dependencies.
 */
object DroidSessionFinder {

    private val JSON = Json { ignoreUnknownKeys = true }

    private fun getBaseDir(): File {
        val homeDir = System.getProperty("user.home")
        return File(homeDir, ".factory/sessions")
    }

    /**
     * List all session files across all projects.
     */
    fun listSessions(): List<DroidSessionInfo> {
        val baseDir = getBaseDir()
        if (!baseDir.exists()) return emptyList()

        val sessions = mutableListOf<DroidSessionInfo>()

        baseDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val files = projectDir.listFiles()
                ?.filter { it.name.endsWith(".jsonl") && !it.name.endsWith(".settings.jsonl") }
                ?: emptyList()

            for (file in files) {
                val info = parseSessionInfo(file, projectDir.name)
                if (info != null) {
                    sessions.add(info)
                }
            }
        }

        return sessions.sortedByDescending { it.updated }
    }

    /**
     * Find a specific session file by ID across all projects.
     */
    fun findSessionFile(sessionId: String): File? {
        val baseDir = getBaseDir()
        if (!baseDir.exists()) return null

        baseDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val files = projectDir.listFiles()
                ?.filter { it.name.endsWith(".jsonl") && !it.name.endsWith(".settings.jsonl") }
                ?: emptyList()

            for (file in files) {
                try {
                    val firstLine = file.bufferedReader().readLine() ?: continue
                    val json = JSON.parseToJsonElement(firstLine).jsonObject
                    if (json["type"]?.jsonPrimitive?.content == "session_start") {
                        val id = json["id"]?.jsonPrimitive?.content
                        if (id == sessionId) return file
                    }
                } catch (_: Exception) {
                    // Skip invalid files
                }
            }
        }

        return null
    }

    private fun parseSessionInfo(file: File, projectDirName: String): DroidSessionInfo? {
        return try {
            val firstLine = file.bufferedReader().readLine() ?: return null
            val json = JSON.parseToJsonElement(firstLine).jsonObject

            if (json["type"]?.jsonPrimitive?.content != "session_start") return null

            val sessionId = json["id"]?.jsonPrimitive?.content ?: return null
            val cwd = json["cwd"]?.jsonPrimitive?.content
            val projectPath = cwd ?: unsanitizeProjectName(projectDirName)
            val projectName = File(projectPath).name
            val title = json["title"]?.jsonPrimitive?.content
                ?: json["sessionTitle"]?.jsonPrimitive?.content
                ?: "Droid Session"

            val modified = java.time.Instant.ofEpochMilli(file.lastModified()).toString()
            val lineCount = file.readLines().filter { it.isNotBlank() }.size

            DroidSessionInfo(
                sessionId = sessionId,
                filePath = file.absolutePath,
                projectPath = projectPath,
                projectName = projectName,
                title = title,
                created = modified,
                updated = modified,
                messageCount = lineCount - 1
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Convert sanitized directory name back to project path.
     * e.g., "-home-daniel-plugins" -> "/home/daniel/plugins"
     */
    private fun unsanitizeProjectName(name: String): String {
        var result = name
        if (result.startsWith("-")) {
            result = "/" + result.substring(1)
        }
        return result.replace("-", "/")
    }
}

package de.espend.ml.llm.session.adapter.gemini

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Session metadata for a Gemini CLI session file.
 */
data class GeminiSessionInfo(
    val sessionId: String,
    val projectHash: String,
    val projectName: String,
    val projectPath: String,
    val filePath: String,
    val created: String,
    val updated: String,
    val messageCount: Int
)

/**
 * Standalone finder for Gemini CLI session files.
 * Locates session files in ~/.gemini/tmp/{project-hash}/chats/
 * Each project directory contains a .project_root file with the actual path.
 * No IntelliJ dependencies.
 */
object GeminiSessionFinder {

    private val JSON = Json { ignoreUnknownKeys = true }

    private fun getBaseDir(): File {
        val homeDir = System.getProperty("user.home")
        return File(homeDir, ".gemini/tmp")
    }

    /**
     * Read project path from .project_root file.
     */
    private fun readProjectRoot(projectDir: File): String? {
        val projectRootFile = File(projectDir, ".project_root")
        if (!projectRootFile.exists()) return null

        return try {
            val content = projectRootFile.readText().trim()
            content.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * List all session files across all projects.
     */
    fun listSessions(): List<GeminiSessionInfo> {
        val baseDir = getBaseDir()
        if (!baseDir.exists()) return emptyList()

        val sessions = mutableListOf<GeminiSessionInfo>()

        baseDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val projectRoot = readProjectRoot(projectDir) ?: return@forEach
            val projectName = File(projectRoot).name

            val chatsDir = File(projectDir, "chats")
            if (!chatsDir.exists()) return@forEach

            val files = chatsDir.listFiles()
                ?.filter { it.name.endsWith(".json") && it.name.startsWith("session-") }
                ?: emptyList()

            for (file in files) {
                try {
                    val content = file.readText()
                    val json = JSON.parseToJsonElement(content).jsonObject

                    val sessionId = json["sessionId"]?.jsonPrimitive?.content ?: continue
                    val projectHash = json["projectHash"]?.jsonPrimitive?.content ?: ""
                    val startTime = json["startTime"]?.jsonPrimitive?.content ?: ""
                    val lastUpdated = json["lastUpdated"]?.jsonPrimitive?.content ?: ""
                    val messageCount = json["messages"]?.jsonArray?.size ?: 0

                    sessions.add(GeminiSessionInfo(
                        sessionId = sessionId,
                        projectHash = projectHash,
                        projectName = projectName,
                        projectPath = projectRoot,
                        filePath = file.absolutePath,
                        created = startTime,
                        updated = lastUpdated,
                        messageCount = messageCount
                    ))
                } catch (_: Exception) {
                    // Skip invalid session files
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
            val chatsDir = File(projectDir, "chats")
            if (!chatsDir.exists()) return@forEach

            chatsDir.listFiles()
                ?.filter { it.name.endsWith(".json") && it.name.startsWith("session-") }
                ?.forEach { file ->
                    try {
                        val content = file.readText()
                        val json = JSON.parseToJsonElement(content).jsonObject
                        if (json["sessionId"]?.jsonPrimitive?.content == sessionId) {
                            return file
                        }
                    } catch (_: Exception) {
                        // Skip invalid files
                    }
                }
        }

        return null
    }
}

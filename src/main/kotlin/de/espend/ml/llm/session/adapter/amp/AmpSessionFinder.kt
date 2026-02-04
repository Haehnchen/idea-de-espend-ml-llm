package de.espend.ml.llm.session.adapter.amp

import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/**
 * Represents an AMP session/thread.
 */
data class AmpSession(
    val sessionId: String,
    val created: Long,
    val messageCount: Int,
    val firstPrompt: String? = null,
    val cwd: String? = null
)

/**
 * Standalone utility to find AMP session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 */
object AmpSessionFinder {

    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Gets the AMP storage directory.
     */
    fun getAmpDir(): Path? {
        val homeDir = System.getProperty("user.home")
        val ampDir = Paths.get(homeDir, ".local", "share", "amp")
        return if (ampDir.exists()) ampDir else null
    }

    /**
     * Gets the AMP threads directory.
     */
    fun getThreadsDir(): Path? {
        val ampDir = getAmpDir() ?: return null
        val threadsDir = ampDir.resolve("threads")
        return if (threadsDir.exists()) threadsDir else null
    }

    /**
     * Lists all AMP sessions (threads).
     */
    fun listSessions(): List<AmpSession> {
        val threadsDir = getThreadsDir() ?: return emptyList()

        return threadsDir.listDirectoryEntries("T-*.json")
            .mapNotNull { file ->
                try {
                    parseSessionFile(file)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.created }
    }

    /**
     * Lists AMP sessions from a custom directory (for testing).
     * @param threadsDir Path to a directory containing T-*.json session files
     */
    fun listSessions(threadsDir: Path): List<AmpSession> {
        if (!threadsDir.exists()) return emptyList()

        return threadsDir.listDirectoryEntries("T-*.json")
            .mapNotNull { file ->
                try {
                    parseSessionFile(file)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.created }
    }

    /**
     * Finds a session file by ID.
     */
    fun findSessionFile(sessionId: String): Path? {
        val threadsDir = getThreadsDir() ?: return null
        val file = threadsDir.resolve("$sessionId.json")
        return if (file.exists() && file.isRegularFile()) file else null
    }

    /**
     * Parses a session file to extract metadata.
     */
    private fun parseSessionFile(file: Path): AmpSession? {
        val content = file.readText()
        val json = JSON.parseToJsonElement(content).jsonObject

        // Use filename as session ID (consistent with findSessionFile)
        val id = file.fileName.toString().removeSuffix(".json")
        val created = json["created"]?.jsonPrimitive?.longOrNull ?: 0L

        // Count messages
        val messagesArray = json["messages"]?.jsonArray
        val messageCount = messagesArray?.size ?: 0

        // Extract first user message for title
        val firstPrompt = extractFirstPrompt(json)

        // Extract working directory from tool results (they contain absolute paths)
        val cwd = extractWorkingDirectory(json)

        return AmpSession(
            sessionId = id,
            created = created,
            messageCount = messageCount,
            firstPrompt = firstPrompt,
            cwd = cwd
        )
    }

    /**
     * Extracts the first user prompt from the session JSON.
     */
    private fun extractFirstPrompt(json: kotlinx.serialization.json.JsonObject): String? {
        val messagesArray = json["messages"]?.jsonArray ?: return null

        // Find first user message
        messagesArray.forEach { messageElement ->
            val message = messageElement.jsonObject
            if (message["role"]?.jsonPrimitive?.content == "user") {
                val contentArray = message["content"]?.jsonArray ?: return@forEach
                contentArray.forEach { element ->
                    val content = element.jsonObject
                    if (content["type"]?.jsonPrimitive?.content == "text") {
                        val text = content["text"]?.jsonPrimitive?.content
                        if (!text.isNullOrBlank()) {
                            return text.take(100) + if (text.length > 100) "..." else ""
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Extracts the working directory from env.initial.trees[].uri.
     * This is the authoritative source for the session's working directory.
     */
    private fun extractWorkingDirectory(json: kotlinx.serialization.json.JsonObject): String? {
        // Navigate to env.initial.trees array
        val env = json["env"]?.jsonObject ?: return null
        val initial = env["initial"]?.jsonObject ?: return null
        val trees = initial["trees"]?.jsonArray ?: return null

        // Get first tree's URI and extract the path
        val firstTree = trees.firstOrNull()?.jsonObject ?: return null
        val uri = firstTree["uri"]?.jsonPrimitive?.content ?: return null

        // Remove "file://" prefix and return
        return uri.removePrefix("file://")
    }
}

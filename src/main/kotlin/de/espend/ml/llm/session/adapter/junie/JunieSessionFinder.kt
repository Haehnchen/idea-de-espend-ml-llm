package de.espend.ml.llm.session.adapter.junie

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
    val cwd: String? = null
)

/**
 * Standalone utility to find Junie session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 */
object JunieSessionFinder {

    private const val JUNIE_DIR = ".junie"
    private const val SESSIONS_DIR = "sessions"
    private const val INDEX_FILE = "index.jsonl"

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
            indexFile.toFile().readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> parseIndexLine(line) }
                .sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parses a single line from index.jsonl.
     */
    private fun parseIndexLine(line: String): JunieSession? {
        val sessionId = extractJsonStringField(line, "sessionId") ?: return null
        val createdAt = extractJsonNumericField(line, "createdAt")?.toLongOrNull() ?: 0L
        val updatedAt = extractJsonNumericField(line, "updatedAt")?.toLongOrNull() ?: 0L
        val taskName = extractJsonStringField(line, "taskName")
        val status = extractJsonStringField(line, "status")

        return JunieSession(
            sessionId = sessionId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            taskName = taskName,
            status = status
        )
    }

    /**
     * Extracts the working directory from a session's events.jsonl.
     * Lightweight: reads lines until it finds a terminal cd command, then stops.
     * Skips expensive lines (AgentStateUpdatedEvent) via string pre-filter.
     */
    fun extractCwd(sessionId: String): String? {
        val eventsFile = findSessionFile(sessionId) ?: return null
        return extractCwdFromFile(eventsFile.toFile())
    }

    private val CD_PATTERN = """"command"\s*:\s*"cd\s+(/[^\s"&;\\]+)""".toRegex()

    internal fun extractCwdFromFile(file: File): String? {
        try {
            file.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    // Skip expensive lines
                    if (line.contains("AgentStateUpdatedEvent")) return@forEach
                    if (!line.contains("TerminalBlockUpdatedEvent")) return@forEach

                    val match = CD_PATTERN.find(line)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }
        return null
    }

    /**
     * Extracts a JSON string field value.
     */
    private fun extractJsonStringField(json: String, fieldName: String): String? {
        val pattern = """"$fieldName"\s*:\s*"([^"]*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    /**
     * Extracts a JSON numeric field value.
     */
    private fun extractJsonNumericField(json: String, fieldName: String): String? {
        val pattern = """"$fieldName"\s*:\s*(\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }
}

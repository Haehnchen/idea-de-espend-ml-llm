package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.claude.ClaudeSessionFinder
import de.espend.ml.llm.session.adapter.claude.ClaudeSessionParser
import de.espend.ml.llm.session.model.ParsedMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Metadata extracted from parsing a Claude Code JSONL file.
 */
data class ClaudeSessionMetadata(
    val summary: String? = null,
    val messageCount: Int? = null,
    val created: String? = null,
    val modified: String? = null
)

/**
 * Adapter for reading and parsing Claude Code session files.
 * Uses ClaudeSessionParser and ClaudeSessionFinder for the actual work.
 * This class adds IntelliJ Project integration.
 */
class ClaudeSessionAdapter(private val project: Project) {

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Parses a Claude Code .jsonl file content and extracts messages with metadata.
         * Delegates to ClaudeSessionParser.
         */
        fun parseSessionFile(content: String): Pair<List<ParsedMessage>, SessionMetadata?> {
            return ClaudeSessionParser.parseContent(content)
        }

        /**
         * Parses JSONL file content to extract session metadata (lightweight, for list view).
         * Optimization: Only fully parses the first 10 rows to find firstPrompt,
         * after that just counts lines and extracts timestamp via simple string search.
         */
        fun parseJsonlMetadata(content: String): ClaudeSessionMetadata {
            return try {
                val lines = content.lines()
                var summary: String? = null
                var messageCount = 0
                var created: String? = null
                var lastTimestamp: String? = null
                var parsedLines = 0
                val maxLinesToParse = 10

                for (line in lines) {
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("{")) continue

                    messageCount++

                    // Extract timestamp via simple string search for all lines
                    val timestamp = extractTimestamp(trimmed)
                    if (timestamp != null) {
                        if (created == null) created = timestamp
                        lastTimestamp = timestamp
                    }

                    // Only fully parse the first 10 lines to find summary
                    if (parsedLines < maxLinesToParse && summary == null) {
                        parsedLines++
                        try {
                            val json = JSON.parseToJsonElement(trimmed).jsonObject

                            // Skip meta messages (e.g., local-command-caveat)
                            val isMeta = json["isMeta"]?.jsonPrimitive?.booleanOrNull ?: false
                            if (isMeta) continue

                            val type = json["type"]?.jsonPrimitive?.content
                            if (type == "user") {
                                val message = json["message"]?.jsonObject
                                when (val contentElement = message?.get("content")) {
                                    // Handle content as string (simple format)
                                    is JsonPrimitive -> {
                                        val text = contentElement.content
                                        // Skip command messages and local-command-stdout
                                        if (text.contains("<command-name>") || text.contains("<local-command-stdout>")) {
                                            continue
                                        }
                                        if (text.isNotEmpty()) {
                                            summary = if (text.length > 100) text.take(100) + "..." else text
                                        }
                                    }
                                    // Handle content as array (structured format)
                                    is JsonArray -> {
                                        val text = contentElement.mapNotNull { item ->
                                            item.jsonObject["text"]?.jsonPrimitive?.content
                                        }.joinToString("")
                                        if (text.isNotEmpty()) {
                                            summary = if (text.length > 100) text.take(100) + "..." else text
                                        }
                                    }
                                    else -> { /* Unknown content format, skip */ }
                                }
                            }
                        } catch (_: Exception) {
                            // Line counted but couldn't parse, continue
                        }
                    }
                }

                ClaudeSessionMetadata(
                    summary = summary,
                    messageCount = messageCount,
                    created = created,
                    modified = lastTimestamp
                )
            } catch (_: Exception) {
                ClaudeSessionMetadata()
            }
        }

        /**
         * Extracts timestamp from a line using simple string search.
         * Looks for pattern: "timestamp":"2026-01-24T14:48:01.288Z"
         */
        fun extractTimestamp(line: String): String? {
            val marker = "\"timestamp\":\""
            val startIndex = line.indexOf(marker)
            if (startIndex == -1) return null

            val valueStart = startIndex + marker.length
            val endIndex = line.indexOf('"', valueStart)
            if (endIndex == -1) return null

            return line.substring(valueStart, endIndex)
        }
    }

    /**
     * Finds all Claude Code sessions for the current project.
     * Uses 4 threads for parallel file processing.
     */
    fun findSessions(): List<SessionListItem> {
        val projectDir = getProjectDir() ?: return emptyList()

        return try {
            val files = projectDir.listFiles()?.filter { it.name.endsWith(".jsonl") } ?: return emptyList()
            if (files.isEmpty()) return emptyList()

            val executor = Executors.newFixedThreadPool(4)
            try {
                val tasks = files.map { file ->
                    Callable {
                        try {
                            val sessionId = file.nameWithoutExtension
                            val metadata = parseJsonlMetadata(file.readText())
                            val created = metadata.created ?: java.time.Instant.now().toString()
                            val modified = metadata.modified ?: java.time.Instant.ofEpochMilli(file.lastModified()).toString()

                            SessionListItem(
                                sessionId = sessionId,
                                title = metadata.summary ?: "Untitled",
                                provider = SessionProvider.CLAUDE_CODE,
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
     * Gets the Claude project directory for the current IntelliJ project.
     */
    private fun getProjectDir(): File? {
        val projectPath = project.basePath ?: return null
        val claudeDir = ClaudeSessionFinder.getClaudeProjectsDir()
        val normalizedPath = ClaudeSessionFinder.projectPathToClaudeDir(projectPath)
        val projectDir = File(claudeDir, normalizedPath)
        return if (projectDir.exists()) projectDir else null
    }

    /**
     * Gets detailed session information for a specific session ID.
     */
    fun getSessionDetail(sessionId: String): SessionDetail? {
        val file = getSessionFile(sessionId) ?: return null
        return ClaudeSessionParser.parseFile(file)
    }

    /**
     * Gets the file for a specific session ID.
     */
    fun getSessionFile(sessionId: String): File? {
        val projectDir = getProjectDir() ?: return null
        val file = File(projectDir, "$sessionId.jsonl")
        return if (file.exists()) file else null
    }
}

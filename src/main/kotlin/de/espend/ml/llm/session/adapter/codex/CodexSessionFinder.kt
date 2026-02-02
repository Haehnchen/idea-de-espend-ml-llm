package de.espend.ml.llm.session.adapter.codex

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Represents a Codex session with basic metadata extracted from the first line.
 */
data class CodexSession(
    val sessionId: String,
    val cwd: String?,
    val originator: String?,
    val cliVersion: String?,
    val model: String?,
    val gitBranch: String?,
    val created: Long,
    val updated: Long
)

/**
 * Utility to find Codex session files.
 * Searches in multiple locations:
 * - JetBrains IDE: ~/.cache/JetBrains/{IDE}/aia/codex/sessions/{year}/{month}/{day}/
 * - Standalone CLI: ~/.codex/sessions/{year}/{month}/{day}/
 *
 * File format: rollout-{timestamp}-{sessionId}.jsonl
 */
object CodexSessionFinder {

    private const val MAX_DAYS_TO_SEARCH = 30

    /**
     * Gets all Codex sessions directories.
     * Searches in:
     * - Current IDE via PathManager
     * - Standalone Codex CLI directory (~/.codex/sessions)
     */
    fun getCodexSessionsDirs(): List<File> {
        val dirs = mutableSetOf<String>() // Use canonical paths for deduplication
        val homeDir = System.getProperty("user.home")

        // Use PathManager to get the current IDE's sessions directory
        try {
            val pathManagerClass = Class.forName("com.intellij.openapi.application.PathManager")
            val getSystemPath = pathManagerClass.getMethod("getSystemPath")
            val systemPath = getSystemPath.invoke(null) as String
            val ideSessionsDir = File(systemPath, "aia/codex/sessions")
            if (ideSessionsDir.exists() && ideSessionsDir.isDirectory) {
                dirs.add(ideSessionsDir.canonicalPath)
            }
        } catch (_: Exception) {
            // PathManager not available
        }

        // Standalone Codex CLI sessions directory
        val cliSessionsDir = File(homeDir, ".codex/sessions")
        if (cliSessionsDir.exists() && cliSessionsDir.isDirectory) {
            dirs.add(cliSessionsDir.canonicalPath)
        }

        return dirs.map { File(it) }
    }

    /**
     * Lists all session files across all JetBrains IDEs, searching up to MAX_DAYS_TO_SEARCH days.
     * Deduplicates by session ID, keeping the most recently modified file.
     */
    fun listSessionFiles(): List<File> {
        val sessionDirs = getCodexSessionsDirs()
        if (sessionDirs.isEmpty()) return emptyList()

        val today = LocalDate.now()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        val filesBySessionId = mutableMapOf<String, File>() // Deduplicate by session ID

        for (sessionsDir in sessionDirs) {
            for (dayOffset in 0 until MAX_DAYS_TO_SEARCH) {
                val date = today.minusDays(dayOffset.toLong())
                val datePath = date.format(dateFormatter)
                val dayDir = File(sessionsDir, datePath)

                if (dayDir.exists() && dayDir.isDirectory) {
                    dayDir.listFiles()
                        ?.filter { it.name.endsWith(".jsonl") && it.name.startsWith("rollout-") }
                        ?.forEach { file ->
                            val sessionId = extractSessionId(file)
                            if (sessionId != null) {
                                val existing = filesBySessionId[sessionId]
                                // Keep the most recently modified file
                                if (existing == null || file.lastModified() > existing.lastModified()) {
                                    filesBySessionId[sessionId] = file
                                }
                            }
                        }
                }
            }
        }

        return filesBySessionId.values.sortedByDescending { it.lastModified() }
    }

    /**
     * Finds a session file by ID across all JetBrains IDEs.
     * Session ID is the UUID part of the filename: rollout-{timestamp}-{sessionId}.jsonl
     */
    fun findSessionFile(sessionId: String): File? {
        val sessionDirs = getCodexSessionsDirs()
        if (sessionDirs.isEmpty()) return null

        val today = LocalDate.now()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

        for (sessionsDir in sessionDirs) {
            for (dayOffset in 0 until MAX_DAYS_TO_SEARCH) {
                val date = today.minusDays(dayOffset.toLong())
                val datePath = date.format(dateFormatter)
                val dayDir = File(sessionsDir, datePath)

                if (dayDir.exists() && dayDir.isDirectory) {
                    val file = dayDir.listFiles()
                        ?.find { it.name.endsWith("-$sessionId.jsonl") }
                    if (file != null) return file
                }
            }
        }

        return null
    }

    /**
     * Extracts session ID from filename.
     * Format: rollout-{timestamp}-{sessionId}.jsonl
     * Example: rollout-2026-01-26T12-27-20-019bfa0e-ec98-70e0-8575-5132b767abff.jsonl
     */
    fun extractSessionId(file: File): String? {
        val name = file.nameWithoutExtension
        if (!name.startsWith("rollout-")) return null

        // Find the UUID pattern at the end: 8-4-4-4-12 hex digits
        val uuidRegex = Regex("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$")
        return uuidRegex.find(name)?.groupValues?.get(1)
    }

    /**
     * Lists sessions filtered by project path (cwd).
     * Parses the first line of each file to extract cwd and filter by project.
     */
    fun listSessionsForProject(projectPath: String): List<File> {
        return listSessionFiles().filter { file ->
            try {
                val firstLine = file.bufferedReader().use { it.readLine() }
                if (firstLine != null && firstLine.startsWith("{")) {
                    firstLine.contains("\"cwd\":\"${projectPath.replace("\\", "\\\\")}\"") ||
                        firstLine.contains("\"cwd\":\"$projectPath\"")
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}

package de.espend.ml.llm.session.adapter.opencode

import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import java.nio.file.Path as JavaPath

/**
 * Represents an OpenCode session.
 */
data class OpenCodeSession(
    val sessionId: String,
    val slug: String,
    val title: String,
    val created: Long,
    val updated: Long,
    val messageCount: Int = 0
)

/**
 * Standalone utility to find OpenCode session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 */
object OpenCodeSessionFinder {

    /**
     * Gets the OpenCode storage directory.
     */
    fun getStorageDir(): JavaPath? {
        val homeDir = System.getProperty("user.home")
        val storageDir = Paths.get(homeDir, ".local", "share", "opencode", "storage")
        return if (storageDir.exists()) storageDir else null
    }

    /**
     * Lists all OpenCode sessions.
     */
    fun listSessions(): List<OpenCodeSession> {
        val storageDir = getStorageDir() ?: return emptyList()
        val sessionDir = storageDir.resolve("session")
        val messageDir = storageDir.resolve("message")

        if (!sessionDir.exists()) {
            return emptyList()
        }

        val allSessions = mutableListOf<OpenCodeSession>()

        // Collect sessions from all project directories
        sessionDir.listDirectoryEntries().forEach { projectSessionDir ->
            if (!projectSessionDir.isDirectory()) return@forEach

            projectSessionDir.listDirectoryEntries("*.json").forEach { sessionFile ->
                try {
                    val content = sessionFile.readText()
                    val session = OpenCodeSessionParser.JSON.decodeFromString<OpenCodeSessionData>(content)
                    val msgCount = countMessages(messageDir, session.id)
                    allSessions.add(
                        OpenCodeSession(
                            sessionId = session.id,
                            slug = session.slug,
                            title = session.title,
                            created = session.time.created,
                            updated = session.time.updated,
                            messageCount = msgCount
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid sessions
                }
            }
        }

        return allSessions.sortedByDescending { it.updated }
    }

    /**
     * Finds a session file by ID.
     */
    fun findSessionFile(sessionId: String): JavaPath? {
        val storageDir = getStorageDir() ?: return null
        val sessionDir = storageDir.resolve("session")

        // Search through all project directories for the session file
        sessionDir.listDirectoryEntries().forEach { projectDir ->
            if (!projectDir.isDirectory()) return@forEach

            projectDir.listDirectoryEntries("*.json").forEach { sessionFile ->
                try {
                    val content = sessionFile.readText()
                    val session = OpenCodeSessionParser.JSON.decodeFromString<OpenCodeSessionData>(content)
                    if (session.id == sessionId) {
                        return sessionFile
                    }
                } catch (e: Exception) {
                    // Continue searching
                }
            }
        }

        return null
    }

    /**
     * Counts messages for a session by checking the message directory.
     */
    private fun countMessages(messageDir: JavaPath, sessionId: String): Int {
        val sessionMessageDir = messageDir.resolve(sessionId)
        if (!sessionMessageDir.exists()) {
            return 0
        }
        return try {
            sessionMessageDir.listDirectoryEntries("*.json").size
        } catch (e: Exception) {
            0
        }
    }
}

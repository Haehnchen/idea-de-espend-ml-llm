package de.espend.ml.llm.session.adapter.claude

import java.io.File

/**
 * Standalone utility to find Claude session files.
 * No IntelliJ dependencies - can be used from CLI or IDE.
 */
object ClaudeSessionFinder {

    private const val CLAUDE_DIR = ".claude"
    private const val PROJECTS_DIR = "projects"

    /**
     * Gets the Claude projects directory.
     */
    fun getClaudeProjectsDir(): File {
        val homeDir = System.getProperty("user.home")
        return File(homeDir, "$CLAUDE_DIR/$PROJECTS_DIR")
    }

    /**
     * Converts a project path to Claude's directory format.
     */
    fun projectPathToClaudeDir(projectPath: String): String {
        return projectPath.replace("/", "-").replace(":", "")
    }

    /**
     * Finds a session file by ID across all projects.
     */
    fun findSessionFile(sessionId: String): File? {
        val claudeDir = getClaudeProjectsDir()
        if (!claudeDir.exists()) return null

        // Search all project directories
        claudeDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val file = File(projectDir, "$sessionId.jsonl")
            if (file.exists()) return file
        }

        return null
    }

    /**
     * Lists all session files across all projects.
     */
    fun listSessionFiles(): List<File> {
        val claudeDir = getClaudeProjectsDir()
        if (!claudeDir.exists()) return emptyList()

        return claudeDir.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { dir -> dir.listFiles()?.filter { it.name.endsWith(".jsonl") } ?: emptyList() }
            ?: emptyList()
    }
}

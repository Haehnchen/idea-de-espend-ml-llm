package de.espend.ml.llm

import java.io.File

/**
 * Utilities for finding command paths in the system.
 */
object CommandPathUtils {
    private val isWindows: Boolean = System.getProperty("os.name")?.startsWith("Windows", ignoreCase = true) == true

    private fun windowsExecutableExtensions(): List<String> {
        if (!isWindows) {
            return emptyList()
        }

        return (System.getenv("PATHEXT") ?: ".COM;.EXE;.BAT;.CMD")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun findExecutableInDirectory(dir: String, command: String): String? {
        val normalizedDir = dir.trim().trim('"')
        if (normalizedDir.isEmpty()) {
            return null
        }

        File(normalizedDir, command).takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        if (!isWindows) {
            return null
        }

        val commandHasExtension = command.contains('.')
        for (extension in windowsExecutableExtensions()) {
            val candidateName = if (commandHasExtension) command else "$command$extension"
            File(normalizedDir, candidateName).takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }
        }

        return null
    }

    /**
     * Finds the absolute path of a command in PATH.
     * Returns null if not found.
     */
    fun findCommandPath(command: String): String? {
        val pathEnv = System.getenv("PATH") ?: return null
        val pathSeparator = System.getProperty("path.separator", ":")

        val directories = pathEnv.split(pathSeparator)
        for (dir in directories) {
            findExecutableInDirectory(dir, command)?.let { return it }
        }

        return null
    }
}

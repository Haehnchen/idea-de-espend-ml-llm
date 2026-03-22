package de.espend.ml.llm

import java.io.File

/**
 * Utilities for finding command paths in the system.
 */
object CommandPathUtils {

    /**
     * Finds the absolute path of a command in PATH.
     * Returns null if not found.
     */
    fun findCommandPath(command: String): String? {
        val pathEnv = System.getenv("PATH") ?: return null
        val pathSeparator = System.getProperty("path.separator", ":")

        val directories = pathEnv.split(pathSeparator)
        for (dir in directories) {
            val cmdFile = File(dir, command)
            if (cmdFile.exists() && cmdFile.canExecute()) {
                return cmdFile.absolutePath
            }
        }

        return null
    }

    /**
     * Finds the opencode command with specific fallback paths.
     * Order: PATH -> $HOME/bin -> $HOME/.opencode/bin
     * Returns null if not found.
     */
    fun findOpenCodePath(): String? {
        findCommandPath("opencode")?.let { return it }

        val userHome = System.getProperty("user.home") ?: return null

        // Check $HOME/bin
        File(userHome, "bin/opencode").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        // Check $HOME/.opencode/bin
        return File(userHome, ".opencode/bin/opencode")
            .takeIf { it.exists() && it.canExecute() }
            ?.absolutePath
    }

    /**
     * Finds the gemini command with specific fallback paths.
     * Order: PATH -> $HOME/bin -> $HOME/.gemini/bin
     * Returns null if not found.
     */
    fun findGeminiPath(): String? {
        findCommandPath("gemini")?.let { return it }

        val userHome = System.getProperty("user.home") ?: return null

        // Check $HOME/bin
        File(userHome, "bin/gemini").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        // Check $HOME/.gemini/bin
        return File(userHome, ".gemini/bin/gemini")
            .takeIf { it.exists() && it.canExecute() }
            ?.absolutePath
    }

    /**
     * Finds the claude-agent-acp command with specific fallback paths.
     * Order: PATH -> /usr/bin -> $HOME/bin -> $HOME/.local/bin
     * Returns null if not found.
     */
    fun findClaudeAgentAcpPath(): String? {
        findCommandPath("claude-agent-acp")?.let { return it }

        // Check /usr/bin
        File("/usr/bin/claude-agent-acp").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        val userHome = System.getProperty("user.home") ?: return null

        // Check $HOME/bin
        File(userHome, "bin/claude-agent-acp").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        // Check $HOME/.local/bin
        return File(userHome, ".local/bin/claude-agent-acp")
            .takeIf { it.exists() && it.canExecute() }
            ?.absolutePath
    }

    /**
     * Finds the Cursor agent command with specific fallback paths.
     * Cursor now has built-in ACP support via `agent acp`.
     * Order: PATH -> /usr/bin -> $HOME/bin -> $HOME/.local/bin -> $HOME/.cursor/bin
     * Returns null if not found.
     */
    fun findCursorAgentPath(): String? {
        findCommandPath("agent")?.let { return it }

        // Check /usr/bin
        File("/usr/bin/agent").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        val userHome = System.getProperty("user.home") ?: return null

        // Check $HOME/bin
        File(userHome, "bin/agent").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        // Check $HOME/.local/bin
        File(userHome, ".local/bin/agent").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        // Check $HOME/.cursor/bin (Cursor's default install location)
        return File(userHome, ".cursor/bin/agent")
            .takeIf { it.exists() && it.canExecute() }
            ?.absolutePath
    }

    /**
     * Finds the kilo command (Kilo Code CLI) with specific fallback paths.
     * Order: PATH -> /usr/bin -> $HOME/bin -> $HOME/.local/bin
     * Returns null if not found.
     */
    fun findKiloPath(): String? {
        findCommandPath("kilo")?.let { return it }

        // Check /usr/bin
        File("/usr/bin/kilo").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        val userHome = System.getProperty("user.home") ?: return null

        // Check $HOME/bin
        File(userHome, "bin/kilo").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        // Check $HOME/.local/bin
        return File(userHome, ".local/bin/kilo")
            .takeIf { it.exists() && it.canExecute() }
            ?.absolutePath
    }

    /**
     * Finds the claude command with specific fallback paths.
     * Order: PATH -> $HOME/bin -> $HOME/.local/bin
     * Returns null if not found (user can manually specify).
     */
    fun findClaudePath(): String? {
        findCommandPath("claude")?.let { return it }

        val userHome = System.getProperty("user.home") ?: return null

        // Check $HOME/bin
        File(userHome, "bin/claude").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        // Check $HOME/.local/bin
        return File(userHome, ".local/bin/claude")
            .takeIf { it.exists() && it.canExecute() }
            ?.absolutePath
    }

    /**
     * Finds the droid command (Factory.ai CLI) with specific fallback paths.
     * Order: PATH -> /usr/bin -> $HOME/bin -> $HOME/.local/bin
     * Returns null if not found.
     */
    fun findDroidPath(): String? {
        findCommandPath("droid")?.let { return it }

        // Check /usr/bin
        File("/usr/bin/droid").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        val userHome = System.getProperty("user.home") ?: return null

        // Check $HOME/bin
        File(userHome, "bin/droid").takeIf { it.exists() && it.canExecute() }?.let { return it.absolutePath }

        // Check $HOME/.local/bin
        return File(userHome, ".local/bin/droid")
            .takeIf { it.exists() && it.canExecute() }
            ?.absolutePath
    }
}

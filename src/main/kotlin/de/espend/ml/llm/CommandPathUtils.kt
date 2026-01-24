package de.espend.ml.llm

import java.io.File

/**
 * Utilities for finding command paths in the system.
 */
object CommandPathUtils {

    /**
     * Finds the absolute path of a command in PATH.
     * Searches all directories in PATH for the command.
     */
    fun findCommandPath(command: String): String {
        val pathEnv = System.getenv("PATH") ?: return command
        val pathSeparator = System.getProperty("path.separator", ":")

        val directories = pathEnv.split(pathSeparator)
        for (dir in directories) {
            val cmdFile = File(dir, command)
            if (cmdFile.exists() && cmdFile.canExecute()) {
                return cmdFile.absolutePath
            }
        }

        // Fallback to command name
        return command
    }

    /**
     * Finds the opencode command with specific fallback paths.
     * Order: PATH -> $HOME/bin -> $HOME/.opencode/bin -> "opencode"
     */
    fun findOpenCodePath(): String {
        // First try PATH
        val pathResult = findCommandPath("opencode")
        if (pathResult != "opencode") {
            return pathResult
        }

        val userHome = System.getProperty("user.home")
        if (userHome != null) {
            // Check $HOME/bin
            val homeBin = File(userHome, "bin")
            val opencodeInHomeBin = File(homeBin, "opencode")
            if (opencodeInHomeBin.exists() && opencodeInHomeBin.canExecute()) {
                return opencodeInHomeBin.absolutePath
            }

            // Check $HOME/.opencode/bin (default fallback)
            val opencodeBin = File(userHome, ".opencode/bin")
            val opencodeInOpencodeBin = File(opencodeBin, "opencode")
            if (opencodeInOpencodeBin.exists() && opencodeInOpencodeBin.canExecute()) {
                return opencodeInOpencodeBin.absolutePath
            }
        }

        // Final fallback to command name
        return "opencode"
    }

    /**
     * Finds the gemini command with specific fallback paths.
     * Order: PATH -> $HOME/bin -> $HOME/.gemini/bin -> "gemini"
     */
    fun findGeminiPath(): String {
        // First try PATH
        val pathResult = findCommandPath("gemini")
        if (pathResult != "gemini") {
            return pathResult
        }

        val userHome = System.getProperty("user.home")
        if (userHome != null) {
            // Check $HOME/bin
            val homeBin = File(userHome, "bin")
            val geminiInHomeBin = File(homeBin, "gemini")
            if (geminiInHomeBin.exists() && geminiInHomeBin.canExecute()) {
                return geminiInHomeBin.absolutePath
            }

            // Check $HOME/.gemini/bin (default fallback)
            val geminiBin = File(userHome, ".gemini/bin")
            val geminiInGeminiBin = File(geminiBin, "gemini")
            if (geminiInGeminiBin.exists() && geminiInGeminiBin.canExecute()) {
                return geminiInGeminiBin.absolutePath
            }
        }

        // Final fallback to command name
        return "gemini"
    }

    /**
     * Finds the claude-code-acp command with specific fallback paths.
     * Order: PATH -> /usr/bin -> $HOME/bin -> $HOME/.local/bin -> "claude-code-acp"
     */
    fun findClaudeCodeAcpPath(): String {
        // First try PATH
        val pathResult = findCommandPath("claude-code-acp")
        if (pathResult != "claude-code-acp") {
            return pathResult
        }

        // Check /usr/bin (standard path)
        val usrBinClaude = File("/usr/bin/claude-code-acp")
        if (usrBinClaude.exists() && usrBinClaude.canExecute()) {
            return usrBinClaude.absolutePath
        }

        val userHome = System.getProperty("user.home")
        if (userHome != null) {
            // Check $HOME/bin
            val homeBin = File(userHome, "bin")
            val claudeInHomeBin = File(homeBin, "claude-code-acp")
            if (claudeInHomeBin.exists() && claudeInHomeBin.canExecute()) {
                return claudeInHomeBin.absolutePath
            }

            // Check $HOME/.local/bin
            val localBin = File(userHome, ".local/bin")
            val claudeInLocalBin = File(localBin, "claude-code-acp")
            if (claudeInLocalBin.exists() && claudeInLocalBin.canExecute()) {
                return claudeInLocalBin.absolutePath
            }
        }

        // Final fallback to command name
        return "claude-code-acp"
    }

    /**
     * Finds the claude command with specific fallback paths.
     * Order: PATH -> /usr/bin -> $HOME/bin -> $HOME/.local/bin -> "claude"
     */
    fun findClaudePath(): String? {
        // First try PATH
        val pathResult = findCommandPath("claude")
        if (pathResult != "claude") {
            return pathResult
        }

        val userHome = System.getProperty("user.home")
        if (userHome != null) {
            // Check $HOME/bin
            val homeBin = File(userHome, "bin")
            val claudeInHomeBin = File(homeBin, "claude")
            if (claudeInHomeBin.exists() && claudeInHomeBin.canExecute()) {
                return claudeInHomeBin.absolutePath
            }

            // Check $HOME/.local/bin
            val localBin = File(userHome, ".local/bin")
            val claudeInLocalBin = File(localBin, "claude")
            if (claudeInLocalBin.exists() && claudeInLocalBin.canExecute()) {
                return claudeInLocalBin.absolutePath
            }
        }

        // Return null if not found (user can manually specify)
        return null
    }
}

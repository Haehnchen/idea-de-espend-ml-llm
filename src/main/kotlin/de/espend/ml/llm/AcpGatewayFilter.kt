package de.espend.ml.llm

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.Files

private val LOG = Logger.getInstance("de.espend.ml.llm.AcpGatewayFilter")

/**
 * Wraps claude-agent-acp with a Node.js gateway auth filter.
 *
 * Problem: IntelliJ 253+ sends `auth._meta.gateway=true` (ml-llm-253.32098.66) to ALL ACP agents.
 * claude-agent-acp responds by advertising a "gateway" auth method. When users
 * have JetBrains AI + Anthropic access or BYOK Anthropic key configured in JB
 * settings, IntelliJ auto-authenticates via gateway, which calls createEnvForGateway()
 * inside the Node.js process. This overrides ANTHROPIC_BASE_URL and ANTHROPIC_AUTH_TOKEN
 * with JetBrains' own proxy settings - while ANTHROPIC_DEFAULT_*_MODEL passes through
 * (hence "only the model arrives" but custom provider URL is lost).
 *
 * Fix: A Node.js NDJSON proxy that strips the gateway auth method from the
 * initialize response before IntelliJ sees it. Without the gateway method,
 * IntelliJ never calls authenticate("gateway", ...) and our env vars remain intact.
 *
 * Node.js is used because it is guaranteed to be available wherever claude-agent-acp
 * runs (claude-agent-acp is itself a Node.js program). No external dependencies.
 */
object AcpGatewayFilter {

    private var cachedScriptPath: String? = null

    /**
     * Returns (command, args) for running claude-agent-acp wrapped with the gateway filter.
     * Falls back to (acpCommand, emptyList()) if Node.js is unavailable.
     */
    fun wrapCommand(acpCommand: String): Pair<String, List<String>> {
        val node = findNode() ?: run {
            LOG.warn("Node.js not found - gateway auth filter disabled. Custom provider base URLs may be overridden by JetBrains AI gateway auth.")
            return Pair(acpCommand, emptyList())
        }

        val scriptPath = getOrExtractScript() ?: run {
            LOG.warn("Failed to extract ACP gateway filter script - gateway auth filter disabled.")
            return Pair(acpCommand, emptyList())
        }

        LOG.info("Using ACP gateway filter: $node $scriptPath $acpCommand")
        return Pair(node, listOf(scriptPath, acpCommand))
    }

    private fun findNode(): String? {
        // 1. PATH
        CommandPathUtils.findCommandPath("node")?.let { return it }

        val userHome = System.getProperty("user.home")

        // 2. Env vars set by version managers (nvm, volta, fnm) — often missing from IntelliJ's PATH
        val candidates = buildList {
            System.getenv("NVM_BIN")?.let { add("$it/node") }
            System.getenv("VOLTA_HOME")?.let { add("$it/bin/node") }
            System.getenv("FNM_MULTISHELL_PATH")?.let { add("$it/node") }
            System.getenv("NODE_HOME")?.let { add("$it/bin/node") }

            // 3. Common fixed locations
            add("/usr/bin/node")
            add("/usr/local/bin/node")
            if (userHome != null) {
                add("$userHome/.nvm/versions/node/current/bin/node")
                add("$userHome/.local/bin/node")
                add("$userHome/bin/node")
            }
        }
        return candidates.firstOrNull { File(it).canExecute() }
    }

    private fun getOrExtractScript(): String? {
        cachedScriptPath?.let { path ->
            if (File(path).exists()) return path
        }

        val scriptContent = javaClass.getResourceAsStream("/scripts/acp_gateway_filter.js")
            ?.bufferedReader()
            ?.readText()
            ?: run {
                LOG.error("Could not load acp_gateway_filter.js from plugin resources")
                return null
            }

        return try {
            val tempDir = Files.createTempDirectory("idea-ml-llm-acp").toFile()
            tempDir.deleteOnExit()
            val scriptFile = File(tempDir, "acp_gateway_filter.js")
            scriptFile.writeText(scriptContent)
            scriptFile.deleteOnExit()
            scriptFile.absolutePath.also {
                cachedScriptPath = it
                LOG.info("Extracted ACP gateway filter script to: $it")
            }
        } catch (e: Exception) {
            LOG.error("Failed to extract ACP gateway filter script", e)
            null
        }
    }
}

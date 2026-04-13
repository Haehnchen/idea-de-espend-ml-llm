package de.espend.ml.llm

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

object PiAcpTempConfig {
    private const val DEFAULT_AUTH_ENV_NAME = "API_KEY"
    private const val DEFAULT_PROVIDER_NAME = "jetbrains-pi-acp"
    private const val DEFAULT_FORMAT = "anthropic-messages"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun createTempAgentDir(config: AgentConfig): Path {
        val format = config.format.trim().ifEmpty { DEFAULT_FORMAT }
        val baseUrl = config.baseUrl.trim()
        val modelId = config.model.trim()

        require(baseUrl.isNotEmpty()) { "pi-acp provider requires a Base URL" }
        require(modelId.isNotEmpty()) { "pi-acp provider requires a Model" }

        val agentDir = Files.createTempDirectory("pi-acp-agent-")
        val modelsPath = agentDir.resolve("models.json")
        val settingsPath = agentDir.resolve("settings.json")
        val authPath = agentDir.resolve("auth.json")

        val providerName = sanitizeProviderName(config.id.ifBlank { DEFAULT_PROVIDER_NAME })
        val sessionDir = Path.of(System.getProperty("user.home"), ".pi", "agent", "sessions").toString()

        modelsPath.writeText(
            gson.toJson(
                mapOf(
                    "providers" to mapOf(
                        providerName to mapOf(
                            "baseUrl" to baseUrl,
                            "api" to format,
                            "apiKey" to DEFAULT_AUTH_ENV_NAME,
                            "authHeader" to true,
                            "models" to listOf(
                                mapOf(
                                    "id" to modelId,
                                    "name" to modelId,
                                    "reasoning" to false,
                                    "input" to listOf("text", "image"),
                                    "contextWindow" to 200000,
                                    "maxTokens" to 16384
                                )
                            )
                        )
                    )
                )
            ) + "\n"
        )

        settingsPath.writeText(
            gson.toJson(
                mapOf(
                    "defaultProvider" to providerName,
                    "defaultModel" to modelId,
                    "defaultThinkingLevel" to "off",
                    "sessionDir" to sessionDir,
                    "quietStartup" to false
                )
            ) + "\n"
        )

        authPath.writeText("{}\n")

        agentDir.toFile().deleteOnExit()
        modelsPath.toFile().deleteOnExit()
        settingsPath.toFile().deleteOnExit()
        authPath.toFile().deleteOnExit()

        return agentDir
    }

    private fun sanitizeProviderName(value: String): String {
        val sanitized = value
            .lowercase()
            .replace(Regex("[^a-z0-9-_]+"), "-")
            .trim('-')

        return sanitized.ifEmpty { DEFAULT_PROVIDER_NAME }
    }
}

package de.espend.ml.llm.profile

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

object AiProfilePiAcpTempConfig {
    private const val DEFAULT_AUTH_ENV_NAME = "API_KEY"
    private const val DEFAULT_PROVIDER_NAME = "jetbrains-ai-profile"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun createTempAgentDir(
        profileId: String,
        format: String,
        baseUrl: String,
        modelIds: List<String>
    ): Path {
        require(baseUrl.isNotBlank()) { "PI profile requires a Base URL" }
        require(modelIds.isNotEmpty()) { "PI profile requires at least one Model" }

        val normalizedModelIds = modelIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        require(normalizedModelIds.isNotEmpty()) { "PI profile requires at least one Model" }
        val defaultModelId = normalizedModelIds.first()

        val agentDir = Files.createTempDirectory("pi-acp-ai-profile-")
        val modelsPath = agentDir.resolve("models.json")
        val settingsPath = agentDir.resolve("settings.json")
        val authPath = agentDir.resolve("auth.json")

        val providerName = sanitizeProviderName(profileId)
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
                            "models" to normalizedModelIds.map { modelId ->
                                mapOf(
                                    "id" to modelId,
                                    "name" to modelId,
                                    "reasoning" to false,
                                    "input" to listOf("text", "image"),
                                    "contextWindow" to 200000,
                                    "maxTokens" to 16384
                                )
                            }
                        )
                    )
                )
            ) + "\n"
        )

        settingsPath.writeText(
            gson.toJson(
                mapOf(
                    "defaultProvider" to providerName,
                    "defaultModel" to defaultModelId,
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

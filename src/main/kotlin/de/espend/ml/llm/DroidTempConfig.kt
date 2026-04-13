package de.espend.ml.llm

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

object DroidTempConfig {
    private const val DEFAULT_AUTH_ENV_NAME = "FACTORY_CUSTOM_API_KEY"
    private const val DEFAULT_PROVIDER_NAME = "jetbrains-droid-custom"
    private const val DEFAULT_PROVIDER_TYPE = "anthropic"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun createTempHome(config: AgentConfig): Path {
        val providerType = normalizeProviderType(config.format)
        val baseUrl = config.baseUrl.trim()
        val modelId = config.model.trim()

        require(baseUrl.isNotEmpty()) { "droid provider requires a Base URL" }
        require(modelId.isNotEmpty()) { "droid provider requires a Model" }
        require(config.apiKey.trim().isNotEmpty()) { "droid provider requires an API Key" }

        val tempHome = Files.createTempDirectory("droid-home-")
        val factoryDir = tempHome.resolve(".factory").createDirectories()
        val settingsPath = factoryDir.resolve("settings.json")
        val displayName = sanitizeDisplayName(config.id.ifBlank { DEFAULT_PROVIDER_NAME })
        val customModelId = customModelId(config)

        settingsPath.writeText(
            gson.toJson(
                mapOf(
                    "sessionDefaultSettings" to mapOf(
                        "model" to customModelId
                    ),
                    "modelPolicy" to mapOf(
                        "allowCustomModels" to true,
                        "allowAllFactoryModels" to false
                    ),
                    "customModels" to listOf(
                        mapOf(
                            "id" to customModelId,
                            "index" to 0,
                            "model" to modelId,
                            "displayName" to displayName,
                            "baseUrl" to baseUrl,
                            "apiKey" to "\${$DEFAULT_AUTH_ENV_NAME}",
                            "provider" to providerType,
                            "maxOutputTokens" to 16384,
                            "noImageSupport" to false
                        )
                    )
                )
            ) + "\n"
        )

        tempHome.toFile().deleteOnExit()
        factoryDir.toFile().deleteOnExit()
        settingsPath.toFile().deleteOnExit()

        return tempHome
    }

    fun modelSelector(config: AgentConfig): String {
        return customModelId(config)
    }

    fun customModelId(config: AgentConfig): String {
        val baseName = sanitizeIdComponent(config.id.ifBlank { DEFAULT_PROVIDER_NAME })
        return "custom:$baseName"
    }

    private fun normalizeProviderType(value: String): String {
        val normalized = value.trim().lowercase()
        return when (normalized) {
            "anthropic", "openai", "generic-chat-completion-api" -> normalized
            else -> DEFAULT_PROVIDER_TYPE
        }
    }

    private fun sanitizeDisplayName(value: String): String {
        val sanitized = value
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return sanitized.ifEmpty { DEFAULT_PROVIDER_NAME }
    }

    private fun sanitizeIdComponent(value: String): String {
        val slug = value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

        return slug.ifEmpty { DEFAULT_PROVIDER_NAME }
    }
}

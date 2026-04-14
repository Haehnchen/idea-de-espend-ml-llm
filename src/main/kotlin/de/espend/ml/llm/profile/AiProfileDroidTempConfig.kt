package de.espend.ml.llm.profile

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

object AiProfileDroidTempConfig {
    private const val DEFAULT_AUTH_ENV_NAME = "FACTORY_CUSTOM_API_KEY"
    private const val DEFAULT_PROVIDER_NAME = "jetbrains-droid-custom"
    private const val SOUND_OFF = "off"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun createTempHome(
        profileId: String,
        providerType: String,
        baseUrl: String,
        modelIds: List<String>
    ): Path {
        require(baseUrl.isNotBlank()) { "Droid profile requires a Base URL" }
        require(modelIds.isNotEmpty()) { "Droid profile requires at least one Model" }

        val normalizedModelIds = modelIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        require(normalizedModelIds.isNotEmpty()) { "Droid profile requires at least one Model" }

        val tempHome = Files.createTempDirectory("droid-home-")
        val factoryDir = tempHome.resolve(".factory").createDirectories()
        val settingsPath = factoryDir.resolve("settings.json")
        val defaultCustomModelId = customModelId(profileId, 0)

        settingsPath.writeText(
            gson.toJson(
                mapOf(
                    "sessionDefaultSettings" to mapOf(
                        "model" to defaultCustomModelId
                    ),
                    "completionSound" to SOUND_OFF,
                    "awaitingInputSound" to SOUND_OFF,
                    "modelPolicy" to mapOf(
                        "allowCustomModels" to true,
                        "allowAllFactoryModels" to false
                    ),
                    "customModels" to normalizedModelIds.mapIndexed { index, modelId ->
                        mapOf(
                            "id" to customModelId(profileId, index),
                            "index" to index,
                            "model" to modelId,
                            "displayName" to sanitizeDisplayName(modelId),
                            "baseUrl" to baseUrl,
                            "apiKey" to "\${$DEFAULT_AUTH_ENV_NAME}",
                            "provider" to providerType,
                            "maxOutputTokens" to 16384,
                            "noImageSupport" to false
                        )
                    }
                )
            ) + "\n"
        )

        tempHome.toFile().deleteOnExit()
        factoryDir.toFile().deleteOnExit()
        settingsPath.toFile().deleteOnExit()

        return tempHome
    }

    fun customModelId(profileId: String, index: Int): String {
        return "custom:${sanitizeIdComponent(profileId)}-$index"
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

package de.espend.ml.llm

import com.intellij.ml.llm.core.providers.LlmCustomModelsSettingsManager
import com.intellij.ml.llm.core.providers.ThirdPartyLLMProfileId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Startup activity to automatically configure the Z.AI provider model
 * as the default for AI features (including commit message generation).
 */
class LLMModelInitializer : StartupActivity {
    companion object {
        private val LOG = Logger.getInstance(LLMModelInitializer::class.java)
    }

    override fun runActivity(project: Project) {
        try {
            @Suppress("UNCHECKED_CAST")
            val settingsManager = ApplicationManager.getApplication()
                .getService(LlmCustomModelsSettingsManager::class.java) ?: return

            // Only set if no custom model is already configured
            val smartModelId = settingsManager.smartModelId
            LOG.info("=== LLMModelInitializer ===")
            LOG.info("Current smart model ID: $smartModelId")
            if (smartModelId == null) {
                // Set Z.AI glm-4.7 as the default smart model
                val profileId = ThirdPartyLLMProfileId(
                    "ZaiAnthropicProvider",
                    "glm-4.7"
                )
                settingsManager.smartModelId = profileId
                LOG.info("Set smart model ID to: $profileId")
            }
        } catch (e: Exception) {
            // Silently fail - don't break startup if this fails
            LOG.warn("Failed to set smart model ID", e)
        }
    }
}

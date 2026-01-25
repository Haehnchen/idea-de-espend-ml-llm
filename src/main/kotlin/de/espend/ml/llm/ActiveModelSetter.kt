package de.espend.ml.llm

import com.intellij.ml.llm.core.providers.LlmCustomModelsSettingsManager
import com.intellij.ml.llm.core.providers.ThirdPartyAIProvidersSettingsManager
import com.intellij.ml.llm.core.providers.ThirdPartyLLMProfileId
import com.intellij.openapi.application.ApplicationManager

/**
 * Utility class for setting the active model in AI Assistant settings.
 * 
 * This sets the model IDs for smart, quick, and editor contexts,
 * and enables the "Agent" provider in the Providers dropdown.
 */
object ActiveModelSetter {

    /**
     * Sets the specified model as the active model for AI Assistant.
     * 
     * @param provider The provider ID (e.g., "zai", "openrouter")
     * @param model The model name (e.g., "glm-4.7")
     * @throws IllegalArgumentException if model is empty
     */
    fun setActiveModel(provider: String, model: String) {
        require(model.isNotEmpty()) { "Model name cannot be empty" }

        val profileId = ThirdPartyLLMProfileId("Agent", "$provider:$model")
        
        // Set the model IDs for all contexts
        val settingsManager = LlmCustomModelsSettingsManager.getInstance()
        settingsManager.smartModelId = profileId
        settingsManager.quickModelId = profileId
        settingsManager.editorModelId = profileId

        // Enable the "Agent" provider in the Providers dropdown
        val providersManager = ApplicationManager.getApplication()
            .getService(ThirdPartyAIProvidersSettingsManager::class.java)
        providersManager?.enableOnlyOneProvider("Agent")
    }

    /**
     * Resolves the model to use based on input field and provider defaults.
     * 
     * @param inputModel The model from the input field (may be empty)
     * @param providerInfo The provider info containing default models
     * @return The resolved model name, or null if no model available
     */
    fun resolveModel(inputModel: String?, providerInfo: ProviderConfig.ProviderInfo?): String? {
        val trimmedInput = inputModel?.trim() ?: ""
        if (trimmedInput.isNotEmpty()) {
            return trimmedInput
        }
        return providerInfo?.models?.first?.takeIf { it.isNotEmpty() }
    }
}

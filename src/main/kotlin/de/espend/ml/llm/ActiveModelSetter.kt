@file:Suppress("UnstableApiUsage")

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
     * Uses the provider's modelIds Triple for smart, quick, and editor contexts.
     * 
     * Priority for model selection:
     * 1. Custom model (if provided and non-empty)
     * 2. Provider's modelIds Triple values (smart, quick, editor)
     * 3. First item from provider's models Triple (fallback)
     * 
     * @param providerInfo The provider info containing default model IDs
     * @param customModel Optional custom model name (overrides defaults if non-empty)
     * @throws IllegalArgumentException if no model can be resolved
     */
    fun setActiveModel(providerInfo: ProviderConfig.ProviderInfo, customModel: String? = null) {
        val provider = providerInfo.provider
        val modelIds = providerInfo.modelIds
        val models = providerInfo.models
        val trimmedCustom = customModel?.trim() ?: ""
        
        // Resolve models for each context
        // Priority: custom model > provider modelIds value > models.first fallback
        val smartModel = resolveModelForContext(trimmedCustom, modelIds.smart, models.first)
        val quickModel = resolveModelForContext(trimmedCustom, modelIds.quick, models.first)
        
        // Set the model IDs for all contexts (null if empty)
        val settingsManager = LlmCustomModelsSettingsManager.getInstance()
        settingsManager.smartModelId = if (smartModel.isNotEmpty()) ThirdPartyLLMProfileId("Agent", "$provider:$smartModel") else null
        settingsManager.quickModelId = if (quickModel.isNotEmpty()) ThirdPartyLLMProfileId("Agent", "$provider:$quickModel") else null

        // needs to be fast
        settingsManager.editorModelId = ThirdPartyLLMProfileId("JetBrains", "full-line-code-completion")

        // Enable the "Agent" provider in the Providers dropdown
        val providersManager = ApplicationManager.getApplication()
            .getService(ThirdPartyAIProvidersSettingsManager::class.java)
        providersManager?.enableOnlyOneProvider("Agent")
    }
    
    /**
     * Resolves the model for a specific context.
     * 
     * @param customModel Custom model from user input
     * @param modelIdValue Value from provider's modelIds (smart, quick, or editor)
     * @param modelsFirstValue First value from provider's models Triple (fallback)
     * @return The resolved model name
     */
    private fun resolveModelForContext(
        customModel: String,
        modelIdValue: String,
        modelsFirstValue: String
    ): String {
        // Priority 1: Custom model if provided
        if (customModel.isNotEmpty()) {
            return customModel
        }
        // Priority 2: modelIds Triple value
        if (modelIdValue.isNotEmpty()) {
            return modelIdValue
        }
        // Priority 3: First item from models Triple
        return modelsFirstValue
    }

    /**
     * Checks if a model can be resolved based on input field and provider defaults.
     * 
     * @param inputModel The model from the input field (may be empty)
     * @param providerInfo The provider info containing default model IDs
     * @return true if a model is available, false otherwise
     */
    fun hasModel(inputModel: String?, providerInfo: ProviderConfig.ProviderInfo?): Boolean {
        val trimmedInput = inputModel?.trim() ?: ""
        if (trimmedInput.isNotEmpty()) {
            return true
        }
        // Try modelIds.smart, then fall back to models.first
        return providerInfo?.modelIds?.smart?.isNotEmpty() == true
            || providerInfo?.models?.first?.isNotEmpty() == true
    }
}

@file:Suppress("UnstableApiUsage")

package de.espend.ml.llm.chat

import com.intellij.ml.llm.core.providers.AiaLlmProviderType
import com.intellij.ml.llm.core.providers.AiaThirdPartyProfileInfo
import com.intellij.ml.llm.core.providers.ThirdPartyLLMProfileId
import de.espend.ml.llm.ProviderConfig

/**
 * Profile info for Anthropic-compatible models.
 *
 * Uses OpenAIAPI provider type to bypass BYOK filtering.
 *
 * Profile ID format: "providerId:model" (e.g., "zai:glm-4.7")
 * Display format: provided via displayName parameter (e.g., "glm-4.7 (Z.AI)")
 */
class AgentProvidersLlmProfileInfo(
    profileId: ThirdPartyLLMProfileId,
    private val actualProviderId: String,
    private val actualModelName: String,
    private val displayName: String
) : AiaThirdPartyProfileInfo(profileId, AiaLlmProviderType.OpenAIAPI) {

    /**
     * Model name displayed in dropdown
     */
    override val modelName: String
        get() = displayName

    /**
     * The actual provider ID (e.g., "zai")
     */
    val providerId: String
        get() = actualProviderId
}

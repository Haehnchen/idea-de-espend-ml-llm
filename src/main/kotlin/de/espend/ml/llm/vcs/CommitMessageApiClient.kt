package de.espend.ml.llm.vcs

import de.espend.ml.llm.profile.AiProfileApiType
import de.espend.ml.llm.profile.AiProfileConfig
import de.espend.ml.llm.profile.AiProfilePlatformRegistry

object CommitMessageApiClient {

    suspend fun sendRequest(
        config: AiProfileConfig,
        prompt: String
    ): ApiResult {
        return when (AiProfilePlatformRegistry.findApiType(config.effectiveApiType())) {
            AiProfileApiType.OPENAI -> OpenAiApiClient.sendRequest(config, prompt)
            AiProfileApiType.ANTHROPIC, null -> AnthropicApiClient.sendRequest(config, prompt)
        }
    }
}

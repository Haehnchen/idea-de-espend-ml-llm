package de.espend.ml.llm.vcs

import de.espend.ml.llm.profile.AiProfileConfig

internal object CommitMessageAuthHeaders {
    fun bearer(config: AiProfileConfig): Map<String, String> {
        val apiKey = config.apiKey.trim()
        if (apiKey.isEmpty()) {
            return emptyMap()
        }

        return mapOf("Authorization" to "Bearer $apiKey")
    }
}

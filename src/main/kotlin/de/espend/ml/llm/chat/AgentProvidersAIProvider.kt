package de.espend.ml.llm.chat

import ai.grazie.utils.attributes.Attributes
import com.intellij.ml.llm.core.httpClient.ktor.AiaKtorHttpClientUtil
import com.intellij.ml.llm.core.models.api.AiaLlmChatMessage
import com.intellij.ml.llm.core.models.api.AiaLlmStreamData
import com.intellij.ml.llm.core.providers.AiaThirdPartyLlmStreamData
import com.intellij.ml.llm.core.providers.ThirdPartyAIProvider
import com.intellij.ml.llm.core.providers.ThirdPartyAISettingsManager
import com.intellij.ml.llm.core.providers.ThirdPartyLLMProfileId
import com.intellij.ml.llm.smartChat.endpoints.LlmFunctionDescriptor
import com.intellij.openapi.application.ApplicationManager
import de.espend.ml.llm.AgentRegistry
import de.espend.ml.llm.ProviderConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/**
 * Third-party AI provider for Anthropic-compatible APIs.
 *
 * Dynamically lists models from all enabled providers configured in Agent Registry.
 *
 * Profile ID format: "providerId:modelName" (e.g., "zai:glm-4.7")
 */
class AgentProvidersAIProvider : ThirdPartyAIProvider {

    override val providerId: String = "Agent"
    override val name: String = "Agent Providers"
    override val isModelsSelectedExplicitly: Boolean = true

    private val httpClient: HttpClient = AiaKtorHttpClientUtil.httpClient()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private const val ANTHROPIC_VERSION = "2023-06-01"

        fun getProviderCredentials(providerId: String): Pair<String, String> {
            val registry = AgentRegistry.getInstance()
            val config = registry.agentConfigs.firstOrNull { it.provider == providerId && it.isEnabled }

            if (config != null) {
                val providerInfo = ProviderConfig.findProviderInfo(providerId)
                val baseUrl = if (providerInfo?.baseUrl != null) {
                    providerInfo.baseUrl.removeSuffix("/")
                } else {
                    config.baseUrl
                }
                return config.apiKey to baseUrl
            }

            return "" to ""
        }
    }

    override suspend fun testConnection(): ThirdPartyAIProvider.ConnectionError? {
        // Not implemented
        return null
    }

    override suspend fun getAvailableLLMs(): List<AgentProvidersLlmProfileInfo> {
        val registry = AgentRegistry.getInstance()
        val profiles = mutableListOf<AgentProvidersLlmProfileInfo>()

        for (config in registry.agentConfigs.filter { it.isEnabled && it.apiKey.isNotEmpty() }) {
            val providerInfo = ProviderConfig.findProviderInfo(config.provider)
            if (providerInfo == null) continue

            val models = mutableListOf<String>()

            // Custom model always goes first if configured
            if (config.model.isNotEmpty()) {
                models.add(config.model)
            }

            // Then add predefined models (without duplicates)
            val predefinedModels = listOfNotNull(
                providerInfo.models.first,
                providerInfo.models.second,
                providerInfo.models.third
            ).distinct()
            predefinedModels.forEach { model ->
                if (model !in models) {
                    models.add(model)
                }
            }

            for (model in models) {
                if (model.isEmpty()) continue
                val providerLabel = providerInfo.label
                val displayName = "$model ($providerLabel)"
                val profileIdString = "${config.provider}:$model"
                val profileId = ThirdPartyLLMProfileId(providerId, profileIdString)
                profiles.add(AgentProvidersLlmProfileInfo(profileId, config.provider, model, displayName))
            }
        }

        return profiles
    }

    override suspend fun streamingChatCompletion(
        profileId: ThirdPartyLLMProfileId,
        history: List<AiaLlmChatMessage>,
        functions: List<LlmFunctionDescriptor>,
        attributes: Attributes
    ): Flow<AiaLlmStreamData> {
        val messages = history.map { msg ->
            buildJsonObject {
                put("role", when {
                    msg.toString().contains("user", ignoreCase = true) -> "user"
                    msg.toString().contains("assistant", ignoreCase = true) -> "assistant"
                    else -> "user"
                })
                put("content", extractContent(msg))
            }
        }
        return sendRequest(profileId, messages)
    }

    private fun extractContent(message: AiaLlmChatMessage): String {
        return message.toString()
    }

    override suspend fun streamingCompletion(
        profileId: ThirdPartyLLMProfileId,
        prompt: String,
        suffix: String?,
        stopTokens: List<String>?
    ): Flow<AiaLlmStreamData> {
        val messages = listOf(buildJsonObject {
            put("role", "user")
            put("content", prompt)
        })
        return sendRequest(profileId, messages)
    }

    private fun sendRequest(profileId: ThirdPartyLLMProfileId, messages: List<JsonObject>): Flow<AiaLlmStreamData> = flow {
        // Format: "providerId:model" (e.g., "zai:glm-4.7")
        val parts = profileId.profileId.split(":", limit = 2)
        val actualProviderId = if (parts.size == 2) parts[0] else profileId.providerId
        val modelId = if (parts.size == 2) parts[1] else profileId.profileId

        val (apiKey, baseUrl) = getProviderCredentials(actualProviderId)
        if (apiKey.isEmpty()) {
            emit(AiaThirdPartyLlmStreamData.Text("Error: No API key configured for provider"))
            emit(AiaThirdPartyLlmStreamData.Finish)
            return@flow
        }

        val apiUrl = if (baseUrl.isNotEmpty()) "$baseUrl/v1/messages" else {
            emit(AiaThirdPartyLlmStreamData.Text("Error: No base URL configured for provider"))
            emit(AiaThirdPartyLlmStreamData.Finish)
            return@flow
        }

        val requestBody = buildJsonObject {
            put("model", modelId)
            put("max_tokens", 16384)
            put("stream", true)
            putJsonArray("messages") {
                messages.forEach { add(it) }
            }
            putJsonObject("thinking") {
                put("type", "disabled")
            }
        }

        try {
            httpClient.preparePost(apiUrl) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                header("anthropic-version", ANTHROPIC_VERSION)
                setBody(requestBody.toString())
            }.execute { response ->
                if (response.status != HttpStatusCode.OK) {
                    val errorBody = response.bodyAsText()
                    emit(AiaThirdPartyLlmStreamData.Text("Error: ${response.status} - $errorBody"))
                    emit(AiaThirdPartyLlmStreamData.Finish)
                    return@execute
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break

                        try {
                            val jsonData = json.parseToJsonElement(data).jsonObject
                            when (jsonData["type"]?.jsonPrimitive?.contentOrNull) {
                                "content_block_delta" -> {
                                    val text = jsonData["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                    if (!text.isNullOrEmpty()) {
                                        emit(AiaThirdPartyLlmStreamData.Text(text))
                                    }
                                }
                                "message_stop" -> break
                            }
                        } catch (e: Exception) {
                            // Skip parse errors
                        }
                    }
                }

                emit(AiaThirdPartyLlmStreamData.Finish)
            }
        } catch (e: Exception) {
            emit(AiaThirdPartyLlmStreamData.Text("Error: ${e.message}"))
            emit(AiaThirdPartyLlmStreamData.Finish)
        }
    }

    override fun getSettingsUIBuilder(scope: CoroutineScope): ThirdPartyAIProvider.SettingsUIBuilder {
        return AgentProvidersSettingsUIBuilder(scope)
    }

    override fun getSettings(): ThirdPartyAISettingsManager {
        return ApplicationManager.getApplication().getService(AgentProvidersSettingsManager::class.java)
    }
}

package de.espend.ml.llm.vcs

import com.intellij.openapi.diagnostic.Logger
import de.espend.ml.llm.profile.AiProfileConfig
import de.espend.ml.llm.profile.AiProfilePlatformRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

private val LOG = Logger.getInstance(AnthropicApiClient::class.java)

/**
 * Result of an API call.
 */
sealed class ApiResult {
    data class Success(val message: String) : ApiResult()
    data class Error(val error: String) : ApiResult()
}

/**
 * HTTP client for Anthropic-compatible API calls.
 */
object AnthropicApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Sends a request to an Anthropic-compatible API.
     */
    suspend fun sendRequest(
        config: AiProfileConfig,
        prompt: String
    ): ApiResult {
        val platform = AiProfilePlatformRegistry.findPlatform(config.platform)
            ?: return ApiResult.Error("Unknown AI profile platform: ${config.platform}")
        val endpoint = AiProfilePlatformRegistry.resolveEndpoint(platform, config.effectiveApiType())
            ?: return ApiResult.Error("AI profile '${config.name.ifBlank { config.id }}' is not API-based")

        // Validate configuration
        val baseUrl = AiProfilePlatformRegistry.getResolvedBaseUrl(endpoint, config.baseUrl)
            .trimEnd('/')
            .takeIf { it.isNotBlank() }
            ?: return ApiResult.Error("No base URL configured for ${config.name.ifBlank { config.id }}")

        val apiKey = config.apiKey.trim().takeIf { it.isNotEmpty() }
            ?: return ApiResult.Error("No API key configured for ${config.name.ifBlank { config.id }}")

        val model = config.model.takeIf { it.isNotEmpty() }
            ?: platform.defaultModel.takeIf { it.isNotBlank() }
            ?: return ApiResult.Error("No model configured for ${config.name.ifBlank { config.id }}")

        return withContext(Dispatchers.IO) {
            try {
                val url = URI("$baseUrl/v1/messages").toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("anthropic-version", "2023-06-01")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 60000

                // Send request
                val requestBody = buildRequestBody(model, prompt)
                connection.outputStream.use { os ->
                    os.write(requestBody.toByteArray(StandardCharsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorBody = readErrorStream(connection)
                    return@withContext ApiResult.Error("API error ($responseCode): ${errorBody.take(200)}")
                }

                // Read and parse response
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseResponse(responseBody)

            } catch (e: Exception) {
                LOG.error("API request failed", e)
                ApiResult.Error("Request failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun readErrorStream(connection: HttpURLConnection): String {
        return try {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
        } catch (e: Exception) {
            "Failed to read error: ${e.message}"
        }
    }

    private fun buildRequestBody(model: String, prompt: String): String {
        val body = kotlinx.serialization.json.JsonObject(mapOf(
            "model" to kotlinx.serialization.json.JsonPrimitive(model),
            "max_tokens" to kotlinx.serialization.json.JsonPrimitive(10240),
            "messages" to kotlinx.serialization.json.JsonArray(listOf(
                kotlinx.serialization.json.JsonObject(mapOf(
                    "role" to kotlinx.serialization.json.JsonPrimitive("user"),
                    "content" to kotlinx.serialization.json.JsonArray(listOf(
                        kotlinx.serialization.json.JsonObject(mapOf(
                            "type" to kotlinx.serialization.json.JsonPrimitive("text"),
                            "text" to kotlinx.serialization.json.JsonPrimitive(prompt)
                        ))
                    ))
                ))
            ))
        ))
        return body.toString()
    }

    private fun parseResponse(response: String): ApiResult {
        try {
            val jsonObject = json.parseToJsonElement(response).jsonObject

            // Get content array
            val content = jsonObject["content"]?.jsonArray
            if (content.isNullOrEmpty()) {
                return ApiResult.Error("No content in API response")
            }

            // Extract text from content blocks
            val textBuilder = StringBuilder()
            for (item in content) {
                val itemObj = item.jsonObject
                val type = itemObj["type"]?.jsonPrimitive?.content
                if (type == "text") {
                    val text = itemObj["text"]?.jsonPrimitive?.content
                    if (!text.isNullOrEmpty()) {
                        textBuilder.append(text)
                    }
                }
            }

            val message = textBuilder.toString().trim()
            if (message.isEmpty()) {
                return ApiResult.Error("Empty response from API")
            }

            return ApiResult.Success(message)

        } catch (e: Exception) {
            LOG.error("Failed to parse API response: ${response.take(500)}", e)
            return ApiResult.Error("Failed to parse response: ${e.message}")
        }
    }
}

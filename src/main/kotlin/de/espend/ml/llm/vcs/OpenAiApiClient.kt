package de.espend.ml.llm.vcs

import com.intellij.openapi.diagnostic.Logger
import de.espend.ml.llm.profile.AiProfileConfig
import de.espend.ml.llm.profile.AiProfilePlatformRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

private val OPEN_AI_LOG = Logger.getInstance(OpenAiApiClient::class.java)

/**
 * HTTP client for OpenAI-compatible chat completion APIs.
 */
object OpenAiApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendRequest(
        config: AiProfileConfig,
        prompt: String
    ): ApiResult {
        val platform = AiProfilePlatformRegistry.findPlatform(config.platform)
            ?: return ApiResult.Error("Unknown AI profile platform: ${config.platform}")
        val endpoint = AiProfilePlatformRegistry.resolveEndpoint(platform, config.effectiveApiType())
            ?: return ApiResult.Error("AI profile '${config.name.ifBlank { config.id }}' is not API-based")

        val baseUrl = AiProfilePlatformRegistry.getResolvedBaseUrl(endpoint, config.baseUrl)
            .trimEnd('/')
            .takeIf { it.isNotBlank() }
            ?: return ApiResult.Error("No base URL configured for ${config.name.ifBlank { config.id }}")

        val apiKey = config.apiKey.trim().takeIf { it.isNotEmpty() }
            ?: return ApiResult.Error("No API key configured for ${config.name.ifBlank { config.id }}")

        val model = config.model
            .split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: platform.defaultModel.takeIf { it.isNotBlank() }
            ?: return ApiResult.Error("No model configured for ${config.name.ifBlank { config.id }}")

        return withContext(Dispatchers.IO) {
            try {
                val url = URI("$baseUrl/chat/completions").toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 60000

                val requestBody = buildRequestBody(model, prompt)
                connection.outputStream.use { os ->
                    os.write(requestBody.toByteArray(StandardCharsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorBody = readErrorStream(connection)
                    return@withContext ApiResult.Error("API error ($responseCode): ${errorBody.take(200)}")
                }

                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseResponse(responseBody)
            } catch (e: Exception) {
                OPEN_AI_LOG.error("OpenAI API request failed", e)
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
        val body = JsonObject(
            mapOf(
                "model" to JsonPrimitive(model),
                "temperature" to JsonPrimitive(0),
                "messages" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "role" to JsonPrimitive("user"),
                                "content" to JsonPrimitive(prompt)
                            )
                        )
                    )
                )
            )
        )

        return body.toString()
    }

    private fun parseResponse(response: String): ApiResult {
        return try {
            val jsonObject = json.parseToJsonElement(response).jsonObject
            val choice = jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return ApiResult.Error("No choices in API response")
            val message = choice["message"]?.jsonObject
                ?: return ApiResult.Error("No message in API response")
            val content = message["content"]
                ?: return ApiResult.Error("No content in API response")

            val text = extractContentText(content).trim()
            if (text.isEmpty()) {
                ApiResult.Error("Empty response from API")
            } else {
                ApiResult.Success(text)
            }
        } catch (e: Exception) {
            OPEN_AI_LOG.error("Failed to parse OpenAI response: ${response.take(500)}", e)
            ApiResult.Error("Failed to parse response: ${e.message}")
        }
    }

    private fun extractContentText(content: JsonElement): String {
        return when (content) {
            is JsonPrimitive -> content.contentOrNull.orEmpty()
            is JsonArray -> content.joinToString("") { element ->
                element.jsonObject["text"]?.let { extractContentText(it) }.orEmpty()
            }
            is JsonObject -> content["text"]?.let { extractContentText(it) }.orEmpty()
        }
    }
}

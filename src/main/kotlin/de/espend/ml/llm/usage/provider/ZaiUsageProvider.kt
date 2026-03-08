package de.espend.ml.llm.usage.provider

import com.google.gson.JsonParser
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.usage.ProviderInfo
import de.espend.ml.llm.usage.UsageAccountConfig
import de.espend.ml.llm.usage.UsageAccountState
import de.espend.ml.llm.usage.UsageData
import de.espend.ml.llm.usage.UsageFetchResult
import de.espend.ml.llm.usage.UsageFormatUtils
import de.espend.ml.llm.usage.ui.UsageFormPanel
import de.espend.ml.llm.usage.UsageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import javax.swing.JPanel

/**
 * Usage provider for z.ai coding plan
 *
 * API Endpoint: https://api.z.ai/api/monitor/usage/quota/limit
 * Only uses TOKENS_LIMIT with percentage and nextResetTime.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ZaiUsageProvider : UsageProvider {

    override val providerInfo = ProviderInfo(PROVIDER_ID, PROVIDER_NAME, PluginIcons.ZAI, usageEntryCount = 1)
    override val configClass = ZaiUsageAccountConfig::class

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun openForm(panel: UsageFormPanel, initial: UsageAccountConfig): () -> Unit {
        val zaiConfig = initial as? ZaiUsageAccountConfig
            ?: throw RuntimeException("Expected ZaiUsageAccountConfig but got ${initial::class.simpleName}")

        val smallFont = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        val hintColor = UIUtil.getContextHelpForeground()

        // Add provider-specific field: API Key
        val apiKeyField = JBTextField(zaiConfig.apiKey).apply { font = smallFont }
        panel.providerPanel.add(JBLabel("API Key").apply { font = smallFont }, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(2)
        })
        panel.providerPanel.add(apiKeyField, GridBagConstraints().apply {
            gridx = 1
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(2)
        })

        // Hint with clickable link
        val hintPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(JBLabel("Get API key: ").apply {
                font = smallFont
                foreground = hintColor
            })
            add(ActionLink("z.ai/manage-apikey") { BrowserUtil.browse("https://z.ai/manage-apikey/apikey-list") }.apply {
                font = smallFont
            })
        }
        panel.providerPanel.add(hintPanel, GridBagConstraints().apply {
            gridx = 0
            gridy = 1
            gridwidth = 2
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(0, 0, 2, 2)
        })

        // Return save callback - caller invokes if dialog is confirmed
        return {
            zaiConfig.apiKey = apiKeyField.text.trim()
        }
    }

    override suspend fun fetchUsage(account: UsageAccountConfig): UsageFetchResult {
        val zaiAccount = account as? ZaiUsageAccountConfig
            ?: return UsageFetchResult.error("Invalid zai account config")
        val apiKey = zaiAccount.apiKey.ifEmpty { return UsageFetchResult.error("No API key configured") }

        return try {
            val response = fetchWithAuth("https://api.z.ai/api/monitor/usage/quota/limit", apiKey)
            parseResponse(response)
        } catch (e: Exception) {
            UsageFetchResult.error("Error: ${e.message}")
        }
    }

    private suspend fun fetchWithAuth(url: String, apiKey: String): HttpResponse<String> {
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer $apiKey")
                .header("Accept-Language", "en-US,en")
                .header("Content-Type", "application/json")
                .GET()
                .build()

            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }

    private fun parseResponse(response: HttpResponse<String>): UsageFetchResult {
        if (response.statusCode() != 200) {
            return UsageFetchResult.error("HTTP ${response.statusCode()} from z.ai")
        }

        return parseResponseBody(response.body())
    }

    /**
     * Parses the usage response body from Z.AI API.
     * Public for testing purposes.
     *
     * @param body The JSON response body string (may be null)
     * @return UsageFetchResult with percentage used and subtitle, or error
     */
    fun parseResponseBody(body: String?): UsageFetchResult {
        body ?: return UsageFetchResult.error("Empty response")

        return try {
            val root = JsonParser.parseString(body).asJsonObject

            val limitsArray = when {
                root.has("data") && root.getAsJsonObject("data").has("limits") ->
                    root.getAsJsonObject("data").getAsJsonArray("limits")
                root.has("limits") ->
                    root.getAsJsonArray("limits")
                else -> return UsageFetchResult.error("No limits found in response")
            }

            for (element in limitsArray) {
                val limitObj = element.asJsonObject
                if (limitObj.get("type")?.asString != "TOKENS_LIMIT") continue

                val percentageUsed = (limitObj.get("percentage")?.asFloat ?: 0f).coerceIn(0f, 100f)
                val resetAt = limitObj.get("nextResetTime")?.asLong
                    ?.takeIf { it > 0 }
                    ?.let { Instant.ofEpochMilli(it) }
                val subtitle = UsageFormatUtils.formatResetTime(resetAt)

                return UsageFetchResult.success(
                    UsageData(
                        percentageUsed = percentageUsed,
                        subtitle = subtitle
                    )
                )
            }

            UsageFetchResult.error("No token quota found in response")
        } catch (e: Exception) {
            UsageFetchResult.error("Parse error: ${e.message}")
        }
    }

    override fun fromState(state: UsageAccountState): UsageAccountConfig {
        return ZaiUsageAccountConfig().apply {
            id = state.id
            name = state.label
            isEnabled = state.isEnabled
            apiKey = state.getString("apiKey")
        }
    }

    override fun toState(config: UsageAccountConfig): UsageAccountState {
        val zaiConfig = config as ZaiUsageAccountConfig
        return UsageAccountState(id = zaiConfig.id, providerId = PROVIDER_ID, label = zaiConfig.name, isEnabled = zaiConfig.isEnabled).apply {
            putString("apiKey", zaiConfig.apiKey)
        }
    }

    companion object {
        const val PROVIDER_ID = "zai"
        const val PROVIDER_NAME = "Z.AI"
    }

    class ZaiUsageAccountConfig : UsageAccountConfig() {
        override val providerId: String = PROVIDER_ID
        var apiKey: String = ""
    }
}

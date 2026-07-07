package de.espend.ml.llm.usage.provider

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.usage.AccountPanelInfo
import de.espend.ml.llm.usage.ProviderInfo
import de.espend.ml.llm.usage.UsageAccountConfig
import de.espend.ml.llm.usage.UsageAccountState
import de.espend.ml.llm.usage.UsageData
import de.espend.ml.llm.usage.UsageEntry
import de.espend.ml.llm.usage.UsageFetchResult
import de.espend.ml.llm.usage.UsageFormatUtils
import de.espend.ml.llm.usage.UsageProvider
import de.espend.ml.llm.usage.ui.UsageFormPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.swing.JPanel

/**
 * Usage provider for ClinePass.
 *
 * Fetches three Cline plan windows:
 *  - five_hour
 *  - weekly
 *  - monthly
 */
class ClinePassUsageProvider : UsageProvider {

    override val providerInfo = ProviderInfo(PROVIDER_ID, PROVIDER_NAME, PluginIcons.CLINEPASS)
    override fun getAccountPanelInfo(account: UsageAccountConfig): AccountPanelInfo = AccountPanelInfo.progressbar(3)
    override val configClass = ClinePassUsageAccountConfig::class

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun openForm(panel: UsageFormPanel, initial: UsageAccountConfig): () -> Unit {
        val config = initial as? ClinePassUsageAccountConfig
            ?: throw RuntimeException("Expected ClinePassUsageAccountConfig but got ${initial::class.simpleName}")

        val smallFont = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        val hintColor = UIUtil.getContextHelpForeground()

        val apiKeyField = JBTextField(config.apiKey).apply {
            font = smallFont
            emptyText.text = "sk_..."
        }

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

        val hintPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(JBLabel("Reads ClinePass usage limits from the Cline API. Open: ").apply {
                font = smallFont
                foreground = hintColor
            })
            add(ActionLink("Cline dashboard") { BrowserUtil.browse(DASHBOARD_URL) }.apply {
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

        return {
            config.apiKey = apiKeyField.text.trim()
        }
    }

    override suspend fun fetchUsage(account: UsageAccountConfig): UsageFetchResult {
        val config = account as? ClinePassUsageAccountConfig
            ?: return UsageFetchResult.error("Invalid ClinePass account config")
        val apiKey = config.apiKey.ifBlank { return UsageFetchResult.error("No API key configured") }

        return try {
            val response = fetchWithAuth(apiKey)
            parseUsageResponse(response)
        } catch (e: Exception) {
            UsageFetchResult.error("Error: ${e.message?.take(100)}")
        }
    }

    private suspend fun fetchWithAuth(apiKey: String): HttpResponse<String> {
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(USAGE_LIMITS_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .GET()
                .build()

            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }

    private fun parseUsageResponse(response: HttpResponse<String>): UsageFetchResult {
        return when (response.statusCode()) {
            200 -> parseResponseBody(response.body())
            401, 403 -> UsageFetchResult.error("Authentication failed - check your Cline API key")
            else -> UsageFetchResult.error("HTTP ${response.statusCode()} from Cline API")
        }
    }

    /**
     * Parses the Cline usage-limits API response.
     * Public for tests.
     */
    fun parseResponseBody(body: String?, now: Instant = Instant.now()): UsageFetchResult {
        if (body.isNullOrBlank()) return UsageFetchResult.error("Empty response body")

        return try {
            val root = JsonParser.parseString(body).asJsonObject
            if (root.get("success")?.asBoolean == false) {
                val message = root.get("error")?.asString ?: "Cline API returned unsuccessful response"
                return UsageFetchResult.error(message)
            }

            val limitsArray = when {
                root.hasObject("data") && root.getAsJsonObject("data").has("limits") ->
                    root.getAsJsonObject("data").getAsJsonArray("limits")
                root.has("limits") ->
                    root.getAsJsonArray("limits")
                else -> return UsageFetchResult.error("No Cline usage limits found in response")
            }

            val limitsByType = mutableMapOf<String, ClinePassLimit>()
            for (element in limitsArray) {
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject
                val type = obj.get("type")?.asString?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val percent = obj.firstFloat("percentUsed", "percentage", "usagePercent") ?: 0f
                val resetsAt = obj.get("resetsAt")?.toInstantOrNull()
                limitsByType[type] = ClinePassLimit(percent.coerceIn(0f, 100f), resetsAt)
            }

            if (limitsByType.isEmpty()) {
                return UsageFetchResult.error("No Cline usage limits found in response")
            }

            UsageFetchResult.success(
                UsageData(
                    listOf(
                        toEntry(WINDOW_5H_LABEL, limitsByType["five_hour"], now),
                        toEntry(WINDOW_WEEK_LABEL, limitsByType["weekly"], now),
                        toEntry(WINDOW_MONTH_LABEL, limitsByType["monthly"], now)
                    )
                )
            )
        } catch (e: Exception) {
            UsageFetchResult.error("Parse error: ${e.message}")
        }
    }

    private fun toEntry(label: String, limit: ClinePassLimit?, now: Instant): UsageEntry {
        if (limit == null) return UsageEntry(0f, "$label · n/a")

        val reset = limit.resetsAt
            ?.let { (it.epochSecond - now.epochSecond).coerceAtLeast(0) }
            ?.let { UsageFormatUtils.formatSecondsUntilReset(it) }

        return UsageEntry(
            percentageUsed = limit.percentUsed,
            subtitle = if (reset != null) "$label · $reset" else label
        )
    }

    private fun JsonObject.firstFloat(vararg keys: String): Float? {
        return keys.firstNotNullOfOrNull { key ->
            get(key)?.takeIf { it.isJsonPrimitive }?.let {
                runCatching { it.asFloat }.getOrNull()
            }
        }
    }

    private fun JsonObject.hasObject(key: String): Boolean {
        return has(key) && get(key).isJsonObject
    }

    private fun JsonElement.toInstantOrNull(): Instant? {
        if (!isJsonPrimitive) return null
        val value = runCatching { asString.trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            val normalized = value.replace(Regex("""\.(\d{10,})(Z|[+-]\d\d:\d\d)$""")) {
                ".${it.groupValues[1].take(9)}${it.groupValues[2]}"
            }
            runCatching { Instant.parse(normalized) }.getOrNull()
        }
    }

    override fun fromState(state: UsageAccountState): UsageAccountConfig {
        return ClinePassUsageAccountConfig(state).apply {
            apiKey = state.getString("apiKey")
        }
    }

    override fun toState(config: UsageAccountConfig): UsageAccountState {
        val c = config as ClinePassUsageAccountConfig
        return createState(c) {
            putString("apiKey", c.apiKey)
        }
    }

    companion object {
        const val PROVIDER_ID = "cline-pass"
        const val PROVIDER_NAME = "ClinePass"
        private const val USAGE_LIMITS_URL = "https://api.cline.bot/api/v1/users/me/plan/usage-limits"
        private const val DASHBOARD_URL = "https://app.cline.bot/dashboard/subscription?personal=true"

        private const val WINDOW_5H_LABEL = "5h"
        private const val WINDOW_WEEK_LABEL = "Week"
        private const val WINDOW_MONTH_LABEL = "Month"
    }

    private data class ClinePassLimit(
        val percentUsed: Float,
        val resetsAt: Instant?
    )

    class ClinePassUsageAccountConfig(state: UsageAccountState? = null) : UsageAccountConfig(state) {
        override val providerId: String = PROVIDER_ID
        var apiKey: String = ""

        override fun getInfoString(): String {
            return apiKey.takeIf { it.isNotEmpty() }
                ?.let { UsageFormatUtils.formatSecret(it) }
                ?: ""
        }
    }
}

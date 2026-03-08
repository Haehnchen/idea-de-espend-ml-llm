package de.espend.ml.llm.usage.provider

import com.google.gson.JsonParser
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
import de.espend.ml.llm.usage.UsagePlatformRegistry
import de.espend.ml.llm.usage.UsageProvider
import de.espend.ml.llm.usage.ui.UsageFormPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.event.ItemEvent
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * Usage provider for OpenAI Codex.
 *
 * Fetches rate-limit quota from the ChatGPT backend:
 *   GET https://chatgpt.com/backend-api/wham/usage
 *
 * Two credential modes:
 *  - auto:   reads OAuth tokens from ~/.codex/auth.json (same format Codex CLI writes)
 *  - manual: user supplies refresh_token and account_id;
 *            the resolved access_token is cached internally in the registry
 *            and refreshed on demand via https://auth.openai.com/oauth/token
 *
 * Auth JSON format (~/.codex/auth.json):
 * {
 *   "tokens": {
 *     "access_token": "...",
 *     "refresh_token": "...",
 *     "account_id": "..."
 *   },
 *   "last_refresh": "2025-01-15T10:00:00.000Z"
 * }
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class CodexUsageProvider : UsageProvider {

    override val providerInfo = ProviderInfo(PROVIDER_ID, PROVIDER_NAME, PluginIcons.CODEX, usageEntryCount = 1)
    override val configClass = CodexUsageAccountConfig::class

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    override fun openForm(panel: UsageFormPanel, initial: UsageAccountConfig): () -> Unit {
        val config = initial as? CodexUsageAccountConfig
            ?: throw RuntimeException("Expected CodexUsageAccountConfig but got ${initial::class.simpleName}")

        val smallFont = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        val hintColor = UIUtil.getContextHelpForeground()

        val autoRadio = JRadioButton("Auto", config.credentialMode == "auto").apply {
            font = smallFont
            alignmentX = Component.LEFT_ALIGNMENT
        }
        val manualRadio = JRadioButton("Manual", config.credentialMode == "manual").apply {
            font = smallFont
            alignmentX = Component.LEFT_ALIGNMENT
        }
        ButtonGroup().apply { add(autoRadio); add(manualRadio) }

        val refreshTokenField = JBTextField(config.refreshToken).apply { font = smallFont }
        val accountIdField = JBTextField(config.accountId).apply { font = smallFont }

        val refreshRow = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Refresh Token").apply { font = smallFont }, BorderLayout.WEST)
            add(refreshTokenField, BorderLayout.CENTER)
        }
        val accountRow = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Account ID   ").apply { font = smallFont }, BorderLayout.WEST)
            add(accountIdField, BorderLayout.CENTER)
        }

        val manualContentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            isVisible = config.credentialMode == "manual"
            add(refreshRow)
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(accountRow)
        }

        val loadLink = ActionLink("~/.codex/auth.json") {
            val creds = loadCredentialsFromFile()
            if (creds == null) {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    "Could not load credentials from ~/.codex/auth.json",
                    "Codex"
                )
            } else {
                refreshTokenField.text = creds.refreshToken ?: ""
                accountIdField.text = creds.accountId ?: ""
                manualRadio.isSelected = true
                manualContentPanel.isVisible = true
                panel.providerPanel.revalidate()
                panel.providerPanel.repaint()
            }
        }.apply { font = smallFont }

        val manualHintPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Provide manually or load from: ").apply { font = smallFont; foreground = hintColor })
            add(loadLink)
        }

        val box = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(autoRadio)
            add(JBLabel("Load OAuth credentials from ~/.codex/auth.json").apply {
                font = smallFont; foreground = hintColor; alignmentX = Component.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(manualRadio)
            add(manualHintPanel)
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(manualContentPanel)
        }

        panel.providerPanel.add(box, GridBagConstraints().apply {
            gridx = 0; gridy = 0; gridwidth = 2; weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
        })

        autoRadio.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                manualContentPanel.isVisible = false
                panel.providerPanel.revalidate(); panel.providerPanel.repaint()
            }
        }
        manualRadio.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                manualContentPanel.isVisible = true
                panel.providerPanel.revalidate(); panel.providerPanel.repaint()
            }
        }

        return {
            config.credentialMode = if (autoRadio.isSelected) "auto" else "manual"
            config.refreshToken = refreshTokenField.text.trim()
            config.accountId = accountIdField.text.trim()
            // Invalidate cached token when credentials change
            config.cachedAccessToken = ""
        }
    }

    // -------------------------------------------------------------------------
    // Fetch
    // -------------------------------------------------------------------------

    override suspend fun fetchUsage(account: UsageAccountConfig): UsageFetchResult {
        val config = account as? CodexUsageAccountConfig
            ?: return UsageFetchResult.error("Invalid codex account config")

        return try {
            fetchWithTokenRefresh(config)
        } catch (e: Exception) {
            UsageFetchResult.error("Error: ${e.message}")
        }
    }

    private suspend fun fetchWithTokenRefresh(config: CodexUsageAccountConfig): UsageFetchResult {
        val creds: Credentials = when (config.credentialMode) {
            "auto" -> loadCredentialsFromFile()
                ?: return UsageFetchResult.error("Could not load credentials from ~/.codex/auth.json")
            else -> {
                if (config.refreshToken.isEmpty()) {
                    return UsageFetchResult.error("No refresh_token configured")
                }
                Credentials(
                    accessToken = config.cachedAccessToken,
                    refreshToken = config.refreshToken,
                    accountId = config.accountId.ifEmpty { null }
                )
            }
        }

        // Try the existing access_token first (if present)
        if (creds.accessToken.isNotEmpty()) {
            val response = fetchUsageHttp(creds.accessToken, creds.accountId)
            if (response.statusCode() != 401 && response.statusCode() != 403) {
                return parseUsageResponse(response)
            }
        }

        // Access token missing or expired — refresh
        val refreshToken = creds.refreshToken
            ?: return UsageFetchResult.error("No refresh_token available")

        val refreshed = try {
            refreshAccessToken(refreshToken, creds.accountId)
        } catch (e: Exception) {
            return UsageFetchResult.error("Token refresh failed: ${e.message}")
        }

        // Persist the new access_token in plugin state (never modify the CLI credential file)
        config.cachedAccessToken = refreshed.accessToken
        UsagePlatformRegistry.getInstance().updateAccountProperties(
            config.id,
            mapOf("cachedAccessToken" to refreshed.accessToken)
        )

        val response = fetchUsageHttp(refreshed.accessToken, refreshed.accountId)
        return parseUsageResponse(response)
    }

    private suspend fun fetchUsageHttp(accessToken: String, accountId: String?): HttpResponse<String> {
        return withContext(Dispatchers.IO) {
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(USAGE_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .header("User-Agent", "OpenUsage")
                .GET()

            if (accountId != null) {
                builder.header("ChatGPT-Account-Id", accountId)
            }

            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }
    }

    private suspend fun refreshAccessToken(refreshToken: String, accountId: String?): Credentials {
        return withContext(Dispatchers.IO) {
            val body = "grant_type=refresh_token" +
                "&client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)

            val request = HttpRequest.newBuilder()
                .uri(URI.create(REFRESH_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() !in 200..299) {
                val errorMsg = try {
                    val json = JsonParser.parseString(response.body()).asJsonObject
                    // Try nested error object, flat error string, then error_description
                    json.getAsJsonObject("error")?.get("code")?.asString
                        ?: json.get("error")?.asString
                        ?: json.get("error_description")?.asString
                        ?: "HTTP ${response.statusCode()}"
                } catch (_: Exception) { "HTTP ${response.statusCode()}" }
                throw RuntimeException(errorMsg)
            }

            val json = JsonParser.parseString(response.body()).asJsonObject
            val newAccessToken = json.get("access_token")?.asString
                ?: throw RuntimeException("No access_token in refresh response")
            val newRefreshToken = json.get("refresh_token")?.asString ?: refreshToken

            Credentials(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                accountId = accountId
            )
        }
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private fun parseUsageResponse(response: HttpResponse<String>): UsageFetchResult {
        return when (response.statusCode()) {
            401, 403 -> UsageFetchResult.error("Authentication failed — re-authenticate with Codex CLI")
            200 -> parseBodyWithHeaders(response.body(), response.headers())
            else -> UsageFetchResult.error("HTTP ${response.statusCode()} from Codex usage API")
        }
    }

    /**
     * Parses the usage response body from Codex API.
     * Public for testing purposes.
     *
     * @param body The JSON response body string (may be null)
     * @param headerPercent Optional header value for used percent (for testing)
     * @return UsageFetchResult with percentage used and subtitle, or error
     */
    fun parseResponseBody(body: String?, headerPercent: Double? = null): UsageFetchResult {
        body ?: return UsageFetchResult.error("Empty response body")

        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val rateLimit = root.getAsJsonObject("rate_limit")
            val primaryWindow = rateLimit?.getAsJsonObject("primary_window")

            // Prefer provided header value, fall back to JSON body
            val usedPercent: Double = headerPercent
                ?: primaryWindow?.get("used_percent")?.asDouble
                ?: return UsageFetchResult.error("No usage data found in response")

            val percentageUsed = usedPercent.toFloat().coerceIn(0f, 100f)
            val subtitle = buildSubtitle(primaryWindow)

            UsageFetchResult.success(UsageData(percentageUsed = percentageUsed, subtitle = subtitle))
        } catch (e: Exception) {
            UsageFetchResult.error("Parse error: ${e.message}")
        }
    }

    private fun parseBodyWithHeaders(body: String?, headers: java.net.http.HttpHeaders): UsageFetchResult {
        body ?: return UsageFetchResult.error("Empty response body")

        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val rateLimit = root.getAsJsonObject("rate_limit")
            val primaryWindow = rateLimit?.getAsJsonObject("primary_window")

            // Prefer response header, fall back to JSON body
            val usedPercent: Double = headers
                .firstValue("x-codex-primary-used-percent")
                .map { it.toDoubleOrNull() }
                .orElse(null)
                ?: primaryWindow?.get("used_percent")?.asDouble
                ?: return UsageFetchResult.error("No usage data found in response")

            val percentageUsed = usedPercent.toFloat().coerceIn(0f, 100f)
            val subtitle = buildSubtitle(primaryWindow)

            UsageFetchResult.success(UsageData(percentageUsed = percentageUsed, subtitle = subtitle))
        } catch (e: Exception) {
            UsageFetchResult.error("Parse error: ${e.message}")
        }
    }

    private fun buildSubtitle(primaryWindow: com.google.gson.JsonObject?): String? {
        primaryWindow ?: return null

        val nowSeconds = System.currentTimeMillis() / 1000.0
        val resetAtSeconds: Double = primaryWindow.get("reset_at")?.asDouble
            ?: primaryWindow.get("reset_after_seconds")?.asDouble?.let { nowSeconds + it }
            ?: return null

        val secondsUntil = (resetAtSeconds - nowSeconds).toLong()
        return UsageFormatUtils.formatSecondsUntilReset(secondsUntil)
    }

    // -------------------------------------------------------------------------
    // File credential I/O (auto mode)
    // -------------------------------------------------------------------------

    private fun loadCredentialsFromFile(): Credentials? {
        return try {
            val file = File(System.getProperty("user.home"), ".codex/auth.json")
            if (!file.exists()) return null

            val json = JsonParser.parseString(file.readText()).asJsonObject
            val tokens = json.getAsJsonObject("tokens") ?: return null
            val accessToken = tokens.get("access_token")?.asString?.takeIf { it.isNotEmpty() }
                ?: return null

            Credentials(
                accessToken = accessToken,
                refreshToken = tokens.get("refresh_token")?.asString,
                accountId = tokens.get("account_id")?.asString
            )
        } catch (_: Exception) { null }
    }


    // -------------------------------------------------------------------------
    // State serialization
    // -------------------------------------------------------------------------

    override fun fromState(state: UsageAccountState): UsageAccountConfig {
        return CodexUsageAccountConfig().apply {
            id = state.id
            name = state.label
            isEnabled = state.isEnabled
            credentialMode = state.getString("credentialMode", "auto")
            refreshToken = state.getString("refreshToken")
            accountId = state.getString("accountId")
            cachedAccessToken = state.getString("cachedAccessToken")
        }
    }

    override fun toState(config: UsageAccountConfig): UsageAccountState {
        val c = config as CodexUsageAccountConfig
        return UsageAccountState(id = c.id, providerId = PROVIDER_ID, label = c.name, isEnabled = c.isEnabled).apply {
            putString("credentialMode", c.credentialMode)
            putString("refreshToken", c.refreshToken)
            putString("accountId", c.accountId)
            putString("cachedAccessToken", c.cachedAccessToken)
        }
    }

    // -------------------------------------------------------------------------
    // Companion / inner types
    // -------------------------------------------------------------------------

    companion object {
        const val PROVIDER_ID = "codex"
        const val PROVIDER_NAME = "Codex"
        private const val USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
        private const val REFRESH_URL = "https://auth.openai.com/oauth/token"
        private const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    }

    private data class Credentials(
        val accessToken: String,
        val refreshToken: String?,
        val accountId: String?
    )

    class CodexUsageAccountConfig : UsageAccountConfig() {
        override val providerId: String = PROVIDER_ID
        var credentialMode: String = "auto"    // "auto" or "manual"
        var refreshToken: String = ""          // user-provided (manual mode)
        var accountId: String = ""             // user-provided (manual mode, optional)
        var cachedAccessToken: String = ""     // internal: updated after each token refresh
    }
}

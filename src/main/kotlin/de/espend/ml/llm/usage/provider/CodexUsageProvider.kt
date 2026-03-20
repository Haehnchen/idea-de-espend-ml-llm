package de.espend.ml.llm.usage.provider

import com.google.gson.JsonParser
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.usage.AccountPanelInfo
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.Component
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
 *  - manual: user supplies refresh_token;
 *            the resolved access_token is cached internally in the registry
 *            and refreshed on demand via https://auth.openai.com/oauth/token
 *
 * Auth JSON format (~/.codex/auth.json):
 * {
 *   "tokens": {
 *     "access_token": "...",
 *     "refresh_token": "...",
 *   },
 *   "last_refresh": "2025-01-15T10:00:00.000Z"
 * }
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class CodexUsageProvider : UsageProvider {

    override val providerInfo = ProviderInfo(PROVIDER_ID, PROVIDER_NAME, PluginIcons.CODEX)

    override fun getAccountPanelInfo(account: UsageAccountConfig) = AccountPanelInfo.progressbar(1)
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

        // Manual mode: Load button + status message (no text input)
        val loadButton = javax.swing.JButton("Load OAuth Token").apply {
            font = smallFont
            toolTipText = "Load and refresh OAuth token from ~/.codex/auth.json"
        }

        val statusLabel = JBLabel("").apply {
            font = smallFont
            alignmentX = Component.LEFT_ALIGNMENT
        }

        // Helper to show token status - only displays token on success (green)
        fun updateTokenStatus(token: String) {
            if (token.isEmpty()) {
                statusLabel.text = ""
                return
            }
            statusLabel.text = "Token: ${UsageFormatUtils.formatSecret(token)}"
            statusLabel.foreground = com.intellij.ui.JBColor.GREEN.darker()
        }

        // Helper to show error message (red)
        fun showError(message: String) {
            statusLabel.text = message
            statusLabel.foreground = com.intellij.ui.JBColor.RED
        }

        // Show current token status if already configured
        if (config.credentialMode == "manual" && config.cachedAccessToken.isNotEmpty()) {
            updateTokenStatus(config.cachedAccessToken)
        }

        val manualContentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            isVisible = config.credentialMode == "manual"
            add(loadButton)
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(statusLabel)
        }

        // Load button action
        loadButton.addActionListener {
            statusLabel.text = "Loading..."
            statusLabel.foreground = hintColor

            val creds = loadCredentialsFromFile()
            if (creds == null) {
                showError("✗ No credentials found — run `codex` to relogin")
            } else if (creds.refreshToken != null) {
                try {
                    val refreshed = runBlocking { refreshAccessToken(creds.refreshToken) }
                    config.cachedAccessToken = refreshed.accessToken
                    config.cachedRefreshToken = refreshed.refreshToken ?: ""
                    updateTokenStatus(refreshed.accessToken)
                } catch (e: Exception) {
                    // Refresh failed - show truncated error
                    val msg = e.message?.take(40)?.let { ": $it" } ?: ""
                    showError("✗ Refresh failed$msg")
                }
            } else {
                // No refresh token available - cannot use
                showError("✗ No refresh token — run `codex` to relogin")
            }

            manualContentPanel.revalidate()
            manualContentPanel.repaint()
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
            add(JBLabel("Reuse refresh token from ~/.codex/auth.json to mint independent access tokens (requires valid login)").apply {
                font = smallFont; foreground = hintColor; alignmentX = Component.LEFT_ALIGNMENT
            })
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
                statusLabel.text = ""
                // Clear cached tokens on mode switch
                config.cachedAccessToken = ""
                config.cachedRefreshToken = ""
                panel.providerPanel.revalidate(); panel.providerPanel.repaint()
            }
        }
        manualRadio.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                manualContentPanel.isVisible = true
                // Clear cached tokens on mode switch
                config.cachedAccessToken = ""
                config.cachedRefreshToken = ""
                panel.providerPanel.revalidate(); panel.providerPanel.repaint()
            }
        }

        return {
            config.credentialMode = if (autoRadio.isSelected) "auto" else "manual"
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
        val isAutoMode = config.credentialMode == "auto"

        val creds: Credentials = if (isAutoMode) {
            // Auto mode: Load all credentials from CLI file (accessToken, refreshToken)
            loadCredentialsFromFile()
                ?: return UsageFetchResult.error("Could not load credentials from ~/.codex/auth.json")
        } else {
            // Manual mode: Use cached token + cached refreshToken (if loaded from file)
            if (config.cachedAccessToken.isEmpty()) {
                return UsageFetchResult.error("No access token — click 'Load OAuth Token' to hijack session")
            }
            Credentials(
                accessToken = config.cachedAccessToken,
                refreshToken = config.cachedRefreshToken.takeIf { it.isNotEmpty() }
            )
        }

        // Try the existing access_token first (if present)
        if (creds.accessToken.isNotEmpty()) {
            val response = fetchUsageHttp(creds.accessToken)
            if (response.statusCode() != 401 && response.statusCode() != 403) {
                return parseUsageResponse(response)
            }
        }

        // Access token missing or expired — refresh
        val refreshToken = creds.refreshToken
            ?: return UsageFetchResult.error("No refresh_token available")

        val refreshed = try {
            refreshAccessToken(refreshToken)
        } catch (e: Exception) {
            return UsageFetchResult.error("Token refresh failed: ${e.message}")
        }

        // Persist the new access_token in plugin state (never modify the CLI credential file)
        config.cachedAccessToken = refreshed.accessToken
        config.cachedRefreshToken = refreshed.refreshToken ?: config.cachedRefreshToken
        UsagePlatformRegistry.getInstance().updateAccountProperties(
            config.id,
            buildMap {
                put("cachedAccessToken", refreshed.accessToken)
                if (refreshed.refreshToken != null) put("cachedRefreshToken", refreshed.refreshToken)
            }
        )

        val response = fetchUsageHttp(refreshed.accessToken)
        return parseUsageResponse(response)
    }

    private suspend fun fetchUsageHttp(accessToken: String): HttpResponse<String> {
        return withContext(Dispatchers.IO) {
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(USAGE_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .header("User-Agent", "OpenUsage")
                .GET()

            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }
    }

    private suspend fun refreshAccessToken(refreshToken: String): Credentials {
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
                refreshToken = newRefreshToken
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
                refreshToken = tokens.get("refresh_token")?.asString
            )
        } catch (_: Exception) { null }
    }


    // -------------------------------------------------------------------------
    // State serialization
    // -------------------------------------------------------------------------

    override fun fromState(state: UsageAccountState): UsageAccountConfig {
        return CodexUsageAccountConfig(state).apply {
            credentialMode = state.getString("credentialMode", "auto")
            cachedAccessToken = state.getString("cachedAccessToken")
            cachedRefreshToken = state.getString("cachedRefreshToken")
        }
    }

    override fun toState(config: UsageAccountConfig): UsageAccountState {
        val c = config as CodexUsageAccountConfig
        return createState(c) {
            putString("credentialMode", c.credentialMode)
            putString("cachedAccessToken", c.cachedAccessToken)
            putString("cachedRefreshToken", c.cachedRefreshToken)
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
        val refreshToken: String?
    )

    class CodexUsageAccountConfig(state: UsageAccountState? = null) : UsageAccountConfig(state) {
        override val providerId: String = PROVIDER_ID
        var credentialMode: String = "auto"       // "auto" or "manual"
        var cachedAccessToken: String = ""        // internal: updated after each token refresh
        var cachedRefreshToken: String = ""       // internal: updated after each token refresh

        override fun getInfoString(): String {
            val parts = mutableListOf(credentialMode)
            if (credentialMode == "manual" && cachedAccessToken.isNotEmpty()) {
                parts += UsageFormatUtils.formatSecret(cachedAccessToken)
            }

            return parts.joinToString(" · ")
        }
    }
}

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
import de.espend.ml.llm.usage.UsageEntry
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
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * Usage provider for Claude OAuth (Anthropic).
 *
 * Fetches quota data from the Anthropic OAuth usage API:
 *   GET https://api.anthropic.com/api/oauth/usage
 *
 * Three credential modes:
 *  - auto:   reads OAuth tokens from ~/.claude/.credentials.json (same format Claude Code writes)
 *  - manual: user supplies an access_token directly (no automatic refresh possible)
 *  - web:    user supplies a sessionKey cookie (sk-ant-...) for claude.ai web API
 *
 * Credentials JSON format (~/.claude/.credentials.json):
 * {
 *   "claudeAiOauth": {
 *     "accessToken": "...",
 *     "refreshToken": "...",
 *     "expiresAt": 1234567890000,
 *     "subscriptionType": "claude_pro"
 *   }
 * }
 *
 * Web API endpoints (sessionKey mode):
 *   GET https://claude.ai/api/organizations → get org UUID
 *   GET https://claude.ai/api/organizations/{org_id}/usage → usage data
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ClaudeUsageProvider : UsageProvider {

    override val providerInfo = ProviderInfo(PROVIDER_ID, PROVIDER_NAME, PluginIcons.CLAUDE, usageEntryCount = 2)
    override val configClass = ClaudeUsageAccountConfig::class

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    override fun openForm(panel: UsageFormPanel, initial: UsageAccountConfig): () -> Unit {
        val config = initial as? ClaudeUsageAccountConfig
            ?: throw RuntimeException("Expected ClaudeUsageAccountConfig but got ${initial::class.simpleName}")

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
        val webRadio = JRadioButton("Web API", config.credentialMode == "web").apply {
            font = smallFont
            alignmentX = Component.LEFT_ALIGNMENT
        }
        ButtonGroup().apply { add(autoRadio); add(manualRadio); add(webRadio) }

        // Manual mode: Access Token field
        val accessTokenField = JBTextField(config.manualToken).apply { font = smallFont }

        val tokenRow = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Access Token").apply { font = smallFont }, BorderLayout.WEST)
            add(accessTokenField, BorderLayout.CENTER)
        }

        val manualContentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            isVisible = config.credentialMode == "manual"
            add(tokenRow)
        }

        // Web API mode: Session Key field
        val sessionKeyField = JBTextField(config.sessionKey).apply { font = smallFont }

        val sessionKeyRow = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Session Key").apply { font = smallFont }, BorderLayout.WEST)
            add(sessionKeyField, BorderLayout.CENTER)
        }

        val webContentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            isVisible = config.credentialMode == "web"
            add(sessionKeyRow)
            add(JBLabel("Cookie from claude.ai (starts with sk-ant-)").apply {
                font = smallFont; foreground = hintColor; alignmentX = Component.LEFT_ALIGNMENT
            })
        }

        val loadLink = ActionLink("~/.claude/.credentials.json") {
            val creds = loadCredentialsFromFile()
            if (creds == null) {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    "Could not load credentials from ~/.claude/.credentials.json",
                    "Claude OAuth"
                )
            } else {
                accessTokenField.text = creds.accessToken
                manualRadio.isSelected = true
                manualContentPanel.isVisible = true
                webContentPanel.isVisible = false
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
            add(JBLabel("Load OAuth credentials from ~/.claude/.credentials.json").apply {
                font = smallFont; foreground = hintColor; alignmentX = Component.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(manualRadio)
            add(manualHintPanel)
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(manualContentPanel)
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(webRadio)
            add(JBLabel("Use browser cookies (sessionKey) from claude.ai starts with 'sk-*'").apply {
                font = smallFont; foreground = hintColor; alignmentX = Component.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(webContentPanel)
        }

        panel.providerPanel.add(box, GridBagConstraints().apply {
            gridx = 0; gridy = 0; gridwidth = 2; weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
        })

        autoRadio.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                manualContentPanel.isVisible = false
                webContentPanel.isVisible = false
                panel.providerPanel.revalidate(); panel.providerPanel.repaint()
            }
        }
        manualRadio.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                manualContentPanel.isVisible = true
                webContentPanel.isVisible = false
                panel.providerPanel.revalidate(); panel.providerPanel.repaint()
            }
        }
        webRadio.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                manualContentPanel.isVisible = false
                webContentPanel.isVisible = true
                panel.providerPanel.revalidate(); panel.providerPanel.repaint()
            }
        }

        return {
            config.credentialMode = when {
                autoRadio.isSelected -> "auto"
                webRadio.isSelected -> "web"
                else -> "manual"
            }
            config.manualToken = accessTokenField.text.trim()
            config.sessionKey = sessionKeyField.text.trim()
            // Invalidate cached tokens when credentials change
            config.cachedAccessToken = ""
            config.cachedRefreshToken = ""
            config.cachedExpiresAt = 0L
        }
    }

    // -------------------------------------------------------------------------
    // Fetch
    // -------------------------------------------------------------------------

    override suspend fun fetchUsage(account: UsageAccountConfig): UsageFetchResult {
        val config = account as? ClaudeUsageAccountConfig
            ?: return UsageFetchResult.error("Invalid Claude OAuth account config")

        return try {
            when (config.credentialMode) {
                "web" -> fetchWithWebSessionKey(config)
                else -> fetchWithTokenRefresh(config)
            }
        } catch (e: Exception) {
            UsageFetchResult.error("Error: ${e.message}")
        }
    }

    private suspend fun fetchWithTokenRefresh(config: ClaudeUsageAccountConfig): UsageFetchResult {
        val creds: Credentials = when (config.credentialMode) {
            "auto" -> loadCredentialsFromFile()
                ?: return UsageFetchResult.error("Could not load credentials from ~/.claude/.credentials.json")
            else -> {
                if (config.manualToken.isEmpty()) {
                    return UsageFetchResult.error("No access token configured")
                }
                Credentials(
                    accessToken = config.manualToken,
                    refreshToken = null,
                    expiresAt = null
                )
            }
        }

        // Check if token needs refresh before using it
        if (needsRefresh(creds.expiresAt) && creds.refreshToken != null) {
            val refreshed = try {
                refreshAccessToken(creds.refreshToken)
            } catch (e: Exception) {
                return UsageFetchResult.error("Token refresh failed: ${e.message}")
            }
            persistRefreshedToken(config, refreshed)
            val response = fetchUsageHttp(refreshed.accessToken)
            return parseUsageResponse(response)
        }

        // Try the current access_token
        val response = fetchUsageHttp(creds.accessToken)

        // On 401/403, try to refresh once if we have a refresh token
        if ((response.statusCode() == 401 || response.statusCode() == 403) && creds.refreshToken != null) {
            val refreshed = try {
                refreshAccessToken(creds.refreshToken)
            } catch (e: Exception) {
                return UsageFetchResult.error("Token refresh failed: ${e.message}")
            }
            persistRefreshedToken(config, refreshed)
            val retryResponse = fetchUsageHttp(refreshed.accessToken)
            return parseUsageResponse(retryResponse)
        }

        return parseUsageResponse(response)
    }

    private suspend fun fetchUsageHttp(accessToken: String): HttpResponse<String> {
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(USAGE_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer ${accessToken.trim()}")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("anthropic-beta", "oauth-2025-04-20")
                .header("User-Agent", "claude-code/2.1.71 (Claude Code)")
                .GET()
                .build()

            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }

    // -------------------------------------------------------------------------
    // Web API (sessionKey cookie) authentication
    // -------------------------------------------------------------------------

    private suspend fun fetchWithWebSessionKey(config: ClaudeUsageAccountConfig): UsageFetchResult {
        val sessionKey = config.sessionKey.trim()
        if (sessionKey.isEmpty()) {
            return UsageFetchResult.error("No session key configured for Web API mode")
        }

        if (!sessionKey.startsWith("sk-ant-")) {
            return UsageFetchResult.error("Invalid session key format (must start with sk-ant-)")
        }

        return try {
            // Step 1: Get organization ID
            val orgId = fetchOrganizationId(sessionKey)
                ?: return UsageFetchResult.error("No Claude organization found")

            // Step 2: Fetch usage data
            val response = fetchWebUsageHttp(orgId, sessionKey)
            parseWebUsageResponse(response)
        } catch (e: Exception) {
            UsageFetchResult.error("Web API error: ${e.message}")
        }
    }

    private suspend fun fetchOrganizationId(sessionKey: String): String? {
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(WEB_ORGANIZATIONS_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", "sessionKey=$sessionKey")
                .header("Accept", "application/json")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) return@withContext null

            val json = JsonParser.parseString(response.body()).asJsonArray
            val org = json.firstOrNull()?.asJsonObject ?: return@withContext null
            org.get("uuid")?.asString
        }
    }

    private suspend fun fetchWebUsageHttp(orgId: String, sessionKey: String): HttpResponse<String> {
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$WEB_BASE_URL/organizations/$orgId/usage"))
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", "sessionKey=$sessionKey")
                .header("Accept", "application/json")
                .GET()
                .build()

            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }

    private fun parseWebUsageResponse(response: HttpResponse<String>): UsageFetchResult {
        return when (response.statusCode()) {
            200 -> parseUsageBody(response.body())
            401, 403 -> UsageFetchResult.error("Session expired — please refresh your claude.ai session key")
            429 -> UsageFetchResult.error("Rate limited — try again in a few minutes")
            500, 502, 503 -> UsageFetchResult.error("Claude.ai is temporarily unavailable")
            else -> UsageFetchResult.error("Failed to fetch usage (HTTP ${response.statusCode()})")
        }
    }

    private suspend fun refreshAccessToken(refreshToken: String): Credentials {
        return withContext(Dispatchers.IO) {
            val body = """{"grant_type":"refresh_token","refresh_token":"$refreshToken","client_id":"$CLIENT_ID","scope":"$SCOPES"}"""

            val request = HttpRequest.newBuilder()
                .uri(URI.create(REFRESH_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() !in 200..299) {
                val errorMsg = try {
                    val json = JsonParser.parseString(response.body()).asJsonObject
                    json.get("error")?.asString
                        ?: json.get("error_description")?.asString
                        ?: "HTTP ${response.statusCode()}"
                } catch (_: Exception) { "HTTP ${response.statusCode()}" }
                throw RuntimeException(errorMsg)
            }

            val json = JsonParser.parseString(response.body()).asJsonObject
            val newAccessToken = json.get("access_token")?.asString
                ?: throw RuntimeException("No access_token in refresh response")
            val newRefreshToken = json.get("refresh_token")?.asString ?: refreshToken
            val expiresIn = json.get("expires_in")?.asLong
            val expiresAt = expiresIn?.let { System.currentTimeMillis() + it * 1000L }

            Credentials(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                expiresAt = expiresAt
            )
        }
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private fun parseUsageResponse(response: HttpResponse<String>): UsageFetchResult {
        return when (response.statusCode()) {
            200 -> parseUsageBody(response.body())
            401, 403 -> UsageFetchResult.error("Authentication failed — run `claude` to re-authenticate")
            429 -> UsageFetchResult.error("Rate limited — please try again later")
            else -> UsageFetchResult.error("HTTP ${response.statusCode()} from Claude usage API")
        }
    }

    /**
     * Parses usage response body from Claude API (OAuth or Web).
     * Format: {"five_hour": {"utilization": 50, "resets_at": "..."}, "seven_day": {...}}
     */
    internal fun parseUsageBody(body: String?): UsageFetchResult {
        body ?: return UsageFetchResult.error("Empty response body")

        return try {
            val root = JsonParser.parseString(body).asJsonObject

            val fiveHour = root.getAsJsonObject("five_hour")
            val sevenDay = root.getAsJsonObject("seven_day")

            val entries = mutableListOf<UsageEntry>()

            // Add 5-hour window entry (always show, even if not started)
            val fiveHourUtil = fiveHour?.get("utilization")
            if (fiveHourUtil != null && !fiveHourUtil.isJsonNull) {
                val utilization = fiveHourUtil.asDouble
                val percentageUsed = utilization.toFloat().coerceIn(0f, 100f)
                val resetsAt = fiveHour.get("resets_at")?.takeIf { !it.isJsonNull }?.asString
                val resetText = formatResetText(resetsAt)
                val subtitle = "5h · ${resetText ?: "not started"}"
                entries.add(UsageEntry(percentageUsed, subtitle))
            } else {
                entries.add(UsageEntry(0f, "5h · not started"))
            }

            // Add 7-day window entry (always show, even if not started)
            val sevenDayUtil = sevenDay?.get("utilization")
            if (sevenDayUtil != null && !sevenDayUtil.isJsonNull) {
                val utilization = sevenDayUtil.asDouble
                val percentageUsed = utilization.toFloat().coerceIn(0f, 100f)
                val resetsAt = sevenDay.get("resets_at")?.takeIf { !it.isJsonNull }?.asString
                val resetText = formatResetText(resetsAt)
                val subtitle = "7d · ${resetText ?: "not started"}"
                entries.add(UsageEntry(percentageUsed, subtitle))
            } else {
                entries.add(UsageEntry(0f, "7d · not started"))
            }

            UsageFetchResult.success(entries)
        } catch (_: IllegalStateException) {
            // JSON parsing errors (e.g., trying to get object from null)
            UsageFetchResult.error("Could not parse usage data")
        } catch (e: Exception) {
            UsageFetchResult.error("Could not parse usage data: ${e.message?.take(50)}")
        }
    }

    private fun formatResetText(resetsAt: String?): String? {
        resetsAt ?: return null
        return try {
            val resetInstant = java.time.Instant.parse(resetsAt)
            val secondsUntil = resetInstant.epochSecond - System.currentTimeMillis() / 1000L
            UsageFormatUtils.formatSecondsUntilReset(secondsUntil)
        } catch (_: Exception) { null }
    }

    // -------------------------------------------------------------------------
    // Token persistence
    // -------------------------------------------------------------------------

    /** Persist refreshed tokens in plugin state (never modify the CLI credential file). */
    private fun persistRefreshedToken(config: ClaudeUsageAccountConfig, refreshed: Credentials) {
        config.cachedAccessToken = refreshed.accessToken
        config.cachedRefreshToken = refreshed.refreshToken ?: config.cachedRefreshToken
        config.cachedExpiresAt = refreshed.expiresAt ?: config.cachedExpiresAt
        UsagePlatformRegistry.getInstance().updateAccountProperties(
            config.id,
            buildMap {
                put("cachedAccessToken", refreshed.accessToken)
                if (refreshed.refreshToken != null) put("cachedRefreshToken", refreshed.refreshToken)
                if (refreshed.expiresAt != null) put("cachedExpiresAt", refreshed.expiresAt.toString())
            }
        )
    }

    // -------------------------------------------------------------------------
    // Token expiry check
    // -------------------------------------------------------------------------

    /** Returns true if the token is expired or expiring within 5 minutes. */
    private fun needsRefresh(expiresAt: Long?): Boolean {
        expiresAt ?: return true
        val bufferMs = 5 * 60 * 1000L
        return System.currentTimeMillis() + bufferMs >= expiresAt
    }

    // -------------------------------------------------------------------------
    // File credential I/O (auto mode)
    // -------------------------------------------------------------------------

    private fun loadCredentialsFromFile(): Credentials? {
        return try {
            val file = File(System.getProperty("user.home"), ".claude/.credentials.json")
            if (!file.exists()) return null

            val json = JsonParser.parseString(file.readText()).asJsonObject
            val oauth = json.getAsJsonObject("claudeAiOauth") ?: return null
            val accessToken = oauth.get("accessToken")?.asString?.takeIf { it.isNotBlank() }
                ?: return null

            Credentials(
                accessToken = accessToken,
                refreshToken = oauth.get("refreshToken")?.asString,
                expiresAt = oauth.get("expiresAt")?.asLong
            )
        } catch (_: Exception) { null }
    }

    // -------------------------------------------------------------------------
    // State serialization
    // -------------------------------------------------------------------------

    override fun fromState(state: UsageAccountState): UsageAccountConfig {
        return ClaudeUsageAccountConfig().apply {
            id = state.id
            name = state.label
            isEnabled = state.isEnabled
            credentialMode = state.getString("credentialMode", "auto")
            manualToken = state.getString("manualToken")
            sessionKey = state.getString("sessionKey")
            cachedAccessToken = state.getString("cachedAccessToken")
            cachedRefreshToken = state.getString("cachedRefreshToken")
            cachedExpiresAt = state.getString("cachedExpiresAt").toLongOrNull() ?: 0L
        }
    }

    override fun toState(config: UsageAccountConfig): UsageAccountState {
        val c = config as ClaudeUsageAccountConfig
        return UsageAccountState(id = c.id, providerId = PROVIDER_ID, label = c.name, isEnabled = c.isEnabled).apply {
            putString("credentialMode", c.credentialMode)
            putString("manualToken", c.manualToken)
            putString("sessionKey", c.sessionKey)
            putString("cachedAccessToken", c.cachedAccessToken)
            putString("cachedRefreshToken", c.cachedRefreshToken)
            putString("cachedExpiresAt", c.cachedExpiresAt.toString())
        }
    }

    // -------------------------------------------------------------------------
    // Companion / inner types
    // -------------------------------------------------------------------------

    companion object {
        const val PROVIDER_ID = "claude"
        const val PROVIDER_NAME = "Claude"
        private const val FALLBACK_VERSION = "2.1.0"
        private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
        private const val REFRESH_URL = "https://platform.claude.com/v1/oauth/token"
        private const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        private const val SCOPES = "user:profile user:inference user:sessions:claude_code"
        // Web API endpoints (sessionKey cookie authentication)
        private const val WEB_BASE_URL = "https://claude.ai/api"
        private const val WEB_ORGANIZATIONS_URL = "https://claude.ai/api/organizations"
    }

    private data class Credentials(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAt: Long?
    )

    class ClaudeUsageAccountConfig : UsageAccountConfig() {
        override val providerId: String = PROVIDER_ID
        var credentialMode: String = "auto"       // "auto", "manual", or "web"
        var manualToken: String = ""              // user-provided access token (manual mode)
        var sessionKey: String = ""               // user-provided sessionKey cookie (web mode)
        var cachedAccessToken: String = ""        // internal: updated after each token refresh
        var cachedRefreshToken: String = ""       // internal: updated after each token refresh
        var cachedExpiresAt: Long = 0L            // internal: expiry timestamp in ms
    }
}

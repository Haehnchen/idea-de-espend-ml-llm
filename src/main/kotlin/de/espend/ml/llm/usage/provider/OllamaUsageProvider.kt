package de.espend.ml.llm.usage.provider

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
import de.espend.ml.llm.usage.UsageEntry
import de.espend.ml.llm.usage.UsageFetchResult
import de.espend.ml.llm.usage.UsageFormatUtils
import de.espend.ml.llm.usage.UsageProvider
import de.espend.ml.llm.usage.ui.UsageFormPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * Usage provider for Ollama Cloud (ollama.com).
 *
 * Fetches quota data by scraping the Ollama settings page:
 *   GET https://ollama.com/settings
 *
 * Authentication: value of the `__Secure-next-auth.session-token` cookie from ollama.com.
 * The cookie header is built internally as:
 *   __Secure-next-auth.session-token=<value>
 *
 * Displays two progress bars:
 *  - Session (5h window) with reset time
 *  - Weekly window with reset time
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class OllamaUsageProvider : UsageProvider {

    override val providerInfo = ProviderInfo(PROVIDER_ID, PROVIDER_NAME, PluginIcons.OLLAMA)
    override fun getAccountPanelInfo(account: UsageAccountConfig) = AccountPanelInfo.progressbar(2)
    override val configClass = OllamaUsageAccountConfig::class

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    override fun openForm(panel: UsageFormPanel, initial: UsageAccountConfig): () -> Unit {
        val config = initial as? OllamaUsageAccountConfig
            ?: throw RuntimeException("Expected OllamaUsageAccountConfig but got ${initial::class.simpleName}")

        val smallFont = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        val hintColor = UIUtil.getContextHelpForeground()

        val sessionTokenField = JBTextField(config.sessionToken).apply {
            font = smallFont
            emptyText.setText("Paste __Secure-session value here")
        }

        val tokenRow = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Session Token:").apply { font = smallFont }, BorderLayout.WEST)
            add(sessionTokenField, BorderLayout.CENTER)
        }

        val hintPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Value of ").apply { font = smallFont; foreground = hintColor })
            add(JBLabel("__Secure-session").apply { font = smallFont; foreground = hintColor })
            add(JBLabel(" cookie from ").apply { font = smallFont; foreground = hintColor })
            add(ActionLink("ollama.com/settings") { BrowserUtil.browse("https://ollama.com/settings") }.apply {
                font = smallFont
            })
        }

        val box = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(tokenRow)
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(hintPanel)
        }

        panel.providerPanel.add(box, GridBagConstraints().apply {
            gridx = 0; gridy = 0; gridwidth = 2; weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
        })

        return {
            config.sessionToken = sessionTokenField.text.trim()
        }
    }

    // -------------------------------------------------------------------------
    // Fetch
    // -------------------------------------------------------------------------

    override suspend fun fetchUsage(account: UsageAccountConfig): UsageFetchResult {
        val config = account as? OllamaUsageAccountConfig
            ?: return UsageFetchResult.error("Invalid Ollama account config")

        if (config.sessionToken.isBlank()) {
            return UsageFetchResult.error("No session token configured — paste __Secure-next-auth.session-token value from ollama.com/settings")
        }

        val cookieHeader = "__Secure-session=${config.sessionToken}"

        return try {
            val html = fetchSettingsHtml(cookieHeader)
            parseHtml(html)
        } catch (e: Exception) {
            UsageFetchResult.error("Error: ${e.message?.take(80)}")
        }
    }

    private suspend fun fetchSettingsHtml(cookieHeader: String): String {
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(SETTINGS_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", cookieHeader)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            when (response.statusCode()) {
                200 -> response.body() ?: ""
                401, 403 -> throw RuntimeException("Session expired — log in at ollama.com and update your cookie")
                else -> throw RuntimeException("HTTP ${response.statusCode()} from ollama.com/settings")
            }
        }
    }

    // -------------------------------------------------------------------------
    // HTML parsing
    // -------------------------------------------------------------------------

    internal fun parseHtml(html: String): UsageFetchResult {
        if (html.isBlank()) return UsageFetchResult.error("Empty response from ollama.com/settings")
        if (looksSignedOut(html)) return UsageFetchResult.error("Not logged in — update cookie from ollama.com/settings")

        val sessionBlock = parseUsageBlock(listOf("Session usage", "Hourly usage"), html)
        val weeklyBlock = parseUsageBlock(listOf("Weekly usage"), html)

        if (sessionBlock == null && weeklyBlock == null) {
            return UsageFetchResult.error("No usage data found — check cookie or visit ollama.com/settings")
        }

        val entries = mutableListOf<UsageEntry>()

        val sessionPercent = sessionBlock?.usedPercent?.toFloat()?.coerceIn(0f, 100f) ?: 0f
        val sessionReset = sessionBlock?.resetsAt?.let { UsageFormatUtils.formatResetTime(it) }
        entries.add(UsageEntry(sessionPercent, "Session · ${sessionReset ?: "not started"}"))

        val weeklyPercent = weeklyBlock?.usedPercent?.toFloat()?.coerceIn(0f, 100f) ?: 0f
        val weeklyReset = weeklyBlock?.resetsAt?.let { UsageFormatUtils.formatResetTime(it) }
        entries.add(UsageEntry(weeklyPercent, "Weekly · ${weeklyReset ?: "not started"}"))

        return UsageFetchResult.success(entries)
    }

    private data class UsageBlock(val usedPercent: Double, val resetsAt: Instant?)

    private fun parseUsageBlock(labels: List<String>, html: String): UsageBlock? {
        for (label in labels) {
            val block = parseSingleUsageBlock(label, html)
            if (block != null) return block
        }
        return null
    }

    private fun parseSingleUsageBlock(label: String, html: String): UsageBlock? {
        val labelIndex = html.indexOf(label)
        if (labelIndex < 0) return null
        val window = html.substring(labelIndex + label.length).take(800)

        val usedPercent = parsePercent(window) ?: return null
        val resetsAt = parseIsoDate(window)
        return UsageBlock(usedPercent, resetsAt)
    }

    private fun parsePercent(text: String): Double? {
        val usedPattern = Regex("""([0-9]+(?:\.[0-9]+)?)\s*%\s*used""", RegexOption.IGNORE_CASE)
        usedPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }

        val widthPattern = Regex("""width:\s*([0-9]+(?:\.[0-9]+)?)%""", RegexOption.IGNORE_CASE)
        widthPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }

        return null
    }

    private fun parseIsoDate(text: String): Instant? {
        val pattern = Regex("""data-time="([^"]+)"""")
        val raw = pattern.find(text)?.groupValues?.get(1) ?: return null
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            // Try without fractional seconds
            try { Instant.parse(raw.substringBefore(".") + "Z") } catch (_: Exception) { null }
        }
    }

    private fun looksSignedOut(html: String): Boolean {
        val lower = html.lowercase()
        val hasSignInHeading = lower.contains("sign in to ollama") || lower.contains("log in to ollama")
        val hasAuthEndpoint = lower.contains("/api/auth/signin") || lower.contains("/auth/signin")
            || lower.contains("href=\"/login\"") || lower.contains("href=\"/signin\"")
        val hasAuthForm = lower.contains("<form")
        val hasPasswordField = lower.contains("type=\"password\"") || lower.contains("name=\"password\"")
        val hasEmailField = lower.contains("type=\"email\"") || lower.contains("name=\"email\"")

        if (hasSignInHeading && hasAuthForm && (hasEmailField || hasPasswordField || hasAuthEndpoint)) return true
        if (hasAuthForm && hasAuthEndpoint) return true
        if (hasAuthForm && hasPasswordField && hasEmailField) return true
        return false
    }

    // -------------------------------------------------------------------------
    // State serialization
    // -------------------------------------------------------------------------

    override fun fromState(state: UsageAccountState): UsageAccountConfig {
        return OllamaUsageAccountConfig().apply {
            id = state.id
            name = state.label
            isEnabled = state.isEnabled
            sessionToken = state.getString("sessionToken")
        }
    }

    override fun toState(config: UsageAccountConfig): UsageAccountState {
        val c = config as OllamaUsageAccountConfig
        return UsageAccountState(id = c.id, providerId = PROVIDER_ID, label = c.name, isEnabled = c.isEnabled).apply {
            putString("sessionToken", c.sessionToken)
        }
    }

    // -------------------------------------------------------------------------
    // Companion / inner types
    // -------------------------------------------------------------------------

    companion object {
        const val PROVIDER_ID = "ollama"
        const val PROVIDER_NAME = "Ollama"
        private const val SETTINGS_URL = "https://ollama.com/settings"
    }

    class OllamaUsageAccountConfig : UsageAccountConfig() {
        override val providerId: String = PROVIDER_ID
        var sessionToken: String = ""

        override fun getInfoString(): String {
            if (sessionToken.isBlank()) return ""
            return UsageFormatUtils.formatSecret(sessionToken)
        }
    }
}

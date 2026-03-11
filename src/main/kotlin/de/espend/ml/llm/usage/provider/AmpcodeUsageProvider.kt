package de.espend.ml.llm.usage.provider

import com.google.gson.JsonParser
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
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import java.io.File
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JRadioButton
import kotlin.math.ceil

/**
 * Usage provider for Ampcode credits.
 *
 * API Endpoint: https://ampcode.com/api/internal
 * Method: userDisplayBalanceInfo
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class AmpcodeUsageProvider : UsageProvider {

    override val providerInfo = ProviderInfo(PROVIDER_ID, PROVIDER_NAME, PluginIcons.AMPCODE)

    override fun getAccountPanelInfo(account: UsageAccountConfig) = AccountPanelInfo.progressbar(1)
    override val configClass = AmpcodeUsageAccountConfig::class

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    /**
     * Creates the configuration form for Ampcode credentials.
     * Supports two modes:
     * - Auto: Loads API key from ~/.local/share/amp/secrets.json
     * - Manual: User enters API key directly
     *
     * @param panel The form panel to add UI components to
     * @param initial The initial configuration state
     * @return A callback that saves the form values when invoked
     */
    override fun openForm(panel: UsageFormPanel, initial: UsageAccountConfig): () -> Unit {
        val ampcodeConfig = initial as? AmpcodeUsageAccountConfig
            ?: throw RuntimeException("Expected AmpcodeUsageAccountConfig but got ${initial::class.simpleName}")

        val smallFont = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        val hintColor = UIUtil.getContextHelpForeground()

        // Credential mode radio buttons
        val autoRadio = JRadioButton("Auto", ampcodeConfig.credentialMode == "auto").apply {
            font = smallFont
            alignmentX = java.awt.Component.LEFT_ALIGNMENT
        }
        val manualRadio = JRadioButton("Manual", ampcodeConfig.credentialMode == "manual").apply {
            font = smallFont
            alignmentX = java.awt.Component.LEFT_ALIGNMENT
        }
        val buttonGroup = ButtonGroup()
        buttonGroup.add(autoRadio)
        buttonGroup.add(manualRadio)

        val apiKeyField = JBTextField(ampcodeConfig.apiKey).apply { font = smallFont }

        // Manual content panel (API key row) - shown only in manual mode
        val manualContentPanel = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = java.awt.Component.LEFT_ALIGNMENT
            isVisible = ampcodeConfig.credentialMode == "manual"
            add(JBLabel("API Key").apply { font = smallFont }, BorderLayout.WEST)
            add(apiKeyField, BorderLayout.CENTER)
        }

        // Single vertical box — no column recalculation, always left-aligned
        val box = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(autoRadio)
            add(JBLabel("Load API key from ~/.local/share/amp/secrets.json").apply {
                font = smallFont
                foreground = hintColor
                alignmentX = java.awt.Component.LEFT_ALIGNMENT
            })
            add(javax.swing.Box.createVerticalStrut(JBUI.scale(4)))
            add(manualRadio)
            add(JBLabel("Enter API key manually").apply {
                font = smallFont
                foreground = hintColor
                alignmentX = java.awt.Component.LEFT_ALIGNMENT
            })
            add(javax.swing.Box.createVerticalStrut(JBUI.scale(2)))
            add(manualContentPanel)
        }

        panel.providerPanel.add(box, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            gridwidth = 2
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        })

        // Toggle visibility based on radio selection
        autoRadio.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                manualContentPanel.isVisible = false
                panel.providerPanel.revalidate()
                panel.providerPanel.repaint()
            }
        }
        manualRadio.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                manualContentPanel.isVisible = true
                panel.providerPanel.revalidate()
                panel.providerPanel.repaint()
            }
        }

        // Return save callback - caller invokes if dialog is confirmed
        return {
            ampcodeConfig.credentialMode = if (autoRadio.isSelected) "auto" else "manual"
            ampcodeConfig.apiKey = apiKeyField.text.trim()
        }
    }

    /**
     * Fetches the current usage data from the Ampcode API.
     * Uses the userDisplayBalanceInfo method to retrieve balance information.
     *
     * @param account The account configuration containing API credentials
     * @return UsageFetchResult containing percentage used and replenishment info
     */
    override suspend fun fetchUsage(account: UsageAccountConfig): UsageFetchResult {
        val ampcodeAccount = account as? AmpcodeUsageAccountConfig
            ?: return UsageFetchResult.error("Invalid ampcode account config")

        val apiKey = when (ampcodeAccount.credentialMode) {
            "auto" -> loadApiKeyFromSecrets()
                ?: return UsageFetchResult.error("Could not load API key from ~/.local/share/amp/secrets.json")
            else -> ampcodeAccount.apiKey.ifEmpty { return UsageFetchResult.error("No API key configured") }
        }

        return try {
            val response = fetchWithAuth("https://ampcode.com/api/internal", apiKey)
            parseResponse(response)
        } catch (e: Exception) {
            UsageFetchResult.error("Error: ${e.message}")
        }
    }

    /**
     * Load API key from amp secrets file.
     * Extracts the key from: ~/.local/share/amp/secrets.json
     * JSON key: "apiKey@https://ampcode.com/"
     */
    private fun loadApiKeyFromSecrets(): String? {
        return try {
            val secretsFile = File(System.getProperty("user.home"), ".local/share/amp/secrets.json")
            if (!secretsFile.exists()) return null

            val json = JsonParser.parseString(secretsFile.readText()).asJsonObject
            json.get("apiKey@https://ampcode.com/")?.asString
                ?: json.get("apiKey")?.asString
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Makes an authenticated POST request to the Ampcode API.
     *
     * @param url The API endpoint URL
     * @param apiKey The Bearer token for authentication
     * @return The HTTP response body as a string
     */
    private suspend fun fetchWithAuth(url: String, apiKey: String): HttpResponse<String> {
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"method\":\"userDisplayBalanceInfo\",\"params\":{}}"))
                .build()

            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }

    /**
     * Parses the Ampcode API response and extracts usage data.
     * Extracts remaining/total balance from displayText using regex patterns.
     *
     * @param response The HTTP response from the Ampcode API
     * @return UsageFetchResult with percentage used and subtitle, or error
     */
    private fun parseResponse(response: HttpResponse<String>): UsageFetchResult {
        if (response.statusCode() != 200) {
            return UsageFetchResult.error("HTTP ${response.statusCode()} from ampcode")
        }

        val body = response.body() ?: return UsageFetchResult.error("Empty response")

        return try {
            val root = JsonParser.parseString(body).asJsonObject
            if (!root.get("ok")?.asBoolean.orFalse()) {
                return UsageFetchResult.error("Ampcode API returned non-ok response")
            }

            val displayText = root.getAsJsonObject("result")?.get("displayText")?.asString
                ?: return UsageFetchResult.error("No displayText found in response")

            val parsed = parseDisplayText(displayText)
                ?: return UsageFetchResult.error("Could not parse free balance from response")

            val (percentageUsed, subtitle, _) = parsed

            UsageFetchResult.success(
                UsageData(
                    percentageUsed = percentageUsed,
                    subtitle = subtitle
                )
            )
        } catch (e: Exception) {
            UsageFetchResult.error("Parse error: ${e.message}")
        }
    }

    /** Helper extension to safely unwrap nullable Boolean values, defaulting to false */
    private fun Boolean?.orFalse(): Boolean = this ?: false

    /**
     * Reconstructs an AmpcodeUsageAccountConfig from persisted state.
     *
     * @param state The persisted state object
     * @return A fully configured AmpcodeUsageAccountConfig instance
     */
    override fun fromState(state: UsageAccountState): UsageAccountConfig {
        return AmpcodeUsageAccountConfig().apply {
            id = state.id
            name = state.label
            isEnabled = state.isEnabled
            credentialMode = state.getString("credentialMode", "auto")
            apiKey = state.getString("apiKey")
        }
    }

    /**
     * Converts an AmpcodeUsageAccountConfig to a persistable state object.
     *
     * @param config The configuration to persist
     * @return A UsageAccountState ready for storage
     */
    override fun toState(config: UsageAccountConfig): UsageAccountState {
        val ampcodeConfig = config as AmpcodeUsageAccountConfig
        return UsageAccountState(id = ampcodeConfig.id, providerId = PROVIDER_ID, label = ampcodeConfig.name, isEnabled = ampcodeConfig.isEnabled).apply {
            putString("credentialMode", ampcodeConfig.credentialMode)
            putString("apiKey", ampcodeConfig.apiKey)
        }
    }

    companion object {
        const val PROVIDER_ID = "ampcode"
        const val PROVIDER_NAME = "Ampcode"

        /** Regex to extract free balance: matches "Free: $X.XX/$Y.YY" capturing remaining and total amounts */
        private val freeBalanceRegex = Regex("""Free:\s*\$(\d+(?:\.\d+)?)/\$(\d+(?:\.\d+)?)""")

        /** Regex to extract hourly replenishment rate: matches "replenishes +$X.XX/hour" capturing the dollar amount */
        private val replenishRegex = Regex("""replenishes\s*\+\$(\d+(?:\.\d+)?)/hour""", RegexOption.IGNORE_CASE)

        /**
         * Parses display text directly for testing purposes.
         * Extracts balance and replenishment info from the raw API displayText.
         *
         * @param displayText The raw display text, e.g., "Amp Free: $8.78/$10 remaining (replenishes +$0.42/hour) - http"
         * @return Triple of (percentageUsed, subtitle, remaining) or null if parsing fails
         */
        fun parseDisplayText(displayText: String): Triple<Float, String?, Double>? {
            val match = freeBalanceRegex.find(displayText) ?: return null

            val remaining = match.groupValues[1].toDoubleOrNull() ?: return null
            val total = match.groupValues[2].toDoubleOrNull() ?: return null

            if (total <= 0.0) return null

            val used = (total - remaining).coerceAtLeast(0.0)
            val percentageUsed = ((used / total) * 100.0).toFloat().coerceIn(0f, 100f)
            val subtitle = buildSubtitleFromText(displayText, remaining, total)

            return Triple(percentageUsed, subtitle, remaining)
        }

        /**
         * Builds a subtitle string from display text for testing.
         *
         * @param displayText The raw display text containing replenishment info
         * @param remaining The current remaining balance in dollars
         * @param total The total balance cap in dollars
         * @return A formatted subtitle like "replenish in 2h 30m (+5.0%/h)" or null if no replenishment info
         */
        fun buildSubtitleFromText(displayText: String, remaining: Double, total: Double): String? {
            val replenishmentPerHour = replenishRegex.find(displayText)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
                ?.takeIf { it > 0.0 && total > 0.0 }
                ?: return null

            val missingAmount = (total - remaining).coerceAtLeast(0.0)
            val hoursUntilFull = missingAmount / replenishmentPerHour

            // If already full or invalid, return null
            if (!hoursUntilFull.isFinite() || hoursUntilFull <= 0.0) {
                return null
            }

            val percentagePerHour = ((replenishmentPerHour / total) * 100).toFloat()
            return "${formatHoursUntilFull(hoursUntilFull)} (+${"%.1f".format(percentagePerHour)}%/h)"
        }

        /**
         * Formats the time until full replenishment into a human-readable string.
         *
         * @param hoursUntilFull The estimated hours until balance is fully replenished
         * @return A formatted time string like "replenish in 1d 4h" or "replenished soon"
         */
        fun formatHoursUntilFull(hoursUntilFull: Double): String {
            require(hoursUntilFull.isFinite() && hoursUntilFull > 0.0) {
                "hoursUntilFull must be finite and positive, but was: $hoursUntilFull"
            }

            val totalMinutes = ceil(hoursUntilFull * 60.0).toLong().coerceAtLeast(1)
            val days = totalMinutes / (24 * 60)
            val hours = (totalMinutes % (24 * 60)) / 60
            val minutes = totalMinutes % 60

            return buildString {
                append("replenish in ")
                when {
                    days > 0 -> {
                        append(days).append("d")
                        if (hours > 0) append(" ").append(hours).append("h")
                    }
                    hours > 0 -> {
                        append(hours).append("h")
                        if (minutes > 0) append(" ").append(minutes).append("m")
                    }
                    else -> append(minutes).append("m")
                }
            }
        }
    }

    /**
     * Configuration for an Ampcode usage account.
     * Supports two credential modes:
     * - auto: Automatically load API key from ~/.local/share/amp/secrets.json
     * - manual: Use a user-provided API key
     */
    class AmpcodeUsageAccountConfig : UsageAccountConfig() {
        override val providerId: String = PROVIDER_ID
        var credentialMode: String = "auto"  // "auto" or "manual"
        var apiKey: String = ""

        override fun getInfoString(): String {
            val parts = mutableListOf(credentialMode)
            if (credentialMode == "manual" && apiKey.isNotEmpty()) {
                parts += UsageFormatUtils.formatSecret(apiKey)
            }

            return parts.joinToString(" · ")
        }
    }
}

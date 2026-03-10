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
import de.espend.ml.llm.usage.UsageEntry
import de.espend.ml.llm.usage.UsageFetchResult
import de.espend.ml.llm.usage.UsageLine
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
 * Usage provider for JetBrains Junie.
 *
 * Fetches balance and license info from the Junie API:
 *   GET https://ingrazzio-cloud-prod.labs.jb.gg/auth/test
 *
 * Two credential modes:
 *  - auto:   reads API key from ~/.junie/credentials.json (same format Junie CLI writes)
 *  - manual: user supplies the API key directly
 *
 * Credentials JSON format (~/.junie/credentials.json):
 * {
 *   "junieApiKey": "perm-..."
 * }
 *
 * API Response:
 * {
 *   "balanceLeft": 297.73,
 *   "balanceUnit": "USD",
 *   "licenseType": "JUNP",
 *   "active": true
 * }
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class JunieUsageProvider : UsageProvider {

    override val providerInfo = ProviderInfo(PROVIDER_ID, PROVIDER_NAME, PluginIcons.JUNIE, usageEntryCount = 0, lineCount = 1)
    override val configClass = JunieUsageAccountConfig::class

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    override fun openForm(panel: UsageFormPanel, initial: UsageAccountConfig): () -> Unit {
        val config = initial as? JunieUsageAccountConfig
            ?: throw RuntimeException("Expected JunieUsageAccountConfig but got ${initial::class.simpleName}")

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

        val apiKeyField = JBTextField(config.apiKey).apply { font = smallFont }

        val apiKeyRow = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("API Key").apply { font = smallFont }, BorderLayout.WEST)
            add(apiKeyField, BorderLayout.CENTER)
        }

        val manualContentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            isVisible = config.credentialMode == "manual"
            add(apiKeyRow)
        }

        val loadLink = ActionLink("~/.junie/credentials.json") {
            val apiKey = loadApiKeyFromFile()
            if (apiKey == null) {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    "Could not load API key from ~/.junie/credentials.json",
                    "Junie"
                )
            } else {
                apiKeyField.text = apiKey
                manualRadio.isSelected = true
                manualContentPanel.isVisible = true
                panel.providerPanel.revalidate()
                panel.providerPanel.repaint()
            }
        }.apply { font = smallFont }

        val generateLink = ActionLink("Generate token") {
            com.intellij.ide.BrowserUtil.browse("https://junie.jetbrains.com/cli")
        }.apply { font = smallFont }

        val manualHintPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Provide manually or load from: ").apply { font = smallFont; foreground = hintColor })
            add(loadLink)
            add(JBLabel("  |  ").apply { font = smallFont; foreground = hintColor })
            add(generateLink)
        }

        val box = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(autoRadio)
            add(JBLabel("Load API key from ~/.junie/credentials.json").apply {
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
            config.apiKey = apiKeyField.text.trim()
        }
    }

    // -------------------------------------------------------------------------
    // Fetch
    // -------------------------------------------------------------------------

    override suspend fun fetchUsage(account: UsageAccountConfig): UsageFetchResult {
        val config = account as? JunieUsageAccountConfig
            ?: return UsageFetchResult.error("Invalid junie account config")

        val apiKey = when (config.credentialMode) {
            "auto" -> loadApiKeyFromFile()
                ?: return UsageFetchResult.error("Could not load API key from ~/.junie/credentials.json")
            else -> config.apiKey.ifEmpty {
                return UsageFetchResult.error("No API key configured")
            }
        }

        return try {
            fetchUsageHttp(apiKey)
        } catch (e: Exception) {
            UsageFetchResult.error("Error: ${e.message}")
        }
    }

    private suspend fun fetchUsageHttp(apiKey: String): UsageFetchResult {
        val response = withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(USAGE_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .header("X-Accept-EAP-License", "true")
                .GET()
                .build()

            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        return parseUsageResponse(response)
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private fun parseUsageResponse(response: HttpResponse<String>): UsageFetchResult {
        return when (response.statusCode()) {
            401, 403 -> UsageFetchResult.error("Authentication failed - check your API key")
            200 -> parseResponseBody(response.body())
            else -> UsageFetchResult.error("HTTP ${response.statusCode()} from Junie API")
        }
    }

    /**
     * Parses the usage response body from Junie API.
     * Public for testing purposes.
     */
    fun parseResponseBody(body: String?): UsageFetchResult {
        body ?: return UsageFetchResult.error("Empty response body")

        return try {
            val root = JsonParser.parseString(body).asJsonObject

            val balanceLeft = root.get("balanceLeft")?.asDouble
            val balanceUnit = root.get("balanceUnit")?.asString ?: "USD"
            val licenseType = root.get("licenseType")?.asString
            val active = root.get("active")?.asBoolean ?: true

            if (!active) {
                return UsageFetchResult.error("Account is not active")
            }

            val licenseName = getLicenseDisplayName(licenseType)
            val lineText = formatBalance(balanceLeft, balanceUnit, licenseName)

            // Return as text line - no progress bar needed for balance display
            UsageFetchResult.success(UsageData(lines = listOf(UsageLine(lineText))))
        } catch (e: Exception) {
            UsageFetchResult.error("Parse error: ${e.message}")
        }
    }

    private fun formatBalance(balanceLeft: Double?, balanceUnit: String, licenseName: String?): String {
        val balanceStr = if (balanceLeft != null) {
            if (balanceUnit == "USD") {
                "$${formatCost(balanceLeft)}"
            } else {
                "${formatCost(balanceLeft / 100000.0)} AI Credits"
            }
        } else {
            "Unknown"
        }

        return if (licenseName != null) {
            "$licenseName | $balanceStr"
        } else {
            balanceStr
        }
    }

    private fun formatCost(cost: Double): String {
        return when {
            cost == 0.0 -> "0.00"
            cost >= 0.1 -> String.format(java.util.Locale.US, "%.2f", cost)
            cost >= 0.001 -> String.format(java.util.Locale.US, "%.4f", cost)
            else -> String.format(java.util.Locale.US, "%.6f", cost)
        }.trimEnd('0').trimEnd('.')
    }

    private fun getLicenseDisplayName(licenseType: String?): String? {
        return when (licenseType) {
            "JUNP" -> "Junie EAP"
            "AIF" -> "JetBrains AI Free"
            "AIP" -> "JetBrains AI Pro"
            "AIU" -> "JetBrains AI Ultimate"
            "NONE" -> "No active license"
            else -> licenseType
        }
    }

    // -------------------------------------------------------------------------
    // File credential I/O (auto mode)
    // -------------------------------------------------------------------------

    private fun loadApiKeyFromFile(): String? {
        return try {
            val file = File(System.getProperty("user.home"), ".junie/credentials.json")
            if (!file.exists()) return null

            val json = JsonParser.parseString(file.readText()).asJsonObject
            json.get("junieApiKey")?.asString?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    // -------------------------------------------------------------------------
    // State serialization
    // -------------------------------------------------------------------------

    override fun fromState(state: UsageAccountState): UsageAccountConfig {
        return JunieUsageAccountConfig().apply {
            id = state.id
            name = state.label
            isEnabled = state.isEnabled
            credentialMode = state.getString("credentialMode", "auto")
            apiKey = state.getString("apiKey")
        }
    }

    override fun toState(config: UsageAccountConfig): UsageAccountState {
        val c = config as JunieUsageAccountConfig
        return UsageAccountState(id = c.id, providerId = PROVIDER_ID, label = c.name, isEnabled = c.isEnabled).apply {
            putString("credentialMode", c.credentialMode)
            putString("apiKey", c.apiKey)
        }
    }

    // -------------------------------------------------------------------------
    // Companion / inner types
    // -------------------------------------------------------------------------

    companion object {
        const val PROVIDER_ID = "junie"
        const val PROVIDER_NAME = "Junie"
        private const val USAGE_URL = "https://ingrazzio-cloud-prod.labs.jb.gg/auth/test"
    }

    class JunieUsageAccountConfig : UsageAccountConfig() {
        override val providerId: String = PROVIDER_ID
        var credentialMode: String = "auto"
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

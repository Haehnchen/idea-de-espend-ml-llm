package de.espend.ml.llm.usage

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.usage.ui.UsageSettingsConfigurable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import javax.swing.*

/**
 * Popup panel displaying usage for all configured providers.
 * Handles widget creation, refresh, and per-provider fetch.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ProviderUsagePanel(
    private val providers: List<UsageAccountConfig>,
    private val service: ProviderUsageService,
    private val scope: CoroutineScope
) : JPanel() {

    private var currentPopup: JBPopup? = null
    private var currentProject: Project? = null
    private var isLoading = false

    private val refreshIconLabel = JBLabel(AllIcons.Actions.Refresh)
    private val providerWidgets = mutableMapOf<String, ProviderWidgets>()

    private data class EntryWidgets(
        val pctLabel: JBLabel,
        val progressBar: JProgressBar
    )

    private data class LineWidgets(
        val lineLabel: JBLabel
    )

    private data class ProviderWidgets(
        val entries: List<EntryWidgets>,
        val lines: List<LineWidgets>
    )

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12, 16)

        val refreshIconButton = object : JPanel() {
            var hovered = false
            override fun paintComponent(g: java.awt.Graphics) {
                if (hovered && !isLoading) {
                    val g2 = g.create() as java.awt.Graphics2D
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = JBUI.CurrentTheme.ActionButton.hoverBackground()
                    g2.fillRoundRect(0, 0, width, height, JBUI.scale(4), JBUI.scale(4))
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }.apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(2)
            add(refreshIconLabel)
        }

        val refreshTextLabel = JBLabel("Refresh").apply { foreground = SECONDARY_COLOR }

        val settingsIconLabel = JBLabel(AllIcons.General.Settings)
        val settingsIconButton = object : JPanel() {
            var hovered = false
            override fun paintComponent(g: java.awt.Graphics) {
                if (hovered) {
                    val g2 = g.create() as java.awt.Graphics2D
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = JBUI.CurrentTheme.ActionButton.hoverBackground()
                    g2.fillRoundRect(0, 0, width, height, JBUI.scale(4), JBUI.scale(4))
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }.apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(2)
            add(settingsIconLabel)
        }

        val refreshRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = 0f
            add(refreshTextLabel)
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(refreshIconButton)
            add(Box.createHorizontalGlue())
            add(settingsIconButton)
        }

        add(refreshRow)
        add(Box.createVerticalStrut(JBUI.scale(8)))

        for ((index, config) in providers.withIndex()) {
            val widgets = createProviderCard(config)
            providerWidgets[config.id] = widgets
            if (index < providers.size - 1) {
                add(Box.createVerticalStrut(JBUI.scale(8)))
                add(createSeparator())
                add(Box.createVerticalStrut(JBUI.scale(8)))
            }
        }

        settingsIconButton.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(evt: java.awt.event.MouseEvent) { settingsIconButton.hovered = true; settingsIconButton.repaint() }
            override fun mouseExited(evt: java.awt.event.MouseEvent) { settingsIconButton.hovered = false; settingsIconButton.repaint() }
            override fun mouseClicked(evt: java.awt.event.MouseEvent) {
                currentPopup?.cancel()
                ShowSettingsUtil.getInstance().showSettingsDialog(currentProject, UsageSettingsConfigurable::class.java)
            }
        })

        refreshIconButton.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(evt: java.awt.event.MouseEvent) { refreshIconButton.hovered = true; refreshIconButton.repaint() }
            override fun mouseExited(evt: java.awt.event.MouseEvent) { refreshIconButton.hovered = false; refreshIconButton.repaint() }
            override fun mouseClicked(evt: java.awt.event.MouseEvent) { if (!isLoading) doRefresh() }
        })
    }

    fun start(popup: JBPopup, project: Project?) {
        currentPopup = popup
        currentProject = project

        if (service.isCacheValid()) {
            showCachedResults()
        } else {
            doRefresh(forceRefresh = false)
        }
    }

    private fun showCachedResults() {
        providers.forEach { config ->
            providerWidgets[config.id]?.let { widgets ->
                val cached = service.getCachedResponse(config.id)
                if (cached != null) {
                    if (cached.usage != null) updateWidgets(widgets, cached.usage)
                    else showWidgetError(widgets, cached.error ?: "Unknown error")
                }
            }
        }
        currentPopup?.pack(true, true)
    }

    private fun doRefresh(forceRefresh: Boolean = true) {
        isLoading = true
        refreshIconLabel.icon = AnimatedIcon.Default()

        val results = mutableMapOf<String, ProviderUsageResponse>()
        val pending = AtomicInteger(providers.size)
        providers.forEach { config ->
            providerWidgets[config.id]?.let { widgets ->
                fetchAndUpdate(config, widgets, results) {
                    if (pending.decrementAndGet() <= 0) {
                        service.updateCache(results)
                        isLoading = false
                        refreshIconLabel.icon = AllIcons.Actions.Refresh
                    }
                }
            }
        }
    }

    private fun fetchAndUpdate(config: UsageAccountConfig, widgets: ProviderWidgets, results: MutableMap<String, ProviderUsageResponse>, onDone: () -> Unit) {
        scope.launch {
            try {
                val response = service.fetchUsage(config)
                synchronized(results) { results[config.id] = response }
                ApplicationManager.getApplication().invokeLater {
                    val popup = currentPopup ?: return@invokeLater
                    if (!popup.isDisposed) {
                        if (response.usage != null) updateWidgets(widgets, response.usage)
                        else showWidgetError(widgets, response.error ?: "Unknown error")
                        onDone()
                        popup.pack(true, true)
                    }
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    val popup = currentPopup ?: return@invokeLater
                    if (!popup.isDisposed) {
                        showWidgetError(widgets, e.message ?: "Error")
                        onDone()
                        popup.pack(true, true)
                    }
                }
            }
        }
    }

    private fun createProviderCard(config: UsageAccountConfig): ProviderWidgets {
        val provider = service.getProvider(config.providerId)
        val providerInfo = provider?.providerInfo
        val icon = providerInfo?.icon?.let { PluginIcons.scaleIcon(it, 14) }
        val providerLabel = providerInfo?.providerName ?: config.providerId
        val accountLabel = config.name
        val panelInfo = provider?.getAccountPanelInfo(config) ?: AccountPanelInfo.progressbar(1)
        val entryCount = panelInfo.usageEntryCount
        val lineCount = panelInfo.lineCount

        val displayLabel = if (accountLabel.isNotBlank()) "$providerLabel · $accountLabel" else providerLabel
        val nameLabel = JBLabel(displayLabel).apply {
            if (icon != null) this.icon = icon
            alignmentX = 0f
        }
        add(nameLabel)
        add(Box.createVerticalStrut(JBUI.scale(4)))

        val entryWidgets = mutableListOf<EntryWidgets>()
        for (i in 0 until entryCount) {
            val pctLabel = JBLabel(" ").apply {
                foreground = SECONDARY_COLOR
                font = font.deriveFont(font.size2D - 1f)
                alignmentX = 0f
            }
            add(pctLabel)
            add(Box.createVerticalStrut(JBUI.scale(4)))

            val progressBar = JProgressBar(0, 100).apply {
                value = 0
                isStringPainted = false
                foreground = COLOR_OK
                border = JBUI.Borders.empty()
                maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(4))
                preferredSize = Dimension(Int.MAX_VALUE, JBUI.scale(4))
                alignmentX = 0f
            }
            add(progressBar)

            entryWidgets.add(EntryWidgets(pctLabel, progressBar))

            // Add spacing between entries if not the last one
            if (i < entryCount - 1) {
                add(Box.createVerticalStrut(JBUI.scale(8)))
            }
        }

        // Add spacing between entries and lines if both exist
        if (entryCount > 0 && lineCount > 0) {
            add(Box.createVerticalStrut(JBUI.scale(8)))
        }

        val lineWidgets = mutableListOf<LineWidgets>()
        for (i in 0 until lineCount) {
            val lineLabel = JBLabel(" ").apply {
                foreground = SECONDARY_COLOR
                font = font.deriveFont(font.size2D - 1f)
                alignmentX = 0f
            }
            add(lineLabel)

            lineWidgets.add(LineWidgets(lineLabel))

            // Add spacing between lines if not the last one
            if (i < lineCount - 1) {
                add(Box.createVerticalStrut(JBUI.scale(4)))
            }
        }

        return ProviderWidgets(entryWidgets, lineWidgets)
    }

    private fun updateWidgets(widgets: ProviderWidgets, usage: ProviderUsage) {
        widgets.entries.forEachIndexed { index, entryWidgets ->
            val entry = usage.entries.getOrNull(index)
            if (entry != null) {
                val percentageUsed = entry.percentageUsed.coerceIn(0f, 100f)
                val percentageUsedInt = percentageUsed.roundToInt().coerceIn(0, 100)
                val subtitle = entry.subtitle?.ifBlank { null }

                entryWidgets.pctLabel.text = buildString {
                    append(percentageUsedInt)
                    append(" %")
                    if (subtitle != null) {
                        append(" · ")
                        append(subtitle)
                    }
                }
                entryWidgets.pctLabel.foreground = SECONDARY_COLOR
                entryWidgets.progressBar.value = percentageUsedInt
                entryWidgets.progressBar.foreground = quotaColor(percentageUsed)
            } else {
                // No data for this entry slot
                entryWidgets.pctLabel.text = " "
                entryWidgets.progressBar.value = 0
            }
        }

        widgets.lines.forEachIndexed { index, lineWidgets ->
            val line = usage.lines.getOrNull(index)
            lineWidgets.lineLabel.text = line?.text?.ifBlank { " " } ?: " "
            lineWidgets.lineLabel.foreground = SECONDARY_COLOR
        }
    }

    private fun showWidgetError(widgets: ProviderWidgets, error: String) {
        widgets.entries.forEach { entryWidgets ->
            entryWidgets.pctLabel.text = error
            entryWidgets.pctLabel.foreground = ERROR_COLOR
            entryWidgets.progressBar.value = 0
        }
        widgets.lines.forEach { lineWidgets ->
            lineWidgets.lineLabel.text = " "
            lineWidgets.lineLabel.foreground = ERROR_COLOR
        }
    }

    private fun createSeparator(): JSeparator {
        return JSeparator(SwingConstants.HORIZONTAL).apply {
            maximumSize = Dimension(Int.MAX_VALUE, 1)
            foreground = JBColor.border()
        }
    }
}

private val SECONDARY_COLOR = JBColor(0x787878, 0x999999)
private val ERROR_COLOR = JBColor(0xD84A4A, 0xE06C75)
private val COLOR_OK = JBColor(0x3574F0, 0x3574F0)

private fun quotaColor(percentageUsed: Float) = when {
    percentageUsed >= 100f -> JBColor(0xD84A4A, 0xE06C75)
    percentageUsed >= 80f -> JBColor(0xD9A343, 0xD19A66)
    else -> COLOR_OK
}

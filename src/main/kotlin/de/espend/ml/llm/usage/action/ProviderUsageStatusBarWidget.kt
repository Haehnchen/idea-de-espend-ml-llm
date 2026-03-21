package de.espend.ml.llm.usage.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.usage.ProviderUsageService
import de.espend.ml.llm.usage.UsagePlatformRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.MouseEvent
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Status bar widget displaying AI provider usage as icons + percentages for all entries.
 * Only shows accounts with "showInStatusBar" enabled (toggled per-account in the usage popup).
 * Refreshes every 2 minutes, fetches immediately on install.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ProviderUsageStatusBarWidget(@Suppress("unused") project: Project) : CustomStatusBarWidget {

    private val contentPanel = createContentPanel()
    private var scheduledFuture: ScheduledFuture<*>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Labels maintained per account: icon label + text label
    private data class AccountLabels(val iconLabel: JBLabel, val textLabel: JBLabel)
    private val accountLabels = mutableListOf<AccountLabels>()

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = NoClickPresentation()

    override fun getComponent(): JComponent = contentPanel

    override fun install(statusBar: StatusBar) {
        scheduledFuture?.cancel(false)
        fetchAndUpdate()
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { fetchAndUpdate() },
            REFRESH_INTERVAL_MIN, REFRESH_INTERVAL_MIN, TimeUnit.MINUTES
        )
    }

    override fun dispose() {
        scheduledFuture?.cancel(false)
        scheduledFuture = null
        scope.cancel()
    }

    /** Called by the popup panel when pin state changes or by the settings configurable. */
    fun refresh() {
        fetchAndUpdate()
    }

    private fun createContentPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(0, JBUI.scale(4))
        }
    }

    private fun fetchAndUpdate() {
        scope.launch {
            val service = ProviderUsageService.getInstance()
            val registry = UsagePlatformRegistry.getInstance()

            // Only accounts with showInStatusBar enabled
            val statusBarAccounts = service.getSupportedAccounts()
                .filter { registry.isShowInStatusBar(it.id) }

            data class AccountResult(
                val providerId: String,
                val accountId: String,
                val displayText: String
            )

            val results = mutableListOf<AccountResult>()
            for (account in statusBarAccounts) {
                try {
                    val response = service.fetchUsage(account)
                    if (response.usage != null) {
                        val displayText = when {
                            response.usage.entries.isNotEmpty() ->
                                response.usage.entries
                                    .map { it.percentageUsed.toInt().coerceIn(0, 999) }
                                    .joinToString(" \u00B7 ") { "$it%" }
                            response.usage.lines.isNotEmpty() ->
                                response.usage.lines.first().text
                            else -> continue
                        }
                        results.add(AccountResult(account.providerId, account.id, displayText))
                    }
                } catch (_: Exception) {
                    // Skip silently
                }
            }


            withContext(Dispatchers.Main) {
                rebuildPanel(results.map { result ->
                    val provider = service.getProvider(result.providerId)
                    val icon = provider?.providerInfo?.icon?.let { PluginIcons.scaleIcon(it, JBUI.scale(13)) }
                    Triple(result.accountId, icon, result.displayText)
                })
            }
        }
    }

    private fun rebuildPanel(items: List<Triple<String, javax.swing.Icon?, String>>) {
        contentPanel.removeAll()
        accountLabels.clear()

        items.forEachIndexed { index, (_, icon, displayText) ->
            if (index > 0) {
                contentPanel.add(JBLabel(" \u00B7 ").apply {
                    foreground = JBColor(0xAAAAAA, 0x666666)
                    font = font.deriveFont(font.size2D - 1f)
                    alignmentY = java.awt.Component.CENTER_ALIGNMENT
                })
            }

            val iconLabel = JBLabel(icon).apply {
                border = JBUI.Borders.emptyRight(JBUI.scale(4))
                alignmentY = java.awt.Component.CENTER_ALIGNMENT
            }
            val textLabel = JBLabel(displayText).apply {
                font = font.deriveFont(font.size2D - 1f)
                foreground = JBColor(0x555555, 0xAAAAAA)
                alignmentY = java.awt.Component.CENTER_ALIGNMENT
            }

            contentPanel.add(iconLabel)
            contentPanel.add(textLabel)
            accountLabels.add(AccountLabels(iconLabel, textLabel))
        }

        contentPanel.revalidate()
        contentPanel.repaint()
    }

    companion object {
        const val WIDGET_ID = "de.espend.ml.llm.ProviderUsageWidget"
        private const val REFRESH_INTERVAL_MIN = 2L

        /**
         * Refresh the statusbar widget for a given project after config changes.
         * Handles both widget availability (install/remove) and data refresh.
         */
        fun refreshWidget(project: Project) {
            @Suppress("IncorrectServiceRetrieving") // StatusBarWidgetsManager is @Service(PROJECT) — false positive
            project.getService(StatusBarWidgetsManager::class.java)
                ?.updateWidget(ProviderUsageStatusBarWidgetFactory::class.java)
            com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project)
                ?.getWidget(WIDGET_ID)
                ?.let { (it as? ProviderUsageStatusBarWidget)?.refresh() }
        }
    }

    private class NoClickPresentation : StatusBarWidget.WidgetPresentation {
        override fun getClickConsumer(): com.intellij.util.Consumer<MouseEvent>? = null
        override fun getTooltipText(): String? = null
    }
}

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
 * Status bar widget displaying AI provider usage as icons + percentages.
 *
 * Registers a cache listener on [ProviderUsageService] so it rebuilds the display
 * whenever any source (popup panel, scheduled fetch, etc.) writes fresh data.
 * A 2-minute scheduler triggers background fetches for stale status-bar accounts.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ProviderUsageStatusBarWidget(@Suppress("unused") project: Project) : CustomStatusBarWidget {

    private val contentPanel = createContentPanel()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var removeCacheListener: (() -> Unit)? = null

    private data class AccountLabels(val iconLabel: JBLabel, val textLabel: JBLabel)
    private val accountLabels = mutableListOf<AccountLabels>()

    override fun ID(): String = WIDGET_ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = NoClickPresentation()
    override fun getComponent(): JComponent = contentPanel

    override fun install(statusBar: StatusBar) {
        println("[StatusBar] registering cache listener")
        removeCacheListener = ProviderUsageService.getInstance().addCacheListener {
            rebuildFromCache()
        }

        // Show whatever is already cached, then kick off a fetch if stale
        rebuildFromCache()
        triggerFetch()

        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { triggerFetch() },
            REFRESH_INTERVAL_MIN, REFRESH_INTERVAL_MIN, TimeUnit.MINUTES
        )
    }

    override fun dispose() {
        println("[StatusBar] unregistering cache listener")
        removeCacheListener?.invoke()
        removeCacheListener = null
        scheduledFuture?.cancel(false)
        scheduledFuture = null
        scope.cancel()
    }

    /** Called externally (e.g. settings configurable) to force a fresh fetch. */
    fun refresh() {
        triggerFetch()
    }

    private fun createContentPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(0, JBUI.scale(4))
        }
    }

    /**
     * Rebuild the status bar display from whatever is currently in the cache.
     * Called by the cache listener — no network access.
     */
    private fun rebuildFromCache() {
        scope.launch {
            val service = ProviderUsageService.getInstance()
            val registry = UsagePlatformRegistry.getInstance()

            val items = service.getSupportedAccounts()
                .filter { registry.isShowInStatusBar(it.id) }
                .mapNotNull { account ->
                    val response = service.getCachedResponse(account.id) ?: return@mapNotNull null
                    val usage = response.usage ?: return@mapNotNull null
                    val displayText = when {
                        usage.entries.isNotEmpty() ->
                            usage.entries
                                .map { it.percentageUsed.toInt().coerceIn(0, 999) }
                                .joinToString(" \u00B7 ") { "$it%" }
                        usage.lines.isNotEmpty() -> usage.lines.first().text
                        else -> return@mapNotNull null
                    }
                    val icon = service.getProvider(account.providerId)
                        ?.providerInfo?.icon
                        ?.let { PluginIcons.scaleIcon(it, JBUI.scale(13)) }
                    Triple(account.id, icon, displayText)
                }

            if (items.isEmpty()) return@launch

            println("[StatusBar] cache update received: ${items.size} account(s) → ${items.joinToString { (id, _, text) -> "$id=$text" }}")

            withContext(Dispatchers.Main) {
                rebuildPanel(items)
            }
        }
    }

    /**
     * Fetch stale status-bar accounts in the background.
     * [updateCache] on the service fires the cache listener which triggers [rebuildFromCache].
     */
    private fun triggerFetch() {
        scope.launch {
            val service = ProviderUsageService.getInstance()
            val registry = UsagePlatformRegistry.getInstance()

            val staleAccounts = service.getSupportedAccounts()
                .filter { registry.isShowInStatusBar(it.id) }
                .filter { !service.isCacheValidForAccount(it.id, ProviderUsageService.STATUS_BAR_CACHE_TTL_MS) }

            service.fetchAndUpdateAccounts(staleAccounts) // updates cache → fires listener → rebuildFromCache()
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

        fun refreshWidget(project: Project) {
            @Suppress("IncorrectServiceRetrieving")
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

package de.espend.ml.llm.usage.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.options.ShowSettingsUtil
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
import de.espend.ml.llm.usage.ui.UsageSettingsConfigurable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.MouseAdapter
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
class ProviderUsageStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    private val contentPanel = createContentPanel()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var removeCacheListener: (() -> Unit)? = null

    private data class AccountLabels(val iconLabel: JBLabel, val textLabel: JBLabel)
    private val accountLabels = mutableListOf<AccountLabels>()
    private data class StatusBarItem(val icon: javax.swing.Icon?, val text: String)

    override fun ID(): String = WIDGET_ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = NoPresentation()
    override fun getComponent(): JComponent = contentPanel

    override fun install(statusBar: StatusBar) {
        contentPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = showDropupMenu()
        })
        contentPanel.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)

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

            val statusBarAccounts = service.getSupportedAccounts()
                .filter { registry.isShowInStatusBar(it.id) }

            if (statusBarAccounts.isEmpty()) {
                return@launch
            }

            val items = statusBarAccounts
                .mapNotNull { account ->
                    val response = service.getCachedResponse(account.id) ?: return@mapNotNull null
                    val provider = service.getProvider(account.providerId)
                    val icon = provider?.providerInfo?.icon
                        ?.let { PluginIcons.scaleIcon(it, JBUI.scale(13)) }
                    if (response.error != null) {
                        val entryCount = provider?.getAccountPanelInfo(account)?.usageEntryCount ?: 1
                        val errText = (1..entryCount.coerceAtLeast(1))
                            .joinToString(" \u00B7 ") { "err" }
                        StatusBarItem(icon, errText)
                    } else {
                        val usage = response.usage ?: return@mapNotNull null
                        val displayText = when {
                            usage.entries.isNotEmpty() ->
                                usage.entries
                                    .map { it.percentageUsed.toInt().coerceIn(0, 999) }
                                    .joinToString(" \u00B7 ") { "$it%" }
                            usage.lines.isNotEmpty() -> usage.lines.first().text
                            else -> return@mapNotNull null
                        }
                        StatusBarItem(icon, displayText)
                    }
                }

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

    private fun rebuildPanel(items: List<StatusBarItem>) {
        contentPanel.removeAll()
        accountLabels.clear()

        items.forEachIndexed { index, item ->
            if (index > 0) {
                contentPanel.add(JBLabel(" \u00B7 ").apply {
                    foreground = JBColor(0xAAAAAA, 0x666666)
                    font = font.deriveFont(font.size2D - 1f)
                    alignmentY = java.awt.Component.CENTER_ALIGNMENT
                })
            }

            val iconLabel = JBLabel(item.icon).apply {
                border = JBUI.Borders.emptyRight(JBUI.scale(4))
                alignmentY = java.awt.Component.CENTER_ALIGNMENT
            }
            val textLabel = JBLabel(item.text).apply {
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

    private fun showDropupMenu() {
        val service = ProviderUsageService.getInstance()
        val registry = UsagePlatformRegistry.getInstance()
        val group = DefaultActionGroup()
        val menuComponent = ActionManager.getInstance().createActionPopupMenu("LlmStatusBar", group).component

        registry.getAccountStates()
            .filter { it.isEnabled }
            .forEach { account ->
                val provider = service.getProvider(account.providerId)
                val icon = provider?.providerInfo?.icon
                    ?.let { PluginIcons.scaleIcon(it, JBUI.scale(12)) }
                val providerName = provider?.providerInfo?.providerName ?: account.providerId
                val label = if (account.label.isBlank()) {
                    providerName
                } else {
                    val name = account.label.let { if (it.length > 15) it.take(14) + "…" else it }
                    "$name ($providerName)"
                }
                val accountId = account.id
                group.add(object : ToggleAction(label, null, icon) {
                    override fun getActionUpdateThread() = ActionUpdateThread.BGT
                    override fun isSelected(e: AnActionEvent) =
                        registry.getAccountStates().find { it.id == accountId }?.enableStatusBar ?: false
                    override fun setSelected(e: AnActionEvent, state: Boolean) {
                        menuComponent.isVisible = false
                        registry.setEnableStatusBar(accountId, state)
                        rebuildFromCache()
                        refreshWidget(project)
                    }
                })
            }

        group.addSeparator()

        group.add(object : AnAction("Settings", null, AllIcons.General.Settings) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) {
                menuComponent.isVisible = false
                ShowSettingsUtil.getInstance().showSettingsDialog(null, UsageSettingsConfigurable::class.java)
            }
        })

        menuComponent.show(contentPanel, 0, 0)
        val loc = contentPanel.locationOnScreen
        menuComponent.setLocation(
            loc.x + contentPanel.width - menuComponent.width,
            loc.y - menuComponent.height
        )
    }

    private class NoPresentation : StatusBarWidget.WidgetPresentation {
        override fun getClickConsumer(): com.intellij.util.Consumer<MouseEvent>? = null
        override fun getTooltipText(): String? = null
    }
}

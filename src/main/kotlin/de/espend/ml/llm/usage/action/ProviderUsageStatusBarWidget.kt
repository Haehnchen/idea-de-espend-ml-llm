package de.espend.ml.llm.usage.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
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
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JPanel

/**
 * Status bar widget displaying AI provider usage as icons + percentages.
 *
 * Uses [EditorBasedStatusBarPopup] so popup positioning follows IntelliJ's
 * standard status bar behavior and opens above the widget.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ProviderUsageStatusBarWidget(
    project: Project
) : EditorBasedStatusBarPopup(project, false) {

    private val contentPanel = createContentPanel()
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var removeCacheListener: (() -> Unit)? = null
    private var currentItems: List<StatusBarItem> = emptyList()

    private data class StatusBarItem(val icon: Icon?, val text: String)

    override fun ID(): String = WIDGET_ID

    override fun createInstance(project: Project): StatusBarWidget = ProviderUsageStatusBarWidget(project)

    override fun createComponent(): JPanel = contentPanel

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)

        removeCacheListener = ProviderUsageService.getInstance().addCacheListener {
            rebuildFromCache()
        }

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
        fetchScope.cancel()
        super.dispose()
    }

    override fun getWidgetState(file: VirtualFile?): WidgetState {
        if (!UsagePlatformRegistry.getInstance().hasAnyStatusBarAccount()) {
            return WidgetState.HIDDEN
        }

        return WidgetState("AI Provider Usage", "", true)
    }

    override fun updateComponent(state: WidgetState) {
        contentPanel.toolTipText = state.toolTip
        rebuildPanel(currentItems)
    }

    override fun createPopup(context: DataContext): ListPopup {
        val service = ProviderUsageService.getInstance()
        val registry = UsagePlatformRegistry.getInstance()
        val group = DefaultActionGroup()

        registry.getAccountStates()
            .filter { it.isEnabled }
            .forEach { account ->
                val provider = service.getProvider(account.providerId)
                val icon = provider?.providerInfo?.icon
                    ?.let { PluginIcons.scaleIcon(it, 12) }
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
                        registry.setEnableStatusBar(accountId, state)
                        rebuildFromCache()
                        refreshAllWidgets()
                    }
                })
            }

        group.addSeparator()

        group.add(object : AnAction("Settings", null, AllIcons.General.Settings) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun actionPerformed(e: AnActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, UsageSettingsConfigurable::class.java)
            }
        })

        return JBPopupFactory.getInstance().createActionGroupPopup(
            "AI Provider Usage",
            group,
            context,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false
        )
    }

    /** Called externally (e.g. settings configurable) to force a fresh fetch. */
    fun refresh() {
        triggerFetch()
        update()
    }

    private fun createContentPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(0, JBUI.scale(4))
        }
    }

    private fun rebuildFromCache() {
        fetchScope.launch {
            val service = ProviderUsageService.getInstance()
            val registry = UsagePlatformRegistry.getInstance()

            val statusBarAccounts = service.getSupportedAccounts()
                .filter { registry.isShowInStatusBar(it.id) }

            val items = statusBarAccounts
                .mapNotNull { account ->
                    val response = service.getCachedResponse(account.id) ?: return@mapNotNull null
                    val provider = service.getProvider(account.providerId)
                    val icon = provider?.providerInfo?.icon
                        ?.let { PluginIcons.scaleIcon(it, 13) }

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
                currentItems = items
                update()
            }
        }
    }

    private fun triggerFetch() {
        fetchScope.launch {
            val service = ProviderUsageService.getInstance()
            val registry = UsagePlatformRegistry.getInstance()

            val staleAccounts = service.getSupportedAccounts()
                .filter { registry.isShowInStatusBar(it.id) }
                .filter { !service.isCacheValidForAccount(it.id, ProviderUsageService.STATUS_BAR_CACHE_TTL_MS) }

            service.fetchAndUpdateAccounts(staleAccounts)
        }
    }

    private fun rebuildPanel(items: List<StatusBarItem>) {
        contentPanel.removeAll()

        items.forEachIndexed { index, item ->
            if (index > 0) {
                contentPanel.add(JBLabel(" \u00B7 ").apply {
                    foreground = JBColor(0xAAAAAA, 0x666666)
                    font = font.deriveFont(font.size2D - 1f)
                    alignmentY = java.awt.Component.CENTER_ALIGNMENT
                })
            }

            contentPanel.add(JBLabel(item.icon).apply {
                border = JBUI.Borders.emptyRight(JBUI.scale(4))
                alignmentY = java.awt.Component.CENTER_ALIGNMENT
            })

            contentPanel.add(JBLabel(item.text).apply {
                font = font.deriveFont(font.size2D - 1f)
                foreground = JBColor(0x555555, 0xAAAAAA)
                alignmentY = java.awt.Component.CENTER_ALIGNMENT
            })
        }

        contentPanel.revalidate()
        contentPanel.repaint()
    }

    companion object {
        const val WIDGET_ID = "de.espend.ml.llm.ProviderUsageWidget"
        private const val REFRESH_INTERVAL_MIN = 2L

        fun refreshAllWidgets() {
            ProjectManager.getInstance().openProjects.forEach { project ->
                refreshWidget(project)
            }
        }

        fun refreshWidget(project: Project) {
            @Suppress("IncorrectServiceRetrieving")
            project.getService(StatusBarWidgetsManager::class.java)
                ?.updateWidget(ProviderUsageStatusBarWidgetFactory::class.java)
            com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project)
                ?.getWidget(WIDGET_ID)
                ?.let { (it as? ProviderUsageStatusBarWidget)?.refresh() }
        }
    }
}

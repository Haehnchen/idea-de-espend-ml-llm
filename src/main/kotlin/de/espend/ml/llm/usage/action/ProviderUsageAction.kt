package de.espend.ml.llm.usage.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.usage.ProviderUsagePanel
import de.espend.ml.llm.usage.ProviderUsageService
import de.espend.ml.llm.usage.UsageAccountConfig
import de.espend.ml.llm.usage.UsagePlatformRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.Dimension
import java.awt.Point
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Action for displaying provider usage in a popup.
 *
 * Maintains a pre-rendered {@link ProviderUsagePanel} that is continuously updated in the
 * background, so the popup opens immediately with current data — no loading state.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ProviderUsageAction : AnAction(), DumbAware {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scheduledFuture: ScheduledFuture<*>? = null

    // Pre-rendered popup panel — always up to date, shown immediately on click.
    private var cachedPanel: ProviderUsagePanel? = null
    private var cachedProviders: List<UsageAccountConfig>? = null

    init {
        refreshCachedPanel()
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { refreshCachedPanel() },
            REFRESH_INTERVAL_MIN, REFRESH_INTERVAL_MIN, TimeUnit.MINUTES
        )
    }

    /**
     * Periodically refresh the pre-rendered panel's data so it's always current.
     */
    private fun refreshCachedPanel() {
        val service = ProviderUsageService.getInstance()
        val providers = service.getSupportedAccounts()
        if (providers.isEmpty()) {
            cachedPanel = null
            cachedProviders = null
            return
        }

        // Rebuild panel only when provider list changes
        val panel = if (cachedProviders != providers) {
            val p = ProviderUsagePanel(providers, service, scope)
            cachedPanel = p
            cachedProviders = providers.toList()
            p
        } else {
            cachedPanel ?: return
        }

        // Create a transient popup reference for pack() to work during refresh
        val transientPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setResizable(false)
            .setMovable(false)
            .setRequestFocus(false)
            .setMinSize(Dimension(JBUI.scale(POPUP_WIDTH), 0))
            .createPopup()

        panel.start(transientPopup, null)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val service = ProviderUsageService.getInstance()
        val providers = service.getSupportedAccounts()
        val popupWidth = JBUI.scale(POPUP_WIDTH)

        // Use the pre-rendered panel — it's already loaded with current data.
        val panel = cachedPanel
        val actualPanel = if (panel != null && cachedProviders == providers) {
            panel
        } else {
            // Fallback: rebuild panel if providers changed
            val newPanel = ProviderUsagePanel(providers, service, scope)
            cachedPanel = newPanel
            cachedProviders = providers.toList()
            newPanel
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(actualPanel, null)
            .setResizable(false)
            .setMovable(false)
            .setRequestFocus(true)
            .setMinSize(Dimension(popupWidth, 0))
            .createPopup()

        actualPanel.start(popup, e.project)

        val component = e.inputEvent?.component
        if (component != null) {
            popup.show(RelativePoint(component, Point(component.width - popupWidth, component.height)))
        } else {
            popup.showInBestPositionFor(e.dataContext)
        }
    }

    override fun update(e: AnActionEvent) {
        val providers = ProviderUsageService.getInstance().getSupportedAccounts()
        val rtkEnabled = UsagePlatformRegistry.getInstance().state.showRtkStats
        e.presentation.icon = PluginIcons.USAGE
        e.presentation.isEnabledAndVisible = providers.isNotEmpty() || rtkEnabled
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private companion object {
        const val POPUP_WIDTH = 240
        const val REFRESH_INTERVAL_MIN = 2L
    }
}

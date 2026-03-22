package de.espend.ml.llm.usage.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.usage.ProviderUsagePanel
import de.espend.ml.llm.usage.ProviderUsageService
import de.espend.ml.llm.usage.UsagePlatformRegistry
import java.awt.Dimension
import java.awt.Point

/**
 * Action for displaying provider usage in a popup.
 *
 * The panel is created (or reused when providers haven't changed) on each click.
 * Data is loaded on open: cached values are shown immediately; a background fetch
 * is triggered only when the cache is older than [ProviderUsageService.PANEL_CACHE_TTL_MS].
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ProviderUsageAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val popupWidth = JBUI.scale(POPUP_WIDTH)
        val actualPanel = ProviderUsagePanel()

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(actualPanel, null)
            .setResizable(false)
            .setMovable(false)
            .setRequestFocus(true)
            .setMinSize(Dimension(popupWidth, 0))
            .createPopup()

        actualPanel.start(popup)

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
    }
}

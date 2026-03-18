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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.Dimension
import java.awt.Point

/**
 * Action for displaying provider usage in a popup
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ProviderUsageAction : AnAction(), DumbAware {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun actionPerformed(e: AnActionEvent) {
        val service = ProviderUsageService.getInstance()
        val providers = service.getSupportedAccounts()
        val popupWidth = JBUI.scale(POPUP_WIDTH)

        val panel = ProviderUsagePanel(providers, service, scope)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setResizable(false)
            .setMovable(false)
            .setRequestFocus(true)
            .setMinSize(Dimension(popupWidth, 0))
            .createPopup()

        panel.start(popup, e.project)

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

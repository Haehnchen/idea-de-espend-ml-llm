package de.espend.ml.llm.usage.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.ProjectResolutionUtils
import de.espend.ml.llm.usage.ProviderUsagePanel
import de.espend.ml.llm.usage.ProviderUsageService
import de.espend.ml.llm.usage.UsagePlatformRegistry
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Action for displaying provider usage in a popup.
 *
 * The panel is created (or reused when providers haven't changed) on each click.
 * Data is loaded on open: cached values are shown immediately; a background fetch
 * is triggered only when the cache is older than [ProviderUsageService.PANEL_CACHE_TTL_MS].
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ProviderUsageAction : AnAction(), DumbAware, CustomComponentAction {

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val popupWidth = JBUI.scale(POPUP_WIDTH)
        val anchor = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return
        val actualPanel = ProviderUsagePanel(ProjectResolutionUtils.resolveProject(e))

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(actualPanel, null)
            .setResizable(false)
            .setMovable(false)
            .setRequestFocus(true)
            .setMinSize(Dimension(popupWidth, 0))
            .createPopup()

        actualPanel.start(popup)
        popup.showUnderneathOf(anchor)
    }

    override fun update(e: AnActionEvent) {
        val providers = ProviderUsageService.getInstance().getSupportedAccounts()
        val state = UsagePlatformRegistry.getInstance().state
        val toolPanelEnabled = state.showRtkStats || state.showTokscaleStats
        e.presentation.icon = PluginIcons.USAGE
        e.presentation.isEnabledAndVisible = providers.isNotEmpty() || toolPanelEnabled
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private companion object {
        const val POPUP_WIDTH = 240
    }
}

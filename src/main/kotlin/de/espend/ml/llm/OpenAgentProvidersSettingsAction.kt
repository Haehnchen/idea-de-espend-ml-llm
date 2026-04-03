package de.espend.ml.llm

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction

/**
 * Action to open Agent Providers settings from the chat toolbar or agent dropdown.
 */
class OpenAgentProvidersSettingsAction : DumbAwareAction(
    "Agent Providers",
    "Open Agent Providers settings",
    PluginIcons.AI_PROVIDER_16
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = ProjectResolutionUtils.resolveProject(e) ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentSettingsConfigurable::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

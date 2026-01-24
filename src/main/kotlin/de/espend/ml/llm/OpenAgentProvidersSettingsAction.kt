package de.espend.ml.llm

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware

/**
 * Action to open Agent Providers settings from the chat toolbar.
 */
class OpenAgentProvidersSettingsAction : AnAction(), DumbAware {

    init {
        val presentation = templatePresentation
        presentation.icon = PluginIcons.AI_PROVIDER_16
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentSettingsConfigurable::class.java)
    }
}

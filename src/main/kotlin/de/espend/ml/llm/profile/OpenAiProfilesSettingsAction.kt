package de.espend.ml.llm.profile

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.ProjectResolutionUtils
import de.espend.ml.llm.profile.ui.AiProfilesSettingsConfigurable

class OpenAiProfilesSettingsAction : DumbAwareAction(
    "Agent Profiles",
    "Open Agent Profiles settings",
    PluginIcons.AI_PROVIDER_16
) {
    override fun actionPerformed(e: AnActionEvent) {
        openAiProfilesSettings(ProjectResolutionUtils.resolveProject(e))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal fun openAiProfilesSettings(project: Project?) {
    ShowSettingsUtil.getInstance().showSettingsDialog(
        project,
        AiProfilesSettingsConfigurable::class.java
    )
}

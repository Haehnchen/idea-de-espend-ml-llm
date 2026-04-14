package de.espend.ml.llm.profile

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.ProjectResolutionUtils
import de.espend.ml.llm.profile.ui.AiProfilesSettingsConfigurable

class OpenAiProfilesSettingsAction : DumbAwareAction(
    "AI Profiles",
    "Open AI Profiles settings",
    PluginIcons.AI_PROVIDER_16
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = ProjectResolutionUtils.resolveProject(e) ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, AiProfilesSettingsConfigurable::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

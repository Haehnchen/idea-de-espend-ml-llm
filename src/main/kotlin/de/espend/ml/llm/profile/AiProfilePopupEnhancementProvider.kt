package de.espend.ml.llm.profile

import com.intellij.ml.llm.core.chat.ui.AgentPopupEnhancementProvider
import com.intellij.ml.llm.core.chat.ui.AgentPopupFirstLevelEntry
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import de.espend.ml.llm.PluginIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.swing.Icon

class AiProfilePopupEnhancementProvider : AgentPopupEnhancementProvider {
    override fun handlesAgent(agentId: String): Boolean {
        return AiProfileRegistry.getInstance().containsProfile(agentId)
    }

    override fun getFirstLevelActions(): List<AnAction> {
        return listOf(OpenAiProfilesSettingsAction())
    }

    override fun getFirstLevelEntries(project: Project): List<AgentPopupFirstLevelEntry> {
        return listOf(
            AgentPopupFirstLevelEntry(
                ENTRY_AGENT_PROFILES,
                "Agent Profiles",
                PluginIcons.AI_PROVIDER_16,
                "Open Agent Profiles settings"
            )
        )
    }

    override fun handleFirstLevelEntrySelection(entryId: String, project: Project): Boolean {
        if (entryId != ENTRY_AGENT_PROFILES) {
            return false
        }

        openAiProfilesSettings(project)
        return true
    }

    override fun hasUpdate(agentId: String): Boolean = false

    override fun getBadgedIcon(agentId: String): Icon? = null

    override fun createBadgedIcon(baseIcon: Icon): Icon = baseIcon

    override fun getUpdateVersion(agentId: String): String? = null

    override fun getUpdateTooltip(agentId: String): String? = null

    override fun canDelete(agentId: String): Boolean = false

    override suspend fun deleteAgent(agentId: String, project: Project) {}

    override fun updateAgent(agentId: String, project: Project?) {}

    override fun isAgentReady(agentId: String, project: Project): Boolean = true

    override fun getNotReadyReason(agentId: String, project: Project): String? = null

    override fun getAgentReadinessFlow(agentId: String): Flow<Boolean> = flowOf(true)

    private companion object {
        const val ENTRY_AGENT_PROFILES = "de.espend.ml.llm.agentProfiles"
    }
}

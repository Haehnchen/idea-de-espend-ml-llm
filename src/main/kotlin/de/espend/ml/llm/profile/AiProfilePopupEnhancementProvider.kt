package de.espend.ml.llm.profile

import com.intellij.ml.llm.core.chat.ui.AgentPopupEnhancementProvider
import com.intellij.openapi.actionSystem.AnAction
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

    override fun hasUpdate(agentId: String): Boolean = false

    override fun getBadgedIcon(agentId: String): Icon? = null

    override fun createBadgedIcon(baseIcon: Icon): Icon = baseIcon

    override fun getUpdateVersion(agentId: String): String? = null

    override fun getUpdateTooltip(agentId: String): String? = null

    override fun canDelete(agentId: String): Boolean = false

    override suspend fun deleteAgent(agentId: String) {}

    override fun updateAgent(agentId: String, project: com.intellij.openapi.project.Project?) {}

    override fun isAgentReady(agentId: String): Boolean = true

    override fun getNotReadyReason(agentId: String): String? = null

    override fun getAgentReadinessFlow(agentId: String): Flow<Boolean> = flowOf(true)
}

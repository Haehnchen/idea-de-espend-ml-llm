package de.espend.ml.llm

import com.intellij.ml.llm.core.chat.ui.AgentPopupEnhancementProvider
import com.intellij.openapi.actionSystem.AnAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.swing.Icon

/**
 * Provides Agent Providers integration with the AI Assistant agent dropdown.
 * Adds "Agent Providers" action to the first level of the agent popup menu.
 */
class ProviderAgentPopupEnhancementProvider : AgentPopupEnhancementProvider {

    /**
     * Returns true if this provider handles the given agent ID.
     * We handle agents registered by our plugin (identified by checking AgentRegistry).
     */
    override fun handlesAgent(agentId: String): Boolean {
        return AgentRegistry.getInstance().agentConfigs.any { it.id == agentId }
    }

    /**
     * Returns actions to show in the first level of the agent popup.
     * This adds "Agent Providers" to the dropdown menu.
     */
    override fun getFirstLevelActions(): List<AnAction> {
        return listOf(OpenAgentProvidersSettingsAction())
    }

    // Default implementations for other interface methods

    override fun hasUpdate(agentId: String): Boolean = false

    override fun getBadgedIcon(agentId: String): Icon? = null

    override fun createBadgedIcon(baseIcon: Icon): Icon = baseIcon

    override fun getUpdateVersion(agentId: String): String? = null

    override fun getUpdateTooltip(agentId: String): String? = null

    override fun canDelete(agentId: String): Boolean = false

    override suspend fun deleteAgent(agentId: String) {
        // Not supported - agents are managed through settings
    }

    override fun updateAgent(agentId: String, project: com.intellij.openapi.project.Project?) {
        // Not supported - agents are managed through settings
    }

    override fun isAgentReady(agentId: String): Boolean = true

    override fun getNotReadyReason(agentId: String): String? = null

    override fun getAgentReadinessFlow(agentId: String): Flow<Boolean> = flowOf(true)
}

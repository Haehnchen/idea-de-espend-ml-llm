package de.espend.ml.llm.profile

import com.intellij.ml.llm.agents.acp.registry.AcpAgentId
import com.intellij.ml.llm.agents.acp.registry.AcpChatAgentExtension
import com.intellij.openapi.application.ApplicationManager
import javax.swing.Icon

class AiProfileAcpChatAgentExtension : AcpChatAgentExtension {
    override fun getIcon(agentId: AcpAgentId): Icon? {
        val registry = ApplicationManager.getApplication().getService(AiProfileRegistry::class.java)
            ?: return null
        return registry.getIconForAgent(agentId)
    }
}

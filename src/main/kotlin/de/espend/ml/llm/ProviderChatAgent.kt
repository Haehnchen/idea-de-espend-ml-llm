package de.espend.ml.llm

import com.intellij.ml.llm.agents.ChatAgent
import com.intellij.ml.llm.agents.acp.registry.DynamicAcpChatAgent
import com.intellij.ml.llm.core.chat.messages.ChatMessage
import com.intellij.ml.llm.core.chat.messages.UserMessage
import com.intellij.ml.llm.core.chat.session.ChatKind
import com.intellij.ml.llm.core.chat.session.ChatSession
import com.intellij.ml.llm.core.chat.ui.AttachmentKindsProvider
import com.intellij.openapi.project.Project
import javax.swing.Icon

/**
 * ChatAgent wrapper that provides a custom icon for a DynamicAcpChatAgent.
 */
class ProviderChatAgent(
    private val delegate: DynamicAcpChatAgent,
    private val customIcon: Icon
) : ChatAgent {
    override val id: String get() = delegate.id
    override val name: String get() = delegate.name
    override val icon: Icon get() = customIcon

    override fun isComposeBased(): Boolean = delegate.isComposeBased()
    override fun isAvailable(): Boolean = delegate.isAvailable()
    override fun isDisabled(): Boolean = delegate.isDisabled()
    override fun getAttachmentKindsProvider(): AttachmentKindsProvider = delegate.getAttachmentKindsProvider()

    override fun createAnswerMessage(project: Project, chat: ChatSession, userMessage: UserMessage, kind: ChatKind): ChatMessage =
        delegate.createAnswerMessage(project, chat, userMessage, kind)

    override suspend fun prepareAnswerMessage(project: Project, chat: ChatSession, answerMessage: ChatMessage) =
        delegate.prepareAnswerMessage(project, chat, answerMessage)

    override suspend fun serveAnswerMessage(project: Project, chat: ChatSession, answerMessage: ChatMessage) =
        delegate.serveAnswerMessage(project, chat, answerMessage)
}

/**
 * Configuration for an AI provider agent.
 * Persisted as XML.
 */
data class AgentConfig(
    var id: String = "",
    var provider: String = "",
    var apiKey: String = "",
    var baseUrl: String = "",
    var model: String = "",
    var isEnabled: Boolean = true,
    var executable: String = ""
)

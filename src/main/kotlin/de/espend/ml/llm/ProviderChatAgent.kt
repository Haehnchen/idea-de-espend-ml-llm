package de.espend.ml.llm

import com.intellij.ml.llm.agents.ChatAgent
import com.intellij.ml.llm.agents.acp.registry.DynamicAcpChatAgent
import com.intellij.ml.llm.core.chat.messages.ChatMessage
import com.intellij.ml.llm.core.chat.messages.UserMessage
import com.intellij.ml.llm.core.chat.session.ChatKind
import com.intellij.ml.llm.core.chat.session.ChatSession
import com.intellij.ml.llm.core.chat.ui.AvailableCommandsProvider
import com.intellij.ml.llm.core.chat.ui.AttachmentKindsProvider
import com.intellij.ml.llm.core.chat.ui.chat.input.chatModeSelector.InlineAction
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
    override val fullName: String get() = delegate.fullName
    override val icon: Icon get() = customIcon
    override val order: Int get() = delegate.order
    override val requiresAuthCheck: Boolean get() = delegate.requiresAuthCheck

    override fun getDescription(): String? = delegate.getDescription()
    override fun isComposeBased(): Boolean = delegate.isComposeBased()
    override fun isAvailable(): Boolean = delegate.isAvailable()
    override fun isDisabled(): Boolean = delegate.isDisabled()
    override fun isBraveModeSupported(): Boolean = delegate.isBraveModeSupported()
    override fun isSupportModelSwitching(): Boolean = delegate.isSupportModelSwitching()
    override fun isProvidesAcpLogs(): Boolean = delegate.isProvidesAcpLogs()
    override fun getAttachmentKindsProvider(): AttachmentKindsProvider = delegate.getAttachmentKindsProvider()
    override fun getAvailableCommandProvider(): AvailableCommandsProvider = delegate.getAvailableCommandProvider()
    override fun getMenuActions(): List<InlineAction> = delegate.getMenuActions()

    override suspend fun activate(project: Project) = delegate.activate(project)

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
    // pi provider format, for example "anthropic-messages" or "openai-completions"
    var format: String = "",
    var baseUrl: String = "",
    var model: String = "",
    var isEnabled: Boolean = true,
    var executable: String = ""
)

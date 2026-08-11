package de.espend.ml.llm.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.components.ActionLink
import java.awt.Component
import java.awt.Container
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Removes the AI Assistant's Feedback link from the chat footer.
 *
 * `com.intellij.ml.llm.shareFeedbackExtension`/
 * `AIAssistantShareFeedbackExtension` controls only `shareFeedback()`, not
 * visibility; `AIAssistantChatPanel` always creates the link and
 * `AIAssistantChatPanelUiFactory` inserts it.
 *
 * With no public visibility extension point, we scan the `AIAssistant` tool
 * window's Swing tree on the EDT, watch container additions for later chats,
 * remove the `ActionLink`, and retry for two seconds while the chat is created
 * lazily. The generated class is matched precisely, with `Feedback` text as
 * fallback.
 *
 * The former internal `com.intellij.ml.llm.chatPanelComponentProvider`
 * approach (`AiAssistantFeedbackPanelComponentProvider`) was removed because
 * `verifyPlugin` reported `INTERNAL_API_USAGES`; do not re-add it to
 * `plugin.xml` without accepting that failure.
 *
 * If the footer changes, inspect the decompiled `intellij.ml.llm.chat` module
 * and `intellij.ml.llm.chat.xml`, then update the matching logic.
 */
class AiAssistantFeedbackLinkHiderActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val connection = project.messageBus.connect()
        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun toolWindowShown(toolWindow: ToolWindow) {
                if (toolWindow.id == AI_ASSISTANT_TOOL_WINDOW_ID) {
                    scheduleRemoval(project)
                }
            }
        })

        scheduleRemoval(project)
    }

    private fun scheduleRemoval(project: Project) {
        SwingUtilities.invokeLater {
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(AI_ASSISTANT_TOOL_WINDOW_ID)
                ?: return@invokeLater

            observeComponentTree(toolWindow.component)
            var attempts = 0
            val timer = Timer(50, null)
            timer.addActionListener {
                if (project.isDisposed ||
                    removeFeedbackLink(toolWindow.component) ||
                    ++attempts >= MAX_ATTEMPTS
                ) {
                    timer.stop()
                }
            }
            timer.start()
        }
    }

    private fun observeComponentTree(component: Component) {
        if (component !is Container || !observedContainers.add(component)) {
            return
        }

        component.addContainerListener(object : ContainerAdapter() {
            override fun componentAdded(event: ContainerEvent) {
                observeComponentTree(event.child)
                removeFeedbackLink(event.container)
            }
        })
        component.components.forEach(::observeComponentTree)
    }

    private fun removeFeedbackLink(component: Component): Boolean {
        if (component is Container) {
            val feedbackLink = component.components
                .firstOrNull(::isFeedbackLink)
            if (feedbackLink != null) {
                component.remove(feedbackLink)
                component.revalidate()
                component.repaint()
                return true
            }

            for (child in component.components) {
                if (removeFeedbackLink(child)) {
                    return true
                }
            }
        }

        return false
    }

    private fun isFeedbackLink(component: Component): Boolean =
        component is ActionLink && (
            component.javaClass.name == FEEDBACK_LINK_CLASS ||
                component.text.equals(FEEDBACK_TEXT, ignoreCase = true)
            )

    private companion object {
        const val AI_ASSISTANT_TOOL_WINDOW_ID = "AIAssistant"
        const val FEEDBACK_LINK_CLASS =
            "com.intellij.ml.llm.core.chat.ui.chat.AIAssistantChatPanel\$shareFeedbackLabel\$1"
        const val FEEDBACK_TEXT = "Feedback"
        const val MAX_ATTEMPTS = 40
    }

    private val observedContainers = Collections.newSetFromMap(WeakHashMap<Container, Boolean>())
}

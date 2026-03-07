package de.espend.ml.llm.vcs

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.ui.awt.RelativePoint
import com.intellij.vcs.commit.CommitMessageUi
import com.intellij.vcs.commit.CommitWorkflowUi
import de.espend.ml.llm.AgentConfig
import de.espend.ml.llm.AgentRegistry
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.ProviderConfig
import kotlinx.coroutines.runBlocking
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.text.JTextComponent

/**
 * Action to generate commit messages using configured AI providers.
 */
class ProviderCommitMessageAction : AnAction(
    "Generate Commit Message Agent Provider",
    "Generate commit message with agent provider",
    PluginIcons.AI_PROVIDER_16
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val context = ActionContext.fromEvent(e)

        val validProviders = AgentRegistry.getInstance().agentConfigs
            .filter { it.isEnabled }
            .filter { isProviderValid(it) }

        val hasChanges = context.hasChanges()
        val hasTarget = context.getCommitMessageTarget() != null
        val hasValidProviders = validProviders.isNotEmpty()
        val tooltip = buildTooltip(validProviders)

        e.presentation.isVisible = hasValidProviders && project != null
        e.presentation.isEnabled = hasValidProviders && hasChanges && hasTarget
        e.presentation.icon = PluginIcons.AI_PROVIDER_16
        // Some commit UI placements render tooltip/title from action text instead of description,
        // so keep both in sync to ensure the dynamic provider tooltip is actually visible.
        e.presentation.description = tooltip
        e.presentation.text = tooltip
    }

    private fun isProviderValid(config: AgentConfig): Boolean {
        val providerInfo = ProviderConfig.findProviderInfo(config.provider)
        val hasBaseUrl = config.baseUrl.isNotEmpty() || providerInfo?.baseUrl != null
        val hasApiKey = config.apiKey.isNotEmpty()
        val hasModel = config.model.isNotEmpty() || providerInfo?.modelIds?.smart != null
        return hasBaseUrl && hasApiKey && hasModel
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val context = ActionContext.fromEvent(e)

        val changes = context.getChanges()
        if (changes.isNullOrEmpty()) {
            return
        }

        val target = context.getCommitMessageTarget() ?: return

        val validProviders = AgentRegistry.getInstance().agentConfigs
            .filter { it.isEnabled && isProviderValid(it) }

        if (validProviders.isEmpty()) return

        if (validProviders.size == 1) {
            generateCommitMessage(project, validProviders.first(), changes, target)
            return
        }

        val popup = createProviderPopup(validProviders, project, changes, target)
        val component = e.inputEvent?.component
        if (component != null) {
            popup.show(RelativePoint(component, Point(0, component.height)))
        } else {
            popup.showInBestPositionFor(e.dataContext)
        }
    }

    private fun createProviderPopup(
        providers: List<AgentConfig>,
        project: Project,
        changes: List<Change>,
        target: CommitMessageTarget
    ): ListPopup {
        val items = providers.map { config ->
            val label = ProviderConfig.findProviderInfo(config.provider)?.label ?: config.provider
            val model = config.model.takeIf { it.isNotEmpty() }
                ?: ProviderConfig.findProviderInfo(config.provider)?.modelIds?.smart
                ?: "default"
            val icon = PluginIcons.getIconForProvider(config.provider)
            ProviderItem(config, "$label ($model)", icon)
        }

        val step = object : BaseListPopupStep<ProviderItem>("Select Provider", items) {
            override fun getTextFor(value: ProviderItem): String = value.label
            override fun getIconFor(value: ProviderItem): Icon = value.icon

            override fun onChosen(selectedValue: ProviderItem, finalChoice: Boolean): PopupStep<*>? {
                generateCommitMessage(project, selectedValue.config, changes, target)
                return FINAL_CHOICE
            }
        }

        return JBPopupFactory.getInstance().createListPopup(step)
    }

    private fun generateCommitMessage(
        project: Project,
        config: AgentConfig,
        changes: List<Change>,
        target: CommitMessageTarget
    ) {
        val existingText = target.getText()
        val providerName = ProviderConfig.findProviderInfo(config.provider)?.label ?: config.provider

        object : Task.Backgroundable(project, "Generating commit message...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val result = runBlocking {
                    CommitMessageGenerator.generate(project, config, changes, existingText)
                }

                // Handle result in UI thread
                ApplicationManager.getApplication().invokeLater {
                    when (result) {
                        is ApiResult.Success -> {
                            val message = result.message.trim()
                            if (message.isNotEmpty()) {
                                target.setText(message)
                            } else {
                                target.setText("❌ Empty response from $providerName")
                            }
                        }
                        is ApiResult.Error -> {
                            target.setText("❌ ${result.error}")
                        }
                    }
                }
            }
        }.queue()
    }

    private fun buildTooltip(validProviders: List<AgentConfig>): String {
        return when {
            validProviders.size == 1 -> "Generate commit message with ${getProviderDisplayName(validProviders.first())}"
            validProviders.size > 1 -> "Generate commit message with agent provider"
            else -> "Generate commit message with agent provider"
        }
    }

    private fun getProviderDisplayName(config: AgentConfig): String {
        val providerLabel = ProviderConfig.findProviderInfo(config.provider)?.label ?: config.provider
        val model = config.model.takeIf { it.isNotEmpty() }
            ?: ProviderConfig.findProviderInfo(config.provider)?.modelIds?.smart
        return model?.let { "$providerLabel ($it)" } ?: providerLabel
    }

    private data class ActionContext(
        val project: Project?,
        val editor: Editor?,
        val commitWorkflowUi: CommitWorkflowUi?,
        val commitMessageControl: Any?,
        private val selectedChanges: Array<Change>?,
        private val selectedChangesInDetails: Array<Change>?,
        private val changes: Array<Change>?,
        private val changeLists: Array<ChangeList>?
    ) {
        fun hasChanges(): Boolean {
            return collectContextChanges().isNotEmpty()
        }

        fun getChanges(): List<Change>? {
            return collectContextChanges().takeIf { it.isNotEmpty() }
        }

        fun getCommitMessageTarget(): CommitMessageTarget? {
            // Primary commit dialog path: `Vcs.MessageActionGroup` actions receive `VcsDataKeys.COMMIT_WORKFLOW_UI`
            // (provided by the VCS commit workflow extension) with writable `CommitMessageUi`.
            if (commitWorkflowUi != null) return CommitMessageUiTarget(commitWorkflowUi.commitMessageUi)

            // Fallback path from the same commit action context: `VcsDataKeys.COMMIT_MESSAGE_CONTROL`.
            if (commitMessageControl is CommitMessageUi) return CommitMessageUiTarget(commitMessageControl)

            // Legacy/non-commit-dialog entry points can still expose plain editor/text controls.
            if (editor != null) return EditorTarget(editor)
            if (commitMessageControl is JTextComponent) return TextComponentTarget(commitMessageControl)
            if (commitMessageControl is JComponent) {
                findTextComponent(commitMessageControl)?.let { return TextComponentTarget(it) }
            }
            return null
        }

        companion object {
            fun fromEvent(e: AnActionEvent): ActionContext {
                return ActionContext(
                    project = e.project,
                    editor = e.getData(CommonDataKeys.EDITOR),
                    // `COMMIT_WORKFLOW_UI` is populated in the VCS commit UI action context.
                    commitWorkflowUi = e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI),
                    // `COMMIT_MESSAGE_CONTROL` is the commit message UI control from VCS commit workflow.
                    commitMessageControl = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL),
                    selectedChanges = e.getData(VcsDataKeys.SELECTED_CHANGES),
                    selectedChangesInDetails = e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS),
                    changes = e.getData(VcsDataKeys.CHANGES),
                    changeLists = e.getData(VcsDataKeys.CHANGE_LISTS)
                )
            }
        }

        private fun collectContextChanges(): List<Change> {
            // Matches commit workflow behavior: use currently included changes from `CommitWorkflowUi` first.
            val includedChanges = commitWorkflowUi?.getIncludedChanges().orEmpty()
            if (includedChanges.isNotEmpty()) {
                return includedChanges
            }

            val explicitSelected = selectedChanges?.toList().orEmpty()
            if (explicitSelected.isNotEmpty()) {
                return explicitSelected
            }

            val selectedFromDetails = selectedChangesInDetails?.toList().orEmpty()
            if (selectedFromDetails.isNotEmpty()) {
                return selectedFromDetails
            }

            val selectedChanges = changes?.toList().orEmpty()
            if (selectedChanges.isNotEmpty()) {
                return selectedChanges
            }

            val contextChangesFromLists = linkedSetOf<Change>()
            changeLists?.forEach { contextChangesFromLists.addAll(it.changes) }
            return contextChangesFromLists.toList()
        }

        private fun findTextComponent(component: JComponent): JTextComponent? {
            if (component is JTextComponent) return component

            for (child in component.components) {
                when (child) {
                    is JTextComponent -> return child
                    is JComponent -> findTextComponent(child)?.let { return it }
                }
            }

            return null
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ActionContext

            if (project != other.project) return false
            if (editor != other.editor) return false
            if (commitWorkflowUi != other.commitWorkflowUi) return false
            if (commitMessageControl != other.commitMessageControl) return false
            if (!selectedChanges.contentEquals(other.selectedChanges)) return false
            if (!selectedChangesInDetails.contentEquals(other.selectedChangesInDetails)) return false
            if (!changes.contentEquals(other.changes)) return false
            if (!changeLists.contentEquals(other.changeLists)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = project?.hashCode() ?: 0
            result = 31 * result + (editor?.hashCode() ?: 0)
            result = 31 * result + (commitWorkflowUi?.hashCode() ?: 0)
            result = 31 * result + (commitMessageControl?.hashCode() ?: 0)
            result = 31 * result + (selectedChanges?.contentHashCode() ?: 0)
            result = 31 * result + (selectedChangesInDetails?.contentHashCode() ?: 0)
            result = 31 * result + (changes?.contentHashCode() ?: 0)
            result = 31 * result + (changeLists?.contentHashCode() ?: 0)
            return result
        }
    }

    private interface CommitMessageTarget {
        fun getText(): String
        fun setText(text: String)
    }

    private class EditorTarget(private val editor: Editor) : CommitMessageTarget {
        override fun getText(): String = editor.document.text
        override fun setText(text: String) {
            ApplicationManager.getApplication().runWriteAction {
                editor.document.setText(text)
            }
        }
    }

    private class TextComponentTarget(private val component: JTextComponent) : CommitMessageTarget {
        override fun getText(): String = component.text

        override fun setText(text: String) {
            component.text = text
        }
    }

    private class CommitMessageUiTarget(private val commitMessageUi: CommitMessageUi) : CommitMessageTarget {
        override fun getText(): String = commitMessageUi.text

        override fun setText(text: String) {
            commitMessageUi.text = text
        }
    }

}

private data class ProviderItem(
    val config: AgentConfig,
    val label: String,
    val icon: Icon
)

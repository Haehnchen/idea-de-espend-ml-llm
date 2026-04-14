package de.espend.ml.llm.vcs

import com.intellij.icons.AllIcons
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
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.ui.awt.RelativePoint
import com.intellij.vcs.commit.CommitMessageUi
import com.intellij.vcs.commit.CommitWorkflowUi
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.profile.AiProfileConfig
import de.espend.ml.llm.profile.AiProfilePlatformRegistry
import de.espend.ml.llm.profile.AiProfileRegistry
import kotlinx.coroutines.runBlocking
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.text.JTextComponent

/**
 * Action to generate commit messages using configured AI providers.
 * Supports aborting generation by clicking again while running.
 */
class ProviderCommitMessageAction : AnAction(
    "Generate Commit Message Agent Provider",
    "Generate commit message with agent provider",
    PluginIcons.AI_PROVIDER_16
) {
    // Track the running progress indicator for abort functionality
    @Volatile
    private var progressIndicator: ProgressIndicator? = null

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val context = ActionContext.fromEvent(e)

        val validProviders = AiProfileRegistry.getInstance().currentState.profiles
            .filter { it.isEnabled }
            .filter { isProviderValid(it) }

        val hasChanges = context.hasChanges()
        val hasTarget = context.getCommitMessageTarget() != null
        val hasValidProviders = validProviders.isNotEmpty()
        val isRunning = progressIndicator?.isRunning == true

        // Show stop icon when running, normal icon otherwise
        e.presentation.icon = if (isRunning) AllIcons.Run.Stop else PluginIcons.AI_PROVIDER_16

        val tooltip = if (isRunning) {
            "Stop generating commit message"
        } else {
            buildTooltip(validProviders)
        }

        e.presentation.isVisible = hasValidProviders && project != null
        e.presentation.isEnabled = hasValidProviders && (isRunning || (hasChanges && hasTarget))
        // Some commit UI placements render tooltip/title from action text instead of description,
        // so keep both in sync to ensure the dynamic provider tooltip is actually visible.
        e.presentation.description = tooltip
        e.presentation.text = tooltip
    }

    private fun isProviderValid(config: AiProfileConfig): Boolean {
        val platform = AiProfilePlatformRegistry.findPlatform(config.platform) ?: return false
        val endpoint = AiProfilePlatformRegistry.resolveEndpoint(platform, config.effectiveApiType()) ?: return false
        val hasBaseUrl = AiProfilePlatformRegistry.getResolvedBaseUrl(endpoint, config.baseUrl).isNotBlank()
        val hasApiKey = config.apiKey.isNotEmpty()
        val hasModel = config.model.isNotEmpty() || platform.defaultModel.isNotEmpty()
        return hasBaseUrl && hasApiKey && hasModel
    }

    override fun actionPerformed(e: AnActionEvent) {
        // If already running, abort the current generation
        if (progressIndicator?.isRunning == true) {
            progressIndicator?.cancel()
            progressIndicator = null
            return
        }

        val project = e.project ?: return
        val context = ActionContext.fromEvent(e)

        val changes = context.getChanges()
        if (changes.isNullOrEmpty()) {
            return
        }

        val target = context.getCommitMessageTarget() ?: return

        val validProviders = AiProfileRegistry.getInstance().currentState.profiles
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
        providers: List<AiProfileConfig>,
        project: Project,
        changes: List<Change>,
        target: CommitMessageTarget
    ): ListPopup {
        val items = providers.map { config ->
            val platform = AiProfilePlatformRegistry.findPlatform(config.platform)
            val label = config.name.ifBlank { platform?.label ?: config.platform }
            val model = config.model.takeIf { it.isNotEmpty() }
                ?: platform?.defaultModel
                ?: "default"
            val icon = platform?.icon ?: PluginIcons.AI_PROVIDER
            ProviderItem(config, "$label ($model)", icon)
        }

        val step = object : BaseListPopupStep<ProviderItem>("Select Profile", items) {
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
        config: AiProfileConfig,
        changes: List<Change>,
        target: CommitMessageTarget
    ) {
        val existingText = target.getText()
        val providerName = getProviderDisplayName(config)

        object : Task.Backgroundable(project, "Generating commit message...", true) {
            override fun run(indicator: ProgressIndicator) {
                // Store indicator for abort functionality
                progressIndicator = indicator
                indicator.isIndeterminate = true

                try {
                    val result = runBlocking {
                        CommitMessageGenerator.generate(project, config, changes, existingText, indicator)
                    }

                    // Check if cancelled before updating UI
                    if (indicator.isCanceled) {
                        return
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
                } finally {
                    // Clear indicator when done
                    progressIndicator = null
                }
            }
        }.queue()
    }

    private fun buildTooltip(validProviders: List<AiProfileConfig>): String {
        return when {
            validProviders.size == 1 -> "Generate commit message with ${getProviderDisplayName(validProviders.first())}"
            validProviders.size > 1 -> "Generate commit message with AI profile"
            else -> "Generate commit message with AI profile"
        }
    }

    private fun getProviderDisplayName(config: AiProfileConfig): String {
        val platform = AiProfilePlatformRegistry.findPlatform(config.platform)
        val providerLabel = config.name.ifBlank { platform?.label ?: config.platform }
        val model = config.model.takeIf { it.isNotEmpty() }
            ?: platform?.defaultModel
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
            val unversionedChanges = commitWorkflowUi?.getIncludedUnversionedFiles()
                .orEmpty()
                .map { Change(null, CurrentContentRevision(it)) }
            if (includedChanges.isNotEmpty() || unversionedChanges.isNotEmpty()) {
                return includedChanges + unversionedChanges
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
    val config: AiProfileConfig,
    val label: String,
    val icon: Icon
)

package de.espend.ml.llm.structurediff

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.io.File
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel

class StructuralDiffConfigurable : BoundSearchableConfigurable(
    "Semantic Diff",
    "de.espend.ml.llm.structurediff.settings",
    "semantic.diff.settings",
) {
    override fun apply() {
        super.apply()
        ApplicationManager.getApplication().messageBus
            .syncPublisher(StructuralDiffSettingsListener.TOPIC)
            .settingsChanged()
    }

    override fun createPanel(): DialogPanel {
        val settings = StructuralDiffSettings.instance
        val tools = StructuralToolTable()
        return panel {
            row {
                label("Semantic diff tools are command-line applications installed separately from this plugin.")
            }
            row {
                StructuralDiffEngineKind.entries.forEach { kind -> browserLink(kind.displayName, kind.installUrl) }
            }
            row {
                cell(tools.component)
                    .label("Tools:", LabelPosition.TOP)
                    .align(AlignX.FILL)
                    .onIsModified { tools.isModified(settings) }
                    .onReset { tools.reset(settings) }
                    .onApply { tools.apply(settings) }
            }
            row {
                button("Detect Installed Tools") {
                    val count = tools.detect()
                    Messages.showInfoMessage(
                        if (count == 0) "No new supported tools were found." else "Added $count tool(s).",
                        "Semantic Diff",
                    )
                }
                comment("Searches PATH and common Homebrew, Cargo, local-bin, Scoop, and Chocolatey locations.")
            }
            row {
                checkBox("Decorate changed tokens and declarations")
                    .bindSelected(settings::decorateStructure)
                    .comment("Adds syntax-aware underlines and declaration boxes to the native viewer.")
            }
            row("Timeout (seconds):") {
                intTextField(1..600).bindIntText(settings::timeoutSeconds)
            }
        }
    }
}

private class StructuralToolTable {
    private val model = ListTableModel<StructuralDiffToolConfig>(
        DefaultColumn(),
        textColumn("Name") { it.name.orEmpty() },
        textColumn("Engine") { StructuralDiffEngineKind.fromId(it.engineId)?.displayName.orEmpty() },
        textColumn("File patterns") { it.filePatterns.orEmpty() },
        textColumn("Executable") { it.executable.orEmpty() },
    )
    private val table = JBTable(model).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        setShowGrid(false)
        columnModel.getColumn(0).maxWidth = 65
    }
    private var defaultName = ""

    val component: JComponent = ToolbarDecorator.createDecorator(table)
        .setAddAction { edit(StructuralDiffToolConfig(), true) }
        .setEditAction { selected()?.let { edit(it, false) } }
        .setRemoveActionUpdater { selected() != null }
        .setRemoveAction {
            selected()?.let { item ->
                model.removeRow(model.indexOf(item))
                if (defaultName == item.name) defaultName = model.items.firstOrNull()?.name.orEmpty()
            }
        }
        .createPanel()

    fun reset(settings: StructuralDiffSettings) {
        model.items = settings.tools.map(StructuralDiffToolConfig::copyConfig)
        defaultName = settings.defaultToolName.orEmpty()
        if (model.items.none { it.name == defaultName }) defaultName = model.items.firstOrNull()?.name.orEmpty()
    }

    fun apply(settings: StructuralDiffSettings) {
        settings.tools = model.items.map(StructuralDiffToolConfig::copyConfig).toMutableList()
        settings.defaultToolName = defaultName
    }

    fun isModified(settings: StructuralDiffSettings): Boolean =
        defaultName != settings.defaultToolName.orEmpty() ||
            model.items.map(StructuralDiffToolConfig::snapshot) != settings.tools.map(StructuralDiffToolConfig::snapshot)

    fun detect(): Int {
        val existingPaths = model.items.mapNotNull { it.executable }.map { File(it).absoluteFile.normalize().path }.toMutableSet()
        val existingNames = model.items.mapNotNull { it.name }.toMutableSet()
        var added = 0
        StructuralDiffDiscovery.discoverInstalled().forEach { found ->
            if (!existingPaths.add(File(found.executable.orEmpty()).absoluteFile.normalize().path)) return@forEach
            val base = found.name.orEmpty().ifBlank { "Diff Tool" }
            var unique = base
            var suffix = 2
            while (unique in existingNames) unique = "$base ${suffix++}"
            found.name = unique
            existingNames += unique
            model.addRow(found)
            if (defaultName.isBlank()) defaultName = unique
            added++
        }
        return added
    }

    private fun selected(): StructuralDiffToolConfig? =
        table.selectedRow.takeIf { it >= 0 }?.let(model::getItem)

    private fun edit(original: StructuralDiffToolConfig, isNew: Boolean) {
        val edited = original.copyConfig()
        if (!StructuralToolDialog(edited).showAndGet()) return
        val name = edited.name.orEmpty()
        if (model.items.any { it.name == name && (isNew || it !== original) }) {
            Messages.showErrorDialog(component, "A tool named '$name' already exists.", "Semantic Diff")
            return
        }
        if (isNew) {
            model.addRow(edited)
            if (defaultName.isBlank()) defaultName = name
        } else {
            val index = model.indexOf(original)
            if (defaultName == original.name) defaultName = name
            model.setItem(index, edited)
        }
    }

    private fun textColumn(title: String, value: (StructuralDiffToolConfig) -> String) =
        object : ColumnInfo<StructuralDiffToolConfig, String>(title) {
            override fun valueOf(item: StructuralDiffToolConfig): String = value(item)
        }

    private inner class DefaultColumn : ColumnInfo<StructuralDiffToolConfig, Boolean>("Default") {
        override fun getColumnClass(): Class<*> = Boolean::class.javaObjectType
        override fun valueOf(item: StructuralDiffToolConfig): Boolean = item.name == defaultName
        override fun isCellEditable(item: StructuralDiffToolConfig): Boolean = true
        override fun setValue(item: StructuralDiffToolConfig, value: Boolean) {
            if (value) {
                defaultName = item.name.orEmpty()
                model.fireTableDataChanged()
            }
        }
    }
}

private class StructuralToolDialog(private val target: StructuralDiffToolConfig) : DialogWrapper(true) {
    private val name = JBTextField(target.name.orEmpty())
    private val executable = TextFieldWithBrowseButton().apply { text = target.executable.orEmpty() }
    private val arguments = JBTextField(target.arguments.orEmpty())
    private val environment = JBTextField(target.environment.entries.joinToString(" ") { "${it.key}=${it.value}" })
    private val patterns = JBTextField(target.filePatterns.orEmpty())
    private val output = JBTextArea(8, 75).apply { isEditable = false }
    private var engine = StructuralDiffEngineKind.fromId(target.engineId) ?: StructuralDiffEngineKind.DIFFTASTIC

    init {
        title = "Semantic Diff Tool"
        executable.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle("Select Diff Tool Executable"),
        )
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Name:") { cell(name).align(AlignX.FILL) }
        row("Engine:") {
            comboBox(StructuralDiffEngineKind.entries.map { it.displayName })
                .bindItem(
                    { engine.displayName },
                    { selected ->
                        val replacement = StructuralDiffEngineKind.entries.first { it.displayName == selected }
                        if (replacement != engine) {
                            val oldDefaults = engine.defaultArguments
                            engine = replacement
                            if (arguments.text.isBlank() || arguments.text == oldDefaults) {
                                arguments.text = replacement.defaultArguments
                            }
                            if (environment.text.isBlank()) {
                                environment.text = replacement.defaultEnvironment.entries.joinToString(" ") {
                                    "${it.key}=${it.value}"
                                }
                            }
                        }
                    },
                )
        }
        row("Executable:") { cell(executable).align(AlignX.FILL) }
        row("Arguments:") {
            cell(arguments).align(AlignX.FILL).comment("%1 = left file, %2 = right file")
        }
        row("Environment:") {
            cell(environment).align(AlignX.FILL).comment("Space-separated NAME=VALUE entries; quotes are supported.")
        }
        row("File patterns:") {
            cell(patterns).align(AlignX.FILL).comment("Optional globs such as *.kt or *.java; first match wins.")
        }
        row {
            button("Test") { testConfiguration() }
        }
        row { cell(JScrollPane(output)).align(AlignX.FILL) }
    }

    override fun doValidate(): ValidationInfo? = when {
        name.text.isBlank() -> ValidationInfo("Enter a name.", name)
        executable.text.isBlank() -> ValidationInfo("Select an executable.", executable.textField)
        else -> null
    }

    override fun doOKAction() {
        copyFields(target)
        super.doOKAction()
    }

    private fun testConfiguration() {
        val candidate = StructuralDiffToolConfig()
        copyFields(candidate)
        var result: StructuralDiffAnalysis = StructuralDiffAnalysis.Failed("Test did not run")
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                result = StructuralDiffEngine.analyze(
                    candidate,
                    "class Sample { int total = 1; }\n",
                    "class Sample { long total = 2; }\n",
                    "Sample.java",
                    ProgressManager.getInstance().progressIndicator,
                )
            },
            "Testing ${engine.displayName}",
            true,
            null,
            contentPanel,
        )
        output.text = when (val tested = result) {
            StructuralDiffAnalysis.Equal -> "The command ran, but reported equal files for a changed sample. Check the arguments."
            is StructuralDiffAnalysis.Failed -> "Failed: ${tested.reason}"
            is StructuralDiffAnalysis.Changed ->
                "OK: ${tested.leftLines.size} left lines, ${tested.rightLines.size} right lines, " +
                    "${tested.tokens.size} token marks, ${tested.regions.size} declaration marks."
        }
    }

    private fun copyFields(destination: StructuralDiffToolConfig) {
        destination.name = name.text.trim()
        destination.engineId = engine.id
        destination.executable = executable.text.trim()
        destination.arguments = arguments.text.trim().ifBlank { engine.defaultArguments }
        destination.environment = parseEnvironment(environment.text).toMutableMap()
        destination.filePatterns = patterns.text.trim()
    }

    private fun parseEnvironment(value: String): Map<String, String> = ParametersListUtil.parse(value)
        .filter { '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
}

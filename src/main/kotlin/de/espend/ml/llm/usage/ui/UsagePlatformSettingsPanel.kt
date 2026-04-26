package de.espend.ml.llm.usage.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.AnActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.TableView
import com.intellij.util.IconUtil
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ElementProducer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import de.espend.ml.llm.usage.ProviderUsageService
import de.espend.ml.llm.usage.UsageAccountConfig
import de.espend.ml.llm.usage.UsageAccountState
import de.espend.ml.llm.usage.UsagePlatformRegistry
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ItemEvent
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.AbstractCellEditor

/**
 * Settings panel for managing usage accounts.
 *
 * Provider-agnostic: all account types are handled generically via [UsageProvider.fromState] /
 * [UsageProvider.toState]. Adding a new provider requires no changes here.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class UsagePlatformSettingsPanel : JPanel(GridBagLayout()) {

    /**
     * Internal wrapper for table display, wraps UsageAccountConfig
     */
    data class UsageRow(
        val config: UsageAccountConfig
    ) {
        val type: String get() = config.providerId
        val label: String get() = config.name
        val info: String get() = config.getInfoString()
    }

    private val rtkStatsCheckBox = JCheckBox("Show RTK token savings panel").apply {
        isOpaque = false
    }

    private val tokscaleStatsCheckBox = JCheckBox("Show Tokscale token usage panel").apply {
        isOpaque = false
    }

    private val rows: MutableList<UsageRow> = mutableListOf()

    private val modelList = com.intellij.util.ui.ListTableModel<UsageRow>(
        arrayOf(EnabledColumn(), LabelColumn(), TypeColumn(), InfoColumn()),
        mutableListOf()
    )

    private val tableView = TableView(modelList).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        rowHeight = JBUI.scale(28)
        intercellSpacing = JBUI.size(0, 4)
        setShowGrid(false)
        autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        preferredScrollableViewportSize = Dimension(0, JBUI.scale(180))
        setDefaultRenderer(String::class.java, SpacedTableCellRenderer())
    }

    init {
        // ── Tools section ────────────────────────────────────────────────
        add(createSectionHeader("Tools"), GridBagConstraints().apply {
            gridx = 0; gridy = 0; weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insetsBottom(6)
        })

        val toolsContent = JPanel(GridBagLayout()).apply {
            isOpaque = false

            add(rtkStatsCheckBox, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
                insets = JBUI.insetsBottom(2)
            })

            val hintFont = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
            val hintRow = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(JBLabel("Reads token savings from ~/.local/share/rtk/history.db written by ").apply {
                    font = hintFont
                    foreground = UIUtil.getContextHelpForeground()
                })
                add(ActionLink("rtk") { BrowserUtil.browse("https://github.com/rtk-ai/rtk") }.apply {
                    font = hintFont
                })
                add(JBLabel(", a transparent CLI proxy that reduces token usage.").apply {
                    font = hintFont
                    foreground = UIUtil.getContextHelpForeground()
                })
            }
            add(hintRow, GridBagConstraints().apply {
                gridx = 0; gridy = 1; anchor = GridBagConstraints.WEST; weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsLeft(20)
            })

            add(tokscaleStatsCheckBox, GridBagConstraints().apply {
                gridx = 0; gridy = 2; anchor = GridBagConstraints.WEST
                insets = JBUI.insets(8, 0, 2, 0)
            })

            val tokscaleHintRow = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(JBLabel("Uses local ").apply {
                    font = hintFont
                    foreground = UIUtil.getContextHelpForeground()
                })
                add(ActionLink("tokscale.") { BrowserUtil.browse("https://www.npmjs.com/package/tokscale") }.apply {
                    font = hintFont
                })
                add(JBLabel(" Falls back to npx and shows week/month input, output, total tokens, and cost.").apply {
                    font = hintFont
                    foreground = UIUtil.getContextHelpForeground()
                })
            }
            add(tokscaleHintRow, GridBagConstraints().apply {
                gridx = 0; gridy = 3; anchor = GridBagConstraints.WEST; weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsLeft(20)
            })
        }

        add(toolsContent, GridBagConstraints().apply {
            gridx = 0; gridy = 1; weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(0, 16, 10, 0)
        })

        // ── Quota Accounts section ────────────────────────────────────────
        add(createSectionHeader("Quota Accounts"), GridBagConstraints().apply {
            gridx = 0; gridy = 2; weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insetsBottom(6)
        })

        installColumnSizing()

        val toolbarDecorator = ToolbarDecorator.createDecorator(tableView, object : ElementProducer<UsageRow> {
            override fun createElement(): UsageRow? = null
            override fun canCreateElement(): Boolean = true
        })

        toolbarDecorator.setAddAction { button -> showProviderMenu(button.contextComponent, button) }
        toolbarDecorator.setEditAction { editSelectedRow() }
        toolbarDecorator.setRemoveAction { removeSelectedRow() }
        toolbarDecorator.disableUpAction()
        toolbarDecorator.disableDownAction()

        add(toolbarDecorator.createPanel(), GridBagConstraints().apply {
            gridx = 0; gridy = 3
            fill = GridBagConstraints.BOTH
            weightx = 1.0; weighty = 1.0
        })
    }

    private fun createSectionHeader(title: String): JPanel = JPanel(GridBagLayout()).apply {
        isOpaque = false
        val label = JBLabel(title).apply {
            font = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD)
        }
        add(label, GridBagConstraints().apply {
            gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
        })
        add(JSeparator(SwingConstants.HORIZONTAL), GridBagConstraints().apply {
            gridx = 1; gridy = 0; weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insetsLeft(8)
        })
    }

    fun loadFrom(registryState: UsagePlatformRegistry.State) {
        rtkStatsCheckBox.isSelected = registryState.showRtkStats
        tokscaleStatsCheckBox.isSelected = registryState.showTokscaleStats

        rows.clear()
        while (modelList.rowCount > 0) modelList.removeRow(0)

        val service = ProviderUsageService.getInstance()
        registryState.accounts.forEach { state ->
            val config = service.getProvider(state.providerId)?.fromState(state) ?: return@forEach
            rows.add(UsageRow(config))
        }

        rows.forEach { modelList.addRow(it) }
        if (modelList.rowCount > 0) tableView.setRowSelectionInterval(0, 0)
    }

    fun isModified(registryState: UsagePlatformRegistry.State): Boolean {
        if (rtkStatsCheckBox.isSelected != registryState.showRtkStats) return true
        if (tokscaleStatsCheckBox.isSelected != registryState.showTokscaleStats) return true
        return toAccountStates() != registryState.accounts
    }

    fun applyTo(registryState: UsagePlatformRegistry.State) {
        registryState.showRtkStats = rtkStatsCheckBox.isSelected
        registryState.showTokscaleStats = tokscaleStatsCheckBox.isSelected
        val accounts = toAccountStates()
        LOG.debug("Updating provider accounts: ${accounts.size} account(s)")
        accounts.forEach { state ->
            LOG.debug("  Account: id=${state.id}, provider=${state.providerId}, label=${state.label}, enabled=${state.isEnabled}, statusBar=${state.enableStatusBar}")
        }
        registryState.accounts = accounts.toMutableList()
    }

    private fun toAccountStates(): List<UsageAccountState> {
        val service = ProviderUsageService.getInstance()
        return modelList.items.mapNotNull { row ->
            service.getProvider(row.type)?.toState(row.config)
        }
    }

    private fun editSelectedRow() {
        val index = tableView.selectedRow
        if (index < 0 || index >= modelList.rowCount) return

        val row = modelList.getItem(index)
        val provider = ProviderUsageService.getInstance().getProvider(row.type) ?: return
        val panel = UsageFormPanel(provider, row.config)
        val onSave = provider.openForm(panel, row.config)
        if (!panel.showDialog(tableView)) return
        onSave()

        row.config.name = panel.nameField.text.trim()
        row.config.isEnabled = panel.enabledCheckBox.isSelected
        row.config.enableStatusBar = panel.enableStatusBarCheckBox.isSelected

        val newRow = UsageRow(row.config)
        modelList.removeRow(index)
        modelList.insertRow(index, newRow)
        tableView.setRowSelectionInterval(index, index)
    }

    private fun removeSelectedRow() {
        val index = tableView.selectedRow
        if (index < 0 || index >= modelList.rowCount) return

        modelList.removeRow(index)
        if (modelList.rowCount > 0) {
            val nextRow = index.coerceAtMost(modelList.rowCount - 1)
            tableView.setRowSelectionInterval(nextRow, nextRow)
        }
    }

    private fun showProviderMenu(anchor: JComponent, button: AnActionButton) {
        val menu = JPopupMenu()
        val service = ProviderUsageService.getInstance()

        service.getAllProviders().forEach { provider ->
            val scaledIcon = scaleIcon(provider.providerInfo.icon, 12)
            val menuItem = JMenuItem(provider.providerInfo.providerName, scaledIcon).apply {
                iconTextGap = JBUI.scale(6)
            }
            menuItem.addActionListener {
                val defaultConfig = provider.createDefaultConfig()

                val panel = UsageFormPanel(provider, defaultConfig)
                val onSave = provider.openForm(panel, defaultConfig)
                if (!panel.showDialog(anchor)) return@addActionListener
                onSave()

                defaultConfig.name = panel.nameField.text.trim()
                defaultConfig.isEnabled = panel.enabledCheckBox.isSelected
                defaultConfig.enableStatusBar = panel.enableStatusBarCheckBox.isSelected

                modelList.addRow(UsageRow(defaultConfig))
                tableView.setRowSelectionInterval(modelList.rowCount - 1, modelList.rowCount - 1)
            }
            menu.add(menuItem)
        }

        val popupPoint = button.preferredPopupPoint
        val comp = popupPoint.component
        val point = popupPoint.getPoint(comp)
        menu.show(comp, point.x, point.y)
    }

    private class LabelColumn : ColumnInfo<UsageRow, String>("Label") {
        override fun valueOf(item: UsageRow?): String? = item?.label
    }

    private class TypeColumn : ColumnInfo<UsageRow, String>("Type") {
        override fun valueOf(item: UsageRow?): String? {
            val providerId = item?.type ?: return null
            val provider = ProviderUsageService.getInstance().getProvider(providerId)
            return provider?.providerInfo?.providerName ?: providerId
        }
    }

    private class InfoColumn : ColumnInfo<UsageRow, String>("Info") {
        override fun valueOf(item: UsageRow?): String? = item?.info
    }

    private fun installColumnSizing() {
        tableView.columnModel.getColumn(0).minWidth = JBUI.scale(44)
        tableView.columnModel.getColumn(0).maxWidth = JBUI.scale(56)
        tableView.columnModel.getColumn(1).minWidth = JBUI.scale(90)
        tableView.columnModel.getColumn(2).minWidth = JBUI.scale(90)
        tableView.columnModel.getColumn(3).minWidth = JBUI.scale(120)

        fun applyWidths() {
            val width = tableView.width.takeIf { it > 0 } ?: return
            val fixedEnabled = JBUI.scale(52)
            val remaining = (width - fixedEnabled).coerceAtLeast(JBUI.scale(300))

            tableView.columnModel.getColumn(0).preferredWidth = fixedEnabled
            tableView.columnModel.getColumn(1).preferredWidth = (remaining * 0.22).toInt()
            tableView.columnModel.getColumn(2).preferredWidth = (remaining * 0.18).toInt()
            tableView.columnModel.getColumn(3).preferredWidth = (remaining * 0.60).toInt()
        }

        tableView.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                applyWidths()
            }
        })

        applyWidths()
    }

    private inner class EnabledColumn : ColumnInfo<UsageRow, Boolean>("") {
        private val editor = EnabledCellEditor()

        override fun valueOf(item: UsageRow?): Boolean? = item?.config?.isEnabled

        override fun getColumnClass(): Class<*> = Boolean::class.java

        override fun isCellEditable(item: UsageRow?): Boolean = true

        override fun getRenderer(item: UsageRow?): TableCellRenderer = CheckboxRenderer

        override fun getEditor(item: UsageRow?): TableCellEditor = editor
    }

    private object CheckboxRenderer : JCheckBox(), TableCellRenderer {
        init {
            horizontalAlignment = JCheckBox.CENTER
            isOpaque = true
            border = JBUI.Borders.empty(4, 8)
        }

        @Suppress("unused")
        private fun readResolve(): Any = CheckboxRenderer

        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            this.isSelected = value as? Boolean ?: false
            if (isSelected) {
                background = table?.selectionBackground
                foreground = table?.selectionForeground
            } else {
                background = table?.background
                foreground = table?.foreground
            }
            return this
        }
    }

    private inner class EnabledCellEditor : AbstractCellEditor(), TableCellEditor {
        private val checkBox = JCheckBox().apply {
            horizontalAlignment = JCheckBox.CENTER
            isOpaque = true
            border = JBUI.Borders.empty(4, 8)
        }

        private var currentRow = -1
        private var itemListener: java.awt.event.ItemListener? = null

        override fun getTableCellEditorComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            row: Int,
            column: Int
        ): Component {
            currentRow = row
            itemListener?.let { checkBox.removeItemListener(it) }
            checkBox.isSelected = value as? Boolean ?: false
            val listener = java.awt.event.ItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED || e.stateChange == ItemEvent.DESELECTED) {
                    if (currentRow >= 0 && currentRow < modelList.rowCount) {
                        modelList.getItem(currentRow).config.isEnabled = checkBox.isSelected
                    }
                }
            }
            itemListener = listener
            checkBox.addItemListener(listener)
            return checkBox
        }

        override fun getCellEditorValue(): Any = checkBox.isSelected
    }

    private class SpacedTableCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (component is JComponent) {
                component.border = JBUI.Borders.empty(4, 8)
            }
            return component
        }
    }

    companion object {
        private val LOG = Logger.getInstance(UsagePlatformSettingsPanel::class.java)
        private fun scaleIcon(icon: Icon, size: Int): Icon {
            val targetSize = JBUI.scale(size)
            val currentSize = icon.iconWidth.coerceAtLeast(1)
            if (currentSize == targetSize) return icon
            return IconUtil.scale(icon, null, targetSize.toFloat() / currentSize)
        }
    }
}

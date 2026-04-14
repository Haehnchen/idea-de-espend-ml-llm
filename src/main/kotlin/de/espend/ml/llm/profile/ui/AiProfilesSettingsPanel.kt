package de.espend.ml.llm.profile.ui

import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ElementProducer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import de.espend.ml.llm.usage.UsageFormatUtils
import de.espend.ml.llm.profile.AiProfileConfig
import de.espend.ml.llm.profile.AiProfilePlatformRegistry
import de.espend.ml.llm.profile.AiProfileRegistry
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import kotlin.random.Random

class AiProfilesSettingsPanel : JPanel(GridBagLayout()) {

    data class AiProfileRow(val config: AiProfileConfig) {
        val name: String
            get() = config.name.trim()

        val model: String
            get() = truncateModel(config.model.trim())

        val platformLabel: String
            get() = AiProfilePlatformRegistry.findPlatform(config.platform)?.label ?: config.platform

        val transportLabel: String
            get() {
                val platform = AiProfilePlatformRegistry.findPlatform(config.platform) ?: return config.effectiveTransport()
                return AiProfilePlatformRegistry.describeTransportOption(
                    platform,
                    config.effectiveTransport(),
                    config.effectiveApiType()
                )
            }

        val info: String
            get() {
                val apiKey = config.apiKey.trim()
                return if (apiKey.isNotBlank()) {
                    "Token: ${UsageFormatUtils.formatSecret(apiKey)}"
                } else {
                    ""
                }
            }
    }

    private val tableModel = ListTableModel<AiProfileRow>(
        arrayOf(
            EnabledColumn(),
            NameColumn(),
            ModelColumn(),
            PlatformColumn(),
            TransportColumn(),
            InfoColumn()
        ),
        mutableListOf()
    )

    private val table = TableView(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        rowHeight = JBUI.scale(28)
        preferredScrollableViewportSize = Dimension(0, JBUI.scale(220))
    }

    init {
        table.columnModel.getColumn(0).apply {
            minWidth = JBUI.scale(42)
            maxWidth = JBUI.scale(42)
            preferredWidth = JBUI.scale(42)
        }
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(160)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(110)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(150)
        table.columnModel.getColumn(4).preferredWidth = JBUI.scale(190)
        table.columnModel.getColumn(5).preferredWidth = JBUI.scale(140)

        val decorator = ToolbarDecorator.createDecorator(table, object : ElementProducer<AiProfileRow> {
            override fun createElement(): AiProfileRow? = null
            override fun canCreateElement(): Boolean = true
        })

        decorator.setAddAction {
            val template = AiProfileConfig(
                id = createProfileId(),
                platform = AiProfilePlatformRegistry.platforms.first().id,
                apiType = AiProfilePlatformRegistry.defaultApiType(AiProfilePlatformRegistry.platforms.first().id),
                transport = AiProfilePlatformRegistry.defaultTransport(AiProfilePlatformRegistry.platforms.first().id),
                isEnabled = true
            )
            val panel = AiProfileFormPanel(template.copy())
            val updatedProfile = template.copy()
            if (!panel.showDialog(table)) {
                return@setAddAction
            }
            panel.applyTo(updatedProfile)
            tableModel.addRow(AiProfileRow(updatedProfile))
            table.setRowSelectionInterval(tableModel.rowCount - 1, tableModel.rowCount - 1)
        }
        decorator.setEditAction {
            val selectedRow = table.selectedRow
            if (selectedRow < 0) {
                return@setEditAction
            }

            val original = tableModel.getItem(selectedRow)
            val edited = original.config.copy()
            val panel = AiProfileFormPanel(edited)
            if (!panel.showDialog(table)) {
                return@setEditAction
            }
            panel.applyTo(edited)
            tableModel.removeRow(selectedRow)
            tableModel.insertRow(selectedRow, AiProfileRow(edited))
            table.setRowSelectionInterval(selectedRow, selectedRow)
        }
        decorator.setRemoveAction {
            val selectedRow = table.selectedRow
            if (selectedRow >= 0) {
                tableModel.removeRow(selectedRow)
            }
        }
        decorator.disableUpAction()
        decorator.disableDownAction()

        add(decorator.createPanel(), GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            weighty = 1.0
            fill = GridBagConstraints.BOTH
        })
    }

    fun loadFrom(state: AiProfileRegistry.State) {
        while (tableModel.rowCount > 0) {
            tableModel.removeRow(0)
        }
        state.profiles.forEach { tableModel.addRow(AiProfileRow(it.copy())) }
        if (tableModel.rowCount > 0) {
            table.setRowSelectionInterval(0, 0)
        }
    }

    fun isModified(state: AiProfileRegistry.State): Boolean {
        return currentProfiles() != state.profiles
    }

    fun applyTo(registry: AiProfileRegistry) {
        registry.replaceProfiles(currentProfiles())
    }

    private fun currentProfiles(): List<AiProfileConfig> {
        return tableModel.items.map { it.config.copy() }
    }

    private fun createProfileId(): String {
        val existingIds = tableModel.items.mapTo(mutableSetOf()) { it.config.id }
        val alphabet = (('a'..'z') + ('0'..'9')).toList()

        repeat(1000) {
            val candidate = buildString {
                append("profile-")
                append(alphabet.random())
                append(alphabet.random())
            }
            if (candidate !in existingIds) {
                return candidate
            }
        }

        for (first in alphabet) {
            for (second in alphabet) {
                val candidate = "profile-$first$second"
                if (candidate !in existingIds) {
                    return candidate
                }
            }
        }

        error("No profile id available")
    }

    private class EnabledColumn : ColumnInfo<AiProfileRow, Boolean>("") {
        override fun valueOf(item: AiProfileRow?): Boolean? = item?.config?.isEnabled
        override fun isCellEditable(item: AiProfileRow?): Boolean = true
        override fun setValue(item: AiProfileRow?, value: Boolean?) {
            if (item != null && value != null) {
                item.config.isEnabled = value
            }
        }

        override fun getColumnClass(): Class<*> = Boolean::class.javaObjectType
    }

    private class NameColumn : ColumnInfo<AiProfileRow, String>("Name") {
        override fun valueOf(item: AiProfileRow?): String? = item?.name
    }

    private class ModelColumn : ColumnInfo<AiProfileRow, String>("Model") {
        override fun valueOf(item: AiProfileRow?): String? = item?.model
    }

    private class PlatformColumn : ColumnInfo<AiProfileRow, String>("Platform") {
        override fun valueOf(item: AiProfileRow?): String? = item?.platformLabel
    }

    private class TransportColumn : ColumnInfo<AiProfileRow, String>("ACP") {
        override fun valueOf(item: AiProfileRow?): String? = item?.transportLabel
    }

    private class InfoColumn : ColumnInfo<AiProfileRow, String>("Info") {
        override fun valueOf(item: AiProfileRow?): String? = item?.info
    }

    companion object {
        private fun truncateModel(value: String): String {
            if (value.isEmpty()) {
                return ""
            }

            return if (value.length > 10) {
                value.take(10) + "..."
            } else {
                value
            }
        }
    }
}

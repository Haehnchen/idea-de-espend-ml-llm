package de.espend.ml.llm

import com.google.gson.Gson
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel

/**
 * API response data classes for /v1/models endpoint.
 */
data class ModelInfo(
    val id: String = "",
    val name: String? = null
)

private data class ModelsResponse(
    val data: List<ModelInfo> = emptyList()
)

/**
 * Dialog for selecting a model from the provider's available models.
 * Shows a searchable table with model IDs and owners, with a refresh button.
 */
class ModelSelectionDialog(
    private val modelsUrl: String,
    private val apiKey: String,
    private val currentModel: String
) : DialogWrapper(true) {

    var selectedModel: String? = null
        private set

    private val allModels = mutableListOf<ModelInfo>()
    private val filteredModels = mutableListOf<ModelInfo>()
    private val tableModel = ModelTableModel()
    private val table = JBTable(tableModel)
    private val searchField = SearchTextField()
    private val statusLabel = JBLabel("")
    private var selectedRow: Int = -1

    init {
        title = "Select Model"
        init()
        fetchModels()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(5)))
        panel.preferredSize = Dimension(500, 400)

        // Top bar: search + refresh
        val topPanel = JPanel(BorderLayout(JBUI.scale(5), 0))
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                filterModels()
            }
        })
        topPanel.add(searchField, BorderLayout.CENTER)
        val refreshButton = JButton("Refresh").apply {
            addActionListener { fetchModels() }
        }
        topPanel.add(refreshButton, BorderLayout.EAST)
        panel.add(topPanel, BorderLayout.NORTH)

        // Table setup
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.columnModel.getColumn(0).preferredWidth = 30
        table.columnModel.getColumn(0).maxWidth = 40
        table.columnModel.getColumn(1).preferredWidth = 180
        table.columnModel.getColumn(2).preferredWidth = 280

        // Disable column 0 editing to avoid showing true/false text on checkbox click
        table.columnModel.getColumn(0).cellEditor = null

        table.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = table.selectedRow
                if (row >= 0) {
                    // Uncheck previous, check new
                    if (selectedRow >= 0 && selectedRow < filteredModels.size) {
                        tableModel.fireTableCellUpdated(selectedRow, 0)
                    }
                    selectedRow = row
                    tableModel.fireTableCellUpdated(row, 0)
                }
            }
        }
        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val col = table.columnAtPoint(e.point)
                if (row >= 0 && col == 0) {
                    // Select the row when checkbox column is clicked
                    table.setRowSelectionInterval(row, row)
                }
            }
        })

        val scrollPane = JBScrollPane(table)
        panel.add(scrollPane, BorderLayout.CENTER)

        // Status bar
        statusLabel.foreground = UIUtil.getContextHelpForeground()
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    override fun doOKAction() {
        if (selectedRow >= 0 && selectedRow < filteredModels.size) {
            selectedModel = filteredModels[selectedRow].id
        }
        super.doOKAction()
    }

    private fun fetchModels() {
        statusLabel.text = "Loading models..."
        allModels.clear()
        filteredModels.clear()
        tableModel.fireTableDataChanged()

        Thread {
            try {
                val uri = URI(modelsUrl)
                val connection = uri.toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                if (apiKey.isNotEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")
                }
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    val response = Gson().fromJson(responseBody, ModelsResponse::class.java)
                    SwingUtilities.invokeLater {
                        allModels.addAll(response.data)
                        filterModels()
                        statusLabel.text = "${allModels.size} models loaded"
                        // Pre-select current model
                        preselectCurrentModel()
                    }
                } else {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "Error: HTTP $responseCode ($uri)"
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "Error: ${e.message} ($modelsUrl)"
                }
            }
        }.start()
    }

    private fun filterModels() {
        val query = searchField.text.lowercase().trim()
        filteredModels.clear()
        if (query.isEmpty()) {
            filteredModels.addAll(allModels)
        } else {
            filteredModels.addAll(allModels.filter {
                it.id.lowercase().contains(query) || it.name?.lowercase()?.contains(query) == true
            })
        }
        // Preserve selection
        val previousSelected = if (selectedRow >= 0 && selectedRow < filteredModels.size) {
            filteredModels[selectedRow].id
        } else null
        tableModel.fireTableDataChanged()
        // Try to reselect
        if (previousSelected != null) {
            val newIdx = filteredModels.indexOfFirst { it.id == previousSelected }
            if (newIdx >= 0) {
                selectedRow = newIdx
                table.setRowSelectionInterval(newIdx, newIdx)
            }
        }
    }

    private fun preselectCurrentModel() {
        if (currentModel.isNotEmpty()) {
            val idx = filteredModels.indexOfFirst { it.id == currentModel }
            if (idx >= 0) {
                selectedRow = idx
                table.setRowSelectionInterval(idx, idx)
                table.scrollRectToVisible(table.getCellRect(idx, 0, true))
                tableModel.fireTableDataChanged()
            }
        }
    }

    private inner class ModelTableModel : AbstractTableModel() {
        private val columns = arrayOf("", "Name", "ID")

        override fun getRowCount(): Int = filteredModels.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
            0 -> Boolean::class.javaObjectType
            else -> String::class.java
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val model = filteredModels[rowIndex]
            return when (columnIndex) {
                0 -> rowIndex == selectedRow
                1 -> model.name ?: model.id.substringAfterLast('/')
                2 -> model.id
                else -> ""
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }
}

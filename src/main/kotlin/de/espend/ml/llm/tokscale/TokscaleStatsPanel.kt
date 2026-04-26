package de.espend.ml.llm.tokscale

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Fixed two-row panel for Tokscale week/month token and cost totals.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class TokscaleStatsPanel : JPanel() {

    private val rows = TokscalePeriod.entries.associateWith { createRowLabels(it) }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        buildHeader()
        add(createTable())
    }

    fun initEmpty() {
        TokscalePeriod.entries.forEach { period ->
            rows[period]?.showPlaceholder()
        }
    }

    fun load(week: TokscaleUsageResult, month: TokscaleUsageResult) {
        updateRow(TokscalePeriod.WEEK, week)
        updateRow(TokscalePeriod.MONTH, month)
        revalidate()
        repaint()
    }

    private fun buildHeader() {
        val headerLabel = JBLabel("Tokscale").apply {
            font = font.deriveFont(Font.BOLD, font.size2D - 1f)
            alignmentX = 0f
        }
        add(headerLabel)
        add(Box.createVerticalStrut(JBUI.scale(6)))
    }

    private fun createTable(): JPanel {
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            alignmentX = 0f

            addHeaderCell("", 0)
            addHeaderCell("In", 1)
            addHeaderCell("Out", 2)
            addHeaderCell("Total", 3)
            addHeaderCell("$", 4)

            TokscalePeriod.entries.forEachIndexed { index, period ->
                val row = rows.getValue(period)
                val rowIndex = index + 1
                addDataCell(row.periodLabel, 0, rowIndex)
                addDataCell(row.inputLabel, 1, rowIndex)
                addDataCell(row.outputLabel, 2, rowIndex)
                addDataCell(row.totalLabel, 3, rowIndex)
                addDataCell(row.costLabel, 4, rowIndex)
            }
        }
    }

    private fun JPanel.addHeaderCell(text: String, column: Int) {
        add(JBLabel(text, SwingConstants.RIGHT).apply {
            font = font.deriveFont(Font.BOLD, font.size2D - 2f)
            foreground = SECONDARY_COLOR
        }, constraints(column, 0))
    }

    private fun JPanel.addDataCell(label: JBLabel, column: Int, row: Int) {
        add(label, constraints(column, row))
    }

    private fun constraints(column: Int, row: Int): GridBagConstraints =
        GridBagConstraints().apply {
            gridx = column
            gridy = row
            weightx = if (column == 0) 0.18 else 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = if (column == 0) JBUI.emptyInsets() else JBUI.insetsLeft(4)
        }

    private fun updateRow(period: TokscalePeriod, result: TokscaleUsageResult) {
        val row = rows.getValue(period)
        val stat = result.stat
        if (stat == null) {
            if (result.error == null) {
                row.showPlaceholder()
                return
            }
            row.showError()
            return
        }

        row.inputLabel.text = TokscaleStatsReader.formatTokens(stat.inputTokens)
        row.outputLabel.text = TokscaleStatsReader.formatTokens(stat.outputTokens)
        row.totalLabel.text = TokscaleStatsReader.formatTokens(stat.totalTokens)
        row.costLabel.text = TokscaleStatsReader.formatCost(stat.cost)
        row.setForeground(SECONDARY_COLOR)
    }

    private fun createRowLabels(period: TokscalePeriod) = RowLabels(
        periodLabel = normalLabel(period.label),
        inputLabel = normalLabel("--"),
        outputLabel = normalLabel("--"),
        totalLabel = normalLabel("--"),
        costLabel = normalLabel("--")
    )

    private fun normalLabel(text: String) = JBLabel(text, SwingConstants.RIGHT).apply {
        font = font.deriveFont(font.size2D - 1.5f)
        foreground = SECONDARY_COLOR
    }

    private data class RowLabels(
        val periodLabel: JBLabel,
        val inputLabel: JBLabel,
        val outputLabel: JBLabel,
        val totalLabel: JBLabel,
        val costLabel: JBLabel
    ) {
        private val allLabels = listOf(periodLabel, inputLabel, outputLabel, totalLabel, costLabel)

        fun showPlaceholder() {
            inputLabel.text = "--"
            outputLabel.text = "--"
            totalLabel.text = "--"
            costLabel.text = "--"
            setForeground(SECONDARY_COLOR)
        }

        fun showError() {
            inputLabel.text = "Error"
            outputLabel.text = "--"
            totalLabel.text = "--"
            costLabel.text = "--"
            setForeground(ERROR_COLOR)
        }

        fun setForeground(color: JBColor) {
            allLabels.forEach { it.foreground = color }
        }
    }

    private companion object {
        private val SECONDARY_COLOR = JBColor(0x787878, 0x999999)
        private val ERROR_COLOR = JBColor(0xD84A4A, 0xE06C75)
    }
}

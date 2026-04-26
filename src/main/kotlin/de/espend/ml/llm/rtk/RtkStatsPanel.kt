package de.espend.ml.llm.rtk

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.time.LocalDate
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Compact 3-column panel showing the last 2 days and the current week of RTK token savings.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class RtkStatsPanel : JPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        buildHeader()
        add(createTable(null, null, null))
    }

    /**
     * Pre-render with placeholder data so the panel has the correct size immediately.
     */
    fun initEmpty() {
        // Already has placeholder table from init; nothing to do.
    }

    fun load(days: List<RtkDayStat>, week: RtkWeekStat?) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val todayStat = days.find { it.date == today }
        val yesterdayStat = days.find { it.date == yesterday }

        // Replace placeholder table with real data.
        remove(componentCount - 1)
        add(createTable(yesterdayStat, todayStat, week))
        revalidate()
        repaint()
    }

    private fun buildHeader() {
        val headerLabel = JBLabel("RTK savings").apply {
            font = font.deriveFont(java.awt.Font.BOLD, font.size2D - 1f)
            alignmentX = 0f
        }
        add(headerLabel)
        add(Box.createVerticalStrut(JBUI.scale(6)))
    }

    private fun createTable(yesterdayStat: RtkDayStat?, todayStat: RtkDayStat?, week: RtkWeekStat?): JPanel {
        val columns = listOf(
            createDayColumn("Yesterday", yesterdayStat),
            createDayColumn("Today", todayStat),
            createWeekColumn(week)
        )

        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            alignmentX = 0f

            columns.forEachIndexed { column, data ->
                addHeaderCell(data.label, column)
                addDataCell(normalLabel(data.tokens), column, 1)
                addDataCell(normalLabel(data.savings), column, 2)
            }
        }
    }

    private fun JPanel.addHeaderCell(text: String, column: Int) {
        add(smallLabel(text), constraints(column, 0))
    }

    private fun JPanel.addDataCell(label: JBLabel, column: Int, row: Int) {
        add(label, constraints(column, row))
    }

    private fun constraints(column: Int, row: Int): GridBagConstraints =
        GridBagConstraints().apply {
            gridx = column
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = if (column == 0) JBUI.emptyInsets() else JBUI.insetsLeft(4)
        }

    private fun createDayColumn(label: String, stat: RtkDayStat?): RtkColumn {
        return if (stat != null) {
            RtkColumn(
                label = label,
                tokens = "${RtkStatsReader.formatTokens(stat.inputTokens)} · ${RtkStatsReader.formatTokens(stat.outputTokens)}",
                savings = "-${"%.1f".format(stat.savingsPct)}%"
            )
        } else {
            RtkColumn(label, "--", " ")
        }
    }

    private fun createWeekColumn(stat: RtkWeekStat?): RtkColumn {
        return if (stat != null) {
            RtkColumn(
                label = "7d",
                tokens = "${RtkStatsReader.formatTokens(stat.inputTokens)} · ${RtkStatsReader.formatTokens(stat.outputTokens)}",
                savings = "-${"%.1f".format(stat.savingsPct)}%"
            )
        } else {
            RtkColumn("7d", "--", " ")
        }
    }

    private fun smallLabel(text: String) = gridLabel(text).apply {
        font = font.deriveFont(font.size2D - 2f)
    }

    private fun normalLabel(text: String) = gridLabel(text).apply {
        font = font.deriveFont(font.size2D - 1.5f)
    }

    private fun gridLabel(text: String) = JBLabel(text, SwingConstants.RIGHT).apply {
        foreground = SECONDARY_COLOR
    }

    private data class RtkColumn(
        val label: String,
        val tokens: String,
        val savings: String
    )

    private companion object {
        private val SECONDARY_COLOR = JBColor(0x787878, 0x999999)
    }

}

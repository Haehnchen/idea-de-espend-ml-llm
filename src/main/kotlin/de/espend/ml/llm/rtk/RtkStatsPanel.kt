package de.espend.ml.llm.rtk

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.GridLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * Compact 3-column panel showing the last 2 days and the current week of RTK token savings.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class RtkStatsPanel : JPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    fun load(days: List<RtkDayStat>, week: RtkWeekStat?) {
        removeAll()

        val headerLabel = JBLabel("RTK Savings").apply {
            font = font.deriveFont(java.awt.Font.BOLD, font.size2D - 1f)
            alignmentX = 0f
        }
        add(headerLabel)
        add(Box.createVerticalStrut(JBUI.scale(6)))

        val grid = JPanel(GridLayout(1, 3, JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = 0f
        }

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val todayStat = days.find { it.date == today }
        val yesterdayStat = days.find { it.date == yesterday }

        grid.add(createDayCard("Yesterday", yesterdayStat))
        grid.add(createDayCard("Today", todayStat))
        grid.add(createWeekCard(week))

        add(grid)
        revalidate()
        repaint()
    }

    private fun createDayCard(label: String, stat: RtkDayStat?): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false

        panel.add(smallLabel(label))
        panel.add(Box.createVerticalStrut(JBUI.scale(2)))
        if (stat != null) {
            panel.add(normalLabel("${RtkStatsReader.formatTokens(stat.inputTokens)} · ${RtkStatsReader.formatTokens(stat.outputTokens)}"))
            panel.add(normalLabel("-${"%.1f".format(stat.savingsPct)}%"))
        } else {
            panel.add(normalLabel("--"))
        }
        return panel
    }

    private fun createWeekCard(stat: RtkWeekStat?): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false

        panel.add(smallLabel("7d"))
        panel.add(Box.createVerticalStrut(JBUI.scale(2)))
        if (stat != null) {
            panel.add(normalLabel("${RtkStatsReader.formatTokens(stat.inputTokens)} · ${RtkStatsReader.formatTokens(stat.outputTokens)}"))
            panel.add(normalLabel("-${"%.1f".format(stat.savingsPct)}%"))
        } else {
            panel.add(normalLabel("--"))
        }
        return panel
    }

    private fun smallLabel(text: String) = JBLabel(text).apply {
        font = font.deriveFont(font.size2D - 2f)
        alignmentX = 0f
    }

    private fun normalLabel(text: String) = JBLabel(text).apply {
        font = font.deriveFont(font.size2D - 1.5f)
        alignmentX = 0f
    }

    companion object {
        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yy-MM-dd")
    }
}

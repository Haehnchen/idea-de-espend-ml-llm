package de.espend.ml.llm.usage.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import de.espend.ml.llm.usage.UsageAccountConfig
import de.espend.ml.llm.usage.UsageProvider
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Pre-built form panel for usage account editing.
 * Contains Type, Enabled checkbox, and Name field (pre-built).
 * Handlers add their provider-specific fields to [providerPanel].
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class UsageFormPanel(
    val provider: UsageProvider,
    val config: UsageAccountConfig
) : JPanel(GridBagLayout()) {

    val enabledCheckBox = JCheckBox("Account enabled", config.isEnabled)
    val enableStatusBarCheckBox = JCheckBox("Show in status bar", config.enableStatusBar)
    val nameField = JBTextField(config.name)

    /**
     * Empty panel for providers to add their provider-specific fields.
     * Providers can use any layout and add any components they need.
     */
    val providerPanel = JPanel(GridBagLayout())

    private val contentPanel = JPanel(GridBagLayout())

    init {
        // Use smaller font for form elements (IntelliJ standard)
        val smallFont = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        enabledCheckBox.font = smallFont
        nameField.font = smallFont

        var row = 0

        // Provider section header
        val providerHeader = JPanel(GridBagLayout()).apply {
            val providerLabel = JBLabel("Provider: ${provider.providerInfo.providerName}").apply {
                font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
            }
            add(providerLabel, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
            })
            add(JSeparator(SwingConstants.HORIZONTAL), GridBagConstraints().apply {
                gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insetsLeft(10)
            })
        }
        contentPanel.add(providerHeader, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(2)
        })
        row++

        // Enabled checkbox (with left padding)
        contentPanel.add(enabledCheckBox, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(4, 24, 2, 2)
        })
        row++

        // Name field (with left padding)
        val nameLabel = JBLabel("Name").apply { font = smallFont }
        contentPanel.add(nameLabel, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(2, 24, 2, 2)
        })
        contentPanel.add(nameField, GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(2, 2, 2, 2)
        })
        row++

        // Statusbar checkbox (with left padding)
        val hintFont = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        enableStatusBarCheckBox.font = smallFont
        contentPanel.add(enableStatusBarCheckBox, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(4, 24, 0, 2)
        })
        row++
        contentPanel.add(JBLabel("Display usage percentage in the IDE status bar").apply {
            font = hintFont
            foreground = UIUtil.getContextHelpForeground()
        }, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(0, 44, 2, 2)
        })
        row++

        // Settings section header
        val settingsHeader = JPanel(GridBagLayout()).apply {
            val settingsLabel = JBLabel("Settings").apply {
                font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
            }
            add(settingsLabel, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
            })
            add(JSeparator(SwingConstants.HORIZONTAL), GridBagConstraints().apply {
                gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insetsLeft(10)
            })
        }
        contentPanel.add(settingsHeader, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insetsTop(10)
        })
        row++

        // Provider-specific panel (handler populates this, with left padding)
        contentPanel.add(providerPanel, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(4, 24, 2, 2)
        })
        row++

        // Vertical filler — pushes all content to the top
        contentPanel.add(JPanel(), GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            weighty = 1.0
            fill = GridBagConstraints.VERTICAL
        })
    }

    /**
     * Show this panel in a modal dialog.
     * Uses a plain JDialog to avoid DialogWrapper limitations inside settings configurables.
     * Dialog grows/shrinks to fit content up to a max height; content scrolls if needed.
     * OK / Cancel buttons are right-aligned in a fixed footer outside the scroll area.
     *
     * @param parentComponent a component from the calling UI for positioning
     * @return true if OK was pressed, false if cancelled
     */
    fun showDialog(parentComponent: Component): Boolean {
        contentPanel.revalidate()

        val maxHeight = JBUI.scale(580)
        val minWidth = JBUI.scale(546)

        val scrollPane = object : JBScrollPane(contentPanel) {
            override fun getPreferredSize(): Dimension {
                val contentHeight = contentPanel.preferredSize.height + JBUI.scale(4)
                return Dimension(minWidth, minOf(contentHeight, maxHeight))
            }
        }.apply {
            border = null
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        val okButton = JButton("OK")
        val cancelButton = JButton("Cancel")

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), JBUI.scale(4)))
        buttonPanel.add(okButton)
        buttonPanel.add(cancelButton)

        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(8)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        val owner = SwingUtilities.getWindowAncestor(parentComponent)
        val dialog =
            JDialog(owner, "${provider.providerInfo.providerName} Usage Account", Dialog.ModalityType.APPLICATION_MODAL)
        dialog.contentPane = mainPanel
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.rootPane.defaultButton = okButton
        dialog.rootPane.registerKeyboardAction(
            { dialog.dispose() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            WHEN_IN_FOCUSED_WINDOW
        )

        var confirmed = false
        okButton.addActionListener { confirmed = true; dialog.dispose() }
        cancelButton.addActionListener { dialog.dispose() }

        dialog.pack()
        dialog.setLocationRelativeTo(parentComponent)
        dialog.isVisible = true // blocks — modal

        return confirmed
    }
}
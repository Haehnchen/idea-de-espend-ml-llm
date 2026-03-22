package de.espend.ml.llm.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

/**
 * Reusable component for displaying npm package info with a dropdown menu.
 * Provides actions to copy install command and open GitHub repository.
 */
class PackageActionLink(
    packageName: String,
    private val githubUrl: String?,
    private val installCommand: String
) : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {

    init {
        isOpaque = false

        // Package name as clickable link (no hover effect, just pointer cursor)
        val packageLink = ActionLink(packageName).apply {
            font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
            addActionListener { showPopupMenu(this) }
        }

        add(packageLink)
    }

    private fun showPopupMenu(invoker: ActionLink) {
        val menu = JPopupMenu().apply {
            border = JBUI.Borders.empty(2)
        }

        // Copy install command action
        val copyItem = JMenuItem("Copy install command").apply {
            border = JBUI.Borders.empty(4)
            addActionListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                val selection = StringSelection(installCommand)
                clipboard.setContents(selection, null)
                menu.isVisible = false
            }
        }
        menu.add(copyItem)

        // Open GitHub action (if URL provided)
        if (githubUrl != null) {
            menu.addSeparator()
            val githubItem = JMenuItem("Open GitHub").apply {
                border = JBUI.Borders.empty(4)
                addActionListener {
                    BrowserUtil.browse(githubUrl)
                }
            }
            menu.add(githubItem)
        }

        menu.show(invoker, 0, invoker.height)
    }

    companion object {
        /**
         * Creates a PackageActionLink for the Anthropic Compatible proxy package.
         */
        fun forAnthropicCompatible(): PackageActionLink {
            return PackageActionLink(
                packageName = "@zed-industries/claude-agent-acp",
                githubUrl = "https://github.com/zed-industries/claude-agent-acp",
                installCommand = "npm install -g @zed-industries/claude-agent-acp"
            )
        }

        /**
         * Creates a PackageActionLink for Kilo Code installation.
         */
        fun forKilo(): PackageActionLink {
            return PackageActionLink(
                packageName = "@kilocode/cli",
                githubUrl = "https://github.com/Kilo-Org/kilocode",
                installCommand = "npm install -g @kilocode/cli"
            )
        }

        /**
         * Creates a PackageActionLink for Cursor installation.
         */
        fun forCursor(): PackageActionLink {
            return PackageActionLink(
                packageName = "cursor.com/install",
                githubUrl = "https://github.com/cursor/cursor",
                installCommand = "curl https://cursor.com/install -fsS | bash"
            )
        }
    }
}

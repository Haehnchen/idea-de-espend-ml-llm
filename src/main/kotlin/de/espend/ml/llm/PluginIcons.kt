package de.espend.ml.llm

import com.intellij.openapi.util.IconLoader
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import javax.swing.Icon

object PluginIcons {
    // Provider icons
    val ZAI: Icon = IconLoader.getIcon("/icons/zai.png", PluginIcons::class.java)
    val AMPCODE: Icon = IconLoader.getIcon("/icons/ampcode.png", PluginIcons::class.java)
    val CODEX: Icon = IconLoader.getIcon("/icons/codex.png", PluginIcons::class.java)
    val CLAUDE: Icon = IconLoader.getIcon("/icons/claude.png", PluginIcons::class.java)
    val CLINEPASS: Icon = IconLoader.getIcon("/icons/clinepass.png", PluginIcons::class.java)
    val OPENCODE_GO: Icon = IconLoader.getIcon("/icons/opencode-go.png", PluginIcons::class.java)
    val JUNIE: Icon = IconLoader.getIcon("/icons/junie.png", PluginIcons::class.java)
    val OLLAMA: Icon = IconLoader.getIcon("/icons/ollama.png", PluginIcons::class.java)

    // Usage toolbar icon (16x16, auto light/dark)
    val USAGE: Icon = IconLoader.getIcon("/icons/usage.svg", PluginIcons::class.java)

    fun scaleIcon(icon: Icon, size: Int): Icon {
        val targetSize = JBUI.scale(size)
        val currentSize = icon.iconWidth.coerceAtLeast(1)
        if (currentSize == targetSize) {
            return icon
        }

        return IconUtil.scale(icon, null, targetSize.toFloat() / currentSize)
    }

}

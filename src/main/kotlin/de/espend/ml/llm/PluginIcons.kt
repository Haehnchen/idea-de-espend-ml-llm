package de.espend.ml.llm

import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.ImageUtil
import javax.swing.Icon
import javax.swing.ImageIcon

object PluginIcons {
    // Provider icons
    val OPENAI: Icon = IconLoader.getIcon("/icons/openai.png", PluginIcons::class.java)
    val ZAI: Icon = IconLoader.getIcon("/icons/zai.png", PluginIcons::class.java)
    val AMPCODE: Icon = IconLoader.getIcon("/icons/ampcode.png", PluginIcons::class.java)
    val CODEX: Icon = IconLoader.getIcon("/icons/codex.png", PluginIcons::class.java)
    val MINIMAX: Icon = IconLoader.getIcon("/icons/minimax.png", PluginIcons::class.java)
    val OPENROUTER: Icon = IconLoader.getIcon("/icons/openrouter.png", PluginIcons::class.java)
    val MIMO: Icon = IconLoader.getIcon("/icons/mimo.png", PluginIcons::class.java)
    val ANTHROPIC: Icon = IconLoader.getIcon("/icons/anthropic.png", PluginIcons::class.java)
    val CLAUDE: Icon = IconLoader.getIcon("/icons/claude.png", PluginIcons::class.java)
    val PI: Icon = IconLoader.getIcon("/icons/pi.png", PluginIcons::class.java)
    val GEMINI: Icon = IconLoader.getIcon("/icons/gemini.png", PluginIcons::class.java)
    val OPENCODE: Icon = IconLoader.getIcon("/icons/opencode.png", PluginIcons::class.java)
    val CURSOR: Icon = IconLoader.getIcon("/icons/cursor.png", PluginIcons::class.java)
    val KILO: Icon = IconLoader.getIcon("/icons/kilo.png", PluginIcons::class.java)
    val DROID: Icon = IconLoader.getIcon("/icons/droid.png", PluginIcons::class.java)
    val REQUESTY: Icon = IconLoader.getIcon("/icons/requesty.png", PluginIcons::class.java)
    val NANOGPT: Icon = IconLoader.getIcon("/icons/nanogpt.png", PluginIcons::class.java)
    val AIHUBMIX: Icon = IconLoader.getIcon("/icons/aihubmix.png", PluginIcons::class.java)
    val MOONSHOT: Icon = IconLoader.getIcon("/icons/moonshot.png", PluginIcons::class.java)
    val JUNIE: Icon = IconLoader.getIcon("/icons/junie.png", PluginIcons::class.java)
    val OLLAMA: Icon = IconLoader.getIcon("/icons/ollama.png", PluginIcons::class.java)
    val NVIDIA: Icon = IconLoader.getIcon("/icons/nvidia.png", PluginIcons::class.java)

    // Plugin icon
    val AI_PROVIDER: Icon = IconLoader.getIcon("/icons/ai-provider.svg", PluginIcons::class.java)

    // Usage toolbar icon (16x16, auto light/dark)
    val USAGE: Icon = IconLoader.getIcon("/icons/usage.svg", PluginIcons::class.java)

    // Scaled icons (16x16 for menus/actions)
    val AI_PROVIDER_16: Icon = scaleIcon(AI_PROVIDER, 16)

    fun scaleIcon(icon: Icon, size: Int): Icon {
        val image = IconLoader.toImage(icon) ?: return icon
        val scaledImage = ImageUtil.scaleImage(image, size, size)
        return ImageIcon(scaledImage)
    }

}

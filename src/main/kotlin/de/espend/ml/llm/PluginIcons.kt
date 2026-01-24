package de.espend.ml.llm

import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.ImageUtil
import javax.swing.Icon
import javax.swing.ImageIcon

object PluginIcons {
    // Provider icons
    val ZAI: Icon = IconLoader.getIcon("/icons/zai.png", PluginIcons::class.java)
    val MINIMAX: Icon = IconLoader.getIcon("/icons/minimax.png", PluginIcons::class.java)
    val OPENROUTER: Icon = IconLoader.getIcon("/icons/openrouter.png", PluginIcons::class.java)
    val MIMO: Icon = IconLoader.getIcon("/icons/mimo.png", PluginIcons::class.java)
    val ANTHROPIC: Icon = IconLoader.getIcon("/icons/anthropic.png", PluginIcons::class.java)
    val CLAUDE: Icon = IconLoader.getIcon("/icons/claude.png", PluginIcons::class.java)
    val GEMINI: Icon = IconLoader.getIcon("/icons/gemini.png", PluginIcons::class.java)
    val OPENCODE: Icon = IconLoader.getIcon("/icons/opencode.png", PluginIcons::class.java)
    val MOONSHOT: Icon = IconLoader.getIcon("/icons/moonshot.png", PluginIcons::class.java)

    // Plugin icon
    val AI_PROVIDER: Icon = IconLoader.getIcon("/icons/ai-provider.svg", PluginIcons::class.java)

    // Scaled icons (16x16 for menus/actions)
    val AI_PROVIDER_16: Icon = scaleIcon(AI_PROVIDER, 16)

    fun scaleIcon(icon: Icon, size: Int): Icon {
        val image = IconLoader.toImage(icon) ?: return icon
        val scaledImage = ImageUtil.scaleImage(image, size, size)
        return ImageIcon(scaledImage)
    }

    fun getIconForProvider(provider: String): Icon {
        val icon = ProviderConfig.PROVIDER_INFOS[provider]?.icon ?: AI_PROVIDER
        return scaleIcon(icon, 16)
    }
}

package de.espend.ml.llm.session.util

import com.intellij.ui.JBColor
import java.awt.Color
import javax.swing.UIManager

/**
 * Utility for extracting IntelliJ theme colors and converting them to CSS.
 * Reads actual UIManager colors to match the IDE theme exactly.
 * Falls back to dark theme when running outside IntelliJ (CLI mode).
 */
object ThemeColors {

    /**
     * Returns true if the current IDE theme is dark.
     * Falls back to true (dark theme) when running outside IntelliJ.
     */
    fun isDark(): Boolean {
        return try {
            val jbColorClass = Class.forName("com.intellij.ui.JBColor")
            val isBrightMethod = jbColorClass.getMethod("isBright")
            !(isBrightMethod.invoke(null) as Boolean)
        } catch (e: Exception) {
            // Not running in IntelliJ - default to dark theme
            true
        }
    }

    /**
     * Converts a Color to CSS hex format (#rrggbb).
     */
    fun toHex(color: Color): String {
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }

    /**
     * Converts a Color to CSS rgba format with alpha.
     */
    fun toRgba(color: Color, alpha: Double): String {
        return "rgba(${color.red}, ${color.green}, ${color.blue}, $alpha)"
    }

    /**
     * Gets a color from UIManager with a fallback.
     */
    private fun getColor(key: String, fallback: Color): Color {
        return UIManager.getColor(key) ?: fallback
    }

    /**
     * Generates CSS variables based on current IntelliJ theme.
     * Reads actual UIManager colors for accurate theme matching.
     */
    fun generateCssVariables(): String {
        val isDark = isDark()

        // Core colors from UIManager with light/dark fallbacks
        val background = getColor("ToolWindow.background", if (isDark) Color(0x2b2b2b) else JBColor.WHITE)
        // Cards: hardcoded colors since dark themes have same color for all backgrounds
        val backgroundSecondary = if (isDark) Color(0x2d2f31) else Color(0xf7f8fa)
        val foreground = if (isDark) Color(0xbbbbbb) else Color(0x2b2b2b)
        val comment = if (isDark) Color(0x858585) else Color(0x6b6b6b)
        val accent = if (isDark) Color(0x4a90d9) else Color(0x2470b3)
        val info = if (isDark) Color(0x6db33f) else Color(0x4a8c2a)
        val error = if (isDark) Color(0xf85149) else Color(0xcf222e)
        val success = if (isDark) Color(0x3fb950) else Color(0x1a7f37)
        // Hover: subtle grey, slightly lighter/darker than card background
        val selection = if (isDark) Color(0x3a3c3e) else Color(0xebecee)
        val scrollThumbColor = if (isDark) Color(0x5a5a5a) else Color(0x999999)
        val border = if (isDark) Color(0x555555) else Color(0xd0d0d0)

        // Semantic message colors - these use tinted backgrounds
        // For dark theme: dark tinted backgrounds
        // For light theme: light tinted backgrounds
        val userBg = if (isDark) Color(0x2c3e50) else Color(0xe3f2fd)
        val userBorder = if (isDark) Color(0x1976d2) else Color(0x1976d2)
        val userLabel = if (isDark) Color(0x64b5f6) else Color(0x1565c0)

        val assistantBg = backgroundSecondary
        val assistantBorder = if (isDark) Color(0x6b6b6b) else Color(0xbdbdbd)
        val assistantLabel = if (isDark) Color(0x9e9e9e) else Color(0x616161)

        // Tool use/result: bluish info-like style
        val toolUseBg = if (isDark) Color(0x1e3a4a) else Color(0xe3f2fd)
        val toolUseBorder = if (isDark) Color(0x2980b9) else Color(0x2980b9)
        val toolUseLabel = if (isDark) Color(0x5dade2) else Color(0x1565c0)

        val toolResultBg = if (isDark) Color(0x1e3a4a) else Color(0xe3f2fd)
        val toolResultBorder = if (isDark) Color(0x2980b9) else Color(0x2980b9)
        val toolResultLabel = if (isDark) Color(0x5dade2) else Color(0x1565c0)

        val thinkingBg = if (isDark) Color(0x3d3d1f) else Color(0xfff8e1)
        val thinkingBorder = if (isDark) Color(0xf39c12) else Color(0xf39c12)
        val thinkingLabel = if (isDark) Color(0xffd54f) else Color(0xf57f17)

        // Info: neutral grey for generic info messages
        val infoBg = if (isDark) Color(0x2d2d2d) else Color(0xeeeeee)
        val infoBorder = if (isDark) Color(0x888888) else Color(0x9e9e9e)
        val infoLabel = if (isDark) Color(0xaaaaaa) else Color(0x757575)

        // Error: red for error messages
        val errorMsgBg = if (isDark) Color(0x3a1a1a) else Color(0xffebee)
        val errorMsgBorder = if (isDark) Color(0xe74c3c) else Color(0xe74c3c)
        val errorMsgLabel = if (isDark) Color(0xef5350) else Color(0xc62828)

        // Code block colors
        val codeBg = if (isDark) Color(0x1a1a1a) else Color(0xf5f5f5)
        val codeFg = if (isDark) Color(0xa9b7c6) else Color(0x2b2b2b)
        val preBg = if (isDark) Color(0x263238) else Color(0xeceff1)
        val preFg = if (isDark) Color(0xaed581) else Color(0x558b2f)

        // Overlay colors for badges and backgrounds
        val overlayLight = if (isDark) "rgba(255,255,255,0.1)" else "rgba(0,0,0,0.05)"
        val overlayMedium = if (isDark) "rgba(255,255,255,0.15)" else "rgba(0,0,0,0.08)"
        val overlayStrong = if (isDark) "rgba(0,0,0,0.15)" else "rgba(0,0,0,0.05)"
        val borderSubtle = if (isDark) "rgba(255,255,255,0.05)" else "rgba(0,0,0,0.08)"
        val borderDashed = if (isDark) "rgba(255,255,255,0.1)" else "rgba(0,0,0,0.15)"

        // Shadow for sticky header
        val stickyHeaderShadow = if (isDark) "0 2px 8px rgba(0,0,0,0.4)" else "0 2px 8px rgba(0,0,0,0.1)"

        return """
            :root {
                /* Core colors */
                --jb-color-background: ${toHex(background)};
                --jb-color-background-secondary: ${toHex(backgroundSecondary)};
                --jb-color-foreground: ${toHex(foreground)};
                --jb-color-comment: ${toHex(comment)};
                --jb-color-accent: ${toHex(accent)};
                --jb-color-accent-subtle: ${toRgba(accent, 0.15)};
                --jb-color-info: ${toHex(info)};
                --jb-color-info-subtle: ${toRgba(info, 0.15)};
                --jb-color-error: ${toHex(error)};
                --jb-color-success: ${toHex(success)};
                --jb-color-selection-inactive: ${toHex(selection)};
                --jb-color-scrollbar-thumb: ${toRgba(scrollThumbColor, if (isDark) 0.5 else 0.7)};
                --jb-color-scrollbar-thumb-hover: ${toRgba(scrollThumbColor, if (isDark) 0.7 else 0.9)};
                --jb-color-border: ${toHex(border)};

                /* Message backgrounds */
                --msg-user-bg: ${toHex(userBg)};
                --msg-user-border: ${toHex(userBorder)};
                --msg-user-label: ${toHex(userLabel)};

                --msg-assistant-bg: ${toHex(assistantBg)};
                --msg-assistant-border: ${toHex(assistantBorder)};
                --msg-assistant-label: ${toHex(assistantLabel)};

                --msg-tool-use-bg: ${toHex(toolUseBg)};
                --msg-tool-use-border: ${toHex(toolUseBorder)};
                --msg-tool-use-label: ${toHex(toolUseLabel)};

                --msg-tool-result-bg: ${toHex(toolResultBg)};
                --msg-tool-result-border: ${toHex(toolResultBorder)};
                --msg-tool-result-label: ${toHex(toolResultLabel)};

                --msg-thinking-bg: ${toHex(thinkingBg)};
                --msg-thinking-border: ${toHex(thinkingBorder)};
                --msg-thinking-label: ${toHex(thinkingLabel)};

                --msg-info-bg: ${toHex(infoBg)};
                --msg-info-border: ${toHex(infoBorder)};
                --msg-info-label: ${toHex(infoLabel)};

                --msg-error-bg: ${toHex(errorMsgBg)};
                --msg-error-border: ${toHex(errorMsgBorder)};
                --msg-error-label: ${toHex(errorMsgLabel)};

                /* Code blocks */
                --code-bg: ${toHex(codeBg)};
                --code-fg: ${toHex(codeFg)};
                --pre-bg: ${toHex(preBg)};
                --pre-fg: ${toHex(preFg)};

                /* Overlays */
                --overlay-light: $overlayLight;
                --overlay-medium: $overlayMedium;
                --overlay-strong: $overlayStrong;
                --border-subtle: $borderSubtle;
                --border-dashed: $borderDashed;

                /* Shadows */
                --sticky-header-shadow: $stickyHeaderShadow;
            }
        """.trimIndent()
    }
}

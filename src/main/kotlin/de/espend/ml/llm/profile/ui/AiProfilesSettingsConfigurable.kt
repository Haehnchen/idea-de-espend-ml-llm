package de.espend.ml.llm.profile.ui

import com.intellij.openapi.options.Configurable
import de.espend.ml.llm.profile.AiProfileRegistry
import javax.swing.JComponent

class AiProfilesSettingsConfigurable : Configurable {
    private val settingsPanel = AiProfilesSettingsPanel()

    override fun getDisplayName(): String = "AI Profiles"

    override fun createComponent(): JComponent {
        settingsPanel.loadFrom(AiProfileRegistry.getInstance().currentState)
        return settingsPanel
    }

    override fun isModified(): Boolean {
        return settingsPanel.isModified(AiProfileRegistry.getInstance().currentState)
    }

    override fun apply() {
        settingsPanel.applyTo(AiProfileRegistry.getInstance())
    }

    override fun reset() {
        settingsPanel.loadFrom(AiProfileRegistry.getInstance().currentState)
    }
}

package de.espend.ml.llm.usage.ui

import com.intellij.openapi.options.Configurable
import de.espend.ml.llm.usage.ProviderUsageService
import de.espend.ml.llm.usage.UsagePlatformRegistry
import javax.swing.JComponent

/**
 * Configurable for usage settings
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class UsageSettingsConfigurable : Configurable {

    private val usagePlatformSettingsPanel = UsagePlatformSettingsPanel()

    override fun getDisplayName(): String = "Account Usage Toolbar"

    override fun createComponent(): JComponent {
        usagePlatformSettingsPanel.loadFrom(UsagePlatformRegistry.getInstance().state)
        return usagePlatformSettingsPanel
    }

    override fun isModified(): Boolean {
        return usagePlatformSettingsPanel.isModified(UsagePlatformRegistry.getInstance().state)
    }

    override fun apply() {
        val registry = UsagePlatformRegistry.getInstance()
        usagePlatformSettingsPanel.applyTo(registry.state)
        registry.notifyStateChanged()
        ProviderUsageService.getInstance().clearCache()
    }

    override fun reset() {
        usagePlatformSettingsPanel.loadFrom(UsagePlatformRegistry.getInstance().state)
    }
}

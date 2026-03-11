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
        usagePlatformSettingsPanel.loadFrom(UsagePlatformRegistry.getInstance().state.accounts)
        return usagePlatformSettingsPanel
    }

    override fun isModified(): Boolean {
        return usagePlatformSettingsPanel.isModified(UsagePlatformRegistry.getInstance().state.accounts)
    }

    override fun apply() {
        usagePlatformSettingsPanel.applyTo(UsagePlatformRegistry.getInstance().state)
        ProviderUsageService.getInstance().clearCache()
    }

    override fun reset() {
        usagePlatformSettingsPanel.loadFrom(UsagePlatformRegistry.getInstance().state.accounts)
    }
}

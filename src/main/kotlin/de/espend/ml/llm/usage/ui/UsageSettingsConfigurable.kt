package de.espend.ml.llm.usage.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import de.espend.ml.llm.usage.ProviderUsageService
import de.espend.ml.llm.usage.UsagePlatformRegistry
import de.espend.ml.llm.usage.action.ProviderUsageStatusBarWidget
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
        usagePlatformSettingsPanel.applyTo(UsagePlatformRegistry.getInstance().state)
        ProviderUsageService.getInstance().clearCache()
        updateStatusBarWidgets()
    }

    override fun reset() {
        usagePlatformSettingsPanel.loadFrom(UsagePlatformRegistry.getInstance().state)
    }

    private fun updateStatusBarWidgets() {
        ProjectManager.getInstance().openProjects.forEach { project ->
            ProviderUsageStatusBarWidget.refreshWidget(project)
        }
    }
}

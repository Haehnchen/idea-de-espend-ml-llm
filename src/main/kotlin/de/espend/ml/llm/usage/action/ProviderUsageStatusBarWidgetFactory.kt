package de.espend.ml.llm.usage.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import de.espend.ml.llm.usage.UsagePlatformRegistry

/**
 * Factory for the AI Provider Usage status bar widget.
 * The widget is shown when at least one account has "show in status bar" enabled.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class ProviderUsageStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ProviderUsageStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "AI Provider Usage"

    override fun isAvailable(project: Project): Boolean =
        UsagePlatformRegistry.getInstance().hasAnyStatusBarAccount()

    override fun createWidget(project: Project): StatusBarWidget = ProviderUsageStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

package de.espend.ml.llm.usage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil
import de.espend.ml.llm.usage.action.ProviderUsageStatusBarWidget

/**
 * Registry for usage platform configurations.
 *
 * Stores a flat list of [UsageAccountState] entries — one per account regardless of provider.
 * Provider-specific fields are held in [UsageAccountState.properties].
 * Providers registered in [ProviderUsageService] own the conversion to/from typed configs.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
@Service(Service.Level.APP)
@State(
    name = "UsagePlatformRegistry",
    storages = [Storage("usage-platforms.xml")]
)
class UsagePlatformRegistry : PersistentStateComponent<UsagePlatformRegistry.State> {

    interface Listener {
        fun stateChanged()
    }

    class State {
        var accounts: MutableList<UsageAccountState> = mutableListOf()
        var showRtkStats: Boolean = false
    }

    private var myState = State()

    companion object {
        @JvmField
        val TOPIC: Topic<Listener> = Topic.create("usage platform registry changes", Listener::class.java)

        fun getInstance(): UsagePlatformRegistry {
            return ApplicationManager.getApplication().getService(UsagePlatformRegistry::class.java)
        }
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
        notifyStateChanged()
    }

    fun getAccountStates(): List<UsageAccountState> = myState.accounts.toList()

    /**
     * Update specific properties of an account's state in-place.
     * Used by providers that need to persist internal state (e.g. a refreshed access_token).
     */
    fun updateAccountProperties(accountId: String, updates: Map<String, String>) {
        myState.accounts.find { it.id == accountId }?.properties?.putAll(updates)
    }

    fun isShowInStatusBar(accountId: String): Boolean =
        myState.accounts.find { it.id == accountId }
            ?.let { it.isEnabled && it.enableStatusBar } ?: false

    fun hasAnyStatusBarAccount(): Boolean =
        myState.accounts.any { it.isEnabled && it.enableStatusBar }

    fun setEnableStatusBar(accountId: String, enabled: Boolean) {
        val account = myState.accounts.find { it.id == accountId } ?: return
        if (account.enableStatusBar == enabled) {
            return
        }

        account.enableStatusBar = enabled
        notifyStateChanged()
    }

    fun notifyStateChanged() {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(TOPIC)
            .stateChanged()

        ProjectManager.getInstance().openProjects.forEach { project ->
            ProviderUsageStatusBarWidget.refreshWidget(project)
        }
    }
}

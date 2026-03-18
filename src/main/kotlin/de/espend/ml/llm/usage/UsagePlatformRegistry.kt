package de.espend.ml.llm.usage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

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

    class State {
        var accounts: MutableList<UsageAccountState> = mutableListOf()
        var showRtkStats: Boolean = false
    }

    private var myState = State()

    companion object {
        fun getInstance(): UsagePlatformRegistry {
            return ApplicationManager.getApplication().getService(UsagePlatformRegistry::class.java)
        }
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun getAccountStates(): List<UsageAccountState> = myState.accounts.toList()

    /**
     * Update specific properties of an account's state in-place.
     * Used by providers that need to persist internal state (e.g. a refreshed access_token).
     */
    fun updateAccountProperties(accountId: String, updates: Map<String, String>) {
        myState.accounts.find { it.id == accountId }?.properties?.putAll(updates)
    }
}

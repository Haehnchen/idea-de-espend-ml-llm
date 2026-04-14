package de.espend.ml.llm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Legacy provider settings storage kept only so old ai-agents.xml data can be
 * migrated into the new AI profile registry.
 */
@Service(Service.Level.APP)
@State(
    name = "AgentRegistry",
    storages = [Storage("ai-agents.xml")]
)
class AgentRegistry : PersistentStateComponent<AgentRegistry.State> {

    class State {
        var agents: MutableList<AgentConfig> = mutableListOf()
        var claudeCodeExecutable: String? = null
    }

    private var myState = State()

    companion object {
        fun getInstance(): AgentRegistry {
            return ApplicationManager.getApplication().getService(AgentRegistry::class.java)
        }
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    val currentState: State
        get() = myState
}

@file:Suppress("UnstableApiUsage")

package de.espend.ml.llm.chat

import com.intellij.ml.llm.core.providers.ThirdPartyAISettingsManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@Service(Service.Level.APP)
@State(name = "AgentProvidersSettingsManager", storages = [Storage("agentProviders.xml")])
class AgentProvidersSettingsManager : ThirdPartyAISettingsManager {

    private val _connectionSettingsUpdatedFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val connectionSettingsUpdatedFlow: SharedFlow<Unit> = _connectionSettingsUpdatedFlow

    override var baseUrl: String = ""
    override var isToolEnabled: Boolean = true

    override suspend fun getApiKey(): String = ""
    override suspend fun setApiKey(apiKey: String) {
        _connectionSettingsUpdatedFlow.tryEmit(Unit)
    }
}

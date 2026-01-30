@file:Suppress("UnstableApiUsage")

package de.espend.ml.llm.chat

import com.intellij.ml.llm.core.providers.ThirdPartyAIProvider
import com.intellij.ml.llm.core.providers.ThirdPartyAISettingsManager
import com.intellij.ui.dsl.builder.*
import kotlinx.coroutines.CoroutineScope

class AgentProvidersSettingsUIBuilder(private val scope: CoroutineScope) : ThirdPartyAIProvider.SettingsUIBuilder() {

    override fun buildUI(parentPanel: Panel, settings: ThirdPartyAISettingsManager): List<Row> {
        val rows = mutableListOf<Row>()

        uiState = object : UIState {
            override val providerWithCurrentSettings: ThirdPartyAIProvider
                get() = AgentProvidersAIProvider()

            override fun onSettingsChanged(listener: () -> Unit) {}
        }

        return rows
    }
}

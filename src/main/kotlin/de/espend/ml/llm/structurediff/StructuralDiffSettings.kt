package de.espend.ml.llm.structurediff

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.WildcardFileNameMatcher
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.OptionTag

class StructuralDiffToolConfig : BaseState() {
    var name: String? by string("")
    var engineId: String? by string("")
    var executable: String? by string("")
    var arguments: String? by string("")
    var environment: MutableMap<String, String> by map()
    var filePatterns: String? by string("")
}

@State(
    name = "de.espend.ml.llm.StructuralDiffSettings",
    storages = [Storage("structural-diff.xml")],
    category = SettingsCategory.TOOLS,
)
class StructuralDiffSettings : BaseState(), PersistentStateComponent<StructuralDiffSettings> {

    @get:OptionTag("TOOLS")
    var tools: MutableList<StructuralDiffToolConfig> by list()

    @get:OptionTag("DEFAULT_TOOL")
    var defaultToolName: String? by string("")

    @get:OptionTag("TIMEOUT_SECONDS")
    var timeoutSeconds: Int by property(15)

    @get:OptionTag("DECORATE_STRUCTURE")
    var decorateStructure: Boolean by property(false)

    @get:OptionTag("DISCOVERY_COMPLETED")
    var discoveryCompleted: Boolean by property(false)

    override fun getState(): StructuralDiffSettings = this

    override fun loadState(state: StructuralDiffSettings) = copyFrom(state)

    fun toolFor(fileName: String?): StructuralDiffToolConfig? {
        val runnable = tools.filter { config ->
            !config.executable.isNullOrBlank() && StructuralDiffEngineKind.fromId(config.engineId) != null
        }
        if (!fileName.isNullOrBlank()) {
            runnable.firstOrNull { it.accepts(fileName) }?.let { return it }
        }
        return runnable.firstOrNull { it.name == defaultToolName } ?: runnable.firstOrNull()
    }

    fun executableFor(engineId: String): String? = tools
        .firstOrNull { it.engineId == engineId && !it.executable.isNullOrBlank() }
        ?.executable

    companion object {
        val instance: StructuralDiffSettings
            get() = service()
    }
}

interface StructuralDiffSettingsListener {
    fun settingsChanged()

    companion object {
        val TOPIC: Topic<StructuralDiffSettingsListener> = Topic.create(
            "Structural diff settings changed",
            StructuralDiffSettingsListener::class.java,
        )
    }
}

internal fun StructuralDiffToolConfig.accepts(fileName: String): Boolean {
    val candidates = filePatterns.orEmpty()
        .split(',', ';', ' ', '\n', '\t')
        .map(String::trim)
        .filter(String::isNotEmpty)
    if (candidates.isEmpty()) return false

    val baseName = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
    return candidates.any { glob -> WildcardFileNameMatcher(glob.lowercase()).acceptsCharSequence(baseName) }
}

internal fun StructuralDiffToolConfig.copyConfig(): StructuralDiffToolConfig = StructuralDiffToolConfig().also {
    it.name = name
    it.engineId = engineId
    it.executable = executable
    it.arguments = arguments
    it.environment = LinkedHashMap(environment)
    it.filePatterns = filePatterns
}

internal fun StructuralDiffToolConfig.snapshot(): List<Any?> =
    listOf(name, engineId, executable, arguments, environment.toSortedMap(), filePatterns)

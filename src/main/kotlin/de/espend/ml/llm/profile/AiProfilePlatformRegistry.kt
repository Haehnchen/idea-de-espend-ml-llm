package de.espend.ml.llm.profile

import de.espend.ml.llm.CommandPathUtils
import de.espend.ml.llm.PluginIcons
import javax.swing.Icon

enum class AiProfileApiType(
    val id: String,
    val label: String,
    val compatibilityLabel: String,
    val piFormat: String,
    val droidProvider: String
) {
    ANTHROPIC("anthropic", "Anthropic", "anthropic-like", "anthropic-messages", "anthropic"),
    OPENAI("openai", "OpenAI", "openai-like", "openai-completions", "generic-chat-completion-api");

    override fun toString(): String {
        return label
    }
}

enum class AiProfileTransport(
    val id: String
) {
    CLAUDE_ACP("claude-agent-acp"),
    PI("pi-acp"),
    DROID("droid"),
    GEMINI("gemini"),
    OPENCODE("opencode"),
    CURSOR("cursor"),
    KILO("kilo");

    override fun toString(): String {
        return id
    }
}

data class AiProfilePlatformEndpoint(
    val baseUrl: String? = null,
    val supportsCustomBaseUrl: Boolean = false
)

data class AiProfileTransportOption(
    val transport: AiProfileTransport,
    val apiType: AiProfileApiType? = null
) {
    val label: String
        get() = apiType?.let { "${transport.id} (${it.compatibilityLabel})" } ?: transport.id

    override fun toString(): String {
        return label
    }
}

data class AiProfilePlatformInfo(
    val id: String,
    val label: String,
    val icon: Icon,
    val directTransport: AiProfileTransport? = null,
    val defaultModel: String = "",
    val modelsUrl: String? = null,
    val anthropic: AiProfilePlatformEndpoint? = null,
    val openai: AiProfilePlatformEndpoint? = null
) {
    val showTransportSelector: Boolean
        get() = directTransport == null
}

object AiProfilePlatformRegistry {
    const val PLATFORM_CLAUDE_CODE = "claude-code"
    const val PLATFORM_PI_DIRECT = "pi-direct"
    const val PLATFORM_GEMINI = "gemini"
    const val PLATFORM_OPENCODE = "opencode"
    const val PLATFORM_CURSOR = "cursor"
    const val PLATFORM_KILO = "kilo"
    const val PLATFORM_FACTORY_AI = "factory-ai"
    const val PLATFORM_ANTHROPIC_COMPATIBLE = "anthropic-compatible"
    const val PLATFORM_OPENAI_COMPATIBLE = "openai-compatible"
    const val PLATFORM_ZAI = "zai"
    const val PLATFORM_MINIMAX = "minimax"
    const val PLATFORM_OPENROUTER = "openrouter"
    const val PLATFORM_MIMO = "mimo"
    const val PLATFORM_MOONSHOT = "moonshot"
    const val PLATFORM_REQUESTY = "requesty"
    const val PLATFORM_NANOGPT = "nano-gpt"
    const val PLATFORM_AIHUBMIX = "aihubmix"
    const val PLATFORM_OLLAMA = "ollama"
    const val PLATFORM_NVIDIA = "nvidia"

    val platforms: List<AiProfilePlatformInfo> = listOf(
        AiProfilePlatformInfo(
            id = PLATFORM_CLAUDE_CODE,
            label = "Claude Code",
            icon = PluginIcons.CLAUDE,
            directTransport = AiProfileTransport.CLAUDE_ACP
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_PI_DIRECT,
            label = "PI",
            icon = PluginIcons.PI,
            directTransport = AiProfileTransport.PI
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_GEMINI,
            label = "Gemini",
            icon = PluginIcons.GEMINI,
            directTransport = AiProfileTransport.GEMINI
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_OPENCODE,
            label = "OpenCode",
            icon = PluginIcons.OPENCODE,
            directTransport = AiProfileTransport.OPENCODE
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_CURSOR,
            label = "Cursor",
            icon = PluginIcons.CURSOR,
            directTransport = AiProfileTransport.CURSOR
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_KILO,
            label = "Kilo Code",
            icon = PluginIcons.KILO,
            directTransport = AiProfileTransport.KILO
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_FACTORY_AI,
            label = "Factory.ai",
            icon = PluginIcons.DROID,
            directTransport = AiProfileTransport.DROID
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_ANTHROPIC_COMPATIBLE,
            label = "Anthropic Compatible",
            icon = PluginIcons.ANTHROPIC,
            anthropic = AiProfilePlatformEndpoint(
                supportsCustomBaseUrl = true
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_OPENAI_COMPATIBLE,
            label = "OpenAI Compatible",
            icon = PluginIcons.AI_PROVIDER,
            openai = AiProfilePlatformEndpoint(
                supportsCustomBaseUrl = true
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_ZAI,
            label = "Z.AI",
            icon = PluginIcons.ZAI,
            defaultModel = "glm-4.7",
            modelsUrl = "https://api.z.ai/api/anthropic/v1/models",
            anthropic = AiProfilePlatformEndpoint(
                baseUrl = "https://api.z.ai/api/anthropic"
            ),
            openai = AiProfilePlatformEndpoint(
                baseUrl = "https://api.z.ai/api/coding/paas/v4"
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_MINIMAX,
            label = "MiniMax",
            icon = PluginIcons.MINIMAX,
            defaultModel = "MiniMax-M2.1",
            anthropic = AiProfilePlatformEndpoint(
                baseUrl = "https://api.minimax.io/anthropic"
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_OPENROUTER,
            label = "OpenRouter",
            icon = PluginIcons.OPENROUTER,
            defaultModel = "z-ai/glm-4.5-air:free",
            modelsUrl = "https://openrouter.ai/api/v1/models",
            anthropic = AiProfilePlatformEndpoint(
                baseUrl = "https://openrouter.ai/api"
            ),
            openai = AiProfilePlatformEndpoint(
                baseUrl = "https://openrouter.ai/api/v1"
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_MIMO,
            label = "Mimo",
            icon = PluginIcons.MIMO,
            defaultModel = "mimo-v2-flash",
            modelsUrl = "https://api.xiaomimimo.com/v1/models",
            anthropic = AiProfilePlatformEndpoint(
                baseUrl = "https://api.xiaomimimo.com/anthropic"
            ),
            openai = AiProfilePlatformEndpoint(
                baseUrl = "https://api.xiaomimimo.com/v1"
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_MOONSHOT,
            label = "Moonshot",
            icon = PluginIcons.MOONSHOT,
            defaultModel = "kimi-k2-thinking-turbo",
            modelsUrl = "https://api.moonshot.ai/v1/models",
            anthropic = AiProfilePlatformEndpoint(
                baseUrl = "https://api.moonshot.ai/anthropic"
            ),
            openai = AiProfilePlatformEndpoint(
                baseUrl = "https://api.moonshot.ai/v1"
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_REQUESTY,
            label = "Requesty.ai",
            icon = PluginIcons.REQUESTY,
            defaultModel = "zai/GLM-4.7",
            modelsUrl = "https://router.requesty.ai/v1/models",
            anthropic = AiProfilePlatformEndpoint(
                baseUrl = "https://router.requesty.ai/"
            ),
            openai = AiProfilePlatformEndpoint(
                baseUrl = "https://router.requesty.ai/v1"
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_NANOGPT,
            label = "Nano-GPT",
            icon = PluginIcons.NANOGPT,
            defaultModel = "gemini-3-pro-preview",
            modelsUrl = "https://nano-gpt.com/api/v1/models",
            anthropic = AiProfilePlatformEndpoint(
                baseUrl = "https://nano-gpt.com/api/v1"
            ),
            openai = AiProfilePlatformEndpoint(
                baseUrl = "https://nano-gpt.com/api/v1"
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_AIHUBMIX,
            label = "AIHubMix",
            icon = PluginIcons.AIHUBMIX,
            defaultModel = "gemini-3-flash-preview-free",
            modelsUrl = "https://aihubmix.com/v1/models",
            anthropic = AiProfilePlatformEndpoint(
                baseUrl = "https://aihubmix.com"
            ),
            openai = AiProfilePlatformEndpoint(
                baseUrl = "https://aihubmix.com/v1"
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_NVIDIA,
            label = "NVIDIA",
            icon = PluginIcons.NVIDIA,
            modelsUrl = "https://integrate.api.nvidia.com/v1/models",
            openai = AiProfilePlatformEndpoint(
                baseUrl = "https://integrate.api.nvidia.com/v1"
            )
        ),
        AiProfilePlatformInfo(
            id = PLATFORM_OLLAMA,
            label = "Ollama",
            icon = PluginIcons.OLLAMA,
            modelsUrl = "https://ollama.com/v1/models",
            anthropic = AiProfilePlatformEndpoint(
                baseUrl = "https://ollama.com"
            ),
            openai = AiProfilePlatformEndpoint(
                baseUrl = "https://ollama.com/v1/"
            )
        )
    )

    fun findPlatform(id: String): AiProfilePlatformInfo? {
        return platforms.firstOrNull { it.id == id }
    }

    fun findTransport(id: String): AiProfileTransport? {
        return AiProfileTransport.entries.firstOrNull { it.id == id }
    }

    fun findApiType(id: String): AiProfileApiType? {
        return AiProfileApiType.entries.firstOrNull { it.id == id }
    }

    fun defaultTransport(platformId: String): String {
        return defaultTransportOption(platformId)?.transport?.id ?: AiProfileTransport.PI.id
    }

    fun defaultApiType(platformId: String): String {
        return defaultTransportOption(platformId)?.apiType?.id.orEmpty()
    }

    fun defaultTransportOption(platformId: String): AiProfileTransportOption? {
        return findPlatform(platformId)?.let(::transportOptions)?.firstOrNull()
    }

    fun transportOptions(platform: AiProfilePlatformInfo): List<AiProfileTransportOption> {
        platform.directTransport?.let { return listOf(AiProfileTransportOption(it)) }

        return buildList {
            if (platform.anthropic != null) {
                add(AiProfileTransportOption(AiProfileTransport.CLAUDE_ACP, AiProfileApiType.ANTHROPIC))
                add(AiProfileTransportOption(AiProfileTransport.DROID, AiProfileApiType.ANTHROPIC))
                add(AiProfileTransportOption(AiProfileTransport.PI, AiProfileApiType.ANTHROPIC))
            }
            if (platform.openai != null) {
                add(AiProfileTransportOption(AiProfileTransport.DROID, AiProfileApiType.OPENAI))
                add(AiProfileTransportOption(AiProfileTransport.PI, AiProfileApiType.OPENAI))
            }
        }.sortedWith(
            compareBy<AiProfileTransportOption>(
                { it.transport.id },
                { it.apiType?.id.orEmpty() }
            )
        )
    }

    fun supportsExecutableOverride(transport: AiProfileTransport): Boolean {
        return when (transport) {
            AiProfileTransport.CLAUDE_ACP,
            AiProfileTransport.PI,
            AiProfileTransport.DROID,
            AiProfileTransport.GEMINI,
            AiProfileTransport.OPENCODE,
            AiProfileTransport.CURSOR,
            AiProfileTransport.KILO -> true
        }
    }

    fun defaultCommand(transport: AiProfileTransport): String {
        return when (transport) {
            AiProfileTransport.CLAUDE_ACP -> "claude"
            AiProfileTransport.PI -> "pi-acp"
            AiProfileTransport.DROID -> "droid"
            AiProfileTransport.GEMINI -> "gemini"
            AiProfileTransport.OPENCODE -> "opencode"
            AiProfileTransport.CURSOR -> "agent"
            AiProfileTransport.KILO -> "kilo"
        }
    }

    fun autoDetectExecutable(transport: AiProfileTransport): String? {
        return when (transport) {
            AiProfileTransport.CLAUDE_ACP -> CommandPathUtils.findClaudePath()
            AiProfileTransport.PI -> CommandPathUtils.findPiAcpPath()
            AiProfileTransport.DROID -> CommandPathUtils.findDroidPath()
            AiProfileTransport.GEMINI -> CommandPathUtils.findGeminiPath()
            AiProfileTransport.OPENCODE -> CommandPathUtils.findOpenCodePath()
            AiProfileTransport.CURSOR -> CommandPathUtils.findCursorAgentPath()
            AiProfileTransport.KILO -> CommandPathUtils.findKiloPath()
        }
    }

    fun transportHelpText(platform: AiProfilePlatformInfo, transport: AiProfileTransport): String {
        return when {
            platform.id == PLATFORM_CLAUDE_CODE && transport == AiProfileTransport.CLAUDE_ACP ->
                "Uses the local Claude Code account via Claude ACP."
            platform.id == PLATFORM_PI_DIRECT && transport == AiProfileTransport.PI ->
                "Runs pi-acp directly without generated API configuration."
            platform.id == PLATFORM_GEMINI && transport == AiProfileTransport.GEMINI ->
                "Uses the Gemini CLI directly via ACP."
            platform.id == PLATFORM_OPENCODE && transport == AiProfileTransport.OPENCODE ->
                "Uses the OpenCode CLI directly via ACP."
            platform.id == PLATFORM_CURSOR && transport == AiProfileTransport.CURSOR ->
                "Uses Cursor's built-in `agent acp` command."
            platform.id == PLATFORM_KILO && transport == AiProfileTransport.KILO ->
                "Uses the Kilo Code CLI via `kilo acp`."
            platform.id == PLATFORM_FACTORY_AI && transport == AiProfileTransport.DROID ->
                "Uses the Factory.ai Droid CLI directly."
            else -> ""
        }
    }

    fun droidProviderType(apiType: AiProfileApiType?): String {
        return apiType?.droidProvider ?: AiProfileApiType.ANTHROPIC.droidProvider
    }

    fun resolveTransportOption(
        platform: AiProfilePlatformInfo,
        transportId: String,
        apiTypeId: String
    ): AiProfileTransportOption? {
        val options = transportOptions(platform)

        return options.firstOrNull {
            it.transport.id == transportId && it.apiType?.id.orEmpty() == apiTypeId
        }
            ?: options.firstOrNull { it.transport.id == transportId && apiTypeId.isBlank() }
            ?: options.firstOrNull { it.transport.id == transportId }
            ?: options.firstOrNull()
    }

    fun resolveEndpoint(platform: AiProfilePlatformInfo, apiTypeId: String): AiProfilePlatformEndpoint? {
        val apiType = findApiType(apiTypeId)

        return when (apiType) {
            AiProfileApiType.ANTHROPIC -> platform.anthropic
            AiProfileApiType.OPENAI -> platform.openai
            null -> platform.anthropic ?: platform.openai
        }
    }

    fun describeTransportOption(
        platform: AiProfilePlatformInfo,
        transportId: String,
        apiTypeId: String
    ): String {
        return resolveTransportOption(platform, transportId, apiTypeId)?.label ?: transportId
    }

    fun getResolvedBaseUrl(endpoint: AiProfilePlatformEndpoint, configuredBaseUrl: String): String {
        return configuredBaseUrl.trim().ifEmpty { endpoint.baseUrl.orEmpty() }
    }

    fun getResolvedModelsUrl(
        platform: AiProfilePlatformInfo,
        endpoint: AiProfilePlatformEndpoint,
        configuredBaseUrl: String
    ): String? {
        platform.modelsUrl?.let { return it }
        if (!endpoint.supportsCustomBaseUrl) {
            return null
        }

        val baseUrl = getResolvedBaseUrl(endpoint, configuredBaseUrl)
        if (baseUrl.isEmpty()) {
            return null
        }

        val normalizedBaseUrl = baseUrl.trimEnd('/')
        return if (normalizedBaseUrl.endsWith("/v1")) {
            "$normalizedBaseUrl/models"
        } else {
            "$normalizedBaseUrl/v1/models"
        }
    }
}

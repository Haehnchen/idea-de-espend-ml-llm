package de.espend.ml.llm

object ProviderConfig {
    const val PROVIDER_ZAI = "z.ai"
    const val PROVIDER_MINIMAX = "minimax"
    const val PROVIDER_OPENROUTER = "openrouter"
    const val PROVIDER_MIMO = "mimo"
    const val PROVIDER_MOONSHOT = "moonshot"
    const val PROVIDER_DROID = "droid"
    const val PROVIDER_REQUESTY = "requesty"
    const val PROVIDER_NANO_GPT = "nano-gpt"
    const val PROVIDER_AIHUBMIX = "aihubmix"
    const val PROVIDER_ANTHROPIC_DEFAULT = "anthropic-default"
    const val PROVIDER_ANTHROPIC_COMPATIBLE = "anthropic-compatible"
    const val PROVIDER_GEMINI = "gemini"
    const val PROVIDER_OPENCODE = "opencode"
    const val PROVIDER_CURSOR = "cursor"

    val PROVIDERS = listOf(PROVIDER_ANTHROPIC_DEFAULT, PROVIDER_ANTHROPIC_COMPATIBLE, PROVIDER_GEMINI, PROVIDER_OPENCODE, PROVIDER_CURSOR, PROVIDER_DROID, PROVIDER_ZAI, PROVIDER_MINIMAX, PROVIDER_OPENROUTER, PROVIDER_MIMO, PROVIDER_MOONSHOT, PROVIDER_REQUESTY, PROVIDER_NANO_GPT, PROVIDER_AIHUBMIX)

    data class ProviderInfo(
        val label: String,
        val icon: javax.swing.Icon,
        val description: String,
        val baseUrl: String?,
        val models: Triple<String, String, String>,
        val autoDiscoveryText: String,
        val modelsUrl: String?,
        val registerUrl: String? = null
    )

    val PROVIDER_INFOS = mapOf(
        PROVIDER_ANTHROPIC_DEFAULT to ProviderInfo(
            label = "Claude CLI",
            icon = PluginIcons.CLAUDE,
            description = "Uses Claude Code's built-in Anthropic integration by default.",
            baseUrl = null,
            models = Triple("", "", ""),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        PROVIDER_ANTHROPIC_COMPATIBLE to ProviderInfo(
            label = "Anthropic Like",
            icon = PluginIcons.ANTHROPIC,
            description = "Supports any Anthropic-like API via npm install -g @zed-industries/claude-code-acp",
            baseUrl = null,
            models = Triple("", "", ""),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        PROVIDER_GEMINI to ProviderInfo(
            label = "Gemini",
            icon = PluginIcons.GEMINI,
            description = "Uses the Gemini CLI. Install: npm install -g @google/generative-ai-cli",
            baseUrl = null,
            models = Triple("", "", ""),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        PROVIDER_OPENCODE to ProviderInfo(
            label = "OpenCode",
            icon = PluginIcons.OPENCODE,
            description = "Uses the OpenCode CLI. Install: npm install -g opencode-cli",
            baseUrl = null,
            models = Triple("", "", ""),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        PROVIDER_CURSOR to ProviderInfo(
            label = "Cursor",
            icon = PluginIcons.CURSOR,
            description = "Uses the Cursor Agent CLI. Install: npm install -g @blowmage/cursor-agent-acp and curl https://cursor.com/install -fsSL | bash",
            baseUrl = null,
            models = Triple("", "", ""),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        PROVIDER_DROID to ProviderInfo(
            label = "Factory.ai",
            icon = PluginIcons.DROID,
            description = "Uses the Factory.ai Droid CLI. Install: curl -fsSL https://app.factory.ai/cli | sh",
            baseUrl = null,
            models = Triple("", "", ""),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        PROVIDER_ZAI to ProviderInfo(
            label = "Z.AI",
            icon = PluginIcons.ZAI,
            description = "Z.AI via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://api.z.ai/api/anthropic",
            models = Triple("glm-4.7", "glm-4.7", "glm-4.5-air"),
            autoDiscoveryText = "glm-4.7",
            modelsUrl = "https://api.z.ai/api/anthropic/v1/models",
            registerUrl = "https://z.ai/subscribe?ic=BCLQG4VJIO"
        ),
        PROVIDER_MINIMAX to ProviderInfo(
            label = "MiniMax",
            icon = PluginIcons.MINIMAX,
            description = "MiniMax via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://api.minimax.io/anthropic",
            models = Triple("MiniMax-M2.1", "MiniMax-M2.1", "MiniMax-M2.1"),
            autoDiscoveryText = "MiniMax-M2.1",
            modelsUrl = null
        ),
        PROVIDER_OPENROUTER to ProviderInfo(
            label = "OpenRouter",
            icon = PluginIcons.OPENROUTER,
            description = "OpenRouter via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://openrouter.ai/api",
            models = Triple("z-ai/glm-4.5-air:free", "z-ai/glm-4.5-air:free", "z-ai/glm-4.5-air:free"),
            autoDiscoveryText = "z-ai/glm-4.5-air:free",
            modelsUrl = "https://openrouter.ai/api/v1/models"
        ),
        PROVIDER_MIMO to ProviderInfo(
            label = "Mimo",
            icon = PluginIcons.MIMO,
            description = "Mimo via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://api.xiaomimimo.com/anthropic",
            models = Triple("mimo-v2-flash", "mimo-v2-flash", "mimo-v2-flash"),
            autoDiscoveryText = "mimo-v2-flash",
            modelsUrl = null
        ),
        PROVIDER_MOONSHOT to ProviderInfo(
            label = "Moonshot",
            icon = PluginIcons.MOONSHOT,
            description = "Moonshot via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://api.moonshot.ai/anthropic",
            models = Triple("kimi-k2-thinking-turbo", "kimi-k2-thinking-turbo", "kimi-k2-thinking-turbo"),
            autoDiscoveryText = "kimi-k2-thinking-turbo",
            modelsUrl = null
        ),
        PROVIDER_REQUESTY to ProviderInfo(
            label = "Requesty.ai",
            icon = PluginIcons.REQUESTY,
            description = "Requesty.ai via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://router.requesty.ai/",
            models = Triple("zai/GLM-4.7", "zai/GLM-4.7", "zai/GLM-4.7"),
            autoDiscoveryText = "zai/GLM-4.7",
            modelsUrl = "https://router.requesty.ai/v1/models"
        ),
        PROVIDER_NANO_GPT to ProviderInfo(
            label = "Nano-GPT",
            icon = PluginIcons.NANOGPT,
            description = "Nano-GPT via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://nano-gpt.com/api/v1",
            models = Triple("gemini-3-pro-preview", "gemini-3-pro-preview", "gemini-3-pro-preview"),
            autoDiscoveryText = "gemini-3-pro-preview",
            modelsUrl = "https://nano-gpt.com/api/v1/models"
        ),
        PROVIDER_AIHUBMIX to ProviderInfo(
            label = "AIHubMix",
            icon = PluginIcons.AIHUBMIX,
            description = "AIHubMix via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://aihubmix.com",
            models = Triple("gemini-3-flash-preview-free", "gemini-3-flash-preview-free", "gemini-3-flash-preview-free"),
            autoDiscoveryText = "gemini-3-flash-preview-free",
            modelsUrl = "https://aihubmix.com/v1/models"
        )
    )
}

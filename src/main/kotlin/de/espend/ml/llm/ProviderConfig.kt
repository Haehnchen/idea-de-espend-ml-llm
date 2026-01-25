package de.espend.ml.llm

object ProviderConfig {
    const val PROVIDER_ANTHROPIC_DEFAULT = "anthropic-default"
    const val PROVIDER_ANTHROPIC_COMPATIBLE = "anthropic-compatible"
    const val PROVIDER_GEMINI = "gemini"
    const val PROVIDER_OPENCODE = "opencode"
    const val PROVIDER_CURSOR = "cursor"
    const val PROVIDER_DROID = "droid"
    const val PROVIDER_ZAI = "zai"

    /**
     * Model IDs for different AI Assistant contexts.
     * @param smart Model ID for core features (e.g., code generation, commit message)
     * @param quick Model ID for instant helpers (e.g., chat title, name suggestions)
     */
    data class ModelIds(
        val smart: String,
        val quick: String
    )

    data class ProviderInfo(
        val provider: String,
        val label: String,
        val icon: javax.swing.Icon,
        val description: String,
        val baseUrl: String?,
        val models: Triple<String, String, String>,
        val modelIds: ModelIds,
        val autoDiscoveryText: String,
        val modelsUrl: String?,
        val registerUrl: String? = null
    )

    val PROVIDER_INFOS = arrayListOf(
        ProviderInfo(
            provider = PROVIDER_ANTHROPIC_DEFAULT,
            label = "Claude CLI",
            icon = PluginIcons.CLAUDE,
            description = "Uses Claude Code's built-in Anthropic integration by default.",
            baseUrl = null,
            models = Triple("", "", ""),
            modelIds = ModelIds(smart = "claude-sonnet-4-20250514", quick = "claude-sonnet-4-20250514"),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        ProviderInfo(
            provider = PROVIDER_ANTHROPIC_COMPATIBLE,
            label = "Anthropic Like",
            icon = PluginIcons.ANTHROPIC,
            description = "Supports any Anthropic-like API via npm install -g @zed-industries/claude-code-acp",
            baseUrl = null,
            models = Triple("", "", ""),
            modelIds = ModelIds(smart = "", quick = ""),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        ProviderInfo(
            provider = PROVIDER_GEMINI,
            label = "Gemini",
            icon = PluginIcons.GEMINI,
            description = "Uses the Gemini CLI. Install: npm install -g @google/generative-ai-cli",
            baseUrl = null,
            models = Triple("", "", ""),
            modelIds = ModelIds(smart = "gemini-2.5-pro", quick = "gemini-2.5-pro"),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        ProviderInfo(
            provider = PROVIDER_OPENCODE,
            label = "OpenCode",
            icon = PluginIcons.OPENCODE,
            description = "Uses the OpenCode CLI. Install: npm install -g opencode-cli",
            baseUrl = null,
            models = Triple("", "", ""),
            modelIds = ModelIds(smart = "", quick = ""),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        ProviderInfo(
            provider = PROVIDER_CURSOR,
            label = "Cursor",
            icon = PluginIcons.CURSOR,
            description = "Uses the Cursor Agent CLI. Install: npm install -g @blowmage/cursor-agent-acp and curl https://cursor.com/install -fsSL | bash",
            baseUrl = null,
            models = Triple("", "", ""),
            modelIds = ModelIds(smart = "", quick = ""),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        ProviderInfo(
            provider = PROVIDER_DROID,
            label = "Factory.ai",
            icon = PluginIcons.DROID,
            description = "Uses the Factory.ai Droid CLI. Install: curl -fsSL https://app.factory.ai/cli | sh",
            baseUrl = null,
            models = Triple("", "", ""),
            modelIds = ModelIds(smart = "", quick = ""),
            autoDiscoveryText = "",
            modelsUrl = null
        ),
        ProviderInfo(
            provider = PROVIDER_ZAI,
            label = "Z.AI",
            icon = PluginIcons.ZAI,
            description = "Z.AI via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://api.z.ai/api/anthropic",
            models = Triple("glm-4.7", "glm-4.7", "glm-4.5-air"),
            modelIds = ModelIds(smart = "glm-4.7", quick = "glm-4.5-air"),
            autoDiscoveryText = "glm-4.7",
            modelsUrl = "https://api.z.ai/api/anthropic/v1/models",
            registerUrl = "https://z.ai/subscribe?ic=BCLQG4VJIO"
        ),
        ProviderInfo(
            provider = "minimax",
            label = "MiniMax",
            icon = PluginIcons.MINIMAX,
            description = "MiniMax via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://api.minimax.io/anthropic",
            models = Triple("MiniMax-M2.1", "MiniMax-M2.1", "MiniMax-M2.1"),
            modelIds = ModelIds(smart = "MiniMax-M2.1", quick = "MiniMax-M2.1"),
            autoDiscoveryText = "MiniMax-M2.1",
            modelsUrl = null
        ),
        ProviderInfo(
            provider = "openrouter",
            label = "OpenRouter",
            icon = PluginIcons.OPENROUTER,
            description = "OpenRouter via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://openrouter.ai/api",
            models = Triple("z-ai/glm-4.5-air:free", "z-ai/glm-4.5-air:free", "z-ai/glm-4.5-air:free"),
            modelIds = ModelIds(smart = "z-ai/glm-4.5-air:free", quick = "z-ai/glm-4.5-air:free"),
            autoDiscoveryText = "z-ai/glm-4.5-air:free",
            modelsUrl = "https://openrouter.ai/api/v1/models"
        ),
        ProviderInfo(
            provider = "mimo",
            label = "Mimo",
            icon = PluginIcons.MIMO,
            description = "Mimo via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://api.xiaomimimo.com/anthropic",
            models = Triple("mimo-v2-flash", "mimo-v2-flash", "mimo-v2-flash"),
            modelIds = ModelIds(smart = "mimo-v2-flash", quick = "mimo-v2-flash"),
            autoDiscoveryText = "mimo-v2-flash",
            modelsUrl = null
        ),
        ProviderInfo(
            provider = "moonshot",
            label = "Moonshot",
            icon = PluginIcons.MOONSHOT,
            description = "Moonshot via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://api.moonshot.ai/anthropic",
            models = Triple("kimi-k2-thinking-turbo", "kimi-k2-thinking-turbo", "kimi-k2-thinking-turbo"),
            modelIds = ModelIds(smart = "kimi-k2-thinking-turbo", quick = "kimi-k2-thinking-turbo"),
            autoDiscoveryText = "kimi-k2-thinking-turbo",
            modelsUrl = null
        ),
        ProviderInfo(
            provider = "requesty",
            label = "Requesty.ai",
            icon = PluginIcons.REQUESTY,
            description = "Requesty.ai via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://router.requesty.ai/",
            models = Triple("zai/GLM-4.7", "zai/GLM-4.7", "zai/GLM-4.7"),
            modelIds = ModelIds(smart = "zai/GLM-4.7", quick = "zai/GLM-4.7"),
            autoDiscoveryText = "zai/GLM-4.7",
            modelsUrl = "https://router.requesty.ai/v1/models"
        ),
        ProviderInfo(
            provider = "nano-gpt",
            label = "Nano-GPT",
            icon = PluginIcons.NANOGPT,
            description = "Nano-GPT via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://nano-gpt.com/api/v1",
            models = Triple("gemini-3-pro-preview", "gemini-3-pro-preview", "gemini-3-pro-preview"),
            modelIds = ModelIds(smart = "gemini-3-pro-preview", quick = "gemini-3-pro-preview"),
            autoDiscoveryText = "gemini-3-pro-preview",
            modelsUrl = "https://nano-gpt.com/api/v1/models"
        ),
        ProviderInfo(
            provider = "aihubmix",
            label = "AIHubMix",
            icon = PluginIcons.AIHUBMIX,
            description = "AIHubMix via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp",
            baseUrl = "https://aihubmix.com",
            models = Triple("gemini-3-flash-preview-free", "gemini-3-flash-preview-free", "gemini-3-flash-preview-free"),
            modelIds = ModelIds(smart = "gemini-3-flash-preview-free", quick = "gemini-3-flash-preview-free"),
            autoDiscoveryText = "gemini-3-flash-preview-free",
            modelsUrl = "https://aihubmix.com/v1/models"
        )
    )

    fun findProviderInfo(provider: String): ProviderInfo? {
        return PROVIDER_INFOS.firstOrNull { it.provider == provider }
    }
}

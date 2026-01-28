package de.espend.ml.llm

import com.intellij.ml.llm.agents.ChatAgent
import com.intellij.ml.llm.agents.acp.config.AgentServerConfig
import com.intellij.ml.llm.agents.acp.config.AcpAgentConfig
import com.intellij.ml.llm.agents.acp.config.DefaultMcpSettings
import com.intellij.ml.llm.agents.acp.config.LocalAcpAgentConfig
import com.intellij.ml.llm.agents.acp.registry.AcpCustomAgentId
import com.intellij.ml.llm.agents.acp.registry.DynamicAcpChatAgent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.util.xmlb.XmlSerializerUtil

private val LOG = Logger.getInstance(AgentRegistry::class.java)

/**
 * AI Provider Agent Registry - manages dynamically registered ChatAgents.
 *
 * Works like acp.json, but with persistent configuration via IDE settings.
 * Each agent is registered as a DynamicAcpChatAgent via the ChatAgent Extension Point.
 */
@Service(Service.Level.APP)
@State(
    name = "AgentRegistry",
    storages = [Storage("ai-agents.xml")]
)
class AgentRegistry : PersistentStateComponent<AgentRegistry.State>, Disposable {

    private val registeredAgents = mutableMapOf<String, AgentRegistration>()

    data class AgentRegistration(
        val agent: ChatAgent,
        val disposable: Disposable
    )

    /**
     * Persistent state for agent configurations.
     */
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
        // Register all saved agents
        reloadAgents()
    }

    /**
     * All currently configured agents.
     */
    val agentConfigs: List<AgentConfig>
        get() = myState.agents.toList()

    /**
     * Reloads all agents based on the current state.
     */
    fun reloadAgents() {
        // Unregister all existing agents
        unregisterAllAgents()

        // Register all enabled agents
        myState.agents.filter { it.isEnabled }.forEach { config ->
            try {
                registerAgentInternal(config)
            } catch (e: Exception) {
                LOG.error("Failed to register AI provider agent: ${config.id}", e)
            }
        }

        LOG.info("AI provider agents reloaded: ${registeredAgents.size} active")
    }

    /**
     * Adds a new agent and registers it.
     */
    fun addAgent(config: AgentConfig) {
        myState.agents.add(config)
        if (config.isEnabled) {
            registerAgentInternal(config)
        }
    }

    /**
     * Removes an agent.
     */
    fun removeAgent(agentId: String) {
        unregisterAgentInternal(agentId)
        myState.agents.removeAll { it.id == agentId }
    }

    /**
     * Creates an AgentServerConfig based on the provider.
     */
    private fun createAgentServerConfig(config: AgentConfig): AgentServerConfig {
        val providerInfo = ProviderConfig.findProviderInfo(config.provider)
            ?: ProviderConfig.findProviderInfo(ProviderConfig.PROVIDER_ZAI)!!
        val baseUrl = when (config.provider) {
            ProviderConfig.PROVIDER_ANTHROPIC_DEFAULT -> ""
            ProviderConfig.PROVIDER_ANTHROPIC_COMPATIBLE -> config.baseUrl
            ProviderConfig.PROVIDER_GEMINI, ProviderConfig.PROVIDER_OPENCODE, ProviderConfig.PROVIDER_CURSOR, ProviderConfig.PROVIDER_DROID -> ""
            else -> providerInfo.baseUrl ?: ""
        }
        val apiKey = when (config.provider) {
            ProviderConfig.PROVIDER_ANTHROPIC_DEFAULT,
            ProviderConfig.PROVIDER_GEMINI,
            ProviderConfig.PROVIDER_OPENCODE,
            ProviderConfig.PROVIDER_CURSOR,
            ProviderConfig.PROVIDER_DROID -> ""
            else -> config.apiKey
        }
        val models = providerInfo.models

        // Build base env with CLAUDE_CODE_EXECUTABLE if set
        fun buildBaseEnv(): Map<String, String> {
            return myState.claudeCodeExecutable?.let { executablePath ->
                mapOf("CLAUDE_CODE_EXECUTABLE" to executablePath)
            } ?: emptyMap()
        }

        // Anthropic Default uses built-in Claude Code integration without any custom env vars
        if (config.provider == ProviderConfig.PROVIDER_ANTHROPIC_DEFAULT) {
            val acpPath = CommandPathUtils.findClaudeCodeAcpPath() ?: "claude-code-acp"
            return AgentServerConfig(
                command = acpPath,
                args = emptyList(),
                env = buildBaseEnv()
            )
        }

        // OpenCode uses special command and args
        if (config.provider == ProviderConfig.PROVIDER_OPENCODE) {
            val opencodePath = if (config.executable.isNotEmpty()) {
                config.executable
            } else {
                CommandPathUtils.findOpenCodePath() ?: "opencode"
            }
            return AgentServerConfig(
                command = opencodePath,
                args = listOf("acp"),
                env = buildBaseEnv()
            )
        }

        // Cursor uses special command
        if (config.provider == ProviderConfig.PROVIDER_CURSOR) {
            val cursorPath = if (config.executable.isNotEmpty()) {
                config.executable
            } else {
                CommandPathUtils.findCursorAgentAcpPath() ?: "cursor-agent-acp"
            }
            return AgentServerConfig(
                command = cursorPath,
                args = emptyList(),
                env = buildBaseEnv()
            )
        }

        // Droid (Factory.ai) uses special command and args
        if (config.provider == ProviderConfig.PROVIDER_DROID) {
            val droidPath = if (config.executable.isNotEmpty()) {
                config.executable
            } else {
                CommandPathUtils.findDroidPath() ?: "droid"
            }
            return AgentServerConfig(
                command = droidPath,
                args = listOf("exec", "--output-format", "acp"),
                env = buildBaseEnv()
            )
        }

        // Gemini uses special command
        if (config.provider == ProviderConfig.PROVIDER_GEMINI) {
            val geminiPath = if (config.executable.isNotEmpty()) {
                config.executable
            } else {
                CommandPathUtils.findGeminiPath() ?: "gemini"
            }
            return AgentServerConfig(
                command = geminiPath,
                args = listOf("--experimental-acp"),
                env = buildBaseEnv()
            )
        }

        // Standard Anthropic-based providers
        return AgentServerConfig(
            command = CommandPathUtils.findClaudeCodeAcpPath() ?: "claude-code-acp",
            args = emptyList(),
            env = buildMap {
                putAll(buildBaseEnv())
                put("ANTHROPIC_AUTH_TOKEN", apiKey)
                if (baseUrl.isNotEmpty()) {
                    put("ANTHROPIC_BASE_URL", baseUrl)
                }
                put("API_TIMEOUT_MS", "3000000")
                // Use the user-defined model if set, otherwise fallback to provider default
                val modelToUse = if (config.model.isNotEmpty()) {
                    config.model
                } else {
                    models.first  // Fallback to default
                }
                put("ANTHROPIC_DEFAULT_HAIKU_MODEL", modelToUse)
                put("ANTHROPIC_DEFAULT_SONNET_MODEL", modelToUse)
                put("ANTHROPIC_DEFAULT_OPUS_MODEL", modelToUse)
                put("ANTHROPIC_API_KEY", "")
            }
        )
    }

    /**
     * Registers an agent dynamically via the ChatAgent Extension Point.
     */
    private fun registerAgentInternal(config: AgentConfig) {
        LOG.info("Registering dynamic agent: ${config.provider} (id: ${config.id})")

        val serverConfig = createAgentServerConfig(config)
        val defaultMcpSettings = DefaultMcpSettings(true, true)
        val providerName = ProviderConfig.findProviderInfo(config.provider)?.label ?: config.provider
        val acpAgentConfig = LocalAcpAgentConfig.fromServerConfig(config.provider, serverConfig, defaultMcpSettings)
        val acpAgentId = AcpCustomAgentId(config.id)
        val delegate = DynamicAcpChatAgent(
            providerName,
            acpAgentConfig,
            acpAgentId
        )
        val agent = ProviderChatAgent(delegate, PluginIcons.getIconForProvider(config.provider))

        // Register dynamically via Extension Point
        val ep: ExtensionPoint<ChatAgent> = ApplicationManager.getApplication()
            .extensionArea
            .getExtensionPoint(ChatAgent.EP_NAME)

        val disposable = Disposer.newDisposable("ai-provider-agent:${config.id}")
        ep.registerExtension(agent, disposable)

        registeredAgents[config.id] = AgentRegistration(agent, disposable)
        LOG.info("Successfully registered agent: ${config.provider}")
    }

    /**
     * Unregisters a single agent.
     */
    private fun unregisterAgentInternal(agentId: String) {
        val registration = registeredAgents.remove(agentId)
        if (registration != null) {
            LOG.info("Unregistering agent: ${registration.agent.name}")
            Disposer.dispose(registration.disposable)
        }
    }

    /**
     * Unregisters all agents.
     */
    private fun unregisterAllAgents() {
        LOG.info("Unregistering all AI provider agents (${registeredAgents.size})")
        registeredAgents.values.forEach { registration ->
            try {
                Disposer.dispose(registration.disposable)
            } catch (e: Exception) {
                LOG.error("Failed to dispose agent: ${registration.agent.name}", e)
            }
        }
        registeredAgents.clear()
    }

    override fun dispose() {
        unregisterAllAgents()
    }
}

/**
 * Startup Activity that initializes the AgentRegistry service.
 * This loads saved agents when the IDE starts.
 */
class AgentStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        AgentRegistry.getInstance()
    }
}

package de.espend.ml.llm

import com.intellij.ml.llm.agents.ChatAgent
import com.intellij.ml.llm.agents.acp.config.AgentServerConfig
import com.intellij.ml.llm.agents.acp.config.DefaultMcpSettings
import com.intellij.ml.llm.agents.acp.config.LocalAcpAgentConfig
import com.intellij.ml.llm.agents.acp.registry.AcpAgentFactory
import com.intellij.ml.llm.agents.acp.registry.AcpCustomAgentId
import com.intellij.ml.llm.agents.acp.registry.AcpAgentInstallationState
import com.intellij.ml.llm.agents.acp.registry.AcpPaths
import com.intellij.ml.llm.agents.acp.registry.AcpDistributionResolver
import com.intellij.ml.llm.agents.acp.registry.AcpRegistryAgentId
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
import java.nio.file.Files

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
        val baseUrl = when {
            ProviderConfig.isClaudeNativeProvider(config.provider) -> ""
            config.provider == ProviderConfig.PROVIDER_ANTHROPIC_COMPATIBLE -> config.baseUrl
            ProviderConfig.usesPiAcp(config.provider) -> config.baseUrl
            ProviderConfig.usesCustomClaudeAcpEnv(config.provider) -> providerInfo.baseUrl ?: ""
            config.provider == ProviderConfig.PROVIDER_GEMINI ||
                config.provider == ProviderConfig.PROVIDER_OPENCODE ||
                config.provider == ProviderConfig.PROVIDER_CURSOR ||
                config.provider == ProviderConfig.PROVIDER_KILO ||
                config.provider == ProviderConfig.PROVIDER_DROID -> ""
            else -> providerInfo.baseUrl ?: ""
        }
        val models = providerInfo.models

        // Build base env with CLAUDE_CODE_EXECUTABLE if set
        fun buildBaseEnv(): Map<String, String> {
            return myState.claudeCodeExecutable?.let { executablePath ->
                mapOf("CLAUDE_CODE_EXECUTABLE" to executablePath)
            } ?: emptyMap()
        }

        fun buildClaudeAcpEnv(): Map<String, String> {
            return buildMap {
                putAll(buildBaseEnv())
                if (ProviderConfig.usesCustomClaudeAcpEnv(config.provider)) {
                    put("ANTHROPIC_AUTH_TOKEN", config.apiKey)
                    if (baseUrl.isNotEmpty()) {
                        put("ANTHROPIC_BASE_URL", baseUrl)
                    }
                    put("API_TIMEOUT_MS", "3000000")

                    val modelToUse = if (config.model.isNotEmpty()) {
                        config.model
                    } else {
                        models.first
                    }

                    put("ANTHROPIC_DEFAULT_HAIKU_MODEL", modelToUse)
                    put("ANTHROPIC_DEFAULT_SONNET_MODEL", modelToUse)
                    put("ANTHROPIC_DEFAULT_OPUS_MODEL", modelToUse)
                    put("ANTHROPIC_API_KEY", "")
                }
            }
        }

        if (ProviderConfig.usesPiAcp(config.provider)) {
            return createPiAcpServerConfig(config, buildBaseEnv())
        }

        if (ProviderConfig.usesClaudeAcp(config.provider)) {
            return createClaudeAcpServerConfig(config, buildClaudeAcpEnv())
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

        // Kilo Code uses `kilo acp` subcommand
        if (config.provider == ProviderConfig.PROVIDER_KILO) {
            val kiloPath = if (config.executable.isNotEmpty()) {
                config.executable
            } else {
                CommandPathUtils.findKiloPath() ?: "kilo"
            }
            return AgentServerConfig(
                command = kiloPath,
                args = listOf("acp"),
                env = buildBaseEnv()
            )
        }

        // Cursor uses built-in ACP via `agent acp` subcommand
        if (config.provider == ProviderConfig.PROVIDER_CURSOR) {
            val agentPath = if (config.executable.isNotEmpty()) {
                config.executable
            } else {
                CommandPathUtils.findCursorAgentPath() ?: "agent"
            }
            return AgentServerConfig(
                command = agentPath,
                args = listOf("acp"),
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

        error("Unsupported provider: ${config.provider}")
    }

    private fun createClaudeAcpServerConfig(config: AgentConfig, env: Map<String, String>): AgentServerConfig {
        resolveInstalledClaudeAcpServerConfig(config, env)?.let { return it }

        return AgentServerConfig(
            command = CommandPathUtils.findClaudeAgentAcpPath() ?: "claude-agent-acp",
            args = emptyList(),
            env = env
        )
    }

    private fun createPiAcpServerConfig(config: AgentConfig, baseEnv: Map<String, String>): AgentServerConfig {
        val tempAgentDir = PiAcpTempConfig.createTempAgentDir(config)
        val env = buildMap {
            putAll(baseEnv)
            put("PI_CODING_AGENT_DIR", tempAgentDir.toString())
            put("API_KEY", config.apiKey)
        }

        resolveInstalledPiAcpServerConfig(config, env)?.let { return it }

        return AgentServerConfig(
            command = CommandPathUtils.findPiAcpPath() ?: "pi-acp",
            args = emptyList(),
            env = env
        )
    }

    private fun resolveInstalledClaudeAcpServerConfig(config: AgentConfig, env: Map<String, String>): AgentServerConfig? {
        val registryAgentId = ProviderConfig.registryAgentIdForProvider(config.provider) ?: return null
        val installedAgent = ApplicationManager.getApplication()
            .getService(AcpAgentInstallationState::class.java)
            ?.getInstalledAgent(AcpRegistryAgentId(registryAgentId))
            ?: return null

        return when (val resolved = AcpDistributionResolver.resolve(installedAgent)) {
            is AcpDistributionResolver.ResolvedDistribution.Package -> {
                val startConfig = AcpDistributionResolver.toAgentStartConfig(resolved)
                LOG.info("Using installed ACP registry package for provider ${config.provider}")
                AgentServerConfig(
                    command = startConfig.command,
                    args = startConfig.args,
                    env = buildMap {
                        putAll(startConfig.env)
                        putAll(env)
                    }
                )
            }

            is AcpDistributionResolver.ResolvedDistribution.Binary -> {
                LOG.info("Installed ACP registry agent for provider ${config.provider} uses binary distribution; falling back to manual command resolution")
                null
            }

            is AcpDistributionResolver.ResolvedDistribution.Unavailable -> {
                LOG.warn("Installed ACP registry agent for provider ${config.provider} is unavailable: ${resolved.reason}; falling back to manual command resolution")
                null
            }
        }
    }

    private fun resolveInstalledPiAcpServerConfig(config: AgentConfig, env: Map<String, String>): AgentServerConfig? {
        val registryAgentId = ProviderConfig.registryAgentIdForProvider(config.provider) ?: return null
        val installedAgent = ApplicationManager.getApplication()
            .getService(AcpAgentInstallationState::class.java)
            ?.getInstalledAgent(AcpRegistryAgentId(registryAgentId))
            ?: return null

        return when (val resolved = AcpDistributionResolver.resolve(installedAgent)) {
            is AcpDistributionResolver.ResolvedDistribution.Package -> {
                val startConfig = AcpDistributionResolver.toAgentStartConfig(resolved)
                LOG.info("Using installed ACP registry package for provider ${config.provider}")
                AgentServerConfig(
                    command = startConfig.command,
                    args = startConfig.args,
                    env = buildMap {
                        putAll(startConfig.env)
                        putAll(env)
                    }
                )
            }

            is AcpDistributionResolver.ResolvedDistribution.Binary -> {
                val acpPaths = ApplicationManager.getApplication().getService(AcpPaths::class.java) ?: return null
                val extractedPath = acpPaths.getAgentVersionDir(AcpRegistryAgentId(registryAgentId), installedAgent.version)
                val startConfig = AcpDistributionResolver.toAgentStartConfig(resolved, extractedPath.toString())

                if (!Files.isExecutable(java.nio.file.Path.of(startConfig.command))) {
                    LOG.info("Installed ACP registry binary for provider ${config.provider} is not ready at ${startConfig.command}; falling back to manual command resolution")
                    return null
                }

                LOG.info("Using installed ACP registry binary for provider ${config.provider}")
                AgentServerConfig(
                    command = startConfig.command,
                    args = startConfig.args,
                    env = buildMap {
                        putAll(startConfig.env)
                        putAll(env)
                    }
                )
            }

            is AcpDistributionResolver.ResolvedDistribution.Unavailable -> {
                LOG.warn("Installed ACP registry agent for provider ${config.provider} is unavailable: ${resolved.reason}; falling back to manual command resolution")
                null
            }
        }
    }

    private fun resolveInstalledRegistryAgentConfig(config: AgentConfig): LocalAcpAgentConfig? {
        if (!ProviderConfig.supportsRegistryFallback(config.provider)) {
            return null
        }

        if (ProviderConfig.usesPiAcp(config.provider)) {
            return null
        }

        if (ProviderConfig.usesCustomClaudeAcpEnv(config.provider)) {
            return null
        }

        if (config.executable.isNotEmpty()) {
            return null
        }

        val registryAgentId = ProviderConfig.registryAgentIdForProvider(config.provider) ?: return null
        val installedAgent = ApplicationManager.getApplication()
            .getService(AcpAgentInstallationState::class.java)
            ?.getInstalledAgent(AcpRegistryAgentId(registryAgentId))
            ?: return null

        val registryConfig = AcpAgentFactory.createAgentConfig(installedAgent)
        if (registryConfig != null) {
            LOG.info("Using installed ACP registry agent config for provider ${config.provider}")
        } else {
            LOG.warn("Installed ACP registry agent config for provider ${config.provider} could not be resolved, falling back to manual command")
        }

        return registryConfig
    }

    private fun createAcpAgentConfig(config: AgentConfig): LocalAcpAgentConfig {
        resolveInstalledRegistryAgentConfig(config)?.let { return it }

        val serverConfig = createAgentServerConfig(config)
        val defaultMcpSettings = DefaultMcpSettings(useCustomMcp = true, useIdeaMcp = true)
        return LocalAcpAgentConfig.fromServerConfig(config.provider, serverConfig, defaultMcpSettings)
    }

    /**
     * Registers an agent dynamically via the ChatAgent Extension Point.
     */
    private fun registerAgentInternal(config: AgentConfig) {
        LOG.info("Registering dynamic agent: ${config.provider} (id: ${config.id})")

        val providerName = ProviderConfig.findProviderInfo(config.provider)?.label ?: config.provider
        val acpAgentConfig = createAcpAgentConfig(config)
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

package de.espend.ml.llm.profile

import com.intellij.ml.llm.agents.ChatAgent
import com.intellij.ml.llm.agents.acp.config.AgentServerConfig
import com.intellij.ml.llm.agents.acp.config.DefaultMcpSettings
import com.intellij.ml.llm.agents.acp.config.LocalAcpAgentConfig
import com.intellij.ml.llm.agents.acp.registry.AcpAgentInstallationState
import com.intellij.ml.llm.agents.acp.registry.AcpCustomAgentId
import com.intellij.ml.llm.agents.acp.registry.AcpDistributionResolver
import com.intellij.ml.llm.agents.acp.registry.AcpPaths
import com.intellij.ml.llm.agents.acp.registry.AcpRegistryAgentId
import com.intellij.ml.llm.agents.acp.registry.DynamicAcpChatAgent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.util.xmlb.XmlSerializerUtil
import de.espend.ml.llm.CommandPathUtils
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.ProviderChatAgent
import java.nio.file.Files

private val LOG = Logger.getInstance(AiProfileRegistry::class.java)

@Service(Service.Level.APP)
@State(
    name = "AiProfileRegistry",
    storages = [Storage("ai-profiles.xml")]
)
class AiProfileRegistry : PersistentStateComponent<AiProfileRegistry.State>, Disposable {

    data class Registration(
        val agent: ChatAgent,
        val disposable: Disposable
    )

    class State {
        var profiles: MutableList<AiProfileConfig> = mutableListOf()
    }

    private val registeredProfiles = mutableMapOf<String, Registration>()
    private var myState = State()

    companion object {
        fun getInstance(): AiProfileRegistry {
            return ApplicationManager.getApplication().getService(AiProfileRegistry::class.java)
        }
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
        reloadProfiles()
    }

    val currentState: State
        get() = myState

    fun containsProfile(profileId: String): Boolean {
        val normalizedAgentId = normalizeAgentId(profileId)
        return myState.profiles.any { normalizedAgentId == it.id || normalizedAgentId.startsWith("${it.id}--") }
    }

    fun replaceProfiles(profiles: List<AiProfileConfig>) {
        myState.profiles = profiles.toMutableList()
        reloadProfiles()
    }

    fun reloadProfiles() {
        unregisterAllProfiles()

        myState.profiles
            .filter { it.isEnabled }
            .forEach { profile ->
                try {
                    registerProfile(profile)
                } catch (e: Exception) {
                    LOG.error("Failed to register AI profile ${profile.id}", e)
                }
            }

        LOG.info("AI profiles reloaded: ${registeredProfiles.size} active")
    }

    private fun registerProfile(profile: AiProfileConfig) {
        val platform = AiProfilePlatformRegistry.findPlatform(profile.platform)
            ?: error("Unknown AI profile platform: ${profile.platform}")

        val runtimeAgentId = runtimeAgentId(profile)
        val acpConfig = createAcpAgentConfig(profile, platform)
        val displayName = resolveDisplayName(profile, platform)
        val delegate = DynamicAcpChatAgent(
            displayName,
            acpConfig,
            AcpCustomAgentId(runtimeAgentId)
        )
        val agent = ProviderChatAgent(delegate, PluginIcons.scaleIcon(platform.icon, 16))

        val ep: ExtensionPoint<ChatAgent> = ApplicationManager.getApplication()
            .extensionArea
            .getExtensionPoint(ChatAgent.EP_NAME)

        val disposable = Disposer.newDisposable("ai-profile:$runtimeAgentId")
        ep.registerExtension(agent, disposable)
        registeredProfiles[profile.id] = Registration(agent, disposable)
    }

    private fun createAcpAgentConfig(
        profile: AiProfileConfig,
        platform: AiProfilePlatformInfo
    ): LocalAcpAgentConfig {
        val serverConfig = createServerConfig(profile, platform)
        val defaultMcpSettings = DefaultMcpSettings(useCustomMcp = true, useIdeaMcp = true)
        return LocalAcpAgentConfig.fromServerConfig(profile.id, serverConfig, defaultMcpSettings)
    }

    private fun createServerConfig(
        profile: AiProfileConfig,
        platform: AiProfilePlatformInfo
    ): AgentServerConfig {
        val transportOption = AiProfilePlatformRegistry.resolveTransportOption(
            platform,
            profile.effectiveTransport(),
            profile.effectiveApiType()
        ) ?: error("Unknown AI profile transport: ${profile.transport}")

        return when (transportOption.transport) {
            AiProfileTransport.CLAUDE_ACP -> createClaudeAcpServerConfig(profile, platform, transportOption)
            AiProfileTransport.PI -> createPiAcpServerConfig(profile, platform, transportOption)
            AiProfileTransport.DROID -> createDroidServerConfig(profile, platform, transportOption)
        }
    }

    private fun createClaudeAcpServerConfig(
        profile: AiProfileConfig,
        platform: AiProfilePlatformInfo,
        transportOption: AiProfileTransportOption
    ): AgentServerConfig {
        val endpoint = AiProfilePlatformRegistry.resolveEndpoint(platform, transportOption.apiType?.id.orEmpty())
        val env = buildMap<String, String> {
            putAll(buildBaseEnv(profile))

            if (transportOption.apiType == AiProfileApiType.ANTHROPIC && endpoint != null) {
                val resolvedBaseUrl = resolveBaseUrl(profile, endpoint)
                val resolvedModel = resolvePrimaryModel(profile, platform)

                if (profile.apiKey.isNotBlank()) {
                    put("ANTHROPIC_AUTH_TOKEN", profile.apiKey.trim())
                    put("ANTHROPIC_API_KEY", "")
                }
                if (resolvedBaseUrl.isNotBlank()) {
                    put("ANTHROPIC_BASE_URL", resolvedBaseUrl)
                }
                if (resolvedModel.isNotBlank()) {
                    put("ANTHROPIC_DEFAULT_HAIKU_MODEL", resolvedModel)
                    put("ANTHROPIC_DEFAULT_SONNET_MODEL", resolvedModel)
                    put("ANTHROPIC_DEFAULT_OPUS_MODEL", resolvedModel)
                }
                put("API_TIMEOUT_MS", "3000000")
            }
        }

        resolveInstalledTransportServerConfig(AiProfileTransport.CLAUDE_ACP, env)?.let { return it }

        return AgentServerConfig(
            command = CommandPathUtils.findClaudeAgentAcpPath() ?: "claude-agent-acp",
            args = emptyList(),
            env = env
        )
    }

    private fun createPiAcpServerConfig(
        profile: AiProfileConfig,
        platform: AiProfilePlatformInfo,
        transportOption: AiProfileTransportOption
    ): AgentServerConfig {
        val endpoint = AiProfilePlatformRegistry.resolveEndpoint(platform, transportOption.apiType?.id.orEmpty())
        val env = buildMap {
            if (endpoint != null) {
                val baseUrl = resolveBaseUrl(profile, endpoint)
                val models = resolveModels(profile, platform)
                val tempAgentDir = AiProfilePiAcpTempConfig.createTempAgentDir(
                    profileId = profile.id,
                    format = (transportOption.apiType ?: AiProfileApiType.ANTHROPIC).piFormat,
                    baseUrl = baseUrl,
                    modelIds = models
                )
                put("PI_CODING_AGENT_DIR", tempAgentDir.toString())
                put("API_KEY", profile.apiKey.trim())
            }
        }

        resolveInstalledTransportServerConfig(AiProfileTransport.PI, env)?.let { return it }

        return AgentServerConfig(
            command = CommandPathUtils.findPiAcpPath() ?: "pi-acp",
            args = emptyList(),
            env = env
        )
    }

    private fun createDroidServerConfig(
        profile: AiProfileConfig,
        platform: AiProfilePlatformInfo,
        transportOption: AiProfileTransportOption
    ): AgentServerConfig {
        val endpoint = AiProfilePlatformRegistry.resolveEndpoint(platform, transportOption.apiType?.id.orEmpty())
            ?: error("Droid transport requires an API endpoint for platform ${platform.id}")
        val baseUrl = resolveBaseUrl(profile, endpoint)
        val modelIds = resolveModels(profile, platform)
        val tempHome = AiProfileDroidTempConfig.createTempHome(
            profileId = profile.id,
            providerType = AiProfilePlatformRegistry.droidProviderType(transportOption.apiType),
            baseUrl = baseUrl,
            modelIds = modelIds
        )
        val customModelId = AiProfileDroidTempConfig.customModelId(profile.id, 0)
        val env = buildMap {
            put("HOME", tempHome.toString())
            put("FACTORY_API_KEY", "fk-fake-invalid-token")
            put("FACTORY_CUSTOM_API_KEY", profile.apiKey.trim())
        }

        resolveInstalledDroidServerConfig(customModelId, env)?.let { return it }

        return AgentServerConfig(
            command = CommandPathUtils.findDroidPath() ?: "droid",
            args = listOf(
                "exec",
                "--output-format",
                "acp",
                "--model",
                customModelId
            ),
            env = env
        )
    }

    private fun resolveInstalledDroidServerConfig(
        customModelId: String,
        env: Map<String, String>
    ): AgentServerConfig? {
        val registryAgentId = registryAgentIdForTransport(AiProfileTransport.DROID) ?: return null
        val installedAgent = ApplicationManager.getApplication()
            .getService(AcpAgentInstallationState::class.java)
            ?.getInstalledAgent(AcpRegistryAgentId(registryAgentId))
            ?: return null

        return when (val resolved = AcpDistributionResolver.resolve(installedAgent)) {
            is AcpDistributionResolver.ResolvedDistribution.Package -> {
                val startConfig = AcpDistributionResolver.toAgentStartConfig(resolved)
                AgentServerConfig(
                    command = startConfig.command,
                    args = startConfig.args + listOf("--model", customModelId),
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
                    return null
                }

                AgentServerConfig(
                    command = startConfig.command,
                    args = startConfig.args + listOf("--model", customModelId),
                    env = buildMap {
                        putAll(startConfig.env)
                        putAll(env)
                    }
                )
            }

            is AcpDistributionResolver.ResolvedDistribution.Unavailable -> null
        }
    }

    private fun resolveInstalledTransportServerConfig(
        transport: AiProfileTransport,
        env: Map<String, String>
    ): AgentServerConfig? {
        val registryAgentId = registryAgentIdForTransport(transport) ?: return null
        val installedAgent = ApplicationManager.getApplication()
            .getService(AcpAgentInstallationState::class.java)
            ?.getInstalledAgent(AcpRegistryAgentId(registryAgentId))
            ?: return null

        return when (val resolved = AcpDistributionResolver.resolve(installedAgent)) {
            is AcpDistributionResolver.ResolvedDistribution.Package -> {
                val startConfig = AcpDistributionResolver.toAgentStartConfig(resolved)
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
                    return null
                }

                AgentServerConfig(
                    command = startConfig.command,
                    args = startConfig.args,
                    env = buildMap {
                        putAll(startConfig.env)
                        putAll(env)
                    }
                )
            }

            is AcpDistributionResolver.ResolvedDistribution.Unavailable -> null
        }
    }

    private fun buildBaseEnv(profile: AiProfileConfig): Map<String, String> {
        val executable = profile.claudeCodeExecutable.trim()
        return if (executable.isNotEmpty()) {
            mapOf("CLAUDE_CODE_EXECUTABLE" to executable)
        } else {
            emptyMap()
        }
    }

    private fun resolveDisplayName(profile: AiProfileConfig, platform: AiProfilePlatformInfo): String {
        val explicitName = profile.name.trim()
        if (explicitName.isNotEmpty()) {
            return explicitName
        }

        val model = resolvePrimaryModel(profile, platform)
        return model.ifEmpty { platform.label }
    }

    private fun resolveBaseUrl(profile: AiProfileConfig, endpoint: AiProfilePlatformEndpoint): String {
        val baseUrl = AiProfilePlatformRegistry.getResolvedBaseUrl(endpoint, profile.baseUrl)
        require(baseUrl.isNotBlank()) { "AI profile '${profile.name.ifBlank { profile.id }}' requires a Base URL" }
        return baseUrl
    }

    private fun resolvePrimaryModel(profile: AiProfileConfig, platform: AiProfilePlatformInfo): String {
        return resolveModels(profile, platform).firstOrNull().orEmpty()
    }

    private fun resolveModels(profile: AiProfileConfig, platform: AiProfilePlatformInfo): List<String> {
        val models = profile.model
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (models.isNotEmpty()) {
            return models
        }

        val defaultModel = platform.defaultModel.trim()
        require(defaultModel.isNotBlank()) { "AI profile '${profile.name.ifBlank { profile.id }}' requires a Model" }
        return listOf(defaultModel)
    }

    private fun registryAgentIdForTransport(transport: AiProfileTransport): String? {
        return when (transport) {
            AiProfileTransport.CLAUDE_ACP -> "claude-acp"
            AiProfileTransport.PI -> "pi-acp"
            AiProfileTransport.DROID -> "factory-droid"
        }
    }

    private fun unregisterAllProfiles() {
        registeredProfiles.values.forEach { registration ->
            try {
                Disposer.dispose(registration.disposable)
            } catch (e: Exception) {
                LOG.error("Failed to dispose AI profile ${registration.agent.id}", e)
            }
        }
        registeredProfiles.clear()
    }

    private fun runtimeAgentId(profile: AiProfileConfig): String {
        return "${profile.id}--${profileFingerprint(profile)}"
    }

    private fun profileFingerprint(profile: AiProfileConfig): String {
        val fingerprintSource = listOf(
            profile.platform,
            profile.effectiveApiType(),
            profile.effectiveTransport(),
            profile.claudeCodeExecutable.trim(),
            profile.apiKey.trim(),
            profile.baseUrl.trim(),
            profile.model.trim(),
            profile.isEnabled.toString()
        ).joinToString("\u0000")

        return fingerprintSource
            .hashCode()
            .toUInt()
            .toString(36)
            .padStart(6, '0')
            .takeLast(6)
    }

    private fun normalizeAgentId(agentId: String): String {
        return agentId.removePrefix("acp.")
    }

    override fun dispose() {
        unregisterAllProfiles()
    }
}

class AiProfileStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        AiProfileRegistry.getInstance()
    }
}

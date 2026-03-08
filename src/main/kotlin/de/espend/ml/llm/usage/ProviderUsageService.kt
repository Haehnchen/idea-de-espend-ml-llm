package de.espend.ml.llm.usage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import de.espend.ml.llm.usage.provider.AmpcodeUsageProvider
import de.espend.ml.llm.usage.provider.ClaudeUsageProvider
import de.espend.ml.llm.usage.provider.CodexUsageProvider
import de.espend.ml.llm.usage.provider.ZaiUsageProvider

/**
 * Service for fetching provider usage data
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
@Service
class ProviderUsageService {

    // Provider registry
    private val providers = mutableMapOf<String, UsageProvider>()

    init {
        registerProvider(ZaiUsageProvider())
        registerProvider(AmpcodeUsageProvider())
        registerProvider(CodexUsageProvider())
        registerProvider(ClaudeUsageProvider())
    }

    fun registerProvider(provider: UsageProvider) {
        providers[provider.providerInfo.providerId] = provider
    }

    fun hasProvider(providerId: String): Boolean = providers.containsKey(providerId)

    fun getProvider(providerId: String): UsageProvider? = providers[providerId]

    fun getAllProviders(): Collection<UsageProvider> = providers.values

    /**
     * Get all configured accounts that have usage providers.
     * Delegates deserialization to each provider via [UsageProvider.fromState].
     */
    fun getSupportedAccounts(): List<UsageAccountConfig> {
        return UsagePlatformRegistry.getInstance()
            .getAccountStates()
            .filter { it.isEnabled }
            .mapNotNull { state -> providers[state.providerId]?.fromState(state) }
    }

    /**
     * Fetch usage for a specific account
     * Called when popup is opened
     */
    suspend fun fetchUsage(account: UsageAccountConfig): ProviderUsageResponse {
        val provider = providers[account.providerId] ?: return ProviderUsageResponse.error("No provider for ${account.providerId}")

        return try {
            val result = provider.fetchUsage(account)
            if (result.data != null) {
                val entries = result.data.entries.map {
                    ProviderUsageEntry(it.percentageUsed, it.subtitle)
                }
                ProviderUsageResponse.success(
                    providerId = provider.providerInfo.providerId,
                    providerName = provider.providerInfo.providerName,
                    entries = entries
                )
            } else {
                ProviderUsageResponse.error(result.error ?: "Unknown error")
            }
        } catch (e: Exception) {
            ProviderUsageResponse.error(e.message ?: "Unknown error")
        }
    }

    companion object {
        fun getInstance(): ProviderUsageService {
            return ApplicationManager.getApplication().getService(ProviderUsageService::class.java)
        }
    }
}

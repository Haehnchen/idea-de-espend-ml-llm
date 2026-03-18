package de.espend.ml.llm.usage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import de.espend.ml.llm.usage.provider.AmpcodeUsageProvider
import de.espend.ml.llm.usage.provider.ClaudeUsageProvider
import de.espend.ml.llm.usage.provider.CodexUsageProvider
import de.espend.ml.llm.usage.provider.JunieUsageProvider
import de.espend.ml.llm.usage.provider.OllamaUsageProvider
import de.espend.ml.llm.usage.provider.ZaiUsageProvider
import java.util.concurrent.TimeUnit

/**
 * Service for fetching provider usage data
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
@Service
class ProviderUsageService {

    // Provider registry
    private val providers = mutableMapOf<String, UsageProvider>()

    // Cache for the full fetch result (all accounts)
    @Volatile
    private var cachedResults: Map<String, ProviderUsageResponse>? = null
    @Volatile
    private var cacheTimestamp: Long = 0L
    private val cacheTtlMs = TimeUnit.SECONDS.toMillis(10)

    init {
        registerProvider(ZaiUsageProvider())
        registerProvider(AmpcodeUsageProvider())
        registerProvider(CodexUsageProvider())
        registerProvider(ClaudeUsageProvider())
        registerProvider(JunieUsageProvider())
        registerProvider(OllamaUsageProvider())
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
                val lines = result.data.lines.map {
                    ProviderUsageLine(it.text)
                }
                ProviderUsageResponse(
                    ProviderUsage(
                        providerId = provider.providerInfo.providerId,
                        providerName = provider.providerInfo.providerName,
                        entries = entries,
                        lines = lines
                    ),
                    null
                )
            } else {
                ProviderUsageResponse.error(result.error ?: "Unknown error")
            }
        } catch (e: Exception) {
            ProviderUsageResponse.error(e.message ?: "Unknown error")
        }
    }

    /**
     * Returns true if the cache is still valid (not expired).
     */
    fun isCacheValid(): Boolean {
        return cachedResults != null && System.currentTimeMillis() - cacheTimestamp <= cacheTtlMs
    }

    /**
     * Returns cached response for a specific account, or null if not cached.
     */
    fun getCachedResponse(accountId: String): ProviderUsageResponse? {
        return cachedResults?.get(accountId)
    }

    /**
     * Store results into the cache.
     */
    fun updateCache(results: Map<String, ProviderUsageResponse>) {
        cachedResults = results
        cacheTimestamp = System.currentTimeMillis()
    }

    /**
     * Clear the usage cache.
     */
    fun clearCache() {
        cachedResults = null
        cacheTimestamp = 0L
    }

    companion object {
        fun getInstance(): ProviderUsageService {
            return ApplicationManager.getApplication().getService(ProviderUsageService::class.java)
        }
    }
}

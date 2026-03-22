package de.espend.ml.llm.usage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.Logger
import de.espend.ml.llm.usage.provider.AmpcodeUsageProvider
import de.espend.ml.llm.usage.provider.ClaudeUsageProvider
import de.espend.ml.llm.usage.provider.CodexUsageProvider
import de.espend.ml.llm.usage.provider.JunieUsageProvider
import de.espend.ml.llm.usage.provider.OllamaUsageProvider
import de.espend.ml.llm.usage.provider.ZaiUsageProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Application-wide service for fetching and caching provider usage data.
 * Cache is per-account: each provider account has its own result and timestamp.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
@Service(Level.APP)
class ProviderUsageService {

    private data class CachedEntry(
        val response: ProviderUsageResponse,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Provider registry
    private val providers = mutableMapOf<String, UsageProvider>()

    // Per-account cache: accountId → CachedEntry
    private val accountCache = ConcurrentHashMap<String, CachedEntry>()

    // Listeners notified on every cache update or clear
    private val cacheListeners = CopyOnWriteArrayList<() -> Unit>()

    /** Register a callback invoked whenever the cache changes. Returns an unregister lambda. */
    fun addCacheListener(listener: () -> Unit): () -> Unit {
        cacheListeners.add(listener)
        return { cacheListeners.remove(listener) }
    }

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

    fun getProvider(providerId: String): UsageProvider? = providers[providerId]

    fun getAllProviders(): Collection<UsageProvider> = providers.values

    /**
     * Get all configured accounts that have usage providers.
     */
    fun getSupportedAccounts(): List<UsageAccountConfig> {
        return UsagePlatformRegistry.getInstance()
            .getAccountStates()
            .filter { it.isEnabled }
            .mapNotNull { state -> providers[state.providerId]?.fromState(state) }
    }

    /**
     * Fetch usage for a specific account.
     */
    suspend fun fetchUsage(account: UsageAccountConfig): ProviderUsageResponse {
        val provider = providers[account.providerId] ?: return ProviderUsageResponse.error("No provider for ${account.providerId}")

        LOG.debug("Fetching usage: provider=${account.providerId}, account=${account.id} (${account.name})")
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
     * Returns cached response for a specific account, or null if not cached.
     */
    fun getCachedResponse(accountId: String): ProviderUsageResponse? = accountCache[accountId]?.response

    /**
     * Returns true if the cache for the given account is still within [maxAgeMs].
     */
    fun isCacheValidForAccount(accountId: String, maxAgeMs: Long): Boolean {
        val entry = accountCache[accountId] ?: return false
        return System.currentTimeMillis() - entry.timestamp <= maxAgeMs
    }

    /**
     * Returns true if ALL given accounts have a valid cache entry within [maxAgeMs].
     * An empty list is considered valid (nothing to fetch).
     */
    fun areCachesValidForAccounts(accountIds: List<String>, maxAgeMs: Long): Boolean {
        if (accountIds.isEmpty()) return true
        return accountIds.all { isCacheValidForAccount(it, maxAgeMs) }
    }

    /**
     * Fetch usage for each account in the list, update the cache, and notify listeners.
     */
    suspend fun fetchAndUpdateAccounts(accounts: List<UsageAccountConfig>) {
        if (accounts.isEmpty()) return
        val results = mutableMapOf<String, ProviderUsageResponse>()
        for (account in accounts) {
            try {
                results[account.id] = fetchUsage(account)
            } catch (e: Exception) {
                LOG.debug("Fetch failed: provider=${account.providerId}, account=${account.id}: ${e.message}")
                results[account.id] = ProviderUsageResponse.error(e.message ?: "Unknown error")
            }
        }
        updateCache(results)
    }

    /**
     * Store results into the per-account cache, stamping each entry with the current time.
     */
    fun updateCache(results: Map<String, ProviderUsageResponse>) {
        val timestamp = System.currentTimeMillis()
        results.forEach { (id, response) ->
            accountCache[id] = CachedEntry(response, timestamp)
        }
        cacheListeners.forEach { it() }
    }

    /**
     * Clear the usage cache for all accounts (e.g. after configuration changes).
     */
    fun clearCache() {
        accountCache.clear()
        cacheListeners.forEach { it() }
    }

    companion object {
        private val LOG = Logger.getInstance(ProviderUsageService::class.java)

        /** Cache TTL used by the status bar widget. */
        val STATUS_BAR_CACHE_TTL_MS: Long = TimeUnit.MINUTES.toMillis(2)

        /** Cache TTL used by the usage popup panel. */
        val PANEL_CACHE_TTL_MS: Long = TimeUnit.SECONDS.toMillis(15)

        fun getInstance(): ProviderUsageService {
            return ApplicationManager.getApplication().getService(ProviderUsageService::class.java)
        }
    }
}

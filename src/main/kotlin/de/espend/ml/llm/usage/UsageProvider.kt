package de.espend.ml.llm.usage

import de.espend.ml.llm.usage.ui.UsageFormPanel
import javax.swing.Icon
import kotlin.reflect.KClass

/**
 * Provider metadata for usage providers
 *
 * @param providerId Unique identifier for the provider
 * @param providerName Display name for the provider
 * @param icon Icon to display for the provider
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
data class ProviderInfo(
    val providerId: String,
    val providerName: String,
    val icon: Icon
)

/**
 * Panel layout info for a specific account.
 * Use [progressbar] for progress bar entries or [lines] for text lines - not both.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
class AccountPanelInfo private constructor(
    val usageEntryCount: Int,
    val lineCount: Int
) {
    companion object {
        /**
         * Create panel info for progress bar entries
         */
        @JvmStatic
        fun progressbar(count: Int) = AccountPanelInfo(usageEntryCount = count, lineCount = 0)

        /**
         * Create panel info for text lines
         */
        @JvmStatic
        fun lines(count: Int) = AccountPanelInfo(usageEntryCount = 0, lineCount = count)
    }
}

/**
 * Interface for provider-specific usage providers.
 *
 * Each provider owns the serialization of its own config type via [fromState] / [toState],
 * so [UsagePlatformRegistry] can store a flat, provider-agnostic list of [UsageAccountState].
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
interface UsageProvider {
    /**
     * Provider metadata (id, name, icon)
     */
    val providerInfo: ProviderInfo

    /**
     * The config class for this provider (used to create instances and identify provider)
     */
    val configClass: KClass<out UsageAccountConfig>

    /**
     * Fetch usage data from the provider
     */
    suspend fun fetchUsage(account: UsageAccountConfig): UsageFetchResult

    /**
     * Create a default UsageAccountConfig for new account creation
     */
    fun createDefaultConfig(): UsageAccountConfig = configClass.java.getDeclaredConstructor().newInstance()

    /**
     * Set up form fields and return a save callback.
     * The panel already contains Type, Enabled, ID, Label fields.
     * Provider adds its specific fields to panel.providerPanel and returns a callback to read values.
     *
     * @param panel Pre-built form panel with common fields and an empty providerPanel
     * @param initial Initial config values to populate the form (modified by callback)
     * @return Callback to invoke when dialog is confirmed, to bind form values to config
     * @throws RuntimeException if initial is not the expected config type
     */
    fun openForm(panel: UsageFormPanel, initial: UsageAccountConfig): () -> Unit

    /**
     * Hydrate a typed config from a generic [UsageAccountState].
     */
    fun fromState(state: UsageAccountState): UsageAccountConfig

    /**
     * Serialize a typed config to a generic [UsageAccountState] for persistence.
     */
    fun toState(config: UsageAccountConfig): UsageAccountState

    /**
     * Get panel layout info for a specific account.
     *
     * @param account The account configuration
     * @return Panel layout info (usageEntryCount, lineCount)
     */
    fun getAccountPanelInfo(account: UsageAccountConfig): AccountPanelInfo
}

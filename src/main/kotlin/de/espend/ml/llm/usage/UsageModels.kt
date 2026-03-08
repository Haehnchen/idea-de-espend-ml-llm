package de.espend.ml.llm.usage

/**
 * Single usage entry returned by handlers
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
data class UsageEntry(
    val percentageUsed: Float,
    val subtitle: String? = null
)

/**
 * Raw usage data returned by handlers (supports multiple entries)
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
data class UsageData(
    val entries: List<UsageEntry>
) {
    constructor(percentageUsed: Float, subtitle: String? = null) : this(listOf(UsageEntry(percentageUsed, subtitle)))
}

/**
 * Result of fetching usage from a handler
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
data class UsageFetchResult(
    val data: UsageData?,
    val error: String?
) {
    companion object {
        fun success(data: UsageData) = UsageFetchResult(data, null)
        fun success(entries: List<UsageEntry>) = UsageFetchResult(UsageData(entries), null)
        fun error(message: String) = UsageFetchResult(null, message)
    }
}

/**
 * Single provider usage entry with metadata
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
data class ProviderUsageEntry(
    val percentageUsed: Float,
    val subtitle: String? = null
)

/**
 * Full provider usage response with metadata (created by service)
 * Supports multiple usage entries per provider
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
data class ProviderUsage(
    val providerId: String,
    val providerName: String,
    val entries: List<ProviderUsageEntry>
)

/**
 * Provider usage response wrapper
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
data class ProviderUsageResponse(
    val usage: ProviderUsage?,
    val error: String?
) {
    companion object {
        fun success(providerId: String, providerName: String, entries: List<ProviderUsageEntry>) =
            ProviderUsageResponse(ProviderUsage(providerId, providerName, entries), null)
        fun error(message: String) = ProviderUsageResponse(null, message)
    }
}

/**
 * Base configuration for usage accounts
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
abstract class UsageAccountConfig {
    abstract val providerId: String
    var id: String = generateAccountId()
    var name: String = ""
    var isEnabled: Boolean = true

    companion object {
        private fun generateAccountId(): String {
            val chars = "0123456789abcdef"
            return "account-" + (1..6).map { chars.random() }.joinToString("")
        }
    }
}

/**
 * Generic serializable state for any provider account — stored as a flat array in XML.
 * Provider-specific fields go into [properties].
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
data class UsageAccountState(
    var id: String = "",
    var providerId: String = "",
    var label: String = "",
    var isEnabled: Boolean = true,
    var properties: MutableMap<String, String> = mutableMapOf()
) {
    fun getString(key: String, default: String = ""): String = properties[key] ?: default
    fun putString(key: String, value: String) { properties[key] = value }

    fun getBool(key: String, default: Boolean = false): Boolean =
        properties[key]?.toBooleanStrictOrNull() ?: default
    fun putBool(key: String, value: Boolean) { properties[key] = value.toString() }
}

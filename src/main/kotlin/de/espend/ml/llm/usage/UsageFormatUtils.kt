package de.espend.ml.llm.usage

import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale

/**
 * Shared formatting utilities for usage providers.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
object UsageFormatUtils {

    /**
     * Formats a secret value for UI display by keeping the beginning and end visible.
     */
    fun formatSecret(secret: String): String {
        if (secret.isEmpty()) return ""

        val edgeLength = 3
        if (secret.length <= edgeLength * 2) return secret

        return "${secret.take(edgeLength)}...${secret.takeLast(edgeLength)}"
    }

    /**
     * Formats token amounts for compact usage panels.
     *
     * Thousands stay whole to save space. Millions and billions keep at most one
     * decimal and are truncated instead of rounded, so 1,999,999 stays 1.9M.
     */
    fun formatTokenAmount(tokens: Long): String = when {
        tokens >= 1_000_000_000L -> formatScaledToken(tokens, 1_000_000_000.0, "B")
        tokens >= 1_000_000L -> formatScaledToken(tokens, 1_000_000.0, "M")
        tokens >= 1_000L -> "${tokens / 1_000}K"
        else -> tokens.toString()
    }

    /**
     * Formats a reset time as a human-readable string relative to now.
     * Returns null if [resetAt] is null.
     */
    fun formatResetTime(resetAt: Instant?): String? {
        resetAt ?: return null

        val secondsUntil = resetAt.epochSecond - Instant.now().epochSecond
        return formatSecondsUntilReset(secondsUntil)
    }

    /**
     * Formats a duration in seconds until reset as a human-readable string.
     */
    fun formatSecondsUntilReset(secondsUntil: Long): String {
        val days = secondsUntil / (24 * 3600)
        val hours = (secondsUntil % (24 * 3600)) / 3600
        val minutes = (secondsUntil % 3600) / 60
        val seconds = secondsUntil % 60
        return when {
            days > 0 && hours > 0 -> "Resets in ${days}d ${hours}h"
            days > 0 -> "Resets in ${days}d"
            hours > 0 -> "Resets in ${hours}h ${minutes}m"
            minutes > 0 -> "Resets in ${minutes}m ${seconds}s"
            else -> "Resets in ${seconds}s"
        }
    }

    private fun formatScaledToken(tokens: Long, divisor: Double, suffix: String): String {
        val locale = Locale.getDefault(Locale.Category.FORMAT)
        val value = tokens / divisor
        val text = if (value >= 100.0) {
            kotlin.math.floor(value).toLong().toString()
        } else {
            val truncated = kotlin.math.floor(value * 10.0) / 10.0
            String.format(locale, "%.1f", truncated)
                .removeSuffix("${DecimalFormatSymbols.getInstance(locale).decimalSeparator}0")
        }

        return "$text$suffix"
    }
}

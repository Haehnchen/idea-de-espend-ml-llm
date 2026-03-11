package de.espend.ml.llm.usage

import java.time.Instant

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
}

package de.espend.ml.llm.session

/**
 * Session provider types.
 */
enum class SessionProvider(val displayName: String, val iconClass: String) {
    CLAUDE_CODE("Claude Code", "claude-icon"),
    OPENCODE("OpenCode", "opencode-icon"),
    CODEX("Codex", "codex-icon"),
    AMP("Amp", "amp-icon")
}

/**
 * Unified session list item for display.
 * This is the general DTO used for session listing, independent of the provider.
 */
data class SessionListItem(
    val sessionId: String,
    val title: String,
    val provider: SessionProvider,
    val updated: Long,
    val created: Long,
    val messageCount: Int? = null
) {
    /**
     * Returns the timestamp to use for sorting (updated if available, otherwise created).
     */
    val sortTimestamp: Long
        get() = if (updated > 0) updated else created
}

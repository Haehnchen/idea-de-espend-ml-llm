package de.espend.ml.llm.mcp

data class SessionSearchResult(
    val query: String,
    val totalSessions: Int,
    val results: List<SessionSearchHit>
)

data class SessionSearchHit(
    val sessionId: String,
    val title: String,
    val provider: String,
    val score: Int,
    val snippets: List<String>
)

data class SessionDetailResult(
    val sessionId: String,
    val title: String,
    val provider: String,
    val metadata: SessionMetadataResult?,
    val messages: List<FormattedMessage>
)

data class SessionMetadataResult(
    val gitBranch: String?,
    val cwd: String?,
    val models: List<Pair<String, Int>>,
    val created: String?,
    val modified: String?,
    val messageCount: Int
)

data class FormattedMessage(
    val sequence: Int,
    val role: String,
    val title: String?,
    val subtitle: String?,
    val timestamp: String,
    val content: String
)

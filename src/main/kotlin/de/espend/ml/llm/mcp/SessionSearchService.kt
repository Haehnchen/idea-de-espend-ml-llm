package de.espend.ml.llm.mcp

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.SessionService
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private const val MAX_SNIPPETS = 5
private const val SNIPPET_CONTEXT_CHARS = 300
private const val MAX_SEARCH_LIMIT = 50

object SessionSearchService {

    fun searchSessions(project: Project, query: String, limit: Int = 20): SessionSearchResult {
        val safeLimit = limit.coerceIn(1, MAX_SEARCH_LIMIT)
        val allSessions = SessionService.getAllSessions(project)

        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks = allSessions.map { session ->
                Callable {
                    try {
                        val detail = SessionService.getSessionDetail(project, session.sessionId, session.provider) ?: return@Callable null
                        val snippets = findSnippets(detail, query)
                        if (snippets.isEmpty()) return@Callable null
                        SessionSearchHit(
                            sessionId = session.sessionId,
                            title = session.title,
                            provider = session.provider.displayName,
                            score = snippets.size,
                            snippets = snippets.take(MAX_SNIPPETS)
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            val results = executor.invokeAll(tasks)
                .mapNotNull { future ->
                    try { future.get() } catch (_: Exception) { null }
                }
                .sortedByDescending { it.score }
                .take(safeLimit)

            return SessionSearchResult(
                query = query,
                totalSessions = results.size,
                results = results
            )
        } finally {
            executor.shutdown()
        }
    }

    fun getSessionDetail(project: Project, sessionId: String): SessionDetailResult? {
        for (providerEnum in SessionProvider.entries) {
            val detail = SessionService.getSessionDetail(project, sessionId, providerEnum)
            if (detail != null) {
                return toDetailResult(detail, providerEnum)
            }
        }
        return null
    }

    private fun toDetailResult(detail: SessionDetail, provider: SessionProvider): SessionDetailResult {
        val meta = detail.metadata?.let {
            SessionMetadataResult(
                gitBranch = it.gitBranch,
                cwd = it.cwd,
                models = it.models,
                created = it.created,
                modified = it.modified,
                messageCount = it.messageCount
            )
        }

        val messages = detail.messages.mapIndexed { index, msg ->
            FormattedMessage(
                sequence = index + 1,
                role = messageRole(msg),
                title = messageTitle(msg),
                subtitle = messageSubtitle(msg),
                timestamp = msg.timestamp,
                content = formatMessageContent(msg)
            )
        }

        return SessionDetailResult(
            sessionId = detail.sessionId,
            title = detail.title,
            provider = provider.displayName,
            metadata = meta,
            messages = messages
        )
    }

    fun findSnippets(detail: SessionDetail, query: String): List<String> {
        val snippets = mutableListOf<String>()
        val lowerQuery = query.lowercase()

        for (msg in detail.messages) {
            if (snippets.size >= MAX_SNIPPETS) break
            val text = formatMessageContent(msg)
            val lowerText = text.lowercase()
            var searchFrom = 0

            while (snippets.size < MAX_SNIPPETS) {
                val idx = lowerText.indexOf(lowerQuery, searchFrom)
                if (idx == -1) break
                searchFrom = idx + lowerQuery.length

                val start = (idx - SNIPPET_CONTEXT_CHARS / 2).coerceAtLeast(0)
                val end = (idx + lowerQuery.length + SNIPPET_CONTEXT_CHARS / 2).coerceAtMost(text.length)
                val snippet = buildString {
                    if (start > 0) append("...")
                    append(text.substring(start, end))
                    if (end < text.length) append("...")
                }
                snippets.add(snippet)
            }
        }
        return snippets
    }

    fun formatMessageContent(msg: ParsedMessage): String {
        return when (msg) {
            is ParsedMessage.User -> formatContentBlocks(msg.content)
            is ParsedMessage.AssistantText -> formatContentBlocks(msg.content)
            is ParsedMessage.AssistantThinking -> msg.thinking
            is ParsedMessage.ToolUse -> buildString {
                append("tool: ${msg.toolName}")
                if (msg.input.isNotEmpty()) {
                    append("\n")
                    msg.input.forEach { (k, v) -> append("  $k: $v\n") }
                }
                for (result in msg.results) {
                    append("\n")
                    append(formatContentBlocks(result.output))
                }
            }
            is ParsedMessage.ToolResult -> formatContentBlocks(msg.output)
            is ParsedMessage.Info -> buildString {
                append(msg.title)
                msg.subtitle?.let { append(" - $it") }
                msg.content?.let { append("\n${formatContentBlock(it)}") }
            }
        }
    }

    private fun formatContentBlocks(blocks: List<MessageContent>): String {
        return blocks.joinToString("\n") { formatContentBlock(it) }
    }

    private fun formatContentBlock(block: MessageContent): String {
        return when (block) {
            is MessageContent.Text -> block.text
            is MessageContent.Code -> "```${block.language ?: ""}\n${block.code}\n```"
            is MessageContent.Markdown -> block.markdown
            is MessageContent.Json -> "```json\n${block.json}\n```"
        }
    }

    private fun messageRole(msg: ParsedMessage): String {
        return when (msg) {
            is ParsedMessage.User -> "user"
            is ParsedMessage.AssistantText -> "assistant"
            is ParsedMessage.AssistantThinking -> "thinking"
            is ParsedMessage.ToolUse -> "tool_use"
            is ParsedMessage.ToolResult -> "tool_result"
            is ParsedMessage.Info -> "info"
        }
    }

    private fun messageTitle(msg: ParsedMessage): String? {
        return when (msg) {
            is ParsedMessage.ToolUse -> msg.toolName
            is ParsedMessage.Info -> msg.title
            else -> null
        }
    }

    private fun messageSubtitle(msg: ParsedMessage): String? {
        return when (msg) {
            is ParsedMessage.ToolUse -> msg.toolCallId
            is ParsedMessage.ToolResult -> msg.toolName
            is ParsedMessage.Info -> msg.subtitle
            else -> null
        }
    }
}

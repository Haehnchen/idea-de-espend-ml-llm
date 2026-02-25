@file:Suppress("FunctionName", "unused")

package de.espend.ml.llm.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import de.espend.ml.llm.mcp.SessionSearchService
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.*

class SearchSessionsMcpToolset : McpToolset {

    @McpTool
    @McpDescription("""
        Search across AI coding sessions (Claude Code, Amp, Codex, OpenCode, Junie, Gemini, Droid, Kilo Code)
        for a given query string. Returns matching sessions with text snippets showing where the
        query was found. Use this to find past conversations about specific topics, code, or problems.

        Returns JSON with fields:
        - query: The search query used
        - totalSessions: Number of sessions with matches
        - results: Array of matching sessions, each with:
          - sessionId: Unique session identifier (use with get_ai_session to see full conversation)
          - title: Session title / first user prompt
          - provider: AI tool name (Claude Code, Amp, etc.)
          - score: Number of matches found
          - snippets: Up to 5 text snippets with surrounding context

        Example usage: search for "authentication" to find sessions discussing auth implementation.
        Then use get_ai_session with a sessionId from the results to see the full conversation.
    """)
    suspend fun search_ai_sessions(
        @McpDescription("Search query string (case-insensitive substring match)")
        query: String,
        @McpDescription("Optional: Maximum number of results to return (default 20, max 50)")
        limit: Int? = null
    ): String {
        val project = currentCoroutineContext().project

        if (query.isBlank()) {
            mcpFail("query parameter is required and must not be blank.")
        }

        val result = SessionSearchService.searchSessions(project, query, limit ?: 20)

        return buildJsonObject {
            put("query", result.query)
            put("totalSessions", result.totalSessions)
            putJsonArray("results") {
                for (hit in result.results) {
                    addJsonObject {
                        put("sessionId", hit.sessionId)
                        put("title", hit.title)
                        put("provider", hit.provider)
                        put("score", hit.score)
                        putJsonArray("snippets") {
                            for (snippet in hit.snippets) {
                                add(snippet)
                            }
                        }
                    }
                }
            }
        }.toString()
    }
}

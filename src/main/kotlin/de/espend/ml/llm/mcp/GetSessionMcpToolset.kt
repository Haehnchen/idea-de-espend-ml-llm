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

class GetSessionMcpToolset : McpToolset {

    @McpTool
    @McpDescription("""
        Fetch the full content of an AI coding session by its session ID. Returns the complete
        conversation with all messages (user prompts, assistant responses, tool usage) in a
        structured format suitable for understanding the conversation flow.

        Returns JSON with fields:
        - sessionId: Session identifier
        - title: Session title
        - provider: AI tool name
        - metadata: Object with gitBranch, cwd, models, created, modified, messageCount (may be null)
        - messages: Array of messages, each with:
          - sequence: Message order number
          - role: One of "user", "assistant", "thinking", "tool_use", "tool_result", "info"
          - title: Optional title (tool name for tool_use, info title)
          - subtitle: Optional subtitle (tool call ID, tool name for results)
          - timestamp: ISO timestamp
          - content: Message content as markdown-formatted text
    """)
    suspend fun get_ai_session(
        @McpDescription("Session ID (e.g. UUID from search_ai_sessions results)")
        sessionId: String
    ): String {
        val project = currentCoroutineContext().project

        if (sessionId.isBlank()) {
            mcpFail("sessionId parameter is required.")
        }

        val result = SessionSearchService.getSessionDetail(project, sessionId)
            ?: mcpFail("Session not found: $sessionId")

        return buildJsonObject {
            put("sessionId", result.sessionId)
            put("title", result.title)
            put("provider", result.provider)
            if (result.metadata != null) {
                put("metadata", buildJsonObject {
                    put("gitBranch", result.metadata.gitBranch?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("cwd", result.metadata.cwd?.let { JsonPrimitive(it) } ?: JsonNull)
                    putJsonArray("models") {
                        for ((name, count) in result.metadata.models) {
                            addJsonObject {
                                put("name", name)
                                put("count", count)
                            }
                        }
                    }
                    put("created", result.metadata.created?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("modified", result.metadata.modified?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("messageCount", result.metadata.messageCount)
                })
            } else {
                put("metadata", JsonNull)
            }
            putJsonArray("messages") {
                for (msg in result.messages) {
                    addJsonObject {
                        put("sequence", msg.sequence)
                        put("role", msg.role)
                        put("title", msg.title?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("subtitle", msg.subtitle?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("timestamp", msg.timestamp)
                        put("content", msg.content)
                    }
                }
            }
        }.toString()
    }
}

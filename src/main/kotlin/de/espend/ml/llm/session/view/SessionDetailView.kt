package de.espend.ml.llm.session.view

import com.intellij.ui.jcef.JBCefJSQuery
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import de.espend.ml.llm.session.util.*

/**
 * Generates HTML for the session detail view.
 * Unified renderer for all providers - uses SessionMetadata fields conditionally.
 *
 * JBCefJSQuery parameters are optional - when null, generates standalone HTML
 * that works in a regular browser (for CLI debugging/export).
 */
class SessionDetailView(
    private val jsQueryRouter: JBCefJSQuery? = null,
    private val jsQueryCursor: JBCefJSQuery? = null
) {

    /**
     * Generates the complete HTML for a session detail view.
     * Works for any provider - renders based on available metadata fields.
     */
    fun generateSessionDetail(sessionId: String, sessionDetail: SessionDetail): String {
        val metadata = sessionDetail.metadata

        return buildString {
            // HTML header
            append(HtmlBuilder.buildHeader(styles = CssStyles.forDetailView()))

            // Sticky header (hidden by default, shown on scroll)
            appendLine("        <div class=\"sticky-header\" id=\"stickyHeader\">")
            appendLine("            <a href=\"${JsHandlers.goBackUrl()}\" class=\"sticky-header-back\">${Icons.arrowLeft(14)}</a>")
            appendLine("            <span class=\"sticky-header-title\">${HtmlBuilder.escapeHtml(sessionDetail.title)}</span>")
            appendLine("            <button class=\"scroll-top-btn\" onclick=\"scrollToTop()\">${Icons.arrowUp(14)}</button>")
            appendLine("        </div>")

            // Body content
            appendLine("        <div class=\"container\">")
            appendLine("            <a href=\"${JsHandlers.goBackUrl()}\" class=\"back-link\">${Icons.arrowLeft(14)} Back to Sessions</a>")
            appendLine("            <h1>${HtmlBuilder.escapeHtml(sessionDetail.title)}</h1>")

            // Metadata card
            if (metadata != null) {
                appendMetadataCard(this, sessionId, metadata)
            } else {
                // No metadata, just show ID
                appendLine("            <div class=\"metadata-card\">")
                appendLine("                <div class=\"metadata-row\">")
                appendLine("                    <div class=\"metadata-item\"><span class=\"metadata-label\">ID:</span> <span class=\"metadata-value session-meta-value\">${HtmlBuilder.escapeHtml(sessionId)}</span></div>")
                appendLine("                </div>")
                appendLine("            </div>")
            }

            // Messages
            if (sessionDetail.messages.isEmpty()) {
                appendLine("            <div class=\"empty-state\">No messages found in this session</div>")
            } else {
                val cwd = metadata?.cwd
                sessionDetail.messages.forEach { msg ->
                    appendMessage(this, msg, cwd)
                }
            }

            appendLine("        </div>")

            // JavaScript handlers
            appendLine("    <script>")
            append(JsHandlers.generateBaseScript(jsQueryRouter, jsQueryCursor))
            append(JsHandlers.generateExpandScript())
            append(JsHandlers.generateStickyHeaderScript())
            appendLine("    </script>")

            // HTML footer
            append(HtmlBuilder.buildFooter())
        }
    }

    /**
     * Generates the complete HTML for a not-found session.
     */
    fun generateNotFoundHtml(sessionId: String): String {
        return buildString {
            // HTML header
            append(HtmlBuilder.buildHeader(styles = CssStyles.forErrorView()))

            // Body content
            appendLine("        <div class=\"container\">")
            appendLine("            <a href=\"${JsHandlers.goBackUrl()}\" class=\"back-link\">${Icons.arrowLeft(14)} Back to Sessions</a>")
            appendLine("            <div class=\"empty-state\">")
            appendLine("                <h2>Session not found</h2>")
            appendLine("                <p>Could not load session: $sessionId</p>")
            appendLine("            </div>")
            appendLine("        </div>")

            // JavaScript handlers
            appendLine("    <script>")
            append(JsHandlers.generateBaseScript(jsQueryRouter, jsQueryCursor))
            appendLine("    </script>")

            // HTML footer
            append(HtmlBuilder.buildFooter())
        }
    }

    /**
     * Unified metadata card renderer - renders fields conditionally based on availability.
     */
    private fun appendMetadataCard(sb: StringBuilder, sessionId: String, metadata: de.espend.ml.llm.session.SessionMetadata) {
        sb.appendLine("            <div class=\"metadata-card\">")

        // Headline row: 2 columns - left (version, branch), right (messages count)
        sb.appendLine("                <div class=\"metadata-headline\">")

        // Left column: version and branch badges
        sb.appendLine("                    <div class=\"headline-left\">")
        if (metadata.version != null) {
            sb.appendLine("                        <span class=\"badge badge-version\" data-tooltip=\"Version\">${HtmlBuilder.escapeHtml(metadata.version)}</span>")
        }
        if (metadata.gitBranch != null) {
            sb.appendLine("                        <span class=\"badge badge-branch\" data-tooltip=\"Branch\">${HtmlBuilder.escapeHtml(metadata.gitBranch)}</span>")
        }
        sb.appendLine("                    </div>")

        // Right column: messages count
        sb.appendLine("                    <div class=\"headline-right\">")
        sb.appendLine("                        <span class=\"metadata-item\"><span class=\"metadata-label\">Messages:</span> <span class=\"metadata-value\">${metadata.messageCount}</span></span>")
        sb.appendLine("                    </div>")

        sb.appendLine("                </div>")

        // Content section: plain text rows
        sb.appendLine("                <div class=\"metadata-content\">")

        // Created timestamp
        if (!metadata.created.isNullOrEmpty()) {
            sb.appendLine("                    <span class=\"metadata-label\">Created:</span> ${HtmlBuilder.formatTimestamp(metadata.created)}<br>")
        }

        // Modified timestamp
        if (!metadata.modified.isNullOrEmpty() && metadata.modified != metadata.created) {
            sb.appendLine("                    <span class=\"metadata-label\">Modified:</span> ${HtmlBuilder.formatTimestamp(metadata.modified)}<br>")
        }

        // Models row - show top 3 models (sorted by usage)
        if (metadata.models.isNotEmpty()) {
            val topModels = metadata.models.take(3).map { it.first }
            val modelText = topModels.joinToString(", ")
            val remaining = metadata.models.size - 3
            val suffix = if (remaining > 0) " +$remaining more" else ""
            sb.appendLine("                    <span class=\"metadata-label\">Models:</span> ${HtmlBuilder.escapeHtml(modelText)}$suffix<br>")
        }

        // Working directory (if available)
        if (metadata.cwd != null) {
            sb.appendLine("                    <span class=\"metadata-label\">Working Dir:</span> <span class=\"session-meta-value\">${HtmlBuilder.escapeHtml(metadata.cwd)}</span><br>")
        }

        // Session ID
        sb.appendLine("                    <span class=\"metadata-label\">ID:</span> <span class=\"session-meta-value\">${HtmlBuilder.escapeHtml(sessionId)}</span>")
        sb.appendLine("                </div>")
        sb.appendLine("            </div>")
    }

    private fun appendMessage(sb: StringBuilder, msg: ParsedMessage, cwd: String? = null) {
        when (msg) {
            is ParsedMessage.User -> appendMessageBlock(sb, "user", "user", null, msg.timestamp, msg.content)
            is ParsedMessage.AssistantText -> appendMessageBlock(sb, "assistant", "text", null, msg.timestamp, msg.content)
            is ParsedMessage.AssistantThinking -> appendThinkingBlock(sb, msg)
            is ParsedMessage.ToolUse -> appendToolUseBlock(sb, msg, cwd)
            is ParsedMessage.ToolResult -> appendMessageBlock(sb, "tool-result", "tool_result", msg.toolCallId?.take(24), msg.timestamp, msg.output)
            is ParsedMessage.Info -> {
                val cssClass = if (msg.style == ParsedMessage.InfoStyle.ERROR) "schema-error" else "info"
                val contentList = msg.content?.let { listOf(it) } ?: listOf(MessageContent.Text("[${msg.title}]"))
                appendMessageBlock(sb, cssClass, msg.title, msg.subtitle, msg.timestamp, contentList)
            }
        }
    }

    private fun appendThinkingBlock(sb: StringBuilder, msg: ParsedMessage.AssistantThinking) {
        sb.appendLine("            <div class=\"message thinking\">")
        appendMessageHeader(sb, "thinking", "thinking", null, msg.timestamp)
        sb.appendLine("                <div class=\"message-content-wrapper\">")
        sb.appendLine("                    <div class=\"message-content\">")
        sb.appendLine("                        <p class=\"content-text\">${HtmlBuilder.formatThinkingBlock(msg.thinking)}</p>")
        sb.appendLine("                    </div>")
        sb.appendLine("                    <div class=\"expand-bar clickable\" onclick=\"toggleExpand(this)\"><span class=\"expand-text\">Expand</span> <span class=\"expand-icon\">${Icons.chevronDown(10)}</span></div>")
        sb.appendLine("                </div>")
        sb.appendLine("            </div>")
    }

    private fun appendToolUseBlock(sb: StringBuilder, msg: ParsedMessage.ToolUse, cwd: String? = null) {
        sb.appendLine("            <div class=\"message tool-use\">")
        appendMessageHeader(sb, "tool-use", "tool_use", msg.toolName, msg.timestamp)
        sb.appendLine("                <div class=\"message-content-wrapper\">")
        sb.appendLine("                    <div class=\"message-content\">")

        // Format input content with path stripping applied
        val formattedInput = formatInputWithPathStripping(msg.input, msg.toolName, cwd)
        sb.appendLine("                        <p class=\"content-text\">$formattedInput</p>")

        msg.results.forEach { result ->
            sb.appendLine("                        ${HtmlBuilder.formatToolResultBlock(ToolResultRenderInfo(result.output, result.isError, result.toolCallId))}")
        }

        sb.appendLine("                    </div>")
        sb.appendLine("                    <div class=\"expand-bar clickable\" onclick=\"toggleExpand(this)\"><span class=\"expand-text\">Expand</span> <span class=\"expand-icon\">${Icons.chevronDown(10)}</span></div>")
        sb.appendLine("                </div>")
        sb.appendLine("            </div>")
    }

    /**
     * Gets a value from the parameter map using normalized key matching.
     * Normalizes keys by converting to lowercase and removing underscores.
     * This allows matching both "old_string" and "oldString" with the same lookup.
     *
     * @param parameters The original parameter map
     * @param normalizedKeys List of normalized keys to look up (e.g., ["oldstring", "oldstr"] will match "old_string", "oldString", "old_str", etc.)
     * @return The value if found, null otherwise
     */
    private fun getParameterValue(parameters: Map<String, String>, vararg normalizedKeys: String): String? {
        for (normalizedKey in normalizedKeys) {
            // First try direct lookup with the normalized key
            if (parameters.containsKey(normalizedKey)) {
                return parameters[normalizedKey]
            }
            // Then search through all keys, normalizing them for comparison
            for ((key, value) in parameters) {
                val keyNormalized = key.lowercase().replace("_", "")
                if (keyNormalized == normalizedKey) {
                    return value
                }
            }
        }
        return null
    }

    /**
     * Formats tool input content with working directory stripping applied to file paths.
     * Handles Edit tool with diff generation directly in the view layer.
     *
     * Normalizes parameter names to handle both underscore (old_string) and camelCase (oldString) formats.
     * Also handles variations like old_str/new_str used by some providers.
     */
    private fun formatInputWithPathStripping(input: Map<String, String>, toolName: String, cwd: String?): String {
        // Special handling for Edit-like tools - generate diff view directly
        // Match tool names containing "edit" (e.g., "Edit", "edit_file", "EditFile")
        if (toolName.lowercase().contains("edit")) {
            // Use normalized parameter lookup to handle various naming conventions:
            // old_string, oldString, old_str, oldStr
            val oldString = getParameterValue(input, "oldstring", "oldstr")
            val newString = getParameterValue(input, "newstring", "newstr")

            if (oldString != null && newString != null) {
                return formatEditDiffHtml(oldString, newString, input, cwd)
            }
        }

        // Generic formatting for all other cases
        return ToolInputFormatter.formatToolInput(input)
    }

    /**
     * Formats an Edit tool diff as HTML with inline diff highlighting.
     * Shows removed lines in red with "-" prefix and added lines in green with "+" prefix.
     *
     * Normalizes parameter names to handle various path formats:
     * file_path, filePath, path, pathInProject, pathinproject
     */
    private fun formatEditDiffHtml(oldString: String, newString: String, parameters: Map<String, String>, cwd: String? = null): String {
        val diffLines = HtmlBuilder.generateInlineDiff(oldString, newString)

        return buildString {
            // Add file path parameter if present using normalized lookup
            // Handle various naming conventions: file_path, filePath, path
            val filePath = getParameterValue(parameters, "filepath", "path")
            filePath?.let { path ->
                val strippedPath = cwd?.let { HtmlBuilder.stripWorkingDirectory(path, it) } ?: path
                append("<span class=\"file-path-label\">file_path: </span>")
                append("<pre class=\"code-block\"><code>${HtmlBuilder.escapeHtml(strippedPath)}</code></pre>")
            }

            append("<div class=\"diff-view\">")
            append("<pre class=\"diff-content\"><code>")
            append(diffLines)
            append("</code></pre>")
            append("</div>")
        }
    }

    private fun appendMessageBlock(
        sb: StringBuilder,
        cssClass: String,
        displayType: String,
        extraLabel: String?,
        timestamp: String,
        content: List<MessageContent>
    ) {
        sb.appendLine("            <div class=\"message $cssClass\">")
        appendMessageHeader(sb, cssClass, displayType, extraLabel, timestamp)
        sb.appendLine("                <div class=\"message-content-wrapper\">")
        sb.appendLine("                    <div class=\"message-content\">")
        sb.appendLine("                        <p class=\"content-text\">${HtmlBuilder.formatContent(content)}</p>")
        sb.appendLine("                    </div>")
        sb.appendLine("                    <div class=\"expand-bar clickable\" onclick=\"toggleExpand(this)\"><span class=\"expand-text\">Expand</span> <span class=\"expand-icon\">${Icons.chevronDown(10)}</span></div>")
        sb.appendLine("                </div>")
        sb.appendLine("            </div>")
    }

    private fun appendMessageHeader(
        sb: StringBuilder,
        cssClass: String,
        displayType: String,
        extraLabel: String?,
        timestamp: String
    ) {
        sb.appendLine("                <div class=\"message-header\">")
        sb.appendLine("                    <div class=\"header-left\">")
        sb.appendLine("                        <span class=\"type-badge $cssClass\">${HtmlBuilder.escapeHtml(displayType)}</span>")
        if (extraLabel != null) {
            sb.appendLine("                        <span class=\"role-label\">${HtmlBuilder.escapeHtml(extraLabel)}</span>")
        }
        sb.appendLine("                    </div>")
        sb.appendLine("                    <div class=\"header-right\">")
        sb.appendLine("                        <span class=\"timestamp\">${HtmlBuilder.formatTimestamp(timestamp)}</span>")
        sb.appendLine("                    </div>")
        sb.appendLine("                </div>")
    }
}

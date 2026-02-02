package de.espend.ml.llm.session.util

import de.espend.ml.llm.session.model.MessageContent

/**
 * Data class for rendering tool results.
 */
data class ToolResultRenderInfo(
    val content: List<MessageContent>,
    val isError: Boolean,
    val toolCallId: String?
)

/**
 * HTML building utilities for generating consistent HTML structure.
 */
object HtmlBuilder {

    /**
     * Returns true if the current IDE theme is dark.
     * Delegates to ThemeColors which handles standalone mode.
     */
    fun isDarkTheme(): Boolean {
        return ThemeColors.isDark()
    }

    /**
     * Escapes HTML special characters.
     */
    fun escapeHtml(text: String?): String {
        if (text == null) return ""
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#039;")
    }

    /**
     * Formats a timestamp to a readable date/time string.
     */
    fun formatTimestamp(timestamp: String?): String {
        if (timestamp == null) return ""
        return try {
            val instant = java.time.Instant.parse(timestamp)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            timestamp
        }
    }

    /**
     * Formats a timestamp (Long) to a readable date/time string.
     */
    fun formatTimestamp(timestamp: Long): String {
        return try {
            val instant = java.time.Instant.ofEpochMilli(timestamp)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            timestamp.toString()
        }
    }

    /**
     * Builds HTML document header.
     * Adds theme class to body: "theme-dark" or "theme-light" based on IntelliJ theme.
     */
    fun buildHeader(title: String = "", styles: String): String {
        val themeClass = if (isDarkTheme()) "theme-dark" else "theme-light"
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html>")
            appendLine("<head>")
            appendLine("    <meta charset=\"UTF-8\">")
            appendLine("    <style>")
            appendLine("        $styles")
            appendLine("    </style>")
            if (title.isNotEmpty()) {
                appendLine("    <title>$title</title>")
            }
            appendLine("</head>")
            appendLine("<body class=\"$themeClass\">")
        }
    }

    /**
     * Builds HTML document footer with optional script content.
     */
    fun buildFooter(scriptContent: String = ""): String {
        return buildString {
            if (scriptContent.isNotEmpty()) {
                appendLine("    <script>")
                appendLine("        $scriptContent")
                appendLine("    </script>")
            }
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    /**
     * Formats a list of MessageContent directly to HTML.
     */
    fun formatContent(blocks: List<MessageContent>): String {
        return buildString {
            blocks.forEach { block ->
                append(formatContentBlock(block))
            }
        }
    }

    /**
     * Formats a single MessageContent to HTML.
     */
    private fun formatContentBlock(block: MessageContent): String {
        return when (block) {
            is MessageContent.Text -> escapeHtml(block.text)
            is MessageContent.Code -> "<pre class=\"code-block\"><code>${escapeHtml(block.code)}</code></pre>"
            is MessageContent.Markdown -> MarkdownConverter.convertIfMarkdown(block.markdown)
            is MessageContent.Json -> "<pre class=\"code-block\"><code>${escapeHtml(block.json)}</code></pre>"
        }
    }

    /**
     * Formats thinking content as simple inline text.
     */
    fun formatThinkingBlock(thinking: String): String {
        return MarkdownConverter.convertIfMarkdown(thinking)
    }

    /**
     * Formats a tool result block to HTML as simple inline content.
     * Shows bold "Result:" label with success/error indicator around the code area.
     */
    fun formatToolResultBlock(result: ToolResultRenderInfo): String {
        val statusClass = if (result.isError) "tool-result-error" else "tool-result-success"
        val statusLabel = if (result.isError) "Error" else "Result"
        return buildString {
            append("<div class=\"tool-result-inline $statusClass\">")
            append("<strong>$statusLabel:</strong>")
            if (!result.toolCallId.isNullOrEmpty()) {
                append(" <span class=\"tool-call-id\">(${escapeHtml(result.toolCallId)})</span>")
            }
            append("<div class=\"tool-result-output\">")
            append(formatContent(result.content))
            append("</div>")
            append("</div>")
        }
    }

    /**
     * Strips the working directory from a file path for cleaner display.
     * Returns the path relative to workingDir, or the original path if stripping is not possible.
     */
    fun stripWorkingDirectory(filePath: String, workingDir: String?): String {
        if (workingDir == null) return filePath

        // Normalize paths for comparison
        val normalizedWorkingDir = workingDir.removeSuffix("/")
        val normalizedFilePath = if (filePath.startsWith(normalizedWorkingDir)) {
            filePath.removePrefix(normalizedWorkingDir).removePrefix("/")
        } else {
            filePath
        }

        return normalizedFilePath
    }

    /**
     * Generates an inline diff using a simple line-by-line comparison.
     * For small changes, shows the full diff. For larger changes, shows changed hunks.
     */
    fun generateInlineDiff(oldText: String, newText: String): String {
        // Strip common leading whitespace to reduce indentation in diff view
        val (strippedOld, strippedNew) = stripCommonLeadingWhitespace(oldText, newText)

        val oldLines = strippedOld.lines()
        val newLines = strippedNew.lines()

        // For single-line content, show simple before/after
        if (oldLines.size <= 1 && newLines.size <= 1) {
            return buildString {
                if (strippedOld.isNotEmpty()) {
                    append("<span class=\"diff-removed\">- ${escapeHtml(strippedOld)}</span>")
                }
                if (strippedNew.isNotEmpty()) {
                    append("<span class=\"diff-added\">+ ${escapeHtml(strippedNew)}</span>")
                }
            }
        }

        // For multi-line content, compute hunks of changes
        val hunks = computeHunks(oldLines, newLines)
        return formatHunksToHtml(hunks, oldLines, newLines)
    }

    /**
     * Strips common leading whitespace from both texts.
     * Returns a pair of (strippedOld, strippedNew) with the minimum common
     * leading whitespace removed from all non-empty lines.
     */
    private fun stripCommonLeadingWhitespace(oldText: String, newText: String): Pair<String, String> {
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val allLines = oldLines + newLines

        // Find minimum leading whitespace among non-empty lines
        val minIndent = allLines
            .filter { it.trim().isNotEmpty() }
            .map { line ->
                line.takeWhile { it == ' ' || it == '\t' }.length
            }
            .minOrNull() ?: 0

        if (minIndent == 0) {
            return oldText to newText
        }

        // Strip the common indentation from all lines
        val strippedOld = oldLines.joinToString("\n") { line ->
            if (line.length <= minIndent || line.trim().isEmpty()) line.trim()
            else line.substring(minIndent)
        }
        val strippedNew = newLines.joinToString("\n") { line ->
            if (line.length <= minIndent || line.trim().isEmpty()) line.trim()
            else line.substring(minIndent)
        }

        return strippedOld to strippedNew
    }

    /**
     * Represents a hunk of changes - a contiguous block of modifications.
     */
    private data class Hunk(
        val oldStart: Int,  // inclusive
        val oldEnd: Int,    // exclusive
        val newStart: Int,  // inclusive
        val newEnd: Int     // exclusive
    )

    /**
     * Computes hunks of differences between two lists of lines.
     * Uses a simple approach: find regions where lines differ.
     */
    private fun computeHunks(oldLines: List<String>, newLines: List<String>): List<Hunk> {
        val hunks = mutableListOf<Hunk>()
        var oldIdx = 0
        var newIdx = 0

        while (oldIdx < oldLines.size || newIdx < newLines.size) {
            // Skip matching lines
            while (oldIdx < oldLines.size && newIdx < newLines.size &&
                   oldLines[oldIdx] == newLines[newIdx]) {
                oldIdx++
                newIdx++
            }

            // Found a difference - find the extent
            val hunkOldStart = oldIdx
            val hunkNewStart = newIdx

            // Find end of hunk (next matching sequence)
            var hunkOldEnd = oldIdx
            var hunkNewEnd = newIdx

            // Look ahead for matching lines to end the hunk
            var foundMatch = false
            val lookAhead = 10  // Look ahead up to 10 lines

            for (i in 0..lookAhead) {
                val testOldIdx = hunkOldEnd + i
                val testNewIdx = hunkNewEnd

                // Check if we can match by skipping ahead in old
                while (testOldIdx < oldLines.size && testNewIdx < newLines.size &&
                       oldLines[testOldIdx] == newLines[testNewIdx]) {
                    hunkOldEnd = testOldIdx
                    hunkNewEnd = testNewIdx
                    foundMatch = true
                    break
                }
            }

            // If no match found looking ahead, consume lines from both sides
            if (!foundMatch) {
                val remainingOld = minOf(10, oldLines.size - hunkOldEnd)
                val remainingNew = minOf(10, newLines.size - hunkNewEnd)
                hunkOldEnd = hunkOldStart + remainingOld
                hunkNewEnd = hunkNewStart + remainingNew
            }

            // Only create hunk if there are actual changes
            if (hunkOldStart != hunkOldEnd || hunkNewStart != hunkNewEnd) {
                hunks.add(Hunk(hunkOldStart, hunkOldEnd, hunkNewStart, hunkNewEnd))
            }

            oldIdx = hunkOldEnd
            newIdx = hunkNewEnd
        }

        return hunks
    }

    /**
     * Formats hunks to HTML with context lines.
     */
    private fun formatHunksToHtml(hunks: List<Hunk>, oldLines: List<String>, newLines: List<String>): String {
        val result = StringBuilder()
        val contextLines = 3

        for (hunk in hunks) {
            // Add context lines before hunk
            val contextStart = maxOf(0, hunk.oldStart - contextLines)
            for (i in contextStart until hunk.oldStart) {
                if (i >= 0 && i < oldLines.size) {
                    result.append("<span class=\"diff-context\">  ${escapeHtml(oldLines[i])}</span>")
                }
            }

            // Add removed lines
            for (i in hunk.oldStart until hunk.oldEnd) {
                result.append("<span class=\"diff-removed\">- ${escapeHtml(oldLines[i])}</span>")
            }

            // Add added lines
            for (i in hunk.newStart until hunk.newEnd) {
                result.append("<span class=\"diff-added\">+ ${escapeHtml(newLines[i])}</span>")
            }

            // Add context lines after hunk
            val contextEnd = minOf(oldLines.size, hunk.oldEnd + contextLines)
            for (i in hunk.oldEnd until contextEnd) {
                result.append("<span class=\"diff-context\">  ${escapeHtml(oldLines[i])}</span>")
            }

            // Add separator between hunks
            if (hunks.indexOf(hunk) < hunks.size - 1) {
                result.append("<br>")
            }
        }

        return result.toString()
    }
}

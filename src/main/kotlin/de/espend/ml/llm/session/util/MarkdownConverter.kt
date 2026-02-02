package de.espend.ml.llm.session.util

import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * Markdown to HTML converter using JetBrains markdown library.
 * Converts markdown content to HTML for session display.
 */
object MarkdownConverter {

    private val flavour = CommonMarkFlavourDescriptor()
    private val parser = MarkdownParser(flavour)

    // Patterns that indicate content is likely markdown
    private val MARKDOWN_PATTERNS = listOf(
        Regex("""\*\*[^*]+\*\*"""),           // **bold**
        Regex("""__[^_]+__"""),                // __bold__
        Regex("""\*[^*]+\*"""),                // *italic*
        Regex("""_[^_]+_"""),                  // _italic_
        Regex("""`[^`]+`"""),                  // `code`
        Regex("""\[.+]\(.+\)"""),              // [link](url)
        Regex("""^#{1,6}\s+.+""", RegexOption.MULTILINE),  // # Header
        Regex("""^[-*+]\s+.+""", RegexOption.MULTILINE),   // - list item
        Regex("""^\d+\.\s+.+""", RegexOption.MULTILINE),   // 1. numbered list
        Regex("""```[\s\S]*?```"""),           // ```code block```
    )

    /**
     * Checks if the content appears to be markdown.
     * Returns true if the content contains markdown patterns.
     */
    fun isMarkdown(content: String): Boolean {
        if (content.isBlank()) return false

        for (pattern in MARKDOWN_PATTERNS) {
            if (pattern.containsMatchIn(content)) {
                return true
            }
        }
        return false
    }

    /**
     * Converts markdown content to HTML using JetBrains markdown library.
     */
    fun toHtml(content: String): String {
        if (content.isBlank()) return ""

        val parsedTree = parser.buildMarkdownTreeFromString(content)
        val html = HtmlGenerator(content, parsedTree, flavour).generateHtml()

        // The library wraps output in <body> tags, remove them
        return html
            .removePrefix("<body>")
            .removeSuffix("</body>")
            .trim()
    }

    /**
     * Converts if content is markdown, otherwise returns HTML-escaped content.
     */
    fun convertIfMarkdown(content: String): String {
        return if (isMarkdown(content)) {
            toHtml(content)
        } else {
            HtmlBuilder.escapeHtml(content)
        }
    }
}

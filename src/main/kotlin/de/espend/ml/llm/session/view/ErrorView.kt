package de.espend.ml.llm.session.view

import com.intellij.ui.jcef.JBCefJSQuery
import de.espend.ml.llm.session.util.CssStyles
import de.espend.ml.llm.session.util.HtmlBuilder
import de.espend.ml.llm.session.util.Icons
import de.espend.ml.llm.session.util.JsHandlers

/**
 * Generates HTML for error and not-found pages.
 */
class ErrorView(
    private val jsQueryRouter: JBCefJSQuery,
    private val jsQueryCursor: JBCefJSQuery
) {

    /**
     * Generates the complete HTML for an error page.
     */
    fun generateErrorPage(title: String, message: String): String {
        return buildString {
            // HTML header
            append(HtmlBuilder.buildHeader(styles = CssStyles.forErrorView()))

            // Body content
            appendLine("        <div class=\"container\">")
            appendLine("            <a href=\"${JsHandlers.goBackUrl()}\" class=\"back-link\">${Icons.arrowLeft(14)} Back to Sessions</a>")
            appendLine("            <div class=\"error-state\">")
            appendLine("                <h2>${HtmlBuilder.escapeHtml(title)}</h2>")
            appendLine("                <p>${HtmlBuilder.escapeHtml(message)}</p>")
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
}

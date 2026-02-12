package de.espend.ml.llm.session.view

import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefJSQuery
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.util.CssStyles
import de.espend.ml.llm.session.util.HtmlBuilder
import de.espend.ml.llm.session.util.JsHandlers

/**
 * Generates HTML for the session list view.
 */
class SessionListView(
    private val project: Project,
    private val jsQueryRouter: JBCefJSQuery,
    private val jsQueryCursor: JBCefJSQuery
) {

    /**
     * Generates the complete HTML for the session list view.
     */
    fun generate(sessions: List<SessionListItem>, showRefreshToast: Boolean = false): String {
        val projectName = project.name

        return buildString {
            // HTML header
            append(HtmlBuilder.buildHeader(styles = CssStyles.forListView()))

            // Body content
            appendLine("    <div class=\"toast\" id=\"toast\">Sessions refreshed</div>")
            appendLine("    <div class=\"container\">")
            appendLine("        <h1>Chat Sessions</h1>")
            appendLine("        <div class=\"subtitle\">Project: $projectName</div>")

            // Unified session list
            if (sessions.isEmpty()) {
                appendLine("        <div class=\"empty-state\">No sessions found for this project</div>")
            } else {
                appendLine("        <div class=\"session-list\">")
                sessions.forEach { session ->
                    appendSessionItem(this, session)
                }
                appendLine("        </div>")
            }

            appendLine("    </div>")

            // JavaScript handlers
            appendLine("    <script>")
            append(JsHandlers.generateToast(showRefreshToast))
            append(JsHandlers.generateBaseScript(jsQueryRouter, jsQueryCursor))
            appendLine("    </script>")

            // HTML footer
            append(HtmlBuilder.buildFooter())
        }
    }

    private fun appendSessionItem(sb: StringBuilder, session: SessionListItem) {
        val providerKey = when (session.provider) {
            SessionProvider.CLAUDE_CODE -> "claude"
            SessionProvider.OPENCODE -> "opencode"
            SessionProvider.CODEX -> "codex"
            SessionProvider.AMP -> "amp"
            SessionProvider.JUNIE -> "junie"
            SessionProvider.DROID -> "droid"
            SessionProvider.GEMINI -> "gemini"
            SessionProvider.KILO_CODE -> "kilocode"
        }
        val actionUrl = JsHandlers.sessionDetailUrl(session.sessionId, providerKey)
        val providerIcon = getProviderIconSvg(session.provider)

        sb.appendLine("                <a href=\"$actionUrl\" class=\"session-item\" data-session-id=\"${session.sessionId}\" data-provider=\"$providerKey\">")
        sb.appendLine("                    <div class=\"session-header\">")
        sb.appendLine("                        <div class=\"provider-icon\">$providerIcon</div>")
        sb.appendLine("                        <div class=\"session-title\">${HtmlBuilder.escapeHtml(session.title)}</div>")
        sb.appendLine("                    </div>")
        sb.appendLine("                    <div class=\"session-meta\">")

        // Unified badges - render based on available data, not provider type
        if (session.messageCount != null && session.messageCount > 0) {
            sb.appendLine("                        <span class=\"badge badge-messages\">${session.messageCount} messages</span>")
        }

        sb.appendLine("                        <span class=\"timestamp\">${HtmlBuilder.formatTimestamp(session.sortTimestamp)}</span>")
        sb.appendLine("                    </div>")
        sb.appendLine("                    <div class=\"session-id\">ID: ${session.sessionId}</div>")
        sb.appendLine("                </a>")
    }

    companion object {
        /**
         * Claude icon SVG - uses currentColor for theme support (adapts to dark/light).
         */
        private const val CLAUDE_ICON_SVG = """<svg class="provider-svg claude-svg" width="16" height="16" viewBox="0 0 24 24" fill="currentColor" fill-rule="evenodd" xmlns="http://www.w3.org/2000/svg"><path d="M4.709 15.955l4.72-2.647.08-.23-.08-.128H9.2l-.79-.048-2.698-.073-2.339-.097-2.266-.122-.571-.121L0 11.784l.055-.352.48-.321.686.06 1.52.103 2.278.158 1.652.097 2.449.255h.389l.055-.157-.134-.098-.103-.097-2.358-1.596-2.552-1.688-1.336-.972-.724-.491-.364-.462-.158-1.008.656-.722.881.06.225.061.893.686 1.908 1.476 2.491 1.833.365.304.145-.103.019-.073-.164-.274-1.355-2.446-1.446-2.49-.644-1.032-.17-.619a2.97 2.97 0 01-.104-.729L6.283.134 6.696 0l.996.134.42.364.62 1.414 1.002 2.229 1.555 3.03.456.898.243.832.091.255h.158V9.01l.128-1.706.237-2.095.23-2.695.08-.76.376-.91.747-.492.584.28.48.685-.067.444-.286 1.851-.559 2.903-.364 1.942h.212l.243-.242.985-1.306 1.652-2.064.73-.82.85-.904.547-.431h1.033l.76 1.129-.34 1.166-1.064 1.347-.881 1.142-1.264 1.7-.79 1.36.073.11.188-.02 2.856-.606 1.543-.28 1.841-.315.833.388.091.395-.328.807-1.969.486-2.309.462-3.439.813-.042.03.049.061 1.549.146.662.036h1.622l3.02.225.79.522.474.638-.079.485-1.215.62-1.64-.389-3.829-.91-1.312-.329h-.182v.11l1.093 1.068 2.006 1.81 2.509 2.33.127.578-.322.455-.34-.049-2.205-1.657-.851-.747-1.926-1.62h-.128v.17l.444.649 2.345 3.521.122 1.08-.17.353-.608.213-.668-.122-1.374-1.925-1.415-2.167-1.143-1.943-.14.08-.674 7.254-.316.37-.729.28-.607-.461-.322-.747.322-1.476.389-1.924.315-1.53.286-1.9.17-.632-.012-.042-.14.018-1.434 1.967-2.18 2.945-1.726 1.845-.414.164-.717-.37.067-.662.401-.589 2.388-3.036 1.44-1.882.93-1.086-.006-.158h-.055L4.132 18.56l-1.13.146-.487-.456.061-.746.231-.243 1.908-1.312-.006.006z"></path></svg>"""

        /**
         * OpenCode icon SVG for dark theme (light colors on dark background).
         * Using viewBox offset to center the 240x240 content area of the 240x300 original.
         */
        private const val OPENCODE_ICON_DARK_SVG = """<svg class="opencode-dark" width="16" height="16" viewBox="0 30 240 240" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M180 240H60V120H180V240Z" fill="#4B4646"/><path d="M180 60H60V240H180V60ZM240 300H0V0H240V300Z" fill="#F1ECEC"/></svg>"""

        /**
         * OpenCode icon SVG for light theme (dark colors on light background).
         * Using viewBox offset to center the 240x240 content area of the 240x300 original.
         */
        private const val OPENCODE_ICON_LIGHT_SVG = """<svg class="opencode-light" width="16" height="16" viewBox="0 30 240 240" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M180 240H60V120H180V240Z" fill="#CFCECD"/><path d="M180 60H60V240H180V60ZM240 300H0V0H240V300Z" fill="#211E1E"/></svg>"""

        /**
         * Codex icon SVG (OpenAI logo) - uses currentColor for theme support.
         */
        private const val CODEX_ICON_SVG = """<svg class="provider-svg codex-svg" width="16" height="16" viewBox="0 0 24 24" fill="currentColor" fill-rule="evenodd" xmlns="http://www.w3.org/2000/svg"><path d="M21.55 10.004a5.416 5.416 0 00-.478-4.501c-1.217-2.09-3.662-3.166-6.05-2.66A5.59 5.59 0 0010.831 1C8.39.995 6.224 2.546 5.473 4.838A5.553 5.553 0 001.76 7.496a5.487 5.487 0 00.691 6.5 5.416 5.416 0 00.477 4.502c1.217 2.09 3.662 3.165 6.05 2.66A5.586 5.586 0 0013.168 23c2.443.006 4.61-1.546 5.361-3.84a5.553 5.553 0 003.715-2.66 5.488 5.488 0 00-.693-6.497v.001zm-8.381 11.558a4.199 4.199 0 01-2.675-.954c.034-.018.093-.05.132-.074l4.44-2.53a.71.71 0 00.364-.623v-6.176l1.877 1.069c.02.01.033.029.036.05v5.115c-.003 2.274-1.87 4.118-4.174 4.123zM4.192 17.78a4.059 4.059 0 01-.498-2.763c.032.02.09.055.131.078l4.44 2.53c.225.13.504.13.73 0l5.42-3.088v2.138a.068.068 0 01-.027.057L9.9 19.288c-1.999 1.136-4.552.46-5.707-1.51h-.001zM3.023 8.216A4.15 4.15 0 015.198 6.41l-.002.151v5.06a.711.711 0 00.364.624l5.42 3.087-1.876 1.07a.067.067 0 01-.063.005l-4.489-2.559c-1.995-1.14-2.679-3.658-1.53-5.63h.001zm15.417 3.54l-5.42-3.088L14.896 7.6a.067.067 0 01.063-.006l4.489 2.557c1.998 1.14 2.683 3.662 1.529 5.633a4.163 4.163 0 01-2.174 1.807V12.38a.71.71 0 00-.363-.623zm1.867-2.773a6.04 6.04 0 00-.132-.078l-4.44-2.53a.731.731 0 00-.729 0l-5.42 3.088V7.325a.068.068 0 01.027-.057L14.1 4.713c2-1.137 4.555-.46 5.707 1.513.487.833.664 1.809.499 2.757h.001zm-11.741 3.81l-1.877-1.068a.065.065 0 01-.036-.051V6.559c.001-2.277 1.873-4.122 4.181-4.12.976 0 1.92.338 2.671.954-.034.018-.092.05-.131.073l-4.44 2.53a.71.71 0 00-.365.623l-.003 6.173v.002zm1.02-2.168L12 9.25l2.414 1.375v2.75L12 14.75l-2.415-1.375v-2.75z"/></svg>"""

        /**
         * AMP icon SVG for dark theme (white fill on dark background).
         */
        private const val AMP_ICON_DARK_SVG = """<svg class="amp-dark" width="16" height="16" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M13.9197 13.61L17.3816 26.566L14.242 27.4049L11.2645 16.2643L0.119926 13.2906L0.957817 10.15L13.9197 13.61Z" fill="#FFFFFF"/><path d="M13.7391 16.0892L4.88169 24.9056L2.58872 22.6019L11.4461 13.7865L13.7391 16.0892Z" fill="#FFFFFF"/><path d="M18.9386 8.58315L22.4005 21.5392L19.2609 22.3781L16.2833 11.2374L5.13879 8.26381L5.97668 5.12318L18.9386 8.58315Z" fill="#FFFFFF"/><path d="M23.9803 3.55632L27.4422 16.5124L24.3025 17.3512L21.325 6.21062L10.1805 3.23698L11.0183 0.0963593L23.9803 3.55632Z" fill="#FFFFFF"/></svg>"""

        /**
         * AMP icon SVG for light theme (black fill on light background).
         */
        private const val AMP_ICON_LIGHT_SVG = """<svg class="amp-light" width="16" height="16" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M13.9197 13.61L17.3816 26.566L14.242 27.4049L11.2645 16.2643L0.119926 13.2906L0.957817 10.15L13.9197 13.61Z" fill="#000000"/><path d="M13.7391 16.0892L4.88169 24.9056L2.58872 22.6019L11.4461 13.7865L13.7391 16.0892Z" fill="#000000"/><path d="M18.9386 8.58315L22.4005 21.5392L19.2609 22.3781L16.2833 11.2374L5.13879 8.26381L5.97668 5.12318L18.9386 8.58315Z" fill="#000000"/><path d="M23.9803 3.55632L27.4422 16.5124L24.3025 17.3512L21.325 6.21062L10.1805 3.23698L11.0183 0.0963593L23.9803 3.55632Z" fill="#000000"/></svg>"""

        /**
         * Junie icon SVG for dark theme (white fill on dark background).
         */
        private const val JUNIE_ICON_DARK_SVG = """<svg class="junie-dark" width="16" height="16" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M25 15H35V16.75C35 29 30.5001 35 16.5001 35H15V25H16.5001C22.6251 25 25 22.875 25 16.75V15Z" fill="#FFFFFF"/><rect x="5" y="15" width="10" height="10" fill="#FFFFFF"/><rect x="15" y="5" width="10" height="10" fill="#FFFFFF"/></svg>"""

        /**
         * Junie icon SVG for light theme (black fill on light background).
         */
        private const val JUNIE_ICON_LIGHT_SVG = """<svg class="junie-light" width="16" height="16" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M25 15H35V16.75C35 29 30.5001 35 16.5001 35H15V25H16.5001C22.6251 25 25 22.875 25 16.75V15Z" fill="#000000"/><rect x="5" y="15" width="10" height="10" fill="#000000"/><rect x="15" y="5" width="10" height="10" fill="#000000"/></svg>"""

        /**
         * Droid icon SVG for dark theme (light teal #2DD4BF on dark background).
         */
        private const val DROID_ICON_DARK_SVG = """<svg class="droid-dark" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path fill="#2DD4BF" d="M12 2C9.243 2 7 4.243 7 7v1H6c-1.103 0-2 .897-2 2v9c0 1.103.897 2 2 2h12c1.103 0 2-.897 2-2v-9c0-1.103-.897-2-2-2h-1V7c0-2.757-2.243-5-5-5zm0 2c1.654 0 3 1.346 3 3v1H9V7c0-1.654 1.346-3 3-3zm-3 8c.552 0 1 .448 1 1s-.448 1-1 1-1-.448-1-1 .448-1 1-1zm6 0c.552 0 1 .448 1 1s-.448 1-1 1-1-.448-1-1 .448-1 1-1z"/></svg>"""

        /**
         * Droid icon SVG for light theme (teal #0D9488 on light background).
         */
        private const val DROID_ICON_LIGHT_SVG = """<svg class="droid-light" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path fill="#0D9488" d="M12 2C9.243 2 7 4.243 7 7v1H6c-1.103 0-2 .897-2 2v9c0 1.103.897 2 2 2h12c1.103 0 2-.897 2-2v-9c0-1.103-.897-2-2-2h-1V7c0-2.757-2.243-5-5-5zm0 2c1.654 0 3 1.346 3 3v1H9V7c0-1.654 1.346-3 3-3zm-3 8c.552 0 1 .448 1 1s-.448 1-1 1-1-.448-1-1 .448-1 1-1zm6 0c.552 0 1 .448 1 1s-.448 1-1 1-1-.448-1-1 .448-1 1-1z"/></svg>"""

        /**
         * Gemini icon SVG for dark theme (light blue #8AB4F8 sparkle on dark background).
         */
        private const val GEMINI_ICON_DARK_SVG = """<svg class="gemini-dark" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path fill="#8AB4F8" d="M12 2L14.5 9.5L22 12L14.5 14.5L12 22L9.5 14.5L2 12L9.5 9.5L12 2Z"/></svg>"""

        /**
         * Gemini icon SVG for light theme (Google blue #4285F4 sparkle on light background).
         */
        private const val GEMINI_ICON_LIGHT_SVG = """<svg class="gemini-light" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path fill="#4285F4" d="M12 2L14.5 9.5L22 12L14.5 14.5L12 22L9.5 14.5L2 12L9.5 9.5L12 2Z"/></svg>"""

        /**
         * Kilo Code icon SVG for dark theme (light blue #60A5FA on dark background).
         */
        private const val KILO_ICON_DARK_SVG = """<svg class="kilo-dark" width="16" height="16" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg"><path fill="#60A5FA" d="M0,0v100h100V0H0ZM92.5925926,92.5925926H7.4074074V7.4074074h85.1851852v85.1851852ZM61.1111044,71.9096084h9.2592593v7.4074074h-11.6402116l-5.026455-5.026455v-11.6402116h7.4074074v9.2592593ZM77.7777711,71.9096084h-7.4074074v-9.2592593h-9.2592593v-7.4074074h11.6402116l5.026455,5.026455v11.6402116ZM46.2962963,61.1114207h-7.4074074v-7.4074074h7.4074074v7.4074074ZM22.2222222,53.7040133h7.4074074v16.6666667h16.6666667v7.4074074h-19.047619l-5.026455-5.026455v-19.047619ZM77.7777711,38.8888889v7.4074074h-24.0740741v-7.4074074h8.2781918v-9.2592593h-8.2781918v-7.4074074h10.6591442l5.026455,5.026455v11.6402116h8.3884749ZM29.6296296,30.5555556h9.2592593l7.4074074,7.4074074v8.3333333h-7.4074074v-8.3333333h-9.2592593v8.3333333h-7.4074074v-24.0740741h7.4074074v8.3333333ZM46.2962963,30.5555556h-7.4074074v-8.3333333h7.4074074v8.3333333Z"/></svg>"""

        /**
         * Kilo Code icon SVG for light theme (blue #2563EB on light background).
         */
        private const val KILO_ICON_LIGHT_SVG = """<svg class="kilo-light" width="16" height="16" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg"><path fill="#2563EB" d="M0,0v100h100V0H0ZM92.5925926,92.5925926H7.4074074V7.4074074h85.1851852v85.1851852ZM61.1111044,71.9096084h9.2592593v7.4074074h-11.6402116l-5.026455-5.026455v-11.6402116h7.4074074v9.2592593ZM77.7777711,71.9096084h-7.4074074v-9.2592593h-9.2592593v-7.4074074h11.6402116l5.026455,5.026455v11.6402116ZM46.2962963,61.1114207h-7.4074074v-7.4074074h7.4074074v7.4074074ZM22.2222222,53.7040133h7.4074074v16.6666667h16.6666667v7.4074074h-19.047619l-5.026455-5.026455v-19.047619ZM77.7777711,38.8888889v7.4074074h-24.0740741v-7.4074074h8.2781918v-9.2592593h-8.2781918v-7.4074074h10.6591442l5.026455,5.026455v11.6402116h8.3884749ZM29.6296296,30.5555556h9.2592593l7.4074074,7.4074074v8.3333333h-7.4074074v-8.3333333h-9.2592593v8.3333333h-7.4074074v-24.0740741h7.4074074v8.3333333ZM46.2962963,30.5555556h-7.4074074v-8.3333333h7.4074074v8.3333333Z"/></svg>"""
    }

    /**
     * Returns an inline SVG icon for the provider that respects dark/light theme.
     */
    private fun getProviderIconSvg(provider: SessionProvider): String {
        return when (provider) {
            SessionProvider.CLAUDE_CODE -> CLAUDE_ICON_SVG
            SessionProvider.OPENCODE -> OPENCODE_ICON_DARK_SVG + OPENCODE_ICON_LIGHT_SVG
            SessionProvider.CODEX -> CODEX_ICON_SVG
            SessionProvider.AMP -> AMP_ICON_DARK_SVG + AMP_ICON_LIGHT_SVG
            SessionProvider.JUNIE -> JUNIE_ICON_DARK_SVG + JUNIE_ICON_LIGHT_SVG
            SessionProvider.DROID -> DROID_ICON_DARK_SVG + DROID_ICON_LIGHT_SVG
            SessionProvider.GEMINI -> GEMINI_ICON_DARK_SVG + GEMINI_ICON_LIGHT_SVG
            SessionProvider.KILO_CODE -> KILO_ICON_DARK_SVG + KILO_ICON_LIGHT_SVG
        }
    }
}

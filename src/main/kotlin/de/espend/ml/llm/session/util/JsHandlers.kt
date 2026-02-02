package de.espend.ml.llm.session.util

import com.intellij.ui.jcef.JBCefJSQuery

/**
 * JavaScript handler generation utilities.
 * Uses a centralized link handler that intercepts all anchor clicks and routes based on href.
 *
 * URL scheme: action:<action-name>?param1=value1&param2=value2
 * Examples:
 *   - action:session-detail?id=abc123&provider=claude
 *   - action:go-back
 */
object JsHandlers {

    /**
     * Supported action types for URL routing.
     */
    object Actions {
        const val SESSION_DETAIL = "session-detail"
        const val GO_BACK = "go-back"
    }

    /**
     * Generates the base JavaScript that should be included on all pages.
     * Includes:
     * - Centralized link handler for action: URLs
     * - Cursor tracking for anchor elements
     *
     * When jsQueryRouter/jsQueryCursor are null, generates standalone JS for browser use.
     */
    fun generateBaseScript(jsQueryRouter: JBCefJSQuery? = null, jsQueryCursor: JBCefJSQuery? = null): String {
        val isStandalone = jsQueryRouter == null || jsQueryCursor == null

        return buildString {
            if (!isStandalone) {
                val routerCall = jsQueryRouter!!.inject("href")
                val cursorCall = jsQueryCursor!!.inject("isClickable")

                // Link handler (JCEF only)
                appendLine("// Centralized link handler - intercepts all anchor clicks")
                appendLine("document.addEventListener('click', function(e) {")
                appendLine("    var target = e.target;")
                appendLine("    while (target && target.tagName !== 'A') {")
                appendLine("        target = target.parentElement;")
                appendLine("    }")
                appendLine("    if (!target) return;")
                appendLine("")
                appendLine("    var href = target.getAttribute('href');")
                appendLine("    if (!href) return;")
                appendLine("")
                appendLine("    if (href.startsWith('action:')) {")
                appendLine("        e.preventDefault();")
                appendLine("        e.stopPropagation();")
                appendLine("        try {")
                appendLine("            $routerCall")
                appendLine("        } catch(err) {")
                appendLine("            console.error('Link handler error:', err);")
                appendLine("        }")
                appendLine("        return false;")
                appendLine("    }")
                appendLine("});")
                appendLine("")

                // Cursor tracking (JCEF only)
                appendLine("// Cursor tracking - communicate with Java to change cursor")
                appendLine("function updateCursor(target) {")
                appendLine("    var isClickable = false;")
                appendLine("    var el = target;")
                appendLine("    while (el && el !== document.body) {")
                appendLine("        if (el.tagName === 'A' || el.tagName === 'BUTTON' || el.classList.contains('clickable')) {")
                appendLine("            isClickable = true;")
                appendLine("            break;")
                appendLine("        }")
                appendLine("        el = el.parentElement;")
                appendLine("    }")
                appendLine("    try {")
                appendLine("        $cursorCall")
                appendLine("    } catch(e) {}")
                appendLine("}")
                appendLine("document.addEventListener('mouseover', function(e) { updateCursor(e.target); });")
                appendLine("document.addEventListener('mousemove', function(e) { updateCursor(e.target); });")
                appendLine("")
                // Fix scroll speed for JCEF browser - wheel events scroll less than normal
                appendLine("// Fix scroll speed for JCEF browser - wheel events scroll less than normal")
                appendLine("document.addEventListener('wheel', function(e) {")
                appendLine("    var container = document.querySelector('.container');")
                appendLine("    if (container && e.target.closest('.container')) {")
                appendLine("        e.preventDefault();")
                appendLine("        var scrollAmount = e.deltaY * 3;")  // Multiply by 3 for faster scrolling
                appendLine("        container.scrollTop += scrollAmount;")
                appendLine("    }")
                appendLine("}, { passive: false });")
                appendLine("")
            }

            // Tooltip handling (works in both JCEF and browser)
            appendLine("// Tooltip handling - uses fixed positioning to escape overflow:hidden")
            appendLine("(function() {")
            appendLine("    var tooltip = document.createElement('div');")
            appendLine("    tooltip.className = 'tooltip-popup';")
            appendLine("    document.body.appendChild(tooltip);")
            appendLine("")
            appendLine("    document.addEventListener('mouseenter', function(e) {")
            appendLine("        var target = e.target.closest('[data-tooltip]');")
            appendLine("        if (!target) return;")
            appendLine("        var text = target.getAttribute('data-tooltip');")
            appendLine("        if (!text) return;")
            appendLine("        tooltip.textContent = text;")
            appendLine("        var rect = target.getBoundingClientRect();")
            appendLine("        tooltip.style.left = (rect.left + rect.width / 2) + 'px';")
            appendLine("        tooltip.style.top = (rect.top - 4) + 'px';")
            appendLine("        tooltip.style.transform = 'translate(-50%, -100%)';")
            appendLine("        tooltip.classList.add('visible');")
            appendLine("    }, true);")
            appendLine("")
            appendLine("    document.addEventListener('mouseleave', function(e) {")
            appendLine("        if (e.target.closest('[data-tooltip]')) {")
            appendLine("            tooltip.classList.remove('visible');")
            appendLine("        }")
            appendLine("    }, true);")
            appendLine("})();")
        }
    }

    /**
     * Generates JavaScript for sticky header functionality.
     */
    fun generateStickyHeaderScript(): String {
        return buildString {
            appendLine("// Sticky header - shows when metadata card scrolls out of view")
            appendLine("function scrollToTop() {")
            appendLine("    var container = document.querySelector('.container');")
            appendLine("    if (!container) return;")
            appendLine("    var start = container.scrollTop;")
            appendLine("    var duration = 150;")
            appendLine("    var startTime = performance.now();")
            appendLine("    function animate(time) {")
            appendLine("        var elapsed = time - startTime;")
            appendLine("        var progress = Math.min(elapsed / duration, 1);")
            appendLine("        container.scrollTop = start * (1 - progress);")
            appendLine("        if (progress < 1) requestAnimationFrame(animate);")
            appendLine("    }")
            appendLine("    requestAnimationFrame(animate);")
            appendLine("}")
            appendLine("")
            appendLine("(function() {")
            appendLine("    var stickyHeader = document.getElementById('stickyHeader');")
            appendLine("    var metadataCard = document.querySelector('.metadata-card');")
            appendLine("    if (!stickyHeader || !metadataCard) return;")
            appendLine("")
            appendLine("    var container = document.querySelector('.container');")
            appendLine("    if (!container) return;")
            appendLine("")
            appendLine("    container.addEventListener('scroll', function() {")
            appendLine("        var cardRect = metadataCard.getBoundingClientRect();")
            appendLine("        if (cardRect.bottom < 0) {")
            appendLine("            stickyHeader.classList.add('visible');")
            appendLine("        } else {")
            appendLine("            stickyHeader.classList.remove('visible');")
            appendLine("        }")
            appendLine("    });")
            appendLine("})();")
        }
    }

    /**
     * Generates JavaScript for expand/collapse functionality.
     */
    fun generateExpandScript(): String {
        return buildString {
            appendLine("// Expand/collapse functionality")
            appendLine("function toggleExpand(element) {")
            appendLine("    var wrapper = element.parentElement;")
            appendLine("    var textSpan = element.querySelector('.expand-text');")
            appendLine("    if (wrapper.classList.contains('collapsed')) {")
            appendLine("        wrapper.classList.remove('collapsed');")
            appendLine("        wrapper.classList.add('expanded');")
            appendLine("        if (textSpan) textSpan.textContent = 'Collapse';")
            appendLine("    } else {")
            appendLine("        wrapper.classList.remove('expanded');")
            appendLine("        wrapper.classList.add('collapsed');")
            appendLine("        if (textSpan) textSpan.textContent = 'Expand';")
            appendLine("    }")
            appendLine("}")
            appendLine("")
            appendLine("// Initialize expandable content on load")
            appendLine("document.addEventListener('DOMContentLoaded', function() {")
            appendLine("    var wrappers = document.querySelectorAll('.message-content-wrapper');")
            appendLine("    wrappers.forEach(function(wrapper) {")
            appendLine("        var content = wrapper.querySelector('.message-content');")
            appendLine("        if (content && content.scrollHeight > 220) {")
            appendLine("            wrapper.classList.add('collapsed');")
            appendLine("        }")
            appendLine("    });")
            appendLine("});")
        }
    }

    /**
     * Generates toast notification JavaScript.
     */
    fun generateToast(show: Boolean = true): String {
        if (!show) return ""
        return buildString {
            appendLine("const toast = document.getElementById('toast');")
            appendLine("toast.classList.add('show');")
            appendLine("setTimeout(() => { toast.classList.remove('show'); }, 3000);")
        }
    }

    /**
     * Builds an action URL for session detail navigation.
     */
    fun sessionDetailUrl(sessionId: String, provider: String): String {
        return "action:${Actions.SESSION_DETAIL}?id=$sessionId&provider=$provider"
    }

    /**
     * Builds an action URL for going back.
     */
    fun goBackUrl(): String {
        return "action:${Actions.GO_BACK}"
    }

    /**
     * Parses an action URL and returns the action name and parameters.
     * Returns null if the URL is not a valid action URL.
     */
    fun parseActionUrl(url: String): ActionRoute? {
        if (!url.startsWith("action:")) return null

        val actionPart = url.removePrefix("action:")
        val parts = actionPart.split("?", limit = 2)
        val action = parts[0]
        val params = if (parts.size > 1) {
            parts[1].split("&")
                .mapNotNull { param ->
                    val kv = param.split("=", limit = 2)
                    if (kv.size == 2) kv[0] to kv[1] else null
                }
                .toMap()
        } else {
            emptyMap()
        }

        return ActionRoute(action, params)
    }

    /**
     * Represents a parsed action route.
     */
    data class ActionRoute(
        val action: String,
        val params: Map<String, String>
    ) {
        fun getParam(key: String): String? = params[key]
    }
}

package de.espend.ml.llm.session.util

/**
 * CSS style definitions for the session browser.
 * All styles are centralized here for easy maintenance.
 * Colors are defined via CSS variables from ThemeColors for theme support.
 */
object CssStyles {

    val BASE = """
        * { margin: 0; padding: 0; box-sizing: border-box; }
        html, body { overflow: hidden; height: 100%; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif; background: var(--jb-color-background); color: var(--jb-color-foreground); font-size: 13px; }
        .text-truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    """.trimIndent()

    val TOOLTIP = """
        [data-tooltip] { cursor: default; }
        .tooltip-popup { position: fixed; background: var(--jb-color-foreground); color: var(--jb-color-background); padding: 4px 8px; border-radius: 4px; font-size: 11px; white-space: nowrap; z-index: 10000; pointer-events: none; opacity: 0; transition: opacity 0.15s ease; }
        .tooltip-popup.visible { opacity: 1; }
    """.trimIndent()

    val SCROLLBAR = """
        .container::-webkit-scrollbar { width: 18px; }
        .container::-webkit-scrollbar-track { background: var(--jb-color-background); }
        .container::-webkit-scrollbar-thumb { background: var(--jb-color-scrollbar-thumb); border-radius: 9px; border: 4px solid var(--jb-color-background); min-height: 50px; }
        .container::-webkit-scrollbar-thumb:hover { background: var(--jb-color-scrollbar-thumb-hover); }
        .container { scrollbar-width: auto; scrollbar-color: var(--jb-color-scrollbar-thumb) var(--jb-color-background); }
    """.trimIndent()

    val CONTAINER = """
        .container { height: 100vh; overflow-y: auto; padding: 12px; -webkit-overflow-scrolling: touch; will-change: scroll-position; }
        .container { scroll-behavior: auto; }
    """.trimIndent()

    val BACK_LINK = """
        .back-link { display: inline-flex; align-items: center; gap: 8px; color: var(--jb-color-accent); text-decoration: none; font-size: 14px; padding: 8px 0; margin-bottom: 16px; }
        .back-link:hover { text-decoration: none; }
        .back-link svg { flex-shrink: 0; }
    """.trimIndent()

    val METADATA_CARD = """
        .metadata-card { background: var(--jb-color-background-secondary); border-radius: 8px; padding: 12px 16px; margin-bottom: 20px; }
        .metadata-headline { display: flex; align-items: center; margin-bottom: 8px; white-space: nowrap; }
        .headline-left { flex: 1; display: flex; align-items: center; gap: 8px; white-space: nowrap; }
        .headline-right { flex: 0 0 auto; display: flex; align-items: center; gap: 8px; justify-content: flex-end; white-space: nowrap; }
        .metadata-headline .metadata-item { display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; }
        .metadata-content { line-height: 1.6; padding-top: 8px; border-top: 1px solid var(--border-subtle); }
        .metadata-label { color: var(--jb-color-comment); }
        .metadata-value { color: var(--jb-color-foreground); font-weight: 500; }
        .session-meta-value { font-family: 'SF Mono', 'Monaco', monospace; }
    """.trimIndent()

    val BADGES = """
        .badge { display: inline-block; padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: 500; }
        .badge-version { background: var(--jb-color-accent-subtle); color: var(--jb-color-accent); }
        .badge-branch { background: var(--jb-color-info-subtle); color: var(--jb-color-info); }
        .badge-messages { background: var(--jb-color-info-subtle); color: var(--jb-color-info); }
    """.trimIndent()

    val SESSION_LIST = """
        .session-list { display: flex; flex-direction: column; gap: 8px; }
        a.session-item { display: block; text-decoration: none; color: inherit; padding: 10px 12px; background: var(--jb-color-background-secondary); border-radius: 6px; transition: background 0.15s ease; }
        a.session-item:hover { background: var(--jb-color-selection-inactive); text-decoration: none; }
        .session-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; min-width: 0; }
        .provider-icon { flex-shrink: 0; width: 16px; height: 16px; display: flex; align-items: center; justify-content: center; }
        .provider-svg { display: block; color: var(--jb-color-foreground); }
        .provider-svg.claude-svg { color: var(--jb-color-foreground); }
        .provider-icon svg.opencode-light { display: none; }
        .provider-icon svg.opencode-dark { display: none; }
        .theme-dark .provider-icon svg.opencode-dark { display: block; }
        .theme-light .provider-icon svg.opencode-light { display: block; }
        .provider-icon svg.amp-light { display: none; }
        .provider-icon svg.amp-dark { display: none; }
        .theme-dark .provider-icon svg.amp-dark { display: block; }
        .theme-light .provider-icon svg.amp-light { display: block; }
        .provider-icon svg.junie-light { display: none; }
        .provider-icon svg.junie-dark { display: none; }
        .theme-dark .provider-icon svg.junie-dark { display: block; }
        .theme-light .provider-icon svg.junie-light { display: block; }
        .session-title { font-weight: 500; color: var(--jb-color-foreground); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; word-break: break-word; overflow-wrap: break-word; }
        .session-meta { font-size: 11px; color: var(--jb-color-comment); display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
        .session-id { font-size: 10px; color: var(--jb-color-comment); font-family: 'SF Mono', 'Monaco', 'Cascadia Code', 'Roboto Mono', monospace; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    """.trimIndent()

    val MESSAGES = """
        .message { margin-bottom: 10px; border-radius: 8px; overflow: hidden; background: var(--jb-color-background-secondary); border-left: 3px solid var(--jb-color-border); }
        .message.user { background: var(--msg-user-bg); border-left-color: var(--msg-user-border); }
        .message.assistant { background: var(--msg-assistant-bg); border-left-color: var(--msg-assistant-border); }
        .message.tool_use { background: var(--msg-tool-use-bg); border-left-color: var(--msg-tool-use-border); }
        .message.tool_result { background: var(--msg-tool-result-bg); border-left-color: var(--msg-tool-result-border); }
        .message.thinking { background: var(--msg-thinking-bg); border-left-color: var(--msg-thinking-border); }
        .message.info { background: var(--msg-info-bg); border-left-color: var(--msg-info-border); }
        .message.schema-error { background: var(--msg-error-bg); border-left-color: var(--msg-error-border); }
        .message-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 16px; background: var(--overlay-light); font-size: 0.85rem; flex-wrap: wrap; gap: 8px; }
        .header-left { display: flex; align-items: center; gap: 12px; }
        .header-right { display: flex; align-items: center; gap: 12px; }
        .role-label { font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: var(--jb-color-comment); }
        .user .role-label { color: var(--msg-user-label); }
        .assistant .role-label { color: var(--msg-assistant-label); }
        .tool_use .role-label { color: var(--msg-tool-use-label); }
        .tool_result .role-label { color: var(--msg-tool-result-label); }
        .thinking .role-label { color: var(--msg-thinking-label); }
        .info .role-label { color: var(--msg-info-label); }
        .schema-error .role-label { color: var(--msg-error-label); }
        .timestamp { color: var(--jb-color-comment); font-size: 0.8rem; }
        .message-content { padding: 8px 12px 12px 12px; }
        .message-content p { margin: 0 0 8px 0; }
        .message-content p:last-child { margin-bottom: 0; }
        .content-text { white-space: pre-wrap; word-wrap: break-word; }

        /* Expandable content */
        .message-content-wrapper { position: relative; }
        .message-content-wrapper.collapsed .message-content { max-height: 200px; overflow: hidden; position: relative; }
        .message-content-wrapper.collapsed .message-content::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 60px; background: linear-gradient(transparent, var(--msg-assistant-bg)); pointer-events: none; }
        .message.user .message-content-wrapper.collapsed .message-content::after { background: linear-gradient(transparent, var(--msg-user-bg)); }
        .message.tool_use .message-content-wrapper.collapsed .message-content::after { background: linear-gradient(transparent, var(--msg-tool-use-bg)); }
        .message.tool_result .message-content-wrapper.collapsed .message-content::after { background: linear-gradient(transparent, var(--msg-tool-result-bg)); }
        .message.thinking .message-content-wrapper.collapsed .message-content::after { background: linear-gradient(transparent, var(--msg-thinking-bg)); }
        .message.info .message-content-wrapper.collapsed .message-content::after { background: linear-gradient(transparent, var(--msg-info-bg)); }
        .message.schema-error .message-content-wrapper.collapsed .message-content::after { background: linear-gradient(transparent, var(--msg-error-bg)); }
        .expand-bar { display: none; width: 100%; padding: 8px 0; text-align: center; background: var(--overlay-strong); color: var(--jb-color-accent); font-size: 12px; font-weight: 500; cursor: pointer; user-select: none; border-top: 1px solid var(--border-subtle); transition: background 0.15s ease; }
        .expand-bar:hover { background: var(--overlay-medium); }
        .message-content-wrapper.collapsed .expand-bar { display: block; }
        .message-content-wrapper.expanded .expand-bar { display: block; }
        .expand-bar .expand-icon { display: inline-block; margin-left: 4px; transition: transform 0.2s ease; }
        .message-content-wrapper.expanded .expand-bar .expand-icon { transform: rotate(180deg); }

        /* Children messages (nested hierarchy) */
        .message-children { margin-left: 24px; padding-left: 16px; border-left: 2px dashed var(--border-dashed); margin-top: 8px; }
        .message-children .message { margin-bottom: 8px; }
        .message-children .message:last-child { margin-bottom: 0; }

        /* Type labels - plain text styling */
        .type-badge { display: inline-block; font-size: 0.7rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; margin-right: 8px; }
        .type-badge.user { color: var(--msg-user-label); }
        .type-badge.assistant { color: var(--msg-assistant-label); }
        .type-badge.tool-use { color: var(--msg-tool-use-label); }
        .type-badge.tool_result { color: var(--msg-tool-result-label); }
        .type-badge.thinking { color: var(--msg-thinking-label); }
        .type-badge.info { color: var(--msg-info-label); }
        .type-badge.schema-error { color: var(--msg-error-label); }
    """.trimIndent()

    val CODE_BLOCK = """
        pre { background: var(--pre-bg); color: var(--pre-fg); padding: 8px 10px; border-radius: 4px; overflow-x: auto; font-size: 0.85rem; margin: 4px 0; }
        .code-block { background: var(--code-bg); color: var(--code-fg); padding: 8px 10px; border-radius: 4px; overflow-x: auto; font-size: 0.85rem; font-family: 'SF Mono', 'Monaco', 'Cascadia Code', 'Roboto Mono', monospace; white-space: pre-wrap; word-wrap: break-word; margin: 4px 0; }
        .code-block code { background: none; padding: 0; font-family: inherit; }
        /* When code is inside pre, remove code's background to avoid double background */
        pre code { background: transparent !important; padding: 0; }
        code { background: var(--overlay-light); padding: 1px 4px; border-radius: 3px; font-size: 0.9em; }
        /* Markdown content styles */
        .message-content { line-height: 1.5; }
        .message-content ul, .message-content ol { margin: 8px 0; padding-left: 24px; line-height: 1.5; }
        .message-content li { margin: 4px 0; }
        .message-content p { line-height: 1.5; }
        /* Tool result inline styles (simple output without card wrapper) */
        .tool-result-inline { margin: 12px 0 4px 0; }
        .tool-result-inline strong { font-weight: 600; }
        .tool-result-inline .tool-call-id { font-family: monospace; font-size: 0.8em; opacity: 0.7; }
        .tool-result-inline .tool-result-output { margin-top: 4px; }
        .tool-result-inline.tool-result-success .tool-result-output .code-block { border-left: 3px solid var(--jb-color-success); }
        .tool-result-inline.tool-result-error .tool-result-output .code-block { border-left: 3px solid var(--jb-color-error); }
        .tool-result-inline.tool-result-success strong { color: var(--jb-color-success); }
        .tool-result-inline.tool-result-error strong { color: var(--jb-color-error); }
    """.trimIndent()

    val DIFF_VIEW = """
        /* Diff view for Edit tool uses */
        .diff-view { margin: 12px 0; border-radius: 6px; overflow: hidden; border: 1px solid var(--border-subtle); }
        .diff-content { display: block; margin: 0; padding: 8px 12px; background: var(--code-bg); font-family: 'SF Mono', 'Monaco', 'Cascadia Code', 'Roboto Mono', monospace; font-size: 0.8rem; line-height: 1.2; overflow-x: auto; }
        .diff-content code { display: block; background: none; padding: 0; }
        .diff-removed { display: block; margin: 0; color: var(--diff-removed-fg); }
        .diff-added { display: block; margin: 0; color: var(--diff-added-fg); }
        .diff-context { display: block; margin: 0; color: var(--diff-context-fg); }
        .theme-dark .diff-removed { color: #ffbba6; }
        .theme-dark .diff-added { color: #b4e8c4; }
        .theme-dark .diff-context { color: var(--jb-color-comment); }
        .theme-light .diff-removed { color: #c92a2a; }
        .theme-light .diff-added { color: #2b8a3e; }
        .theme-light .diff-context { color: var(--jb-color-comment); }
    """.trimIndent()

    val EMPTY_STATE = """
        .empty-state { padding: 40px; text-align: center; color: var(--jb-color-comment); }
    """.trimIndent()

    val TOAST = """
        .toast { position: fixed; top: 16px; left: 50%; transform: translateX(-50%); background: var(--jb-color-info); color: white; padding: 10px 20px; border-radius: 6px; font-size: 13px; font-weight: 500; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3); opacity: 0; transition: opacity 0.3s ease; z-index: 1000; }
        .toast.show { opacity: 1; }
    """.trimIndent()

    val LIST_VIEW_SPECIFIC = """
        h1 { font-size: 18px; font-weight: 600; margin-bottom: 8px; color: var(--jb-color-foreground); }
        .subtitle { font-size: 12px; color: var(--jb-color-comment); margin-bottom: 20px; }
        .timestamp { color: var(--jb-color-comment); }
    """.trimIndent()

    val DETAIL_VIEW_SPECIFIC = """
        h1 { font-size: 18px; font-weight: 600; margin-bottom: 8px; color: var(--jb-color-foreground); }
        .session-meta { font-size: 11px; color: var(--jb-color-comment); margin-bottom: 16px; }

        /* Sticky header - appears when metadata card scrolls out of view */
        .sticky-header { position: fixed; top: 0; left: 14px; right: 14px; background: var(--jb-color-background); padding: 0; display: flex; justify-content: space-between; align-items: center; z-index: 1000; box-shadow: var(--sticky-header-shadow); border-radius: 0 0 8px 8px; transform: translateY(-100%); transition: transform 0.2s ease; }
        .sticky-header.visible { transform: translateY(0); }
        .sticky-header-back { display: flex; align-items: center; justify-content: center; padding: 8px 12px; color: var(--jb-color-accent); text-decoration: none; flex-shrink: 0; border-radius: 0 0 0 8px; transition: background 0.15s ease; }
        .sticky-header-back:hover { background: var(--jb-color-accent-subtle); text-decoration: none; }
        .sticky-header-title { font-weight: 500; font-size: 13px; color: var(--jb-color-foreground); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex: 1; text-align: center; padding: 8px 0; }
        .scroll-top-btn { background: transparent; color: var(--jb-color-accent); border: none; outline: none; padding: 8px 12px; border-radius: 0 0 8px 0; font-size: 11px; cursor: pointer; display: flex; align-items: center; justify-content: center; flex-shrink: 0; transition: background 0.15s ease; }
        .scroll-top-btn:hover { background: var(--jb-color-accent-subtle); }
        .scroll-top-btn:focus { outline: none; }
        .scroll-top-btn:active { outline: none; }
    """.trimIndent()

    val ERROR_VIEW_SPECIFIC = """
        .error-state { text-align: center; color: var(--jb-color-error); }
        .error-state h2 { margin-bottom: 12px; }
        .error-state p { color: var(--jb-color-foreground); }
    """.trimIndent()

    /**
     * Combines multiple CSS strings into a single style block.
     */
    fun combine(vararg styles: String): String {
        return styles.joinToString("\n")
    }

    /**
     * Get all CSS for the list view.
     * Includes dynamic theme variables from UIManager.
     */
    fun forListView(): String {
        return combine(
            ThemeColors.generateCssVariables(),
            BASE,
            TOOLTIP,
            SCROLLBAR,
            CONTAINER,
            TOAST,
            LIST_VIEW_SPECIFIC,
            BADGES,
            SESSION_LIST,
            EMPTY_STATE
        )
    }

    /**
     * Get all CSS for the detail view.
     * Includes dynamic theme variables from UIManager.
     */
    fun forDetailView(): String {
        return combine(
            ThemeColors.generateCssVariables(),
            BASE,
            TOOLTIP,
            SCROLLBAR,
            CONTAINER,
            BACK_LINK,
            METADATA_CARD,
            BADGES,
            DETAIL_VIEW_SPECIFIC,
            MESSAGES,
            CODE_BLOCK,
            DIFF_VIEW,
            EMPTY_STATE
        )
    }

    /**
     * Get all CSS for the error view.
     * Includes dynamic theme variables from UIManager.
     */
    fun forErrorView(): String {
        return combine(
            ThemeColors.generateCssVariables(),
            BASE,
            TOOLTIP,
            SCROLLBAR,
            CONTAINER,
            BACK_LINK,
            EMPTY_STATE,
            ERROR_VIEW_SPECIFIC
        )
    }
}

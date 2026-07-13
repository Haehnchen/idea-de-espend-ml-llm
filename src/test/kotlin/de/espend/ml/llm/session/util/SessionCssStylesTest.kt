package de.espend.ml.llm.session.util

import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCssStylesTest {

    @Test
    fun `detail styles should contain themed links and assistant phases`() {
        val css = CssStyles.forDetailView()

        assertTrue(css.contains("--jb-color-link:"))
        assertTrue(css.contains("--jb-color-link-hover:"))
        assertTrue(css.contains("a, a:visited { color: var(--jb-color-link)"))
        assertTrue(css.contains(".message.status"))
        assertTrue(css.contains(".message.result"))
        assertTrue(css.contains(".type-badge.status"))
        assertTrue(css.contains(".type-badge.result"))
    }

    @Test
    fun `tool message selectors should match rendered hyphenated classes`() {
        val css = CssStyles.forDetailView()

        assertTrue(css.contains(".header-left { display: flex; align-items: baseline;"))
        assertTrue(css.contains(".message.tool-use"))
        assertTrue(css.contains(".message.tool-result"))
        assertTrue(css.contains(".type-badge.tool-use"))
        assertTrue(css.contains(".type-badge.tool-result"))
    }

    @Test
    fun `detail styles should contain collapsible tool call group separator`() {
        val css = CssStyles.forDetailView()

        assertTrue(css.contains(".tool-call-group-toggle"))
        assertTrue(css.contains(".tool-call-group-line"))
        assertTrue(css.contains(".tool-call-group-names"))
        assertTrue(css.contains("--tool-call-group-gap: 12px"))
        assertTrue(css.contains("padding-top: var(--tool-call-group-gap)"))
        assertTrue(css.contains(".tool-call-group.collapsed .tool-call-group-items { display: none; }"))
        assertTrue(css.contains(".tool-call-group.expanded .tool-call-group-icon { transform: rotate(180deg); }"))
    }
}

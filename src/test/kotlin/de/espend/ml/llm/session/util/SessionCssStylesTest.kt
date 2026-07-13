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

        assertTrue(css.contains(".message.tool-use"))
        assertTrue(css.contains(".message.tool-result"))
        assertTrue(css.contains(".type-badge.tool-use"))
        assertTrue(css.contains(".type-badge.tool-result"))
    }
}

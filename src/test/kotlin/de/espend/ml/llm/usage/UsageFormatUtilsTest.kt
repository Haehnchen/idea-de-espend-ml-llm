package de.espend.ml.llm.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageFormatUtilsTest {

    @Test
    fun `formatSecret should shorten values longer than six chars`() {
        assertEquals("abc...fgh", UsageFormatUtils.formatSecret("abcdefgh"))
    }

    @Test
    fun `formatSecret should shorten medium values with three chars on each side`() {
        assertEquals("123...abc", UsageFormatUtils.formatSecret("123456789abc"))
    }

    @Test
    fun `formatSecret should shorten long values with three chars on each side`() {
        assertEquals("123...vwx", UsageFormatUtils.formatSecret("1234567890abcdefghijklmnopqrstuvwx"))
    }
}

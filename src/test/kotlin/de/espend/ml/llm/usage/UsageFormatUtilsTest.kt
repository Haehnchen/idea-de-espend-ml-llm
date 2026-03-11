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

    // ==================== formatSecondsUntilReset Tests ====================

    @Test
    fun `formatSecondsUntilReset should format seconds only`() {
        assertEquals("Resets in 45s", UsageFormatUtils.formatSecondsUntilReset(45))
    }

    @Test
    fun `formatSecondsUntilReset should format zero seconds`() {
        assertEquals("Resets in 0s", UsageFormatUtils.formatSecondsUntilReset(0))
    }

    @Test
    fun `formatSecondsUntilReset should format negative seconds`() {
        assertEquals("Resets in -5s", UsageFormatUtils.formatSecondsUntilReset(-5))
    }

    @Test
    fun `formatSecondsUntilReset should format minutes and seconds`() {
        assertEquals("Resets in 5m 30s", UsageFormatUtils.formatSecondsUntilReset(330))
    }

    @Test
    fun `formatSecondsUntilReset should format hours and minutes`() {
        assertEquals("Resets in 2h 30m", UsageFormatUtils.formatSecondsUntilReset(9000))
    }

    @Test
    fun `formatSecondsUntilReset should format days and hours`() {
        assertEquals("Resets in 1d 4h", UsageFormatUtils.formatSecondsUntilReset(100800))
    }

    @Test
    fun `formatSecondsUntilReset should format days only when no hours`() {
        assertEquals("Resets in 2d", UsageFormatUtils.formatSecondsUntilReset(172800))
    }
}

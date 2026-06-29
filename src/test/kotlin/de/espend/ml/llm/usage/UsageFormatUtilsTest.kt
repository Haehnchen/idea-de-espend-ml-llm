package de.espend.ml.llm.usage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class UsageFormatUtilsTest {

    private lateinit var previousFormatLocale: Locale

    @Before
    fun setUp() {
        previousFormatLocale = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(Locale.Category.FORMAT, previousFormatLocale)
    }

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

    @Test
    fun `formatTokenAmount should keep thousands whole`() {
        assertEquals("999", UsageFormatUtils.formatTokenAmount(999))
        assertEquals("1K", UsageFormatUtils.formatTokenAmount(1_000))
        assertEquals("9K", UsageFormatUtils.formatTokenAmount(9_500))
        assertEquals("999K", UsageFormatUtils.formatTokenAmount(999_999))
    }

    @Test
    fun `formatTokenAmount should keep one decimal for millions and billions without rounding up`() {
        assertEquals("1M", UsageFormatUtils.formatTokenAmount(1_000_000))
        assertEquals("1.9M", UsageFormatUtils.formatTokenAmount(1_900_000))
        assertEquals("1.9M", UsageFormatUtils.formatTokenAmount(1_999_999))
        assertEquals("26.1M", UsageFormatUtils.formatTokenAmount(26_100_000))
        assertEquals("1.5B", UsageFormatUtils.formatTokenAmount(1_533_409_133))
    }

    @Test
    fun `formatTokenAmount should use system decimal separator`() {
        Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)

        assertEquals("14,7M", UsageFormatUtils.formatTokenAmount(14_719_457))
        assertEquals("1,5B", UsageFormatUtils.formatTokenAmount(1_533_409_133))
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

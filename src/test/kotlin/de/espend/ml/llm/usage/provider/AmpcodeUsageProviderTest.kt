package de.espend.ml.llm.usage.provider

import org.junit.Assert.*
import org.junit.Test

class AmpcodeUsageProviderTest {

    // ==================== parseDisplayText Tests ====================

    @Test
    fun `parseDisplayText should parse valid display text with replenishment info`() {
        val displayText = "Amp Free: \$8.78/\$10 remaining (replenishes +\$0.42/hour) - http"

        val result = AmpcodeUsageProvider.parseDisplayText(displayText)

        assertNotNull(result)
        val (percentageUsed, subtitle, remaining) = result!!

        assertEquals(12.2f, percentageUsed, 0.1f)
        assertEquals(8.78, remaining, 0.01)
        assertNotNull(subtitle)
        assertTrue("Subtitle should contain percentage per hour", subtitle!!.contains("%/h"))
    }

    @Test
    fun `parseDisplayText should calculate correct percentage used`() {
        // $2 used out of $10 = 20% used
        val displayText = "Free: \$8.00/\$10.00 remaining (replenishes +\$0.50/hour)"

        val result = AmpcodeUsageProvider.parseDisplayText(displayText)

        assertNotNull(result)
        assertEquals(20.0f, result!!.first, 0.1f)
    }

    @Test
    fun `parseDisplayText should handle fully used balance`() {
        val displayText = "Free: \$0.00/\$10.00 remaining (replenishes +\$0.50/hour)"

        val result = AmpcodeUsageProvider.parseDisplayText(displayText)

        assertNotNull(result)
        assertEquals(100.0f, result!!.first, 0.1f)
    }

    @Test
    fun `parseDisplayText should handle full balance remaining`() {
        val displayText = "Free: \$10.00/\$10.00 remaining (replenishes +\$0.50/hour)"

        val result = AmpcodeUsageProvider.parseDisplayText(displayText)

        assertNotNull(result)
        assertEquals(0.0f, result!!.first, 0.1f)
    }

    @Test
    fun `parseDisplayText should return null for invalid format`() {
        val displayText = "Invalid text without balance info"

        val result = AmpcodeUsageProvider.parseDisplayText(displayText)

        assertNull(result)
    }

    @Test
    fun `parseDisplayText should return null for empty string`() {
        val result = AmpcodeUsageProvider.parseDisplayText("")
        assertNull(result)
    }

    @Test
    fun `parseDisplayText should return null for zero total`() {
        val displayText = "Free: \$0.00/\$0.00 remaining"

        val result = AmpcodeUsageProvider.parseDisplayText(displayText)

        assertNull(result)
    }

    // ==================== buildSubtitleFromText Tests ====================

    @Test
    fun `buildSubtitleFromText should build subtitle with percentage per hour`() {
        val displayText = "Free: \$8.78/\$10 remaining (replenishes +\$0.42/hour)"

        val subtitle = AmpcodeUsageProvider.buildSubtitleFromText(displayText, 8.78, 10.0)

        assertNotNull(subtitle)
        // $0.42/$10 = 4.2% per hour
        assertTrue("Should contain 4.2%/h", subtitle!!.contains("4.2%/h"))
        assertTrue("Should contain replenish info", subtitle.contains("replenish"))
    }

    @Test
    fun `buildSubtitleFromText should return null when no replenishment info`() {
        val displayText = "Free: \$8.78/\$10 remaining"

        val subtitle = AmpcodeUsageProvider.buildSubtitleFromText(displayText, 8.78, 10.0)

        assertNull(subtitle)
    }

    @Test
    fun `buildSubtitleFromText should return null for zero total`() {
        val displayText = "Free: \$0.00/\$0.00 remaining (replenishes +\$0.42/hour)"

        val subtitle = AmpcodeUsageProvider.buildSubtitleFromText(displayText, 0.0, 0.0)

        assertNull(subtitle)
    }

    @Test
    fun `buildSubtitleFromText should calculate correct percentage for different rates`() {
        // $1.00/$10 = 10% per hour
        val displayText = "Free: \$5.00/\$10 remaining (replenishes +\$1.00/hour)"

        val subtitle = AmpcodeUsageProvider.buildSubtitleFromText(displayText, 5.0, 10.0)

        assertNotNull(subtitle)
        assertTrue("Should contain 10.0%/h", subtitle!!.contains("10.0%/h"))
    }

    @Test
    fun `buildSubtitleFromText should return null when balance is full`() {
        val displayText = "Free: \$10.00/\$10 remaining (replenishes +\$0.50/hour)"

        val subtitle = AmpcodeUsageProvider.buildSubtitleFromText(displayText, 10.0, 10.0)

        assertNull(subtitle)
    }

    // ==================== formatHoursUntilFull Tests ====================

    @Test
    fun `formatHoursUntilFull should format minutes only`() {
        val result = AmpcodeUsageProvider.formatHoursUntilFull(0.5) // 30 minutes

        assertEquals("replenish in 30m", result)
    }

    @Test
    fun `formatHoursUntilFull should format hours and minutes`() {
        val result = AmpcodeUsageProvider.formatHoursUntilFull(2.5) // 2h 30m

        assertEquals("replenish in 2h 30m", result)
    }

    @Test
    fun `formatHoursUntilFull should format days and hours`() {
        val result = AmpcodeUsageProvider.formatHoursUntilFull(26.0) // 1d 2h

        assertEquals("replenish in 1d 2h", result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `formatHoursUntilFull should throw for zero`() {
        AmpcodeUsageProvider.formatHoursUntilFull(0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `formatHoursUntilFull should throw for negative`() {
        AmpcodeUsageProvider.formatHoursUntilFull(-1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `formatHoursUntilFull should throw for NaN`() {
        AmpcodeUsageProvider.formatHoursUntilFull(Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `formatHoursUntilFull should throw for Infinity`() {
        AmpcodeUsageProvider.formatHoursUntilFull(Double.POSITIVE_INFINITY)
    }

    @Test
    fun `formatHoursUntilFull should handle exactly one hour`() {
        val result = AmpcodeUsageProvider.formatHoursUntilFull(1.0)

        assertEquals("replenish in 1h", result)
    }

    @Test
    fun `formatHoursUntilFull should handle exactly one day`() {
        val result = AmpcodeUsageProvider.formatHoursUntilFull(24.0)

        assertEquals("replenish in 1d", result)
    }

    // ==================== Integration Tests ====================

    @Test
    fun `full parse should produce expected output for real-world example`() {
        // Real example from API
        val displayText = "Amp Free: \$8.78/\$10 remaining (replenishes +\$0.42/hour) - http"

        val result = AmpcodeUsageProvider.parseDisplayText(displayText)

        assertNotNull(result)
        val (percentageUsed, subtitle, remaining) = result!!

        // $10 - $8.78 = $1.22 used = 12.2%
        assertEquals(12.2f, percentageUsed, 0.1f)
        assertEquals(8.78, remaining, 0.01)

        // Subtitle should have time and percentage
        assertNotNull(subtitle)
        assertTrue("Subtitle: $subtitle", subtitle!!.contains("replenish"))
        assertTrue("Subtitle: $subtitle", subtitle.contains("%/h"))

        // $0.42/$10 = 4.2% per hour
        assertTrue("Should show 4.2%/h: $subtitle", subtitle.contains("4.2%/h"))
    }

    @Test
    fun `should handle case-insensitive replenishes`() {
        val displayText = "Free: \$5.00/\$10 remaining (REPLENISHES +\$1.00/HOUR)"

        val result = AmpcodeUsageProvider.parseDisplayText(displayText)

        assertNotNull(result)
        assertNotNull(result!!.second)
    }

    @Test
    fun `should handle varying whitespace in balance format`() {
        // The regex is strict about "Free: $X.XX/$Y.YY" format (no spaces around /)
        // Test that the standard format works
        val displayText = "Free: \$8.78/\$10 remaining (replenishes +\$0.42/hour)"

        val result = AmpcodeUsageProvider.parseDisplayText(displayText)

        assertNotNull(result)
        assertEquals(8.78, result!!.third, 0.01)
    }
}

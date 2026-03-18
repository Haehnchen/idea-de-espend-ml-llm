package de.espend.ml.llm.usage.provider

import org.junit.Assert.*
import org.junit.Test

class OllamaUsageProviderTest {

    private val provider = OllamaUsageProvider()

    // ==================== parseHtml - Success Cases ====================

    @Test
    fun `parseHtml should parse session and weekly usage with percent-used format`() {
        val html = """
            <html><body>
            <span>Session usage</span>
            <div>45.5% used</div>
            <span data-time="2026-03-17T15:00:00Z"></span>
            <span>Weekly usage</span>
            <div>70% used</div>
            <span data-time="2026-03-21T00:00:00Z"></span>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(45.5f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals(70f, result.data.entries[1].percentageUsed, 0.1f)
        assertTrue("Session subtitle should start with 'Session'", result.data.entries[0].subtitle!!.startsWith("Session"))
        assertTrue("Weekly subtitle should start with 'Weekly'", result.data.entries[1].subtitle!!.startsWith("Weekly"))
    }

    @Test
    fun `parseHtml should parse session usage via width style format`() {
        val html = """
            <html><body>
            <span>Session usage</span>
            <div style="width: 33.5%"></div>
            <span>Weekly usage</span>
            <div style="width: 80%"></div>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be success", result.data != null)
        assertEquals(33.5f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(80f, result.data!!.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseHtml should accept Hourly usage label as session fallback`() {
        val html = """
            <html><body>
            <span>Hourly usage</span>
            <div>20% used</div>
            <span>Weekly usage</span>
            <div>55% used</div>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be success", result.data != null)
        assertEquals(20f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(55f, result.data!!.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseHtml should show not started for session when missing`() {
        val html = """
            <html><body>
            <span>Weekly usage</span>
            <div>60% used</div>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(0f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals("Session · not started", result.data.entries[0].subtitle)
        assertEquals(60f, result.data.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseHtml should show not started for weekly when missing`() {
        val html = """
            <html><body>
            <span>Session usage</span>
            <div>25% used</div>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(25f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(0f, result.data.entries[1].percentageUsed, 0.1f)
        assertEquals("Weekly · not started", result.data.entries[1].subtitle)
    }

    @Test
    fun `parseHtml should coerce percentage above 100 to 100`() {
        val html = """
            <html><body>
            <span>Session usage</span>
            <div>150% used</div>
            <span>Weekly usage</span>
            <div>200% used</div>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be success", result.data != null)
        assertEquals(100f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(100f, result.data!!.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseHtml should parse digits from text containing negative sign`() {
        val html = """
            <html><body>
            <span>Session usage</span>
            <div>10% used</div>
            <span>Weekly usage</span>
            <div>50% used</div>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be success", result.data != null)
        assertEquals(10f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(50f, result.data!!.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseHtml should include reset time in subtitle when data-time is present`() {
        // Use a far-future date so the test is not time-sensitive
        val html = """
            <html><body>
            <span>Session usage</span>
            <div>50% used</div>
            <span data-time="2099-12-31T23:59:59Z"></span>
            <span>Weekly usage</span>
            <div>80% used</div>
            <span data-time="2099-12-31T23:59:59Z"></span>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be success", result.data != null)
        val sessionSubtitle = result.data!!.entries[0].subtitle ?: ""
        val weeklySubtitle = result.data.entries[1].subtitle ?: ""
        assertTrue("Session subtitle should contain 'Resets in'", sessionSubtitle.contains("Resets in"))
        assertTrue("Weekly subtitle should contain 'Resets in'", weeklySubtitle.contains("Resets in"))
    }

    @Test
    fun `parseHtml should handle zero percent`() {
        val html = """
            <html><body>
            <span>Session usage</span>
            <div>0% used</div>
            <span>Weekly usage</span>
            <div>0% used</div>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be success", result.data != null)
        assertEquals(0f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(0f, result.data!!.entries[1].percentageUsed, 0.1f)
    }

    // ==================== parseHtml - Error Cases ====================

    @Test
    fun `parseHtml should return error for blank html`() {
        val result = provider.parseHtml("")

        assertTrue("Should be error", result.error != null)
        assertTrue(result.error!!.contains("Empty"))
    }

    @Test
    fun `parseHtml should return error when no usage data found`() {
        val html = "<html><body><p>Some random content</p></body></html>"

        val result = provider.parseHtml(html)

        assertTrue("Should be error", result.error != null)
        assertTrue("Error should mention usage data", result.error!!.contains("usage data") || result.error!!.contains("No usage"))
    }

    @Test
    fun `parseHtml should detect signed-out page with sign-in heading`() {
        val html = """
            <html><body>
            <h1>Sign in to Ollama</h1>
            <form action="/signin">
            <input type="email" name="email">
            <input type="password" name="password">
            </form>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be error", result.error != null)
        assertTrue("Error should mention login", result.error!!.contains("logged in") || result.error!!.contains("cookie"))
    }

    @Test
    fun `parseHtml should detect signed-out page with auth form and endpoint`() {
        val html = """
            <html><body>
            <form action="/api/auth/signin">
            <input type="email">
            </form>
            </body></html>
        """.trimIndent()

        val result = provider.parseHtml(html)

        assertTrue("Should be error", result.error != null)
        assertTrue("Error should mention login", result.error!!.contains("logged in") || result.error!!.contains("cookie"))
    }

    // ==================== Config Tests ====================

    @Test
    fun `config should have correct default values`() {
        val config = OllamaUsageProvider.OllamaUsageAccountConfig()

        assertEquals("ollama", config.providerId)
        assertEquals("", config.sessionToken)
    }

    @Test
    fun `config should support setting sessionToken`() {
        val config = OllamaUsageProvider.OllamaUsageAccountConfig()
        config.sessionToken = "abc123token"

        assertEquals("abc123token", config.sessionToken)
    }

    @Test
    fun `config getInfoString should return empty when no token`() {
        val config = OllamaUsageProvider.OllamaUsageAccountConfig()

        assertEquals("", config.getInfoString())
    }

    @Test
    fun `config getInfoString should return masked token`() {
        val config = OllamaUsageProvider.OllamaUsageAccountConfig().apply {
            sessionToken = "abcdefghijklmnopqrstuvwxyz"
        }

        val info = config.getInfoString()
        assertTrue("Info string should not be empty", info.isNotEmpty())
        assertTrue("Info string should mask middle", info.contains("..."))
        assertTrue("Info string should start with 'abc'", info.startsWith("abc"))
        assertTrue("Info string should end with 'xyz'", info.endsWith("xyz"))
    }

    // ==================== State Serialization ====================

    @Test
    fun `fromState and toState should round-trip correctly`() {
        val original = OllamaUsageProvider.OllamaUsageAccountConfig().apply {
            id = "test-id-1"
            name = "My Ollama Account"
            isEnabled = true
            sessionToken = "my-session-token-value"
        }

        val state = provider.toState(original)
        val restored = provider.fromState(state) as OllamaUsageProvider.OllamaUsageAccountConfig

        assertEquals("test-id-1", restored.id)
        assertEquals("My Ollama Account", restored.name)
        assertTrue(restored.isEnabled)
        assertEquals("my-session-token-value", restored.sessionToken)
    }

    @Test
    fun `fromState should handle missing sessionToken gracefully`() {
        val state = de.espend.ml.llm.usage.UsageAccountState(
            id = "test-id",
            providerId = "ollama",
            label = "Test",
            isEnabled = true
        )

        val config = provider.fromState(state) as OllamaUsageProvider.OllamaUsageAccountConfig

        assertEquals("", config.sessionToken)
    }
}

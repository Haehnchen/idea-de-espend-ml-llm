package de.espend.ml.llm.usage.provider

import java.nio.file.Path
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinePassUsageProviderTest {

    private val provider = ClinePassUsageProvider()

    @Test
    fun `parseResponseBody should parse Cline usage limits fixture`() {
        val body = fixture("usage-limits.json")
        val now = Instant.parse("2026-07-07T07:34:14Z")

        val result = provider.parseResponseBody(body, now)

        assertNotNull(result.data)
        val entries = result.data!!.entries
        assertEquals(3, entries.size)
        assertEquals(12.5f, entries[0].percentageUsed, 0.1f)
        assertEquals(34f, entries[1].percentageUsed, 0.1f)
        assertEquals(56.75f, entries[2].percentageUsed, 0.1f)
        assertEquals("5h · Resets in 5h 0m", entries[0].subtitle)
        assertEquals("Week · Resets in 7d", entries[1].subtitle)
        assertEquals("Month · Resets in 30d", entries[2].subtitle)
    }

    @Test
    fun `parseResponseBody should clamp percentages and expose missing monthly window`() {
        val body = fixture("missing-monthly.json")
        val now = Instant.parse("2026-07-07T07:34:14Z")

        val result = provider.parseResponseBody(body, now)

        assertNotNull(result.data)
        val entries = result.data!!.entries
        assertEquals(3, entries.size)
        assertEquals(0f, entries[0].percentageUsed, 0.1f)
        assertEquals(100f, entries[1].percentageUsed, 0.1f)
        assertEquals("Month · n/a", entries[2].subtitle)
    }

    @Test
    fun `parseResponseBody should report unsuccessful API response`() {
        val result = provider.parseResponseBody(fixture("error-response.json"))

        assertTrue(result.error?.contains("plan unavailable") == true)
    }

    @Test
    fun `config state should round trip api key`() {
        val original = ClinePassUsageProvider.ClinePassUsageAccountConfig().apply {
            id = "account-1"
            name = "ClinePass"
            apiKey = "sk_test_123456"
            enableStatusBar = true
            weight = 7
        }

        val state = provider.toState(original)
        val restored = provider.fromState(state) as ClinePassUsageProvider.ClinePassUsageAccountConfig

        assertEquals("account-1", restored.id)
        assertEquals("ClinePass", restored.name)
        assertEquals("sk_test_123456", restored.apiKey)
        assertEquals("sk_test_123456", state.getString("apiKey"))
        assertTrue(restored.enableStatusBar)
        assertEquals(7, restored.weight)
    }

    @Test
    fun `panel info should reserve three usage entries`() {
        val info = provider.getAccountPanelInfo(ClinePassUsageProvider.ClinePassUsageAccountConfig())

        assertEquals(3, info.usageEntryCount)
        assertEquals(0, info.lineCount)
    }

    private fun fixture(name: String): String {
        val url = javaClass.classLoader.getResource("fixtures/clinepass/$name")
            ?: error("Missing fixture: $name")
        return Path.of(url.toURI()).toFile().readText()
    }
}

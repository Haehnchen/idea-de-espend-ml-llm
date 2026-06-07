package de.espend.ml.llm.usage.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeGoUsageProviderTest {

    private val provider = OpenCodeGoUsageProvider()

    @Test
    fun `parseResponseText should parse seroval usage with 5h week and month`() {
        val d = "$"
        val text = "_${d}HY.r[\"lite.subscription.get[\\\"wrk_LIVE123\\\"]\"]=${d}R[17];" +
            "${d}R[24](${d}R[18],${d}R[27]={mine:!0,useBalance:!1," +
            "rollingUsage:${d}R[28]={status:\"ok\",resetInSec:17591,usagePercent:10}," +
            "weeklyUsage:${d}R[29]={status:\"ok\",resetInSec:444552,usagePercent:20}," +
            "monthlyUsage:${d}R[30]={status:\"ok\",resetInSec:2591424,usagePercent:30}});"

        val result = provider.parseResponseText(text)

        assertNotNull(result.data)
        assertEquals(3, result.data!!.entries.size)
        assertEquals(10f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals(20f, result.data.entries[1].percentageUsed, 0.1f)
        assertEquals(30f, result.data.entries[2].percentageUsed, 0.1f)
        assertTrue(result.data.entries[0].subtitle!!.startsWith("5h"))
        assertTrue(result.data.entries[1].subtitle!!.startsWith("Week"))
        assertTrue(result.data.entries[2].subtitle!!.startsWith("Month"))
    }

    @Test
    fun `parseResponseText should parse json ratio percentages and reset fields`() {
        val json = """
            {
              "usage": {
                "rollingUsage": { "usagePercent": 0.25, "resetInSec": 300 },
                "weeklyUsage": { "usagePercent": 0.5, "resetInSec": 600 },
                "monthlyUsage": { "usagePercent": 0.75, "resetInSec": 900 }
              }
            }
        """.trimIndent()

        val result = provider.parseResponseText(json)

        assertNotNull(result.data)
        assertEquals(25f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(50f, result.data.entries[1].percentageUsed, 0.1f)
        assertEquals(75f, result.data.entries[2].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseText should compute percentages from used and limit totals`() {
        val json = """
            {
              "windows": {
                "primaryWindow": { "used": 10, "limit": 100, "resetInSec": 300 },
                "secondaryWindow": { "used": 40, "limit": 200, "resetInSec": 600 },
                "monthlyBucket": { "used": 90, "limit": 300, "resetInSec": 900 }
              }
            }
        """.trimIndent()

        val result = provider.parseResponseText(json)

        assertNotNull(result.data)
        assertEquals(10f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(20f, result.data.entries[1].percentageUsed, 0.1f)
        assertEquals(30f, result.data.entries[2].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseText should expose missing month as third unavailable entry`() {
        val json = """
            {
              "rollingUsage": { "usagePercent": 10, "resetInSec": 300 },
              "weeklyUsage": { "usagePercent": 20, "resetInSec": 600 }
            }
        """.trimIndent()

        val result = provider.parseResponseText(json)

        assertNotNull(result.data)
        assertEquals(3, result.data!!.entries.size)
        assertEquals(0f, result.data.entries[2].percentageUsed, 0.1f)
        assertEquals("Month · n/a", result.data.entries[2].subtitle)
    }

    @Test
    fun `normalizeCookieHeader should keep only auth cookies from copied browser header`() {
        val normalized = OpenCodeGoUsageProvider.normalizeCookieHeader(
            """
            Cookie
                auth=secret-value; oc_locale=de; theme=dark
            """.trimIndent()
        )

        assertEquals("auth=secret-value", normalized)
    }

    @Test
    fun `config state should round trip cookie`() {
        val original = OpenCodeGoUsageProvider.OpenCodeGoUsageAccountConfig().apply {
            id = "account-1"
            name = "OpenCode Go"
            cookieHeader = "auth=secret-value; oc_locale=de"
        }

        val state = provider.toState(original)
        val restored = provider.fromState(state) as OpenCodeGoUsageProvider.OpenCodeGoUsageAccountConfig

        assertEquals("account-1", restored.id)
        assertEquals("OpenCode Go", restored.name)
        assertEquals("auth=secret-value", state.getString("cookieHeader"))
        assertEquals("auth=secret-value", restored.cookieHeader)
    }

    @Test
    fun `panel info should reserve three usage entries`() {
        val info = provider.getAccountPanelInfo(OpenCodeGoUsageProvider.OpenCodeGoUsageAccountConfig())

        assertEquals(3, info.usageEntryCount)
        assertEquals(0, info.lineCount)
    }
}

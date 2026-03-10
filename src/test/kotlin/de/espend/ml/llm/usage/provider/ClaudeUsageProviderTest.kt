package de.espend.ml.llm.usage.provider

import org.junit.Assert.*
import org.junit.Test

class ClaudeUsageProviderTest {

    private val provider = ClaudeUsageProvider()

    // ==================== parseUsageBody - Success Cases ====================

    @Test
    fun `parseUsageBody should parse valid response with both windows`() {
        val json = """
            {
                "five_hour": {
                    "utilization": 50,
                    "resets_at": "2026-03-08T12:00:00Z"
                },
                "seven_day": {
                    "utilization": 75,
                    "resets_at": "2026-03-15T00:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(50f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals(75f, result.data.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseUsageBody should parse response with only five_hour and show not started for seven_day`() {
        val json = """
            {
                "five_hour": {
                    "utilization": 25,
                    "resets_at": "2026-03-08T12:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(25f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals(0f, result.data.entries[1].percentageUsed, 0.1f)
        assertEquals("7d · not started", result.data.entries[1].subtitle)
    }

    @Test
    fun `parseUsageBody should parse response with only seven_day and show not started for five_hour`() {
        val json = """
            {
                "seven_day": {
                    "utilization": 80,
                    "resets_at": "2026-03-15T00:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(0f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals("5h · not started", result.data.entries[0].subtitle)
        assertEquals(80f, result.data.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseUsageBody should handle zero utilization`() {
        val json = """
            {
                "five_hour": {
                    "utilization": 0,
                    "resets_at": "2026-03-08T12:00:00Z"
                },
                "seven_day": {
                    "utilization": 0,
                    "resets_at": "2026-03-15T00:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(0f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals(0f, result.data.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseUsageBody should handle 100 percent utilization`() {
        val json = """
            {
                "five_hour": {
                    "utilization": 100,
                    "resets_at": "2026-03-08T12:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(100f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals("7d · not started", result.data.entries[1].subtitle)
    }

    @Test
    fun `parseUsageBody should coerce utilization above 100`() {
        val json = """
            {
                "five_hour": {
                    "utilization": 150,
                    "resets_at": "2026-03-08T12:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(100f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals("7d · not started", result.data.entries[1].subtitle)
    }

    @Test
    fun `parseUsageBody should coerce negative utilization to 0`() {
        val json = """
            {
                "five_hour": {
                    "utilization": -50,
                    "resets_at": "2026-03-08T12:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(0f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals("7d · not started", result.data.entries[1].subtitle)
    }

    // ==================== parseUsageBody - Error Cases ====================

    @Test
    fun `parseUsageBody should return error for null body`() {
        val result = provider.parseUsageBody(null)

        assertTrue("Should be error", result.error != null)
        assertTrue("Error message should mention empty", result.error?.contains("Empty") == true)
    }

    @Test
    fun `parseUsageBody should return error for empty string`() {
        val result = provider.parseUsageBody("")
        assertTrue("Should be error", result.error != null)
    }

    @Test
    fun `parseUsageBody should return error for invalid JSON`() {
        val result = provider.parseUsageBody("not valid json")
        assertTrue("Should be error", result.error != null)
    }

    @Test
    fun `parseUsageBody should show not started when no utilization data`() {
        val json = """
            {
                "some_other_field": "value"
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals("5h · not started", result.data.entries[0].subtitle)
        assertEquals("7d · not started", result.data.entries[1].subtitle)
    }

    @Test
    fun `parseUsageBody should show not started for window with null utilization`() {
        val json = """
            {
                "five_hour": {
                    "utilization": null,
                    "resets_at": "2026-03-08T12:00:00Z"
                },
                "seven_day": {
                    "utilization": 50,
                    "resets_at": "2026-03-15T00:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(0f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals("5h · not started", result.data.entries[0].subtitle)
        assertEquals(50f, result.data.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseUsageBody should handle five_hour zero with seven_day null`() {
        val json = """
            {
                "five_hour": {
                    "utilization": 0.0,
                    "resets_at": null
                },
                "seven_day": {
                    "utilization": null,
                    "resets_at": "2026-03-13T13:59:59.827968+00:00"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        // 0.0 is valid, so five_hour should be parsed; seven_day should show not started
        assertTrue("Should be success, not error: ${result.error}", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(0f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals("7d · not started", result.data.entries[1].subtitle)
    }

    @Test
    fun `parseUsageBody should handle real web api response with extra fields`() {
        // Real response from claude.ai Web API
        val json = """
            {
                "five_hour": {
                    "utilization": 0.0,
                    "resets_at": null
                },
                "seven_day": {
                    "utilization": 14.0,
                    "resets_at": "2026-03-13T13:59:59.827968+00:00"
                },
                "seven_day_oauth_apps": null,
                "seven_day_opus": null,
                "seven_day_sonnet": null,
                "seven_day_cowork": null,
                "iguana_necktie": null,
                "extra_usage": {
                    "is_enabled": false,
                    "monthly_limit": null,
                    "used_credits": null,
                    "utilization": null
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(0f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals(14f, result.data.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseUsageBody should show not started when both utilizations are null`() {
        // Both utilizations null - should show "not started" for both
        val json = """
            {
                "five_hour": {
                    "utilization": null,
                    "resets_at": null
                },
                "seven_day": {
                    "utilization": null,
                    "resets_at": null
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success when both utilizations are null", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals("5h · not started", result.data.entries[0].subtitle)
        assertEquals("7d · not started", result.data.entries[1].subtitle)
    }

    // ==================== parseUsageBody - Real-world Examples ====================

    @Test
    fun `parseUsageBody should parse real OAuth API response`() {
        val json = """
            {
                "five_hour": {
                    "utilization": 42,
                    "resets_at": "2026-03-08T17:00:00Z"
                },
                "seven_day": {
                    "utilization": 68,
                    "resets_at": "2026-03-14T00:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(42f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals(68f, result.data.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseUsageBody should parse real Web API response`() {
        val json = """
            {
                "five_hour": {
                    "utilization": 15,
                    "resets_at": "2026-03-08T14:30:00Z"
                },
                "seven_day": {
                    "utilization": 35,
                    "resets_at": "2026-03-14T08:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(15f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals(35f, result.data.entries[1].percentageUsed, 0.1f)
    }

    @Test
    fun `parseUsageBody should handle fractional utilization values`() {
        val json = """
            {
                "five_hour": {
                    "utilization": 33.5,
                    "resets_at": "2026-03-08T12:00:00Z"
                }
            }
        """.trimIndent()

        val result = provider.parseUsageBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(33.5f, result.data.entries[0].percentageUsed, 0.1f)
        assertEquals("7d · not started", result.data.entries[1].subtitle)
    }

    // ==================== Config Tests ====================

    @Test
    fun `config should have correct default values`() {
        val config = ClaudeUsageProvider.ClaudeUsageAccountConfig()

        assertEquals("claude", config.providerId)
        assertEquals("auto", config.credentialMode)
        assertEquals("", config.manualToken)
        assertEquals("", config.sessionKey)
        assertEquals("", config.cachedAccessToken)
        assertEquals("", config.cachedRefreshToken)
        assertEquals(0L, config.cachedExpiresAt)
    }

    @Test
    fun `config should support all credential modes`() {
        val config = ClaudeUsageProvider.ClaudeUsageAccountConfig()

        config.credentialMode = "auto"
        assertEquals("auto", config.credentialMode)

        config.credentialMode = "manual"
        config.manualToken = "test-access-token"
        assertEquals("manual", config.credentialMode)
        assertEquals("test-access-token", config.manualToken)

        config.credentialMode = "web"
        config.sessionKey = "sk-ant-test123"
        assertEquals("web", config.credentialMode)
        assertEquals("sk-ant-test123", config.sessionKey)
    }

    @Test
    fun `config info string should include session key in web mode`() {
        val config = ClaudeUsageProvider.ClaudeUsageAccountConfig().apply {
            credentialMode = "web"
            sessionKey = "sk-ant-api03-1234567890abcdefghijklmnopqrst"
        }

        assertEquals("web · sk-...rst", config.getInfoString())
    }
}

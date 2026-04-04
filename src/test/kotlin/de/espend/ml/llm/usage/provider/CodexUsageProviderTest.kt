package de.espend.ml.llm.usage.provider

import org.junit.Assert.*
import org.junit.Test

class CodexUsageProviderTest {

    private val provider = CodexUsageProvider()

    // ==================== parseResponseBody - Success Cases ====================

    @Test
    fun `parseResponseBody should parse valid response with rate_limit`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 45.5,
                        "reset_at": 1741440000.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(45.5f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should use header value when provided`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 45.5
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json, headerPercent = 75.0)

        assertTrue("Should be success", result.data != null)
        assertEquals(75.0f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should fall back to body when header is null`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 45.5
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json, headerPercent = null)

        assertTrue("Should be success", result.data != null)
        assertEquals(45.5f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should handle zero usage`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 0,
                        "reset_at": 1741440000.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(0f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should handle 100 percent usage`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 100,
                        "reset_at": 1741440000.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(100f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should coerce percentage above 100`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 150,
                        "reset_at": 1741440000.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(100f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should coerce negative percentage to 0`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": -50,
                        "reset_at": 1741440000.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(0f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should handle reset_after_seconds field`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 50,
                        "reset_after_seconds": 3600.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(50f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertNotNull("Should have subtitle", result.data.entries[0].subtitle)
    }

    @Test
    fun `parseResponseBody should handle missing rate_limit`() {
        val json = """
            {
                "some_other_field": "value"
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be error", result.error != null)
    }

    @Test
    fun `parseResponseBody should include spark primary window when enabled`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 15.5,
                        "reset_at": 1741443600.0
                    }
                },
                "additional_rate_limits": [
                    {
                        "limit_name": "GPT-5.3-Codex-Spark",
                        "rate_limit": {
                            "primary_window": {
                                "used_percent": 7.0,
                                "reset_at": 1741447200.0
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json, includeSparkPrimaryWindow = true)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(15.5f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(7.0f, result.data!!.entries[1].percentageUsed, 0.1f)
        assertTrue(result.data!!.entries[1].subtitle?.contains("Spark") == true)
    }

    @Test
    fun `parseResponseBody should use empty spark entry when enabled but unavailable`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 15.5,
                        "reset_at": 1741443600.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json, includeSparkPrimaryWindow = true)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(0f, result.data!!.entries[1].percentageUsed, 0.1f)
        assertEquals("Spark unavailable", result.data!!.entries[1].subtitle)
    }

    // ==================== parseResponseBody - Error Cases ====================

    @Test
    fun `parseResponseBody should return error for null body`() {
        val result = provider.parseResponseBody(null)

        assertTrue("Should be error", result.error != null)
        assertTrue("Error message should mention empty", result.error?.contains("Empty") == true)
    }

    @Test
    fun `parseResponseBody should return error for empty string`() {
        val result = provider.parseResponseBody("")
        assertTrue("Should be error", result.error != null)
    }

    @Test
    fun `parseResponseBody should return error for invalid JSON`() {
        val result = provider.parseResponseBody("not valid json")
        assertTrue("Should be error", result.error != null)
    }

    @Test
    fun `parseResponseBody should return error when no usage data found`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {}
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be error", result.error != null)
        assertTrue("Error should mention usage data", result.error?.contains("usage data") == true)
    }

    @Test
    fun `parseResponseBody should return error when primary_window is missing`() {
        val json = """
            {
                "rate_limit": {}
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be error", result.error != null)
    }

    // ==================== Config Tests ====================

    @Test
    fun `config should have correct default values`() {
        val config = CodexUsageProvider.CodexUsageAccountConfig()

        assertEquals("codex", config.providerId)
        assertEquals("auto", config.credentialMode)
        assertEquals("", config.cachedAccessToken)
        assertEquals("", config.cachedRefreshToken)
        assertFalse(config.showSparkPrimaryWindow)
    }

    @Test
    fun `config should support all credential modes`() {
        val config = CodexUsageProvider.CodexUsageAccountConfig()

        config.credentialMode = "auto"
        assertEquals("auto", config.credentialMode)

        config.credentialMode = "manual"
        config.cachedAccessToken = "test-access-token"
        config.cachedRefreshToken = "test-refresh-token"
        assertEquals("manual", config.credentialMode)
        assertEquals("test-access-token", config.cachedAccessToken)
        assertEquals("test-refresh-token", config.cachedRefreshToken)
    }

    @Test
    fun `config should support cached access token`() {
        val config = CodexUsageProvider.CodexUsageAccountConfig()
        config.cachedAccessToken = "cached-token-123"

        assertEquals("cached-token-123", config.cachedAccessToken)
    }

    @Test
    fun `config info string should include manual access token`() {
        val config = CodexUsageProvider.CodexUsageAccountConfig().apply {
            credentialMode = "manual"
            cachedAccessToken = "1234567890abcdefghijklmnopqrstuvwx"
        }

        assertEquals("manual · 123...vwx", config.getInfoString())
    }

    @Test
    fun `config info string should include spark marker when enabled`() {
        val config = CodexUsageProvider.CodexUsageAccountConfig().apply {
            showSparkPrimaryWindow = true
        }

        assertEquals("auto · spark", config.getInfoString())
    }

    @Test
    fun `getAccountPanelInfo should return two progress bars when spark is enabled`() {
        val config = CodexUsageProvider.CodexUsageAccountConfig().apply {
            showSparkPrimaryWindow = true
        }

        val panelInfo = provider.getAccountPanelInfo(config)

        assertEquals(2, panelInfo.usageEntryCount)
        assertEquals(0, panelInfo.lineCount)
    }

    // ==================== Real-world Examples ====================

    @Test
    fun `parseResponseBody should parse real ChatGPT API response`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 15.5,
                        "reset_at": 1741443600.0
                    },
                    "secondary_window": {
                        "used_percent": 5.2
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(15.5f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should parse response with header override`() {
        // Simulate the case where header has a different value than body
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 30.0,
                        "reset_at": 1741443600.0
                    }
                }
            }
        """.trimIndent()

        // Header takes precedence
        val result = provider.parseResponseBody(json, headerPercent = 42.0)

        assertTrue("Should be success", result.data != null)
        assertEquals(42.0f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should handle fractional percentage values`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 33.57,
                        "reset_at": 1741443600.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(33.57f, result.data!!.entries[0].percentageUsed, 0.01f)
    }
}

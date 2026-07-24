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
                        "limit_window_seconds": 18000,
                        "reset_at": 1741440000.0
                    },
                    "secondary_window": {
                        "used_percent": 12.0,
                        "limit_window_seconds": 604800,
                        "reset_at": 1741880000.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(45.5f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(12.0f, result.data!!.entries[1].percentageUsed, 0.1f)
        assertTrue(result.data!!.entries[0].subtitle?.startsWith("5h") == true)
        assertTrue(result.data!!.entries[1].subtitle?.startsWith("Weekly") == true)
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
    fun `parseResponseBody should include spark windows when enabled`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 15.5,
                        "limit_window_seconds": 18000,
                        "reset_at": 1741443600.0
                    },
                    "secondary_window": {
                        "used_percent": 22.5,
                        "limit_window_seconds": 604800,
                        "reset_at": 1741880000.0
                    }
                },
                "additional_rate_limits": [
                    {
                        "limit_name": "GPT-5.3-Codex-Spark",
                        "rate_limit": {
                            "primary_window": {
                                "used_percent": 7.0,
                                "limit_window_seconds": 18000,
                                "reset_at": 1741447200.0
                            },
                            "secondary_window": {
                                "used_percent": 13.0,
                                "limit_window_seconds": 604800,
                                "reset_at": 1741887200.0
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json, includeSparkPrimaryWindow = true)

        assertTrue("Should be success", result.data != null)
        assertEquals(4, result.data!!.entries.size)
        assertEquals(15.5f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(22.5f, result.data!!.entries[1].percentageUsed, 0.1f)
        assertEquals(7.0f, result.data!!.entries[2].percentageUsed, 0.1f)
        assertEquals(13.0f, result.data!!.entries[3].percentageUsed, 0.1f)
        assertTrue(result.data!!.entries[2].subtitle?.contains("Spark") == true)
        assertTrue(result.data!!.entries[3].subtitle?.contains("Weekly") == true)
    }

    @Test
    fun `parseResponseBody should use empty spark entries when enabled but unavailable`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 15.5,
                        "reset_at": 1741443600.0
                    },
                    "secondary_window": {
                        "used_percent": 8.0,
                        "reset_at": 1741880000.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json, includeSparkPrimaryWindow = true)

        assertTrue("Should be success", result.data != null)
        assertEquals(4, result.data!!.entries.size)
        assertEquals(15.5f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(8.0f, result.data!!.entries[1].percentageUsed, 0.1f)
        assertEquals(0f, result.data!!.entries[2].percentageUsed, 0.1f)
        assertEquals(0f, result.data!!.entries[3].percentageUsed, 0.1f)
        assertEquals("Spark 5h · n/a", result.data!!.entries[2].subtitle)
        assertEquals("Spark Weekly · n/a", result.data!!.entries[3].subtitle)
    }

    @Test
    fun `parseResponseBody should use empty weekly entry when secondary window is missing`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 15.5,
                        "limit_window_seconds": 18000,
                        "reset_at": 1741443600.0
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(15.5f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(0f, result.data!!.entries[1].percentageUsed, 0.1f)
        assertEquals("Weekly · n/a", result.data!!.entries[1].subtitle)
    }

    @Test
    fun `parseResponseBody should assign primary weekly window by duration and keep fixed slots`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 0,
                        "limit_window_seconds": 604800,
                        "reset_after_seconds": 604748,
                        "reset_at": 1784559469
                    },
                    "secondary_window": null
                },
                "additional_rate_limits": [
                    {
                        "limit_name": "GPT-5.3-Codex-Spark",
                        "rate_limit": {
                            "primary_window": {
                                "used_percent": 0,
                                "limit_window_seconds": 604800,
                                "reset_after_seconds": 604800,
                                "reset_at": 1784559521
                            },
                            "secondary_window": null
                        }
                    }
                ]
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json, includeSparkPrimaryWindow = true)

        assertTrue("Should be success", result.data != null)
        assertEquals(4, result.data!!.entries.size)
        assertEquals("5h · n/a", result.data!!.entries[0].subtitle)
        assertTrue(result.data!!.entries[1].subtitle?.startsWith("Weekly · Resets in") == true)
        assertEquals("Spark 5h · n/a", result.data!!.entries[2].subtitle)
        assertTrue(result.data!!.entries[3].subtitle?.startsWith("Spark Weekly · Resets in") == true)
    }

    @Test
    fun `parseResponseBody should use unknown label when duration is missing`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 15.5
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals("Unknown", result.data!!.entries[0].subtitle)
        assertEquals("Weekly · n/a", result.data!!.entries[1].subtitle)
    }

    @Test
    fun `parseResponseBody should round window duration and generate labels`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 15.5,
                        "limit_window_seconds": 20160
                    },
                    "secondary_window": {
                        "used_percent": 7.5,
                        "limit_window_seconds": 108000
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals("6h", result.data!!.entries[0].subtitle)
        assertEquals("1d", result.data!!.entries[1].subtitle)
    }

    @Test
    fun `parseResponseBody should parse free account monthly response with null optional windows`() {
        val json = """
            {
                "plan_type": "free",
                "rate_limit": {
                    "allowed": true,
                    "limit_reached": false,
                    "primary_window": {
                        "used_percent": 10,
                        "limit_window_seconds": 2592000,
                        "reset_after_seconds": 2591253,
                        "reset_at": 1783063148
                    },
                    "secondary_window": null
                },
                "code_review_rate_limit": null,
                "additional_rate_limits": null,
                "credits": {
                    "has_credits": false,
                    "unlimited": false,
                    "overage_limit_reached": false,
                    "balance": null,
                    "approx_local_messages": null,
                    "approx_cloud_messages": null
                },
                "spend_control": {
                    "reached": false,
                    "individual_limit": null
                },
                "rate_limit_reached_type": null,
                "promo": null,
                "referral_beacon": null,
                "rate_limit_reset_credits": {
                    "available_count": 0
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertNull("Should not expose parse/cast errors to the user", result.error)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(10f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertTrue(result.data!!.entries[0].subtitle?.startsWith("Monthly") == true)
        assertEquals(0f, result.data!!.entries[1].percentageUsed, 0.1f)
        assertEquals("Weekly · n/a", result.data!!.entries[1].subtitle)
        assertEquals(1, result.data!!.lines.size)
        assertEquals("0 resets available", result.data!!.lines[0].text)
    }

    @Test
    fun `parseResponseBody should parse rate limit reset credit count`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 10,
                        "reset_after_seconds": 3600
                    },
                    "secondary_window": {
                        "used_percent": 20,
                        "reset_after_seconds": 604800
                    }
                },
                "rate_limit_reset_credits": {
                    "available_count": 3
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(1, result.data!!.lines.size)
        assertEquals("3 resets available", result.data!!.lines[0].text)
    }

    @Test
    fun `parseResponseBody should show next reset credit expiry`() {
        val usageJson = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 10
                    }
                },
                "rate_limit_reset_credits": {
                    "available_count": 2
                }
            }
        """.trimIndent()
        val resetCreditsJson = """
            {
                "credits": [
                    {
                        "id": "RateLimitResetCredit_test-first",
                        "reset_type": "codex_rate_limits",
                        "is_supported_by_plan": true,
                        "status": "available",
                        "granted_at": "2030-01-01T00:00:00Z",
                        "expires_at": "2030-01-15T12:00:00Z",
                        "redeem_started_at": null,
                        "redeemed_at": null,
                        "profile_image_url": "https://example.test/codex-icon.png",
                        "profile_user_id": "test-user",
                        "title": "Test reset",
                        "description": "First test credit."
                    },
                    {
                        "id": "RateLimitResetCredit_test-second",
                        "reset_type": "codex_rate_limits",
                        "is_supported_by_plan": true,
                        "status": "available",
                        "granted_at": "2030-01-05T00:00:00Z",
                        "expires_at": "2030-01-30T12:00:00Z",
                        "redeem_started_at": null,
                        "redeemed_at": null,
                        "profile_image_url": "https://example.test/codex-icon.png",
                        "profile_user_id": "test-user",
                        "title": "Test reset",
                        "description": "Second test credit."
                    }
                ],
                "available_count": 2,
                "total_earned_count": 0
            }
        """.trimIndent()

        val result = provider.parseResponseBody(usageJson, resetCreditsBody = resetCreditsJson)

        assertTrue("Should be success", result.data != null)
        assertEquals("2 resets · next expiry 15 Jan", result.data!!.lines[0].text)
    }

    @Test
    fun `parseResponseBody should keep reset count when reset credit response is malformed`() {
        val usageJson = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 10
                    }
                },
                "rate_limit_reset_credits": {
                    "available_count": 3
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(usageJson, resetCreditsBody = "not json")

        assertTrue("Should be success", result.data != null)
        assertEquals("3 resets available", result.data!!.lines[0].text)
    }

    @Test
    fun `parseResponseBody should use singular reset credit label`() {
        val json = """
            {
                "rate_limit": {
                    "primary_window": {
                        "used_percent": 10
                    }
                },
                "rate_limit_reset_credits": {
                    "available_count": 1
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals("1 reset available", result.data!!.lines[0].text)
    }

    @Test
    fun `parseResponseBody should parse free account response with spark enabled and null additional limits`() {
        val json = """
            {
                "plan_type": "free",
                "rate_limit": {
                    "allowed": true,
                    "limit_reached": false,
                    "primary_window": {
                        "used_percent": 10,
                        "limit_window_seconds": 2592000,
                        "reset_after_seconds": 2591253,
                        "reset_at": 1783063148
                    },
                    "secondary_window": null
                },
                "additional_rate_limits": null
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json, includeSparkPrimaryWindow = true)

        assertTrue("Should be success", result.data != null)
        assertNull("Should not expose parse/cast errors to the user", result.error)
        assertEquals(4, result.data!!.entries.size)
        assertEquals(10f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertTrue(result.data!!.entries[0].subtitle?.startsWith("Monthly") == true)
        assertEquals("Weekly · n/a", result.data!!.entries[1].subtitle)
        assertEquals("Spark 5h · n/a", result.data!!.entries[2].subtitle)
        assertEquals("Spark Weekly · n/a", result.data!!.entries[3].subtitle)
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
    fun `getAccountPanelInfo should return two progress bars by default`() {
        val panelInfo = provider.getAccountPanelInfo(CodexUsageProvider.CodexUsageAccountConfig())

        assertEquals(2, panelInfo.usageEntryCount)
        assertEquals(1, panelInfo.lineCount)
    }

    @Test
    fun `getAccountPanelInfo should return four progress bars when spark is enabled`() {
        val config = CodexUsageProvider.CodexUsageAccountConfig().apply {
            showSparkPrimaryWindow = true
        }

        val panelInfo = provider.getAccountPanelInfo(config)

        assertEquals(4, panelInfo.usageEntryCount)
        assertEquals(1, panelInfo.lineCount)
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
                        "used_percent": 5.2,
                        "limit_window_seconds": 604800
                    }
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(2, result.data!!.entries.size)
        assertEquals(15.5f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertEquals(5.2f, result.data!!.entries[1].percentageUsed, 0.1f)
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

package de.espend.ml.llm.usage.provider

import org.junit.Assert.*
import org.junit.Test

class ZaiUsageProviderTest {

    private val provider = ZaiUsageProvider()

    // ==================== parseResponseBody - Success Cases ====================

    @Test
    fun `parseResponseBody should parse valid response with data wrapper`() {
        val json = """
            {
                "data": {
                    "limits": [
                        {
                            "type": "TOKENS_LIMIT",
                            "percentage": 45.5,
                            "nextResetTime": 1741440000000
                        }
                    ]
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(45.5f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should parse valid response without data wrapper`() {
        val json = """
            {
                "limits": [
                    {
                        "type": "TOKENS_LIMIT",
                        "percentage": 75.0,
                        "nextResetTime": 1741440000000
                    }
                ]
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(75.0f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should skip non-token limits and find TOKENS_LIMIT`() {
        val json = """
            {
                "limits": [
                    {
                        "type": "SOME_OTHER_LIMIT",
                        "percentage": 10.0
                    },
                    {
                        "type": "TOKENS_LIMIT",
                        "percentage": 50.0,
                        "nextResetTime": 1741440000000
                    }
                ]
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(50.0f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should handle zero percentage`() {
        val json = """
            {
                "limits": [
                    {
                        "type": "TOKENS_LIMIT",
                        "percentage": 0,
                        "nextResetTime": 1741440000000
                    }
                ]
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
                "limits": [
                    {
                        "type": "TOKENS_LIMIT",
                        "percentage": 100,
                        "nextResetTime": 1741440000000
                    }
                ]
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
                "limits": [
                    {
                        "type": "TOKENS_LIMIT",
                        "percentage": 150,
                        "nextResetTime": 1741440000000
                    }
                ]
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
                "limits": [
                    {
                        "type": "TOKENS_LIMIT",
                        "percentage": -50,
                        "nextResetTime": 1741440000000
                    }
                ]
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(0f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should handle missing percentage`() {
        val json = """
            {
                "limits": [
                    {
                        "type": "TOKENS_LIMIT",
                        "nextResetTime": 1741440000000
                    }
                ]
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(0f, result.data!!.entries[0].percentageUsed, 0.1f)
    }

    @Test
    fun `parseResponseBody should handle missing nextResetTime`() {
        val json = """
            {
                "limits": [
                    {
                        "type": "TOKENS_LIMIT",
                        "percentage": 50
                    }
                ]
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(50f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertNull("Subtitle should be null when no reset time", result.data.entries[0].subtitle)
    }

    @Test
    fun `parseResponseBody should handle zero nextResetTime`() {
        val json = """
            {
                "limits": [
                    {
                        "type": "TOKENS_LIMIT",
                        "percentage": 50,
                        "nextResetTime": 0
                    }
                ]
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(50f, result.data!!.entries[0].percentageUsed, 0.1f)
        assertNull("Subtitle should be null when reset time is 0", result.data.entries[0].subtitle)
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
    fun `parseResponseBody should return error when no limits found`() {
        val json = """
            {
                "some_other_field": "value"
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be error", result.error != null)
        assertTrue("Error should mention limits", result.error?.contains("limits") == true)
    }

    @Test
    fun `parseResponseBody should return error when empty limits array`() {
        val json = """
            {
                "limits": []
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be error", result.error != null)
        assertTrue("Error should mention token quota", result.error?.contains("token quota") == true)
    }

    @Test
    fun `parseResponseBody should return error when no TOKENS_LIMIT type`() {
        val json = """
            {
                "limits": [
                    {
                        "type": "OTHER_LIMIT",
                        "percentage": 50
                    }
                ]
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be error", result.error != null)
        assertTrue("Error should mention token quota", result.error?.contains("token quota") == true)
    }

    // ==================== Config Tests ====================

    @Test
    fun `config should have correct default values`() {
        val config = ZaiUsageProvider.ZaiUsageAccountConfig()

        assertEquals("zai", config.providerId)
        assertEquals("", config.apiKey)
    }

    @Test
    fun `config should support setting apiKey`() {
        val config = ZaiUsageProvider.ZaiUsageAccountConfig()
        config.apiKey = "test-api-key-123"

        assertEquals("test-api-key-123", config.apiKey)
    }

    // ==================== Real-world Examples ====================

    @Test
    fun `parseResponseBody should parse real API response`() {
        val json = """
            {
                "code": 0,
                "message": "success",
                "data": {
                    "limits": [
                        {
                            "type": "TOKENS_LIMIT",
                            "percentage": 32.5,
                            "nextResetTime": 1741454400000
                        }
                    ]
                }
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be success", result.data != null)
        assertEquals(32.5f, result.data!!.entries[0].percentageUsed, 0.1f)
    }
}

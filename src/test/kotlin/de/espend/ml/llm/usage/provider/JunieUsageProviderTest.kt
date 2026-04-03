package de.espend.ml.llm.usage.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JunieUsageProviderTest {

    private val provider = JunieUsageProvider()

    @Test
    fun `parseResponseBody should use short EAP label without provider prefix`() {
        val json = """
            {
                "balanceLeft": 297.73,
                "balanceUnit": "USD",
                "licenseType": "JUNP",
                "active": true
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertNotNull("Should be success", result.data)
        val data = result.data!!
        assertEquals(1, data.lines.size)
        assertEquals("EAP · $297.73", data.lines[0].text)
    }

    @Test
    fun `parseResponseBody should fall back to raw license type when unknown`() {
        val json = """
            {
                "balanceLeft": 12.5,
                "balanceUnit": "USD",
                "licenseType": "CUSTOM",
                "active": true
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        val data = result.data ?: error("Expected success")
        assertEquals("CUSTOM · $12.5", data.lines[0].text)
    }

    @Test
    fun `parseResponseBody should return error for inactive account`() {
        val json = """
            {
                "balanceLeft": 297.73,
                "balanceUnit": "USD",
                "licenseType": "JUNP",
                "active": false
            }
        """.trimIndent()

        val result = provider.parseResponseBody(json)

        assertTrue("Should be error", result.error != null)
        assertEquals("Account is not active", result.error)
    }
}

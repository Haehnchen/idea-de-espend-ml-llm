package de.espend.ml.llm.vcs

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiApiClientTest {
    @Test
    fun `parses data-wrapped chat completion`() {
        val response = """
            {
              "data": {
                "choices": [
                  {
                    "message": {
                      "content": "Rename oldValue to newValue",
                      "role": "assistant"
                    }
                  }
                ]
              },
              "success": true
            }
        """.trimIndent()

        assertEquals(
            ApiResult.Success("Rename oldValue to newValue"),
            OpenAiApiClient.parseResponse(response)
        )
    }

    @Test
    fun `continues to parse standard OpenAI chat completion`() {
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": "Fix response parsing",
                    "role": "assistant"
                  }
                }
              ]
            }
        """.trimIndent()

        assertEquals(
            ApiResult.Success("Fix response parsing"),
            OpenAiApiClient.parseResponse(response)
        )
    }
}

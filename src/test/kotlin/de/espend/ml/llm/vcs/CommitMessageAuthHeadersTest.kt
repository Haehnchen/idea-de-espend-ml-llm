package de.espend.ml.llm.vcs

import de.espend.ml.llm.profile.AiProfileConfig
import de.espend.ml.llm.profile.AiProfilePlatformRegistry
import de.espend.ml.llm.profile.AiProfileTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CommitMessageAuthHeadersTest {
    @Test
    fun `profile uses trimmed bearer auth`() {
        val headers = CommitMessageAuthHeaders.bearer(
            AiProfileConfig(
                platform = AiProfilePlatformRegistry.PLATFORM_ANTHROPIC_COMPATIBLE,
                transport = AiProfileTransport.PI.id,
                apiType = "anthropic",
                apiKey = " ollama-cloud-key "
            )
        )

        assertEquals("Bearer ollama-cloud-key", headers["Authorization"])
        assertFalse(headers.containsKey("x-api-key"))
    }
}

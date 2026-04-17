package de.espend.ml.llm.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProfileRegistryTest {
    private val registry = AiProfileRegistry()

    @Test
    fun `shouldReuseInstalledTransportConfig should bypass installed claude acp for Claude CLI`() {
        val profile = AiProfileConfig(
            platform = AiProfilePlatformRegistry.PLATFORM_CLAUDE_CODE,
            transport = AiProfileTransport.CLAUDE_ACP.id
        )

        assertFalse(registry.shouldReuseInstalledTransportConfig(profile, AiProfileTransport.CLAUDE_ACP))
    }

    @Test
    fun `shouldReuseInstalledTransportConfig should keep installed claude acp for api backed profiles`() {
        val profile = AiProfileConfig(
            platform = AiProfilePlatformRegistry.PLATFORM_ANTHROPIC_COMPATIBLE,
            transport = AiProfileTransport.CLAUDE_ACP.id
        )

        assertTrue(registry.shouldReuseInstalledTransportConfig(profile, AiProfileTransport.CLAUDE_ACP))
    }
}

package de.espend.ml.llm.profile

import org.junit.Assert.assertEquals
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

    @Test
    fun `removeProfilesWithUnknownPlatforms removes legacy profiles from persisted state`() {
        val supportedProfile = AiProfileConfig(
            id = "supported",
            platform = AiProfilePlatformRegistry.PLATFORM_CLAUDE_CODE,
            isEnabled = false
        )
        registry.currentState.profiles = mutableListOf(
            supportedProfile,
            AiProfileConfig(id = "legacy-gemini", platform = "gemini"),
            AiProfileConfig(id = "legacy-provider", platform = "removed-provider", isEnabled = false)
        )

        registry.removeProfilesWithUnknownPlatforms()

        assertEquals(listOf(supportedProfile), registry.currentState.profiles)
    }
}

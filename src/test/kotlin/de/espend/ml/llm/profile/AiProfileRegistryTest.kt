package de.espend.ml.llm.profile

import com.intellij.ml.llm.agents.acp.client.auth.AcpAgentAuthentication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProfileRegistryTest {
    private val managedExternally = AcpAgentAuthentication.fromId("MANAGED_EXTERNALLY")

    @Test
    fun `preferredAuthentication should keep Claude CLI profiles externally managed`() {
        val profile = AiProfileConfig(
            platform = AiProfilePlatformRegistry.PLATFORM_CLAUDE_CODE,
            transport = AiProfileTransport.CLAUDE_ACP.id
        )

        assertEquals(
            managedExternally,
            AiProfileRegistry.preferredAuthentication()
        )
    }

    @Test
    fun `preferredAuthentication should keep API backed profiles externally managed`() {
        val profile = AiProfileConfig(
            platform = AiProfilePlatformRegistry.PLATFORM_ANTHROPIC_COMPATIBLE,
            transport = AiProfileTransport.CLAUDE_ACP.id,
            apiKey = "test-key",
            baseUrl = "https://example.invalid"
        )

        assertEquals(
            managedExternally,
            AiProfileRegistry.preferredAuthentication()
        )
    }

    @Test
    fun `authenticationTargetIds should update legacy and registry Claude auth entries`() {
        val profile = AiProfileConfig(
            id = "legacy-anthropic-default",
            platform = AiProfilePlatformRegistry.PLATFORM_CLAUDE_CODE,
            transport = AiProfileTransport.CLAUDE_ACP.id
        )

        val targetIds = AiProfileRegistry.authenticationTargetIds(profile, "legacy-anthropic-default--abc123")

        assertTrue("acp.legacy-anthropic-default--abc123" in targetIds)
        assertTrue("acp.legacy-anthropic-default" in targetIds)
        assertTrue("acp.anthropic-default" in targetIds)
        assertTrue("acp.registry.claude-acp" in targetIds)
    }

    @Test
    fun `authenticationTargetIds should not rewrite built in Claude registry auth for api backed profiles`() {
        val profile = AiProfileConfig(
            id = "legacy-zai",
            platform = AiProfilePlatformRegistry.PLATFORM_ZAI,
            transport = AiProfileTransport.CLAUDE_ACP.id
        )

        val targetIds = AiProfileRegistry.authenticationTargetIds(profile, "legacy-zai--abc123")

        assertTrue("acp.legacy-zai--abc123" in targetIds)
        assertTrue("acp.legacy-zai" in targetIds)
        assertFalse("acp.anthropic-default" in targetIds)
        assertFalse("acp.registry.claude-acp" in targetIds)
    }
}

package de.espend.ml.llm.structurediff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuralDiffSettingsTest {
    @Test
    fun `glob routing is case insensitive and overrides the default`() {
        val fallback = tool("Difftastic", "difftastic")
        val java = tool("Sem for Java", "sem").apply { filePatterns = "*.java; build.?radle" }
        val settings = StructuralDiffSettings().apply {
            tools = mutableListOf(fallback, java)
            defaultToolName = fallback.name
        }

        assertTrue(java.accepts("ORDER.JAVA"))
        assertTrue(java.accepts("build.gradle"))
        assertFalse(java.accepts("Order.kt"))
        assertEquals(java, settings.toolFor("Order.java"))
        assertEquals(fallback, settings.toolFor("Order.kt"))
    }

    @Test
    fun `a configuration without a supported engine is ignored`() {
        val invalid = tool("Unknown", "not-an-engine")
        val valid = tool("Difftastic", "difftastic")
        val settings = StructuralDiffSettings().apply {
            tools = mutableListOf(invalid, valid)
            defaultToolName = invalid.name
        }

        assertEquals(valid, settings.toolFor("Example.kt"))
    }

    private fun tool(name: String, engine: String): StructuralDiffToolConfig = StructuralDiffToolConfig().apply {
        this.name = name
        engineId = engine
        executable = "/tools/$engine"
    }
}

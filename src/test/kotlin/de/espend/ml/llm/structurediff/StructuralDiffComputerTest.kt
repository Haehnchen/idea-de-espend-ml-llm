package de.espend.ml.llm.structurediff

import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StructuralDiffComputerTest : BasePlatformTestCase() {
    private val left = "value = 1\nkeep\nitems = listOf(1,2)\n"
    private val right = "value = 2\nkeep\nitems=listOf(1, 2)\n"

    fun `test equal semantic output hides textual formatting changes`() {
        val fragments = computer(StructuralDiffAnalysis.Equal).compute(
            left,
            right,
            ComparisonPolicy.DEFAULT,
            true,
            EmptyProgressIndicator(),
        )

        assertEmpty(fragments)
    }

    fun `test changed line mask filters unrelated textual fragments`() {
        val changed = StructuralDiffAnalysis.Changed(setOf(0), setOf(0))
        val fragments = computer(changed).compute(
            left,
            right,
            ComparisonPolicy.DEFAULT,
            true,
            EmptyProgressIndicator(),
        )

        assertEquals(1, fragments.size)
        assertEquals(0, fragments.single().startLine1)
        assertEquals(1, fragments.single().endLine1)
    }

    fun `test engine failure falls back to IntelliJ comparison`() {
        val fragments = computer(StructuralDiffAnalysis.Failed("broken output")).compute(
            left,
            right,
            ComparisonPolicy.DEFAULT,
            true,
            EmptyProgressIndicator(),
        )

        assertTrue(fragments.isNotEmpty())
    }

    fun `test invalid engine line numbers fall back safely`() {
        val fragments = computer(StructuralDiffAnalysis.Changed(setOf(99), emptySet())).compute(
            left,
            right,
            ComparisonPolicy.DEFAULT,
            true,
            EmptyProgressIndicator(),
        )

        assertTrue(fragments.isNotEmpty())
    }

    private fun computer(result: StructuralDiffAnalysis): StructuralDiffComputer {
        val config = StructuralDiffToolConfig().apply {
            name = "Test"
            engineId = StructuralDiffEngineKind.DIFFTASTIC.id
            executable = "/test/difft"
        }
        return StructuralDiffComputer(
            project = project,
            fileName = "Sample.kt",
            configProvider = { config },
            runner = { _, _, _, _, _ -> result },
        )
    }
}

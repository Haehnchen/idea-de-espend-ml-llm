package de.espend.ml.llm.difftastic

import de.espend.ml.llm.CommandPathUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DifftasticRunnerTest {
    @Test
    fun `run invokes installed difft and parses structural json`() {
        val difft = CommandPathUtils.findDifftasticPath()
        assumeTrue("difft is not installed", difft != null)

        val result = DifftasticRunner.run(
            left = DifftasticSource("old.kt", "fun main() {\n    println(\"hello\")\n}\n"),
            right = DifftasticSource("new.kt", "fun main() {\n    println(\"world\")\n}\n"),
            commandPath = difft,
        )

        assertTrue(result is DifftasticRunResult.Success)
        val success = result as DifftasticRunResult.Success
        assertEquals("changed", success.diff.status)
        assertTrue(success.diff.chunks.flatten().any { line ->
            line.lhs?.changes?.any { it.content == "\"hello\"" } == true &&
                line.rhs?.changes?.any { it.content == "\"world\"" } == true
        })
    }
    @Test
    fun `run defaults to full file context so editor collapse can be toggled locally`() {
        val difft = CommandPathUtils.findDifftasticPath()
        assumeTrue("difft is not installed", difft != null)

        val leftText = numberedKotlinLines(changedValue = "old")
        val rightText = numberedKotlinLines(changedValue = "new")
        val result = DifftasticRunner.run(
            left = DifftasticSource("old.kt", leftText),
            right = DifftasticSource("new.kt", rightText),
            commandPath = difft,
        )

        assertTrue(result is DifftasticRunResult.Success)
        val success = result as DifftasticRunResult.Success
        val fullModel = DifftasticEditorModelBuilder.build(
            DifftasticEditorInput("old.kt", leftText, "new.kt", rightText, success.diff),
        )
        val collapsedModel = DifftasticEditorModelBuilder.build(
            DifftasticEditorInput("old.kt", leftText, "new.kt", rightText, success.diff),
            collapseUnchanged = true,
        )

        assertTrue(fullModel.left.text.contains("val v0 = 0"))
        assertTrue(fullModel.left.text.contains("val v24 = 24"))
        assertTrue(collapsedModel.left.lineMarkers.any { it.kind == DifftasticLineKind.Collapsed })
    }

    private fun numberedKotlinLines(changedValue: String): String {
        return (0..24).joinToString(separator = "\n", postfix = "\n") { index ->
            if (index == 12) {
                "val v$index = $changedValue"
            } else {
                "val v$index = $index"
            }
        }
    }
}

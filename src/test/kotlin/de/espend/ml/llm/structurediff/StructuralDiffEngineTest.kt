package de.espend.ml.llm.structurediff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuralDiffEngineTest {
    @Test
    fun `difftastic output becomes a changed line mask and token decorations`() {
        val result = StructuralDiffEngine.parseOutput(
            StructuralDiffEngineKind.DIFFTASTIC,
            """
                {
                  "status":"changed",
                  "aligned_lines":[[0,0],[1,1]],
                  "chunks":[[{
                    "lhs":{"line_number":1,"changes":[{"start":4,"end":7,"highlight":"keyword"}]},
                    "rhs":{"line_number":1,"changes":[{"start":4,"end":8,"highlight":"keyword"}]}
                  }]]
                }
            """.trimIndent(),
            "one\nold\n",
            "one\nnewer\n",
        ) as StructuralDiffAnalysis.Changed

        assertEquals(setOf(1), result.leftLines)
        assertEquals(setOf(1), result.rightLines)
        assertEquals(2, result.tokens.size)
        assertEquals("keyword", result.tokens.first().style)
    }

    @Test
    fun `diffsitter output uses its reported old and new lines`() {
        val result = StructuralDiffEngine.parseOutput(
            StructuralDiffEngineKind.DIFFSITTER,
            """
                {"hunks":[
                  {"Old":[{"line_index":2,"entries":[{
                    "start_position":{"row":2,"column":1},
                    "end_position":{"row":2,"column":3}
                  }]}]},
                  {"New":[{"line_index":4,"entries":[]}]}
                ]}
            """.trimIndent(),
        ) as StructuralDiffAnalysis.Changed

        assertEquals(setOf(2), result.leftLines)
        assertEquals(setOf(4), result.rightLines)
        assertEquals(1, result.tokens.size)
    }

    @Test
    fun `sem output retains move counterpart information`() {
        val result = StructuralDiffEngine.parseOutput(
            StructuralDiffEngineKind.SEM,
            """
                {"changes":[{
                  "changeType":"moved",
                  "entityType":"method",
                  "entityName":"calculate",
                  "oldStartLine":2,
                  "oldEndLine":3,
                  "startLine":8,
                  "endLine":9
                }]}
            """.trimIndent(),
        ) as StructuralDiffAnalysis.Changed

        assertEquals(setOf(1, 2), result.leftLines)
        assertEquals(setOf(7, 8), result.rightLines)
        assertEquals(2, result.regions.size)
        assertTrue(result.regions.first().description.contains("calculate"))
        assertEquals(7, result.regions.first().counterpartLine)
    }

    @Test
    fun `an unchanged engine response produces no semantic changes`() {
        val result = StructuralDiffEngine.parseOutput(
            StructuralDiffEngineKind.DIFFTASTIC,
            "{\"status\":\"unchanged\"}",
        )

        assertEquals(StructuralDiffAnalysis.Equal, result)
    }
}

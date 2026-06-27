package de.espend.ml.llm.difftastic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DifftasticJsonParserTest {
    @Test
    fun `parse reads changed Kotlin file output`() {
        val diff = DifftasticJsonParser.parse(
            """
            {
              "aligned_lines": [[0, 0], [1, 1], [2, 2]],
              "chunks": [[
                {
                  "lhs": {
                    "line_number": 1,
                    "changes": [{"start": 12, "end": 19, "content": "\"hello\"", "highlight": "string"}]
                  },
                  "rhs": {
                    "line_number": 1,
                    "changes": [{"start": 12, "end": 19, "content": "\"world\"", "highlight": "string"}]
                  }
                }
              ]],
              "language": "Kotlin",
              "path": "/tmp/right.kt",
              "status": "changed"
            }
            """.trimIndent()
        )

        assertEquals("Kotlin", diff.language)
        assertEquals("changed", diff.status)
        assertEquals(3, diff.alignedLines.size)
        assertEquals(1, diff.chunks.size)
        assertEquals(1, diff.chunks.single().single().lhs?.lineNumber)
        assertEquals("\"world\"", diff.chunks.single().single().rhs?.changes?.single()?.content)
    }

    @Test
    fun `parse handles inserted lines with null left side`() {
        val diff = DifftasticJsonParser.parse(
            """
            {
              "aligned_lines": [[0, 0], [null, 1], [1, 2]],
              "chunks": [[
                {
                  "rhs": {
                    "line_number": 1,
                    "changes": [{"start": 0, "end": 1, "content": "x", "highlight": "normal"}]
                  }
                }
              ]],
              "language": "Text",
              "path": "/tmp/right.txt",
              "status": "changed"
            }
            """.trimIndent()
        )

        assertNull(diff.alignedLines[1][0])
        assertEquals(1, diff.chunks.single().single().rhs?.lineNumber)
        assertNull(diff.chunks.single().single().lhs)
    }

    @Test
    fun `parse accepts difftastic array output`() {
        val diff = DifftasticJsonParser.parse(
            """
            [
              {
                "aligned_lines": [[0, 0], [1, 1]],
                "chunks": [[
                  {
                    "lhs": {
                      "line_number": 1,
                      "changes": [{"start": 8, "end": 11, "content": "one", "highlight": "normal"}]
                    },
                    "rhs": {
                      "line_number": 1,
                      "changes": [{"start": 8, "end": 11, "content": "two", "highlight": "normal"}]
                    }
                  }
                ]],
                "language": "Kotlin",
                "path": "/tmp/new.kt",
                "status": "changed"
              }
            ]
            """.trimIndent()
        )

        assertEquals("/tmp/new.kt", diff.path)
        assertEquals("one", diff.chunks.single().single().lhs?.changes?.single()?.content)
        assertEquals("two", diff.chunks.single().single().rhs?.changes?.single()?.content)
    }
}

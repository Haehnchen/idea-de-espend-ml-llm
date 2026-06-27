package de.espend.ml.llm.difftastic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifftasticEditorModelTest {
    @Test
    fun `build aligns inserted rows with a left spacer line`() {
        val diff = DifftasticJsonParser.parse(
            """
            {
              "aligned_lines": [[0, 0], [null, 1], [1, 2]],
              "chunks": [[
                {
                  "rhs": {
                    "line_number": 1,
                    "changes": [{"start": 0, "end": 7, "content": "insert", "highlight": "normal"}]
                  }
                }
              ]],
              "language": "Text",
              "status": "changed"
            }
            """.trimIndent()
        )

        val model = DifftasticEditorModelBuilder.build(
            DifftasticEditorInput(
                leftTitle = "old.txt",
                leftText = "a\nb\n",
                rightTitle = "new.txt",
                rightText = "a\ninsert\nb\n",
                diff = diff,
            )
        )

        assertEquals("a\n\nb", model.left.text)
        assertEquals("a\ninsert\nb", model.right.text)
        assertTrue(model.left.lineMarkers.isEmpty())
        assertEquals(listOf(DifftasticLineMarker(1, DifftasticLineKind.Inserted)), model.right.lineMarkers)
        assertEquals("insert", model.right.text.substring(model.right.tokenMarkers.single().startOffset, model.right.tokenMarkers.single().endOffset))
    }

    @Test
    fun `build aligns removed rows with a right spacer line`() {
        val diff = DifftasticJsonParser.parse(
            """
            {
              "aligned_lines": [[0, 0], [1, null], [2, 1]],
              "chunks": [[
                {
                  "lhs": {
                    "line_number": 1,
                    "changes": [{"start": 0, "end": 6, "content": "delete", "highlight": "normal"}]
                  }
                }
              ]],
              "language": "Text",
              "status": "changed"
            }
            """.trimIndent()
        )

        val model = DifftasticEditorModelBuilder.build(
            DifftasticEditorInput(
                leftTitle = "old.txt",
                leftText = "a\ndelete\nb\n",
                rightTitle = "new.txt",
                rightText = "a\nb\n",
                diff = diff,
            )
        )

        assertEquals("a\ndelete\nb", model.left.text)
        assertEquals("a\n\nb", model.right.text)
        assertEquals(listOf(DifftasticLineMarker(1, DifftasticLineKind.Removed)), model.left.lineMarkers)
        assertTrue(model.right.lineMarkers.isEmpty())
        assertEquals("delete", model.left.text.substring(model.left.tokenMarkers.single().startOffset, model.left.tokenMarkers.single().endOffset))
    }

    @Test
    fun `build maps difftastic byte offsets to editor character offsets`() {
        val leftLine = "println(\"ä\")"
        val rightLine = "println(\"ö\")"
        val start = leftLine.toByteArray(Charsets.UTF_8).indexOfFirst { it == '"'.code.toByte() }
        val end = leftLine.toByteArray(Charsets.UTF_8).size - 1

        val diff = DifftasticJsonParser.parse(
            """
            {
              "aligned_lines": [[0, 0]],
              "chunks": [[
                {
                  "lhs": {
                    "line_number": 0,
                    "changes": [{"start": $start, "end": $end, "content": "\"ä\"", "highlight": "string"}]
                  },
                  "rhs": {
                    "line_number": 0,
                    "changes": [{"start": $start, "end": $end, "content": "\"ö\"", "highlight": "string"}]
                  }
                }
              ]],
              "language": "Kotlin",
              "status": "changed"
            }
            """.trimIndent()
        )

        val model = DifftasticEditorModelBuilder.build(
            DifftasticEditorInput(
                leftTitle = "old.kt",
                leftText = leftLine,
                rightTitle = "new.kt",
                rightText = rightLine,
                diff = diff,
            )
        )

        val leftMarker = model.left.tokenMarkers.single()
        val rightMarker = model.right.tokenMarkers.single()
        assertEquals("\"ä\"", model.left.text.substring(leftMarker.startOffset, leftMarker.endOffset))
        assertEquals("\"ö\"", model.right.text.substring(rightMarker.startOffset, rightMarker.endOffset))
        assertEquals(listOf(DifftasticLineMarker(0, DifftasticLineKind.Modified)), model.left.lineMarkers)
        assertEquals(listOf(DifftasticLineMarker(0, DifftasticLineKind.Modified)), model.right.lineMarkers)
    }

    @Test
    fun `build marks whole right side when difftastic reports created without rows`() {
        val diff = DifftasticJsonParser.parse(
            """
            {
              "language": "Kotlin",
              "path": "/tmp/New.kt",
              "status": "created"
            }
            """.trimIndent()
        )

        val model = DifftasticEditorModelBuilder.build(
            DifftasticEditorInput(
                leftTitle = "New.kt",
                leftText = "",
                rightTitle = "New.kt",
                rightText = "fun main() {\n    println(\"new\")\n}\n",
                diff = diff,
            )
        )

        assertEquals("\n\n\n", model.left.text)
        assertEquals("fun main() {\n    println(\"new\")\n}\n", model.right.text)
        assertTrue(model.left.lineMarkers.isEmpty())
        assertEquals(
            listOf(
                DifftasticLineMarker(0, DifftasticLineKind.Inserted),
                DifftasticLineMarker(1, DifftasticLineKind.Inserted),
                DifftasticLineMarker(2, DifftasticLineKind.Inserted),
                DifftasticLineMarker(3, DifftasticLineKind.Inserted),
            ),
            model.right.lineMarkers,
        )
    }

    @Test
    fun `build marks whole left side when difftastic reports deleted without rows`() {
        val diff = DifftasticJsonParser.parse(
            """
            {
              "language": "Kotlin",
              "path": "/tmp/Old.kt",
              "status": "deleted"
            }
            """.trimIndent()
        )

        val model = DifftasticEditorModelBuilder.build(
            DifftasticEditorInput(
                leftTitle = "Old.kt",
                leftText = "fun old() = Unit\n",
                rightTitle = "Old.kt",
                rightText = "",
                diff = diff,
            )
        )

        assertEquals("fun old() = Unit\n", model.left.text)
        assertEquals("\n", model.right.text)
        assertEquals(
            listOf(
                DifftasticLineMarker(0, DifftasticLineKind.Removed),
                DifftasticLineMarker(1, DifftasticLineKind.Removed),
            ),
            model.left.lineMarkers,
        )
        assertTrue(model.right.lineMarkers.isEmpty())
    }

    @Test
    fun `build can collapse long unchanged fragments`() {
        val diff = DifftasticJsonParser.parse(
            """
            {
              "aligned_lines": [[0, 0], [1, 1], [2, 2], [3, 3], [4, 4], [5, 5], [6, 6], [7, 7], [8, 8], [9, 9], [10, 10], [11, 11], [12, 12]],
              "chunks": [[
                {
                  "lhs": {
                    "line_number": 12,
                    "changes": [{"start": 0, "end": 3, "content": "old", "highlight": "normal"}]
                  },
                  "rhs": {
                    "line_number": 12,
                    "changes": [{"start": 0, "end": 3, "content": "new", "highlight": "normal"}]
                  }
                }
              ]],
              "language": "Text",
              "status": "changed"
            }
            """.trimIndent()
        )
        val unchanged = (0..11).joinToString("\n") { "line-$it" }

        val model = DifftasticEditorModelBuilder.build(
            DifftasticEditorInput(
                leftTitle = "old.txt",
                leftText = "$unchanged\nold\n",
                rightTitle = "new.txt",
                rightText = "$unchanged\nnew\n",
                diff = diff,
            ),
            collapseUnchanged = true,
        )

        assertTrue(model.left.text.contains("... 6 unchanged lines ..."))
        assertTrue(model.right.text.contains("... 6 unchanged lines ..."))
        assertTrue(model.left.lineMarkers.any { it.kind == DifftasticLineKind.Collapsed })
        assertTrue(model.right.lineMarkers.any { it.kind == DifftasticLineKind.Collapsed })
    }

    @Test
    fun `buildUnified emits prefixed inserted and removed lines`() {
        val diff = DifftasticJsonParser.parse(
            """
            {
              "aligned_lines": [[0, 0], [1, 1]],
              "chunks": [[
                {
                  "lhs": {
                    "line_number": 1,
                    "changes": [{"start": 0, "end": 3, "content": "old", "highlight": "normal"}]
                  },
                  "rhs": {
                    "line_number": 1,
                    "changes": [{"start": 0, "end": 3, "content": "new", "highlight": "normal"}]
                  }
                }
              ]],
              "language": "Text",
              "status": "changed"
            }
            """.trimIndent()
        )

        val model = DifftasticEditorModelBuilder.buildUnified(
            DifftasticEditorInput(
                leftTitle = "old.txt",
                leftText = "same\nold\n",
                rightTitle = "new.txt",
                rightText = "same\nnew\n",
                diff = diff,
            )
        )

        assertEquals("  same\n- old\n+ new", model.text)
        assertEquals(
            listOf(
                DifftasticLineMarker(1, DifftasticLineKind.Removed),
                DifftasticLineMarker(2, DifftasticLineKind.Inserted),
            ),
            model.lineMarkers,
        )
        assertEquals("old", model.text.substring(model.tokenMarkers[0].startOffset, model.tokenMarkers[0].endOffset))
        assertEquals("new", model.text.substring(model.tokenMarkers[1].startOffset, model.tokenMarkers[1].endOffset))
    }
}

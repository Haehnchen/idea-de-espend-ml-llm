package de.espend.ml.llm.difftastic

import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

data class DifftasticEditorInput(
    val leftTitle: String,
    val leftText: String,
    val rightTitle: String,
    val rightText: String,
    val diff: DifftasticFileDiff,
)

data class DifftasticEditorModel(
    val leftTitle: String,
    val rightTitle: String,
    val language: String?,
    val status: String?,
    val left: DifftasticEditorSide,
    val right: DifftasticEditorSide,
)

data class DifftasticUnifiedModel(
    val title: String,
    val text: String,
    val lineMarkers: List<DifftasticLineMarker>,
    val tokenMarkers: List<DifftasticTokenMarker>,
)

data class DifftasticEditorSide(
    val text: String,
    val lineMarkers: List<DifftasticLineMarker>,
    val tokenMarkers: List<DifftasticTokenMarker>,
    val originalToDisplayLine: Map<Int, Int>,
)

data class DifftasticLineMarker(
    val line: Int,
    val kind: DifftasticLineKind,
)

data class DifftasticTokenMarker(
    val startOffset: Int,
    val endOffset: Int,
    val kind: DifftasticLineKind = DifftasticLineKind.Modified,
)

enum class DifftasticLineKind {
    Inserted,
    Removed,
    Modified,
    Collapsed,
}

object DifftasticEditorModelBuilder {
    private const val COLLAPSE_CONTEXT_LINES = 3

    fun build(input: DifftasticEditorInput, collapseUnchanged: Boolean = false): DifftasticEditorModel {
        val rows = buildDisplayRows(input, collapseUnchanged)
        val left = buildSide(rows, isLeft = true)
        val right = buildSide(rows, isLeft = false)

        return DifftasticEditorModel(
            leftTitle = input.leftTitle,
            rightTitle = input.rightTitle,
            language = input.diff.language,
            status = input.diff.status,
            left = left,
            right = right,
        )
    }

    fun buildUnified(input: DifftasticEditorInput, collapseUnchanged: Boolean = false): DifftasticUnifiedModel {
        val rows = buildDisplayRows(input, collapseUnchanged)
        val text = StringBuilder()
        val lineMarkers = mutableListOf<DifftasticLineMarker>()
        val tokenMarkers = mutableListOf<DifftasticTokenMarker>()
        var displayLine = 0

        fun appendLine(prefix: String, lineText: String, kind: DifftasticLineKind?, changes: List<DifftasticChange>) {
            if (text.isNotEmpty()) {
                text.append('\n')
            }

            val lineStart = text.length
            text.append(prefix)
            text.append(lineText)
            kind?.let { lineMarkers.add(DifftasticLineMarker(displayLine, it)) }
            appendTokenMarkers(lineText, changes, lineStart + prefix.length, kind ?: DifftasticLineKind.Modified, tokenMarkers)
            displayLine++
        }

        rows.forEach { row ->
            when (row.kind) {
                RowKind.Context -> appendLine("  ", row.rightText.ifEmpty { row.leftText }, null, emptyList())
                RowKind.Inserted -> appendLine("+ ", row.rightText, DifftasticLineKind.Inserted, row.rightChanges)
                RowKind.Removed -> appendLine("- ", row.leftText, DifftasticLineKind.Removed, row.leftChanges)
                RowKind.Modified -> {
                    appendLine("- ", row.leftText, DifftasticLineKind.Removed, row.leftChanges)
                    appendLine("+ ", row.rightText, DifftasticLineKind.Inserted, row.rightChanges)
                }
                RowKind.Collapsed -> appendLine("  ", row.rightText, DifftasticLineKind.Collapsed, emptyList())
            }
        }

        return DifftasticUnifiedModel(
            title = input.rightTitle,
            text = text.toString(),
            lineMarkers = lineMarkers,
            tokenMarkers = tokenMarkers,
        )
    }

    private fun buildDisplayRows(input: DifftasticEditorInput, collapseUnchanged: Boolean): List<ModelRow> {
        val leftLines = splitLines(input.leftText)
        val rightLines = splitLines(input.rightText)
        val highlights = collectHighlights(input.diff)
        return collapseRows(buildRows(input.diff, leftLines, rightLines, highlights), collapseUnchanged)
    }

    private fun buildSide(rows: List<ModelRow>, isLeft: Boolean): DifftasticEditorSide {
        val text = StringBuilder()
        val lineMarkers = mutableListOf<DifftasticLineMarker>()
        val tokenMarkers = mutableListOf<DifftasticTokenMarker>()
        val originalToDisplayLine = mutableMapOf<Int, Int>()

        rows.forEachIndexed { displayLine, row ->
            if (displayLine > 0) {
                text.append('\n')
            }

            val lineStart = text.length
            val lineText = if (isLeft) row.leftText else row.rightText
            text.append(lineText)

            val originalLine = if (isLeft) row.leftLineNumber else row.rightLineNumber
            if (originalLine != null) {
                originalToDisplayLine[originalLine] = displayLine
            }

            markerKind(row.kind, isLeft)?.let { kind ->
                lineMarkers.add(DifftasticLineMarker(displayLine, kind))
            }

            val changes = if (isLeft) row.leftChanges else row.rightChanges
            appendTokenMarkers(lineText, changes, lineStart, tokenKind(row.kind), tokenMarkers)
        }

        return DifftasticEditorSide(
            text = text.toString(),
            lineMarkers = lineMarkers,
            tokenMarkers = tokenMarkers,
            originalToDisplayLine = originalToDisplayLine,
        )
    }

    private fun appendTokenMarkers(
        lineText: String,
        changes: List<DifftasticChange>,
        baseOffset: Int,
        kind: DifftasticLineKind,
        target: MutableList<DifftasticTokenMarker>,
    ) {
        changes.forEach { change ->
            val start = byteOffsetToCharIndex(lineText, change.start)
            val end = byteOffsetToCharIndex(lineText, change.end)
            if (end > start) {
                target.add(
                    DifftasticTokenMarker(
                        startOffset = baseOffset + min(max(start, 0), lineText.length),
                        endOffset = baseOffset + min(max(end, start), lineText.length),
                        kind = kind,
                    )
                )
            }
        }
    }

    private fun tokenKind(kind: RowKind): DifftasticLineKind {
        return when (kind) {
            RowKind.Inserted -> DifftasticLineKind.Inserted
            RowKind.Removed -> DifftasticLineKind.Removed
            RowKind.Modified -> DifftasticLineKind.Modified
            RowKind.Context,
            RowKind.Collapsed,
            -> DifftasticLineKind.Modified
        }
    }

    private fun markerKind(kind: RowKind, isLeft: Boolean): DifftasticLineKind? {
        return when (kind) {
            RowKind.Context -> null
            RowKind.Inserted -> if (isLeft) null else DifftasticLineKind.Inserted
            RowKind.Removed -> if (isLeft) DifftasticLineKind.Removed else null
            RowKind.Modified -> DifftasticLineKind.Modified
            RowKind.Collapsed -> DifftasticLineKind.Collapsed
        }
    }

    private fun buildRows(
        diff: DifftasticFileDiff,
        leftLines: List<String>,
        rightLines: List<String>,
        highlights: HighlightMaps,
    ): List<ModelRow> {
        val alignedRows = diff.alignedLines
            .mapNotNull { pair ->
                if (pair.size < 2) {
                    null
                } else {
                    pair[0] to pair[1]
                }
            }

        if (alignedRows.isNotEmpty()) {
            return alignedRows.map { (leftNumber, rightNumber) ->
                val leftText = leftNumber?.let { leftLines.getOrNull(it) } ?: ""
                val rightText = rightNumber?.let { rightLines.getOrNull(it) } ?: ""
                val leftChanges = leftNumber?.let { highlights.left[it] }.orEmpty()
                val rightChanges = rightNumber?.let { highlights.right[it] }.orEmpty()

                val kind = when {
                    leftNumber == null -> RowKind.Inserted
                    rightNumber == null -> RowKind.Removed
                    leftChanges.isNotEmpty() || rightChanges.isNotEmpty() -> RowKind.Modified
                    leftText != rightText -> RowKind.Modified
                    else -> RowKind.Context
                }

                ModelRow(leftNumber, leftText, leftChanges, rightNumber, rightText, rightChanges, kind)
            }
        }

        val chunkRows = diff.chunks.flatten().map { line ->
            val leftNumber = line.lhs?.lineNumber
            val rightNumber = line.rhs?.lineNumber
            val kind = when {
                line.lhs == null -> RowKind.Inserted
                line.rhs == null -> RowKind.Removed
                else -> RowKind.Modified
            }
            ModelRow(
                leftNumber,
                leftNumber?.let { leftLines.getOrNull(it) }.orEmpty(),
                line.lhs?.changes.orEmpty(),
                rightNumber,
                rightNumber?.let { rightLines.getOrNull(it) }.orEmpty(),
                line.rhs?.changes.orEmpty(),
                kind,
            )
        }

        if (chunkRows.isNotEmpty()) {
            return chunkRows
        }

        return buildWholeFileRows(diff, leftLines, rightLines)
    }

    private fun buildWholeFileRows(
        diff: DifftasticFileDiff,
        leftLines: List<String>,
        rightLines: List<String>,
    ): List<ModelRow> {
        val status = diff.status.orEmpty().lowercase()
        val leftHasText = leftLines.any { it.isNotEmpty() }
        val rightHasText = rightLines.any { it.isNotEmpty() }

        if (status in setOf("created", "added") || !leftHasText && rightHasText) {
            return rightLines.mapIndexed { index, line ->
                ModelRow(null, "", emptyList(), index, line, emptyList(), RowKind.Inserted)
            }
        }

        if (status in setOf("deleted", "removed") || !rightHasText && leftHasText) {
            return leftLines.mapIndexed { index, line ->
                ModelRow(index, line, emptyList(), null, "", emptyList(), RowKind.Removed)
            }
        }

        val maxLineCount = max(leftLines.size, rightLines.size)
        return (0 until maxLineCount).map { index ->
            val leftText = leftLines.getOrNull(index).orEmpty()
            val rightText = rightLines.getOrNull(index).orEmpty()
            ModelRow(
                leftLineNumber = index.takeIf { index < leftLines.size },
                leftText = leftText,
                leftChanges = emptyList(),
                rightLineNumber = index.takeIf { index < rightLines.size },
                rightText = rightText,
                rightChanges = emptyList(),
                kind = if (leftText == rightText) RowKind.Context else RowKind.Modified,
            )
        }
    }

    private fun collapseRows(rows: List<ModelRow>, collapseUnchanged: Boolean): List<ModelRow> {
        if (!collapseUnchanged) {
            return rows
        }

        val result = mutableListOf<ModelRow>()
        var index = 0

        while (index < rows.size) {
            val row = rows[index]
            if (row.kind != RowKind.Context) {
                result.add(row)
                index++
                continue
            }

            val start = index
            while (index < rows.size && rows[index].kind == RowKind.Context) {
                index++
            }

            val block = rows.subList(start, index)
            if (block.size <= COLLAPSE_CONTEXT_LINES * 2 + 1) {
                result.addAll(block)
                continue
            }

            result.addAll(block.take(COLLAPSE_CONTEXT_LINES))
            val hidden = block.size - COLLAPSE_CONTEXT_LINES * 2
            val label = "... $hidden unchanged lines ..."
            result.add(
                ModelRow(
                    leftLineNumber = null,
                    leftText = label,
                    leftChanges = emptyList(),
                    rightLineNumber = null,
                    rightText = label,
                    rightChanges = emptyList(),
                    kind = RowKind.Collapsed,
                )
            )
            result.addAll(block.takeLast(COLLAPSE_CONTEXT_LINES))
        }

        return result
    }

    private fun collectHighlights(diff: DifftasticFileDiff): HighlightMaps {
        val left = mutableMapOf<Int, MutableList<DifftasticChange>>()
        val right = mutableMapOf<Int, MutableList<DifftasticChange>>()

        diff.chunks.flatten().forEach { line ->
            line.lhs?.let { side ->
                left.getOrPut(side.lineNumber) { mutableListOf() }.addAll(side.changes)
            }
            line.rhs?.let { side ->
                right.getOrPut(side.lineNumber) { mutableListOf() }.addAll(side.changes)
            }
        }

        return HighlightMaps(left, right)
    }

    private fun byteOffsetToCharIndex(text: String, offset: Int): Int {
        if (offset <= 0) {
            return 0
        }

        var charIndex = 0
        var bytes = 0
        while (charIndex < text.length) {
            val codePoint = text.codePointAt(charIndex)
            val chars = Character.toChars(codePoint)
            val byteLength = String(chars).toByteArray(StandardCharsets.UTF_8).size
            if (bytes + byteLength > offset) {
                return charIndex
            }
            bytes += byteLength
            charIndex += chars.size
        }

        return text.length
    }

    private fun splitLines(text: String): List<String> {
        val lines = mutableListOf<String>()
        var start = 0

        while (true) {
            val next = text.indexOf('\n', start)
            if (next < 0) {
                lines.add(text.substring(start).removeSuffix("\r"))
                return lines
            }

            lines.add(text.substring(start, next).removeSuffix("\r"))
            start = next + 1
        }
    }

    private data class HighlightMaps(
        val left: Map<Int, List<DifftasticChange>>,
        val right: Map<Int, List<DifftasticChange>>,
    )

    private data class ModelRow(
        val leftLineNumber: Int?,
        val leftText: String,
        val leftChanges: List<DifftasticChange>,
        val rightLineNumber: Int?,
        val rightText: String,
        val rightChanges: List<DifftasticChange>,
        val kind: RowKind,
    )

    private enum class RowKind {
        Context,
        Inserted,
        Removed,
        Modified,
        Collapsed,
    }
}

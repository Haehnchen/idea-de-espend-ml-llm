package de.espend.ml.llm.structurediff

import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.simple.SimpleDiffChange
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.Side
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import javax.swing.Icon

class StructuralDiffTool : FrameDiffTool {
    override fun getName(): String = "Semantic"

    override fun getIcon(): Icon = AllIcons.Actions.GroupByClass

    override fun canShow(context: DiffContext, request: DiffRequest): Boolean {
        if (request !is ContentDiffRequest || request.contents.size != 2) return false
        val fileName = request.preferredFileName()
        return StructuralDiffSettings.instance.toolFor(fileName) != null &&
            SimpleDiffViewer.canShowRequest(context, request)
    }

    override fun createComponent(context: DiffContext, request: DiffRequest): FrameDiffTool.DiffViewer {
        val contentRequest = request as ContentDiffRequest
        val computer = StructuralDiffComputer(context.project, contentRequest.preferredFileName())
        contentRequest.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, computer)
        contentRequest.putUserData(StructuralDiffComputer.KEY, computer)
        return StructuralDiffViewer(context, contentRequest, computer)
    }
}

private class StructuralDiffViewer(
    context: DiffContext,
    request: ContentDiffRequest,
    private val computer: StructuralDiffComputer,
) : SimpleDiffViewer(context, request) {
    private val decorations = mutableListOf<RangeHighlighter>()

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(StructuralDiffSettingsListener.TOPIC, object : StructuralDiffSettingsListener {
                override fun settingsChanged() {
                    ApplicationManager.getApplication().invokeLater {
                        if (!isDisposed) scheduleRediff()
                    }
                }
            })
    }

    override fun apply(changes: MutableList<out SimpleDiffChange>?, isContentsEqual: Boolean): Runnable {
        val applyDefaultPresentation = super.apply(changes, isContentsEqual)
        return Runnable {
            applyDefaultPresentation.run()
            drawStructure()
        }
    }

    override fun clearDiffPresentation() {
        clearDecorations()
        super.clearDiffPresentation()
    }

    override fun onDispose() {
        clearDecorations()
        request.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, null)
        request.putUserData(StructuralDiffComputer.KEY, null)
        super.onDispose()
    }

    private fun drawStructure() {
        clearDecorations()
        if (!StructuralDiffSettings.instance.decorateStructure) return
        val analysis = computer.latestChange() ?: return

        analysis.tokens.forEach { token -> drawToken(token) }
        analysis.regions.forEach { region -> drawRegion(region) }
    }

    private fun drawToken(token: StructuralTokenMark) {
        val editor = editor(token.side)
        val document = editor.document
        if (token.line !in 0 until document.lineCount) return
        val lineText = document.getText(
            com.intellij.openapi.util.TextRange(
                document.getLineStartOffset(token.line),
                document.getLineEndOffset(token.line),
            ),
        )
        val startInLine = byteOffset(lineText, token.startByte) ?: return
        val endInLine = byteOffset(lineText, token.endByte) ?: return
        if (startInLine >= endInLine) return

        val color = tokenColor(token.style)
        val attributes = TextAttributes(null, null, color, com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE, Font.PLAIN)
        val lineStart = document.getLineStartOffset(token.line)
        decorations += editor.markupModel.addRangeHighlighter(
            lineStart + startInLine,
            lineStart + endInLine,
            HighlighterLayer.SELECTION - 1,
            attributes,
            HighlighterTargetArea.EXACT_RANGE,
        )
    }

    private fun drawRegion(region: StructuralRegionMark) {
        val editor = editor(region.side)
        val document = editor.document
        if (region.startLine !in 0 until document.lineCount || region.endLine <= region.startLine) return
        val lastLine = (region.endLine - 1).coerceAtMost(document.lineCount - 1)
        val attributes = TextAttributes(
            null,
            null,
            JBColor(Color(0x6A, 0x87, 0xD5), Color(0x8A, 0xA8, 0xFF)),
            com.intellij.openapi.editor.markup.EffectType.ROUNDED_BOX,
            Font.PLAIN,
        )
        val highlighter = editor.markupModel.addRangeHighlighter(
            document.getLineStartOffset(region.startLine),
            document.getLineEndOffset(lastLine),
            HighlighterLayer.SELECTION - 2,
            attributes,
            HighlighterTargetArea.EXACT_RANGE,
        )
        highlighter.errorStripeTooltip = buildString {
            append(region.description)
            region.counterpartLine?.let { append("; counterpart at line ${it + 1}") }
        }
        decorations += highlighter
    }

    private fun editor(side: StructuralDiffSide): EditorEx =
        if (side == StructuralDiffSide.LEFT) getEditor(Side.LEFT) else getEditor(Side.RIGHT)

    private fun clearDecorations() {
        decorations.forEach(RangeHighlighter::dispose)
        decorations.clear()
    }

    private fun byteOffset(text: String, requested: Int): Int? {
        if (requested < 0) return null
        var bytes = 0
        var chars = 0
        while (chars < text.length) {
            if (bytes == requested) return chars
            val codePoint = text.codePointAt(chars)
            bytes += String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            chars += Character.charCount(codePoint)
            if (bytes > requested) return null
        }
        return chars.takeIf { bytes == requested }
    }

    private fun tokenColor(style: String): Color = when (style.lowercase()) {
        "keyword" -> JBColor(Color(0x00, 0x00, 0x80), Color(0xCC, 0x78, 0x32))
        "string" -> JBColor(Color(0x00, 0x80, 0x00), Color(0x6A, 0x87, 0x59))
        "comment" -> JBColor(Color(0x80, 0x80, 0x80), Color(0x80, 0x80, 0x80))
        "type" -> JBColor(Color(0x20, 0x70, 0x90), Color(0x68, 0x97, 0xBB))
        "tree_sitter_error" -> JBColor.RED
        else -> JBColor(Color(0x70, 0x40, 0x90), Color(0xBB, 0x80, 0xD0))
    }
}

private fun ContentDiffRequest.preferredFileName(): String {
    contents.firstNotNullOfOrNull { content ->
        when (content) {
            is DocumentContent -> content.highlightFile?.name
            is FileContent -> content.file.name
            else -> null
        }
    }?.let { return it }
    return contentTitles.firstOrNull { !it.isNullOrBlank() }
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?: "content.txt"
}

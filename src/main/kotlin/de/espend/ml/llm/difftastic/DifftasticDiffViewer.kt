package de.espend.ml.llm.difftastic

import com.intellij.icons.AllIcons
import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.EmptyContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants

class DifftasticDiffViewer(
    private val context: DiffContext,
    private val request: ContentDiffRequest,
) : FrameDiffTool.DiffViewer {
    private val disposed = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val statusLabel = JLabel("Difftastic", SwingConstants.LEFT)
    private val contentPanel = JPanel(BorderLayout())
    private val panel = JPanel(BorderLayout()).apply {
        add(contentPanel, BorderLayout.CENTER)
    }
    private val activeEditors = mutableListOf<Editor>()
    private var latestSuccess: DifftasticRenderedSuccess? = null
    private var collapseUnchanged = false
    private var synchronizeScrolling = true
    private var unifiedViewer = false

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = activeEditors.firstOrNull()?.component ?: panel

    override fun init(): FrameDiffTool.ToolbarComponents {
        DifftasticDiffToolOrder.ensureDifftasticLastForKnownPlaces()
        renderAsync()

        return FrameDiffTool.ToolbarComponents().also {
            val refreshAction: AnAction = object : AnAction("Refresh Difftastic") {
                override fun actionPerformed(e: AnActionEvent) {
                    renderAsync()
                }
            }
            val editAction: AnAction = object : AnAction("Edit Source", "Open source editor", AllIcons.Actions.EditSource) {
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = false
                }

                override fun actionPerformed(e: AnActionEvent) = Unit
            }
            val collapseAction: AnAction = object : ToggleAction(
                "Collapse Unchanged Fragments",
                "Collapse unchanged fragments",
                AllIcons.Actions.Collapseall,
            ) {
                override fun isSelected(e: AnActionEvent): Boolean = collapseUnchanged

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    collapseUnchanged = state
                    latestSuccess?.let { showSuccess(it) }
                }
            }
            val scrollAction: AnAction = object : ToggleAction(
                "Synchronize Scrolling",
                "Synchronize scrolling",
                AllIcons.Actions.SynchronizeScrolling,
            ) {
                override fun isSelected(e: AnActionEvent): Boolean = synchronizeScrolling

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    synchronizeScrolling = state
                    latestSuccess?.let { showSuccess(it) }
                }
            }
            val unifiedAction: AnAction = object : ToggleAction(
                "Unified Viewer",
                "Use unified viewer",
                AllIcons.Actions.ChangeView,
            ) {
                override fun isSelected(e: AnActionEvent): Boolean = unifiedViewer

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    unifiedViewer = state
                    latestSuccess?.let { showSuccess(it) }
                }
            }
            val actions = listOf(editAction, refreshAction, collapseAction, scrollAction, unifiedAction)
            it.toolbarActions = actions
            it.popupActions = actions
            it.statusPanel = statusLabel
        }
    }

    override fun dispose() {
        disposed.set(true)
        clearContent()
    }

    private fun renderAsync() {
        val runId = generation.incrementAndGet()
        latestSuccess = null
        setStatus("Running Difftastic...")
        showMessage("Running Difftastic...", "")

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = try {
                val sources = ReadAction.computeCancellable<DifftasticPreparedSources, RuntimeException> { extractSources(request) }
                DifftasticViewerRunResult(
                    runResult = DifftasticRunner.run(sources.left.source, sources.right.source),
                    leftFileType = sources.left.fileType,
                    rightFileType = sources.right.fileType,
                )
            } catch (e: Exception) {
                DifftasticViewerRunResult(DifftasticRunResult.Failure("Could not prepare diff contents: ${e.message}"))
            }

            ApplicationManager.getApplication().invokeLater({
                if (!disposed.get() && runId == generation.get()) {
                    applyResult(result)
                    DifftasticDiffToolOrder.ensureDifftasticLastForKnownPlaces()
                }
            }, ModalityState.any())
        }
    }

    private fun applyResult(result: DifftasticViewerRunResult) {
        when (val runResult = result.runResult) {
            is DifftasticRunResult.Success -> {
                latestSuccess = DifftasticRenderedSuccess(runResult, result.leftFileType, result.rightFileType)
                setStatus("Difftastic")
                showSuccess(latestSuccess!!)
            }

            is DifftasticRunResult.Unavailable -> {
                latestSuccess = null
                setStatus("Difftastic unavailable")
                showMessage("Difftastic unavailable", runResult.message)
            }

            is DifftasticRunResult.Failure -> {
                latestSuccess = null
                setStatus("Difftastic failed")
                showMessage(runResult.message, runResult.output)
            }
        }
    }

    private fun setStatus(text: String) {
        statusLabel.text = text
    }

    private fun showSuccess(result: DifftasticRenderedSuccess) {
        val runResult = result.runResult
        val input = DifftasticEditorInput(
            leftTitle = runResult.left.title,
            leftText = runResult.left.text,
            rightTitle = runResult.right.title,
            rightText = runResult.right.text,
            diff = runResult.diff,
        )
        val fileType = fileTypeFor(result.rightFileType, result.leftFileType, input.rightTitle, input.leftTitle)

        if (unifiedViewer) {
            showUnifiedDiff(input, fileType)
        } else {
            showEditorDiff(DifftasticEditorModelBuilder.build(input, collapseUnchanged), fileType)
        }
    }

    private fun showEditorDiff(model: DifftasticEditorModel, fileType: FileType) {
        clearContent()

        val leftEditor = createDiffEditor(model.leftTitle, model.left, fileType)
        val rightEditor = createDiffEditor(model.rightTitle, model.right, fileType)
        activeEditors.add(leftEditor)
        activeEditors.add(rightEditor)

        if (synchronizeScrolling) {
            installScrollSync(leftEditor, rightEditor)
        }

        val splitPane = OnePixelSplitter(false, 0.5f).apply {
            firstComponent = leftEditor.component
            secondComponent = rightEditor.component
            dividerWidth = JBUI.scale(1)
        }

        contentPanel.add(splitPane, BorderLayout.CENTER)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun showUnifiedDiff(input: DifftasticEditorInput, fileType: FileType) {
        clearContent()

        val model = DifftasticEditorModelBuilder.buildUnified(input, collapseUnchanged)
        val editor = createUnifiedEditor(model, fileType)
        activeEditors.add(editor)

        contentPanel.add(editor.component, BorderLayout.CENTER)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun createDiffEditor(
        title: String,
        side: DifftasticEditorSide,
        fileType: FileType,
    ): Editor {
        val factory = EditorFactory.getInstance()
        val document = factory.createDocument(side.text)
        val editor = context.project
            ?.let { factory.createEditor(document, it, fileType, true) }
            ?: factory.createViewer(document)

        configureEditor(editor, title, fileType)
        applyHighlights(editor, side)
        editor.caretModel.moveToOffset(0)
        return editor
    }

    private fun createUnifiedEditor(model: DifftasticUnifiedModel, fileType: FileType): Editor {
        val factory = EditorFactory.getInstance()
        val document = factory.createDocument(model.text)
        val editor = context.project
            ?.let { factory.createEditor(document, it, fileType, true) }
            ?: factory.createViewer(document)

        configureEditor(editor, model.title, fileType)
        applyHighlights(editor, model)
        editor.caretModel.moveToOffset(0)
        return editor
    }

    private fun configureEditor(editor: Editor, title: String, fileType: FileType) {
        editor.settings.isLineNumbersShown = true
        editor.settings.isFoldingOutlineShown = false
        editor.settings.isLineMarkerAreaShown = false
        editor.settings.isIndentGuidesShown = true
        editor.settings.isCaretRowShown = false
        editor.settings.isRightMarginShown = false
        editor.settings.isUseSoftWraps = false

        (editor as? EditorEx)?.apply {
            setViewer(true)
            setVerticalScrollbarVisible(true)
            setHorizontalScrollbarVisible(true)
            val scheme = EditorColorsManager.getInstance().globalScheme
            setColorsScheme(scheme)
            setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, scheme, context.project))
            setPermanentHeaderComponent(editorHeader(title))
        }
    }

    private fun editorHeader(title: String): JComponent {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 8)
            background = UIUtil.getPanelBackground()
            add(JBLabel(title).apply { font = font.deriveFont(Font.BOLD) }, BorderLayout.WEST)
        }
    }

    private fun applyHighlights(editor: Editor, model: DifftasticUnifiedModel) {
        model.lineMarkers.forEach { marker ->
            addCoreLineHighlighter(editor, marker)
        }
        model.tokenMarkers.forEach { marker ->
            addCoreInlineHighlighter(editor, marker)
        }
    }

    private fun applyHighlights(editor: Editor, side: DifftasticEditorSide) {
        side.lineMarkers.forEach { marker ->
            addCoreLineHighlighter(editor, marker)
        }
        side.tokenMarkers.forEach { marker ->
            addCoreInlineHighlighter(editor, marker)
        }
    }

    private fun addCoreLineHighlighter(editor: Editor, marker: DifftasticLineMarker) {
        val type = textDiffType(marker.kind) ?: return
        DiffDrawUtil.createHighlighter(editor, marker.line, marker.line + 1, type, false)
    }

    private fun addCoreInlineHighlighter(editor: Editor, marker: DifftasticTokenMarker) {
        val type = textDiffType(marker.kind) ?: return
        if (marker.endOffset > marker.startOffset) {
            DiffDrawUtil.createInlineHighlighter(editor, marker.startOffset, marker.endOffset, type)
        }
    }

    private fun textDiffType(kind: DifftasticLineKind): TextDiffType? {
        return when (kind) {
            DifftasticLineKind.Inserted -> TextDiffType.INSERTED
            DifftasticLineKind.Removed -> TextDiffType.DELETED
            DifftasticLineKind.Modified -> TextDiffType.MODIFIED
            DifftasticLineKind.Collapsed -> null
        }
    }

    private fun installScrollSync(leftEditor: Editor, rightEditor: Editor) {
        var syncing = false

        leftEditor.scrollingModel.addVisibleAreaListener { event ->
            if (!syncing) {
                syncing = true
                rightEditor.scrollingModel.scrollVertically(event.newRectangle.y)
                syncing = false
            }
        }

        rightEditor.scrollingModel.addVisibleAreaListener { event ->
            if (!syncing) {
                syncing = true
                leftEditor.scrollingModel.scrollVertically(event.newRectangle.y)
                syncing = false
            }
        }
    }

    private fun showMessage(title: String, details: String) {
        clearContent()

        val messagePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(JBLabel(title).apply { font = font.deriveFont(Font.BOLD) }, BorderLayout.NORTH)
            if (details.isNotBlank()) {
                add(
                    JTextArea(details.take(6000)).apply {
                        isEditable = false
                        lineWrap = true
                        wrapStyleWord = true
                        background = UIUtil.getPanelBackground()
                    },
                    BorderLayout.CENTER,
                )
            }
        }

        contentPanel.add(messagePanel, BorderLayout.CENTER)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun clearContent() {
        contentPanel.removeAll()
        if (activeEditors.isNotEmpty()) {
            val factory = EditorFactory.getInstance()
            activeEditors.forEach { editor -> factory.releaseEditor(editor) }
            activeEditors.clear()
        }
    }

    private fun fileTypeFor(primaryType: FileType?, fallbackType: FileType?, primaryTitle: String, fallbackTitle: String): FileType {
        val candidates = listOf(primaryType, fallbackType)
        candidates.firstOrNull { it.isSpecificFileType() }?.let { return it }

        val titleCandidates = listOf(fileTypeForTitle(primaryTitle), fileTypeForTitle(fallbackTitle))
        titleCandidates.firstOrNull { it.isSpecificFileType() }?.let { return it }
        candidates.firstOrNull { it.isKnownFileType() }?.let { return it }
        titleCandidates.firstOrNull { it.isKnownFileType() }?.let { return it }

        return PlainTextFileType.INSTANCE
    }

    private fun FileType?.isSpecificFileType(): Boolean {
        return isKnownFileType() && this != PlainTextFileType.INSTANCE
    }

    private fun FileType?.isKnownFileType(): Boolean {
        return this != null && this !is UnknownFileType
    }

    private fun fileTypeForTitle(title: String): FileType {
        val fileName = title.substringAfterLast('/').substringAfterLast('\\')
        return FileTypeManager.getInstance().getFileTypeByFileName(fileName)
    }

    private fun extractSources(request: ContentDiffRequest): DifftasticPreparedSources {
        val contents = request.contents
        val titles = request.contentTitles

        val left = extractSource(contents[0], titles.getOrNull(0), "left")
        val right = extractSource(contents[1], titles.getOrNull(1), "right")

        return DifftasticPreparedSources(left, right)
    }

    private fun extractSource(content: DiffContent, requestTitle: String?, fallbackTitle: String): DifftasticPreparedSource {
        val contentTitle = defaultTitle(content, fallbackTitle)
        val title = requestTitle
            ?.takeIf { it.isNotBlank() && fileTypeForTitle(it).isSpecificFileType() }
            ?: contentTitle
        val fileType = contentFileType(content)

        val text = when (content) {
            is DocumentContent -> content.document.text
            is FileContent -> VfsUtilCore.loadText(content.file)
            is EmptyContent -> ""
            else -> error("Unsupported diff content: ${content::class.java.name}")
        }

        return DifftasticPreparedSource(DifftasticSource(title, text), fileType)
    }

    private fun defaultTitle(content: DiffContent, fallbackTitle: String): String {
        return when (content) {
            is FileContent -> content.file.name
            is DocumentContent -> content.highlightFile?.name ?: typedFallbackTitle(content.contentType, fallbackTitle)
            else -> typedFallbackTitle(contentFileType(content), fallbackTitle)
        }
    }

    private fun typedFallbackTitle(fileType: FileType?, fallbackTitle: String): String {
        if (fileType == null || fallbackTitle.substringAfterLast('.', "") != "") {
            return fallbackTitle
        }

        val extension = fileType.defaultExtension.takeIf { it.isNotBlank() } ?: return fallbackTitle
        return "$fallbackTitle.$extension"
    }

    private fun contentFileType(content: DiffContent): FileType? {
        return when (content) {
            is EmptyContent -> null
            is FileContent -> content.file.fileType
            is DocumentContent -> content.highlightFile?.fileType ?: content.contentType
            else -> content.contentType
        }.takeIf { it.isKnownFileType() }
    }

    private data class DifftasticPreparedSources(
        val left: DifftasticPreparedSource,
        val right: DifftasticPreparedSource,
    )

    private data class DifftasticPreparedSource(
        val source: DifftasticSource,
        val fileType: FileType?,
    )

    private data class DifftasticViewerRunResult(
        val runResult: DifftasticRunResult,
        val leftFileType: FileType? = null,
        val rightFileType: FileType? = null,
    )

    private data class DifftasticRenderedSuccess(
        val runResult: DifftasticRunResult.Success,
        val leftFileType: FileType?,
        val rightFileType: FileType?,
    )
}

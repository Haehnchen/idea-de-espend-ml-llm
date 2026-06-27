package de.espend.ml.llm.difftastic

import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.EmptyContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import de.espend.ml.llm.CommandPathUtils
import com.intellij.icons.AllIcons
import de.espend.ml.llm.structurediff.StructuralDiffSettings
import javax.swing.Icon

class DifftasticDiffTool : FrameDiffTool {
    override fun getName(): String = "Difftastic Preview"

    override fun getIcon(): Icon = AllIcons.Actions.Preview

    override fun canShow(context: DiffContext, request: DiffRequest): Boolean {
        if (request !is ContentDiffRequest) {
            return false
        }

        val contents = request.contents
        return (StructuralDiffSettings.instance.executableFor("difftastic") != null ||
            CommandPathUtils.findDifftasticPath() != null) &&
            contents.size == 2 &&
            contents.all(::isSupportedContent)
    }

    override fun createComponent(context: DiffContext, request: DiffRequest): FrameDiffTool.DiffViewer {
        return DifftasticDiffViewer(context, request as ContentDiffRequest)
    }

    private fun isSupportedContent(content: DiffContent): Boolean {
        if (content.contentType?.isBinary == true) {
            return false
        }

        return content is DocumentContent || content is FileContent || content is EmptyContent
    }
}

package de.espend.ml.llm.structurediff

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal typealias StructuralAnalysisRunner = (
    StructuralDiffToolConfig,
    String,
    String,
    String,
    ProgressIndicator,
) -> StructuralDiffAnalysis

internal class StructuralDiffComputer(
    private val project: Project?,
    private val fileName: String,
    private val configProvider: (String) -> StructuralDiffToolConfig? = { StructuralDiffSettings.instance.toolFor(it) },
    private val runner: StructuralAnalysisRunner = { config, left, right, name, indicator ->
        StructuralDiffEngine.analyze(config, left, right, name, indicator)
    },
) : DiffUserDataKeysEx.DiffComputer {

    private val latest = AtomicReference<StructuralDiffAnalysis.Changed?>()

    internal fun latestChange(): StructuralDiffAnalysis.Changed? = latest.get()

    override fun compute(
        text1: CharSequence,
        text2: CharSequence,
        policy: ComparisonPolicy,
        innerChanges: Boolean,
        indicator: ProgressIndicator,
    ): List<LineFragment> {
        latest.set(null)
        val config = configProvider(fileName)
            ?: return compareNormally(text1, text2, policy, innerChanges, indicator)
        indicator.checkCanceled()

        val analysis = try {
            runner(config, text1.toString(), text2.toString(), fileName, indicator)
        } catch (cancelled: ProcessCanceledException) {
            throw cancelled
        } catch (exception: Exception) {
            LOG.warn("Structural diff failed", exception)
            StructuralDiffAnalysis.Failed(exception.message ?: exception.toString())
        }

        return when (analysis) {
            StructuralDiffAnalysis.Equal -> emptyList()
            is StructuralDiffAnalysis.Failed -> {
                StructuralDiffNotifications.fallback(project, config.name.orEmpty(), analysis.reason)
                compareNormally(text1, text2, policy, innerChanges, indicator)
            }
            is StructuralDiffAnalysis.Changed -> {
                val validationError = validateLines(analysis, text1, text2)
                if (validationError != null) {
                    StructuralDiffNotifications.fallback(project, config.name.orEmpty(), validationError)
                    compareNormally(text1, text2, policy, innerChanges, indicator)
                } else {
                    latest.set(analysis)
                    compareNormally(text1, text2, policy, innerChanges, indicator)
                        .filter { fragment -> fragment.touches(analysis.leftLines, analysis.rightLines) }
                }
            }
        }
    }

    private fun compareNormally(
        left: CharSequence,
        right: CharSequence,
        policy: ComparisonPolicy,
        innerChanges: Boolean,
        indicator: ProgressIndicator,
    ): List<LineFragment> = ComparisonManager.getInstance().let { comparison ->
        if (innerChanges) comparison.compareLinesInner(left, right, policy, indicator)
        else comparison.compareLines(left, right, policy, indicator)
    }

    private fun validateLines(
        analysis: StructuralDiffAnalysis.Changed,
        left: CharSequence,
        right: CharSequence,
    ): String? {
        val leftCount = documentLineCount(left)
        val rightCount = documentLineCount(right)
        analysis.leftLines.firstOrNull { it !in 0 until leftCount }?.let {
            return "Tool returned left line $it for a $leftCount-line document"
        }
        analysis.rightLines.firstOrNull { it !in 0 until rightCount }?.let {
            return "Tool returned right line $it for a $rightCount-line document"
        }
        return null
    }

    private fun documentLineCount(text: CharSequence): Int = text.count { it == '\n' } + 1

    private fun LineFragment.touches(left: Set<Int>, right: Set<Int>): Boolean {
        val leftHit = left.any { it >= startLine1 && it < endLine1 }
        val rightHit = right.any { it >= startLine2 && it < endLine2 }
        return leftHit || rightHit
    }

    companion object {
        private val LOG = Logger.getInstance(StructuralDiffComputer::class.java)
        val KEY: Key<StructuralDiffComputer> = Key.create("de.espend.ml.llm.structurediff.computer")
    }
}

private object StructuralDiffNotifications {
    private val shown = ConcurrentHashMap.newKeySet<String>()

    fun fallback(project: Project?, toolName: String, reason: String) {
        val key = "$toolName:$reason"
        if (!shown.add(key)) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Structural Diff")
            .createNotification(
                "Semantic diff fell back to the built-in comparison",
                listOf(toolName.ifBlank { "External diff tool" }, reason).joinToString(": "),
                NotificationType.WARNING,
            )
            .notify(project)
    }
}

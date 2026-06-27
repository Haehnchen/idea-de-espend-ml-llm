package de.espend.ml.llm.difftastic

import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.tools.binary.BinaryDiffTool
import com.intellij.diff.tools.dir.DirDiffTool
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.util.DiffPlaces

object DifftasticDiffToolOrder {
    internal val difftasticClassName: String = DifftasticDiffTool::class.java.canonicalName
    internal val defaultBuiltInOrder: List<String> = listOf(
        SimpleDiffTool::class.java.canonicalName,
        UnifiedDiffTool::class.java.canonicalName,
        BinaryDiffTool::class.java.canonicalName,
        DirDiffTool::class.java.canonicalName,
    )

    private val knownPlaces: List<String?> = listOf(
        null,
        DiffPlaces.DEFAULT,
        DiffPlaces.CHANGES_VIEW,
        DiffPlaces.VCS_LOG_VIEW,
        DiffPlaces.VCS_FILE_HISTORY_VIEW,
        DiffPlaces.SHELVE_VIEW,
        DiffPlaces.COMMIT_DIALOG,
        DiffPlaces.TESTS_FAILED_ASSERTIONS,
        DiffPlaces.EXTERNAL,
        DiffPlaces.BLANK,
    )

    fun ensureDifftasticLastForKnownPlaces() {
        knownPlaces.forEach { place ->
            ensureDifftasticLast(place)
        }
    }

    fun ensureDifftasticLast(place: String?) {
        val settings = DiffSettingsHolder.DiffSettings.getSettings(place)
        val normalized = normalizeOrder(settings.diffToolsOrder)
        if (normalized != settings.diffToolsOrder) {
            settings.diffToolsOrder = normalized
        }
    }

    internal fun normalizeOrder(current: List<String>): List<String> {
        val withoutDifftastic = current.filterNot { it == difftasticClassName }
        val base = if (withoutDifftastic.isEmpty()) {
            defaultBuiltInOrder
        } else {
            withoutDifftastic
        }

        return (base + defaultBuiltInOrder)
            .filter { it.isNotBlank() }
            .distinct()
            .plus(difftasticClassName)
    }
}

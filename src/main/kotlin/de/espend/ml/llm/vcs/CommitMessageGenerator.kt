package de.espend.ml.llm.vcs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import de.espend.ml.llm.profile.AiProfileConfig

private val LOG = Logger.getInstance(CommitMessageGenerator::class.java)

/**
 * Generates commit messages using AI providers.
 */
object CommitMessageGenerator {

    /**
     * Maximum diff size to send (characters) - prevents oversized requests
     */
    private const val MAX_DIFF_SIZE = 50000

    /**
     * Generates a commit message for the given changes.
     */
    suspend fun generate(
        project: Project,
        config: AiProfileConfig,
        changes: List<Change>,
        existingText: String,
        indicator: ProgressIndicator? = null
    ): ApiResult {
        if (changes.isEmpty()) {
            return ApiResult.Error("No changes to generate commit message from")
        }

        // Check for cancellation
        if (indicator?.isCanceled == true) {
            return ApiResult.Error("Cancelled")
        }

        // Build diff from changes
        val diff = buildDiff(changes)
        if (diff.isBlank()) {
            return ApiResult.Error("Could not build diff from changes")
        }

        // Check for cancellation
        if (indicator?.isCanceled == true) {
            return ApiResult.Error("Cancelled")
        }

        // Create prompt for commit message generation
        val prompt = buildPrompt(diff, existingText)

        // Call API adapter based on the configured profile API type
        return CommitMessageApiClient.sendRequest(config, prompt)
    }

    /**
     * Builds a unified diff string from the list of changes.
     */
    private fun buildDiff(changes: List<Change>): String {
        val diffBuilder = StringBuilder()

        for (change in changes) {
            try {
                val diffText = buildChangeDiff(change)
                if (diffText.isNotEmpty()) {
                    diffBuilder.append(diffText).append("\n")
                }
            } catch (e: Exception) {
                LOG.warn("Failed to build diff for change: ${change.beforeRevision?.file?.path}", e)
            }
        }

        val result = diffBuilder.toString()
        // Truncate if too large
        return if (result.length > MAX_DIFF_SIZE) {
            result.substring(0, MAX_DIFF_SIZE) + "\n... (diff truncated)"
        } else {
            result
        }
    }

    /**
     * Determines the type of change based on before/after revisions.
     */
    private fun getChangeType(change: Change): ChangeType {
        val hasBefore = change.beforeRevision != null
        val hasAfter = change.afterRevision != null
        return when {
            !hasBefore && hasAfter -> ChangeType.ADDITION
            hasBefore && !hasAfter -> ChangeType.DELETION
            else -> ChangeType.MODIFICATION
        }
    }

    /**
     * Builds a diff string for a single change.
     */
    private fun buildChangeDiff(change: Change): String {
        val beforeFile = change.beforeRevision?.file
        val afterFile = change.afterRevision?.file
        val changeType = getChangeType(change)

        val fileName = when (changeType) {
            ChangeType.ADDITION -> afterFile?.name ?: "new file"
            ChangeType.DELETION -> beforeFile?.name ?: "deleted file"
            ChangeType.MODIFICATION -> afterFile?.name ?: beforeFile?.name ?: "unknown"
        }

        val header = when (changeType) {
            ChangeType.ADDITION -> "+++ b/$fileName (ADDED)"
            ChangeType.DELETION -> "--- a/$fileName (DELETED)"
            ChangeType.MODIFICATION -> "--- a/$fileName\n+++ b/$fileName"
        }

        val contentBuilder = StringBuilder()
        contentBuilder.append("diff --git a/$fileName b/$fileName\n")
        contentBuilder.append("$header\n")

        when (changeType) {
            ChangeType.ADDITION -> {
                val content = change.afterRevision?.content ?: ""
                content.lines().forEach { line ->
                    contentBuilder.append("+$line\n")
                }
            }
            ChangeType.DELETION -> {
                val content = change.beforeRevision?.content ?: ""
                content.lines().forEach { line ->
                    contentBuilder.append("-$line\n")
                }
            }
            ChangeType.MODIFICATION -> {
                val beforeContent = change.beforeRevision?.content ?: ""
                val afterContent = change.afterRevision?.content ?: ""
                val diff = computeSimpleDiff(beforeContent.lines(), afterContent.lines())
                contentBuilder.append(diff)
            }
        }

        return contentBuilder.toString()
    }

    private enum class ChangeType {
        ADDITION, DELETION, MODIFICATION
    }

    private fun computeSimpleDiff(beforeLines: List<String>, afterLines: List<String>): String {
        val result = StringBuilder()
        val lcs = computeLCS(beforeLines, afterLines)

        var beforeIdx = 0
        var afterIdx = 0
        var lcsIdx = 0

        while (lcsIdx < lcs.size || beforeIdx < beforeLines.size || afterIdx < afterLines.size) {
            if (lcsIdx < lcs.size) {
                val lcsLine = lcs[lcsIdx]

                while (beforeIdx < beforeLines.size && beforeLines[beforeIdx] != lcsLine) {
                    result.append("-${beforeLines[beforeIdx]}\n")
                    beforeIdx++
                }

                while (afterIdx < afterLines.size && afterLines[afterIdx] != lcsLine) {
                    result.append("+${afterLines[afterIdx]}\n")
                    afterIdx++
                }

                if (beforeIdx < beforeLines.size && afterIdx < afterLines.size) {
                    result.append(" ${beforeLines[beforeIdx]}\n")
                    beforeIdx++
                    afterIdx++
                    lcsIdx++
                }
            } else {
                while (beforeIdx < beforeLines.size) {
                    result.append("-${beforeLines[beforeIdx]}\n")
                    beforeIdx++
                }
                while (afterIdx < afterLines.size) {
                    result.append("+${afterLines[afterIdx]}\n")
                    afterIdx++
                }
            }
        }

        return result.toString()
    }

    private fun computeLCS(a: List<String>, b: List<String>): List<String> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                if (a[i - 1] == b[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        val lcs = mutableListOf<String>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            if (a[i - 1] == b[j - 1]) {
                lcs.add(0, a[i - 1])
                i--
                j--
            } else if (dp[i - 1][j] > dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }

        return lcs
    }

    private fun buildPrompt(diff: String, existingText: String): String {
        val existingPrompt = if (existingText.isNotBlank()) {
            "\nCurrent draft (improve it):\n$existingText\n"
        } else {
            ""
        }

        return """
            Write a git commit message for this diff.$existingPrompt

            ```
            $diff
            ```

            Format:
            - One line summary (max 72 chars)
            - Use imperative mood
            - No body unless complex changes
            - Output only the message
        """.trimIndent()
    }
}

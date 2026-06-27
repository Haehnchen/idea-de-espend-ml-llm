package de.espend.ml.llm.structurediff

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.execution.ParametersListUtil
import de.espend.ml.llm.difftastic.DifftasticJsonParser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets

enum class StructuralDiffEngineKind(
    val id: String,
    val displayName: String,
    val executableNames: List<String>,
    val defaultArguments: String,
    val defaultEnvironment: Map<String, String>,
    val installUrl: String,
) {
    DIFFTASTIC(
        id = "difftastic",
        displayName = "Difftastic",
        executableNames = listOf("difft", "difftastic"),
        defaultArguments = "--display=json --color=never %1 %2",
        defaultEnvironment = mapOf("DFT_UNSTABLE" to "yes"),
        installUrl = "https://github.com/Wilfred/difftastic",
    ),
    DIFFSITTER(
        id = "diffsitter",
        displayName = "Diffsitter",
        executableNames = listOf("diffsitter"),
        defaultArguments = "-r json --color off %1 %2",
        defaultEnvironment = emptyMap(),
        installUrl = "https://github.com/afnanenayet/diffsitter",
    ),
    SEM(
        id = "sem",
        displayName = "Sem",
        executableNames = listOf("sem"),
        defaultArguments = "diff %1 %2 --format json",
        defaultEnvironment = emptyMap(),
        installUrl = "https://github.com/Ataraxy-Labs/sem",
    );

    fun recognizesVersion(output: String): Boolean {
        val first = output.lineSequence().firstOrNull()?.trim().orEmpty()
        return when (this) {
            DIFFTASTIC -> first.startsWith("Difftastic", ignoreCase = true)
            DIFFSITTER -> first.startsWith("diffsitter", ignoreCase = true)
            SEM -> Regex("(?i)^sem(?:\\s|v|$)").containsMatchIn(first)
        }
    }

    companion object {
        fun fromId(id: String?): StructuralDiffEngineKind? = entries.firstOrNull { it.id == id }
    }
}

internal enum class StructuralDiffSide { LEFT, RIGHT }

internal data class StructuralTokenMark(
    val side: StructuralDiffSide,
    val line: Int,
    val startByte: Int,
    val endByte: Int,
    val style: String,
)

internal data class StructuralRegionMark(
    val side: StructuralDiffSide,
    val startLine: Int,
    val endLine: Int,
    val description: String,
    val counterpartLine: Int? = null,
)

internal sealed interface StructuralDiffAnalysis {
    data object Equal : StructuralDiffAnalysis

    data class Changed(
        val leftLines: Set<Int>,
        val rightLines: Set<Int>,
        val tokens: List<StructuralTokenMark> = emptyList(),
        val regions: List<StructuralRegionMark> = emptyList(),
    ) : StructuralDiffAnalysis

    data class Failed(val reason: String) : StructuralDiffAnalysis
}

internal object StructuralDiffEngine {
    private val json = Json { ignoreUnknownKeys = true }

    fun analyze(
        config: StructuralDiffToolConfig,
        leftText: String,
        rightText: String,
        fileName: String,
        indicator: ProgressIndicator?,
    ): StructuralDiffAnalysis {
        val kind = StructuralDiffEngineKind.fromId(config.engineId)
            ?: return StructuralDiffAnalysis.Failed("Unknown diff engine '${config.engineId}'")
        val executable = config.executable?.takeIf(String::isNotBlank)
            ?: return StructuralDiffAnalysis.Failed("No executable configured for ${kind.displayName}")
        val tempDirectory = FileUtil.createTempDirectory("idea-structural-diff-", null)

        return try {
            val safeName = safeFileName(fileName)
            val leftFile = File(tempDirectory, "left-$safeName").apply { writeText(leftText, StandardCharsets.UTF_8) }
            val rightFile = File(tempDirectory, "right-$safeName").apply { writeText(rightText, StandardCharsets.UTF_8) }
            val template = config.arguments?.takeIf(String::isNotBlank) ?: kind.defaultArguments
            val arguments = ParametersListUtil.parse(template).map { argument ->
                argument.replace("%1", leftFile.absolutePath).replace("%2", rightFile.absolutePath)
            }
            if (arguments.none { leftFile.absolutePath in it } || arguments.none { rightFile.absolutePath in it }) {
                return StructuralDiffAnalysis.Failed("Arguments must contain both %1 and %2 placeholders")
            }

            val output = execute(
                executable = executable,
                arguments = arguments,
                environment = if (config.environment.isEmpty()) kind.defaultEnvironment else config.environment,
                timeoutSeconds = StructuralDiffSettings.instance.timeoutSeconds,
                indicator = indicator,
            )
            val stdout = output.stdout.trim()
            if (stdout.isEmpty()) {
                val detail = output.stderr.trim().ifEmpty { "exit code ${output.exitCode}" }
                return StructuralDiffAnalysis.Failed("${kind.displayName} produced no JSON output: $detail")
            }

            try {
                parseOutput(kind, stdout, leftText, rightText)
            } catch (exception: Exception) {
                StructuralDiffAnalysis.Failed("Could not read ${kind.displayName} output: ${exception.message}")
            }
        } catch (cancelled: ProcessCanceledException) {
            throw cancelled
        } catch (exception: Exception) {
            StructuralDiffAnalysis.Failed("Could not run ${kind.displayName}: ${exception.message}")
        } finally {
            FileUtil.delete(tempDirectory)
        }
    }

    fun probe(kind: StructuralDiffEngineKind, executable: String): String? {
        val output = try {
            execute(executable, listOf("--version"), kind.defaultEnvironment, 5, null)
        } catch (_: Exception) {
            return null
        }
        val text = output.stdout.ifBlank { output.stderr }.trim()
        return text.lineSequence().firstOrNull()?.takeIf { kind.recognizesVersion(text) }
    }

    internal fun parseOutput(
        kind: StructuralDiffEngineKind,
        output: String,
        leftText: String = "",
        rightText: String = "",
    ): StructuralDiffAnalysis = when (kind) {
        StructuralDiffEngineKind.DIFFTASTIC -> parseDifftastic(output, leftText, rightText)
        StructuralDiffEngineKind.DIFFSITTER -> parseDiffsitter(output)
        StructuralDiffEngineKind.SEM -> parseSem(output)
    }

    private fun execute(
        executable: String,
        arguments: List<String>,
        environment: Map<String, String>,
        timeoutSeconds: Int,
        indicator: ProgressIndicator?,
    ): com.intellij.execution.process.ProcessOutput {
        ProgressManager.checkCanceled()
        val commandLine = GeneralCommandLine(listOf(executable) + arguments)
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment(environment)
        val process = CapturingProcessHandler(commandLine).apply { setShouldDestroyProcessRecursively(true) }
        val timeoutMillis = timeoutSeconds.coerceIn(1, 600).times(1_000)
        val output = if (indicator != null) {
            process.runProcessWithProgressIndicator(indicator, timeoutMillis, true)
        } else {
            process.runProcess(timeoutMillis, true)
        }
        if (output.isCancelled) throw ProcessCanceledException()
        if (output.isTimeout) error("process timed out after ${timeoutSeconds.coerceIn(1, 600)} seconds")
        return output
    }

    private fun parseDifftastic(
        output: String,
        leftText: String,
        rightText: String,
    ): StructuralDiffAnalysis {
        val diff = DifftasticJsonParser.parse(output)
        if (diff.status.equals("unchanged", ignoreCase = true)) return StructuralDiffAnalysis.Equal

        val leftLines = linkedSetOf<Int>()
        val rightLines = linkedSetOf<Int>()
        val tokens = mutableListOf<StructuralTokenMark>()
        diff.chunks.flatten().forEach { line ->
            line.lhs?.let { side ->
                leftLines += side.lineNumber
                side.changes.forEach { change ->
                    tokens += StructuralTokenMark(
                        StructuralDiffSide.LEFT,
                        side.lineNumber,
                        change.start,
                        change.end,
                        change.highlight.orEmpty(),
                    )
                }
            }
            line.rhs?.let { side ->
                rightLines += side.lineNumber
                side.changes.forEach { change ->
                    tokens += StructuralTokenMark(
                        StructuralDiffSide.RIGHT,
                        side.lineNumber,
                        change.start,
                        change.end,
                        change.highlight.orEmpty(),
                    )
                }
            }
        }

        when (diff.status?.lowercase()) {
            "created", "added" -> rightLines += lineIndices(rightText)
            "deleted", "removed" -> leftLines += lineIndices(leftText)
        }
        diff.alignedLines.forEach { pair ->
            val left = pair.getOrNull(0)
            val right = pair.getOrNull(1)
            if (left == null && right != null) rightLines += right
            if (right == null && left != null) leftLines += left
        }

        if (leftLines.isEmpty() && rightLines.isEmpty()) {
            return StructuralDiffAnalysis.Failed("Difftastic reported a change without changed lines")
        }
        return StructuralDiffAnalysis.Changed(leftLines, rightLines, tokens)
    }

    private fun parseDiffsitter(output: String): StructuralDiffAnalysis {
        val parsed = json.decodeFromString<DiffsitterPayload>(output)
        if (parsed.hunks.isEmpty()) return StructuralDiffAnalysis.Equal

        val leftLines = linkedSetOf<Int>()
        val rightLines = linkedSetOf<Int>()
        val tokens = mutableListOf<StructuralTokenMark>()
        parsed.hunks.forEach { hunk ->
            hunk.old.orEmpty().forEach { line ->
                leftLines += line.lineIndex
                line.entries.forEach { entry ->
                    tokens += StructuralTokenMark(
                        StructuralDiffSide.LEFT,
                        line.lineIndex,
                        entry.start.column,
                        entry.end.column,
                        "plain",
                    )
                }
            }
            hunk.new.orEmpty().forEach { line ->
                rightLines += line.lineIndex
                line.entries.forEach { entry ->
                    tokens += StructuralTokenMark(
                        StructuralDiffSide.RIGHT,
                        line.lineIndex,
                        entry.start.column,
                        entry.end.column,
                        "plain",
                    )
                }
            }
        }
        return StructuralDiffAnalysis.Changed(leftLines, rightLines, tokens)
    }

    private fun parseSem(output: String): StructuralDiffAnalysis {
        val parsed = json.decodeFromString<SemPayload>(output)
        if (parsed.changes.isEmpty()) return StructuralDiffAnalysis.Equal

        val leftLines = linkedSetOf<Int>()
        val rightLines = linkedSetOf<Int>()
        val regions = mutableListOf<StructuralRegionMark>()
        parsed.changes.forEach { change ->
            val type = change.changeType.ifBlank { "modified" }.lowercase()
            val label = listOfNotNull(type, change.entityType, change.entityName).joinToString(" ")
            if (type != "added" && change.oldStartLine != null && change.oldEndLine != null) {
                val range = (change.oldStartLine - 1) until change.oldEndLine
                leftLines += range
                regions += StructuralRegionMark(
                    StructuralDiffSide.LEFT,
                    range.first,
                    range.last + 1,
                    label,
                    change.startLine?.minus(1),
                )
            }
            if (type != "deleted" && change.startLine != null && change.endLine != null) {
                val range = (change.startLine - 1) until change.endLine
                rightLines += range
                regions += StructuralRegionMark(
                    StructuralDiffSide.RIGHT,
                    range.first,
                    range.last + 1,
                    label,
                    change.oldStartLine?.minus(1),
                )
            }
        }
        if (leftLines.isEmpty() && rightLines.isEmpty()) {
            return StructuralDiffAnalysis.Failed("Sem reported changes without usable line ranges")
        }
        return StructuralDiffAnalysis.Changed(leftLines, rightLines, regions = regions)
    }

    private fun safeFileName(fileName: String): String {
        val cleaned = fileName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "content.txt" }
        return if (cleaned.contains('.')) cleaned else "$cleaned.txt"
    }

    private fun lineIndices(text: String): IntRange =
        if (text.isEmpty()) IntRange.EMPTY else 0 until (text.count { it == '\n' } + if (text.endsWith('\n')) 0 else 1)
}

@Serializable
private data class DiffsitterPayload(val hunks: List<DiffsitterHunk> = emptyList())

@Serializable
private data class DiffsitterHunk(
    @SerialName("Old") val old: List<DiffsitterLine>? = null,
    @SerialName("New") val new: List<DiffsitterLine>? = null,
)

@Serializable
private data class DiffsitterLine(
    @SerialName("line_index") val lineIndex: Int,
    val entries: List<DiffsitterEntry> = emptyList(),
)

@Serializable
private data class DiffsitterEntry(
    @SerialName("start_position") val start: DiffsitterPosition,
    @SerialName("end_position") val end: DiffsitterPosition,
)

@Serializable
private data class DiffsitterPosition(val row: Int, val column: Int)

@Serializable
private data class SemPayload(val changes: List<SemChange> = emptyList())

@Serializable
private data class SemChange(
    val changeType: String = "",
    val entityType: String? = null,
    val entityName: String? = null,
    val startLine: Int? = null,
    val endLine: Int? = null,
    val oldStartLine: Int? = null,
    val oldEndLine: Int? = null,
)

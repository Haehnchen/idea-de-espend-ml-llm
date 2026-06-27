package de.espend.ml.llm.difftastic

import de.espend.ml.llm.CommandPathUtils
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

data class DifftasticSource(
    val title: String,
    val text: String,
)

sealed class DifftasticRunResult {
    data class Success(
        val left: DifftasticSource,
        val right: DifftasticSource,
        val diff: DifftasticFileDiff,
        val rawJson: String,
    ) : DifftasticRunResult()

    data class Unavailable(
        val message: String = "Difftastic executable was not found. Install `difft` and reopen the diff.",
    ) : DifftasticRunResult()

    data class Failure(
        val message: String,
        val output: String = "",
    ) : DifftasticRunResult()
}

object DifftasticRunner {
    private const val DEFAULT_WIDTH = 160
    private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(15)

    fun run(
        left: DifftasticSource,
        right: DifftasticSource,
        commandPath: String? = CommandPathUtils.findDifftasticPath(),
        timeout: Duration = DEFAULT_TIMEOUT,
        contextLines: Int? = null,
        width: Int = DEFAULT_WIDTH,
    ): DifftasticRunResult {
        val command = commandPath ?: return DifftasticRunResult.Unavailable()
        val tempDir = Files.createTempDirectory("idea-difftastic-")
        val effectiveContextLines = contextLines ?: fullFileContextLines(left.text, right.text)

        return try {
            val leftFile = tempDir.resolve(tempFileName("left", left.title))
            val rightFile = tempDir.resolve(tempFileName("right", right.title))
            leftFile.writeText(left.text, StandardCharsets.UTF_8)
            rightFile.writeText(right.text, StandardCharsets.UTF_8)

            val process = ProcessBuilder(
                command,
                "--display=json",
                "--color=never",
                "--context=$effectiveContextLines",
                "--width=$width",
                leftFile.toString(),
                rightFile.toString(),
            )
                .redirectErrorStream(true)
                .also { it.environment()["DFT_UNSTABLE"] = "yes" }
                .start()

            val outputFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }

            val finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return DifftasticRunResult.Failure("Difftastic timed out after ${timeout.seconds}s")
            }

            val output = outputFuture.get(2, TimeUnit.SECONDS).trim()
            if (process.exitValue() != 0) {
                return DifftasticRunResult.Failure("Difftastic exited with code ${process.exitValue()}", output)
            }

            val diff = try {
                DifftasticJsonParser.parse(output)
            } catch (e: Exception) {
                return DifftasticRunResult.Failure("Could not parse Difftastic JSON: ${e.message}", output)
            }

            DifftasticRunResult.Success(left, right, diff, output)
        } catch (e: Exception) {
            DifftasticRunResult.Failure("Could not run Difftastic: ${e.message}")
        } finally {
            tempDir.toFile().deleteRecursively()
            tempDir.deleteIfExists()
        }
    }

    private fun tempFileName(side: String, title: String): String {
        val safeName = title
            .substringAfterLast('/')
            .substringAfterLast(File.separatorChar)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "$side.txt" }

        val withExtension = if (safeName.substringAfterLast('.', "").isNotEmpty()) {
            safeName
        } else {
            "$safeName.txt"
        }

        return "$side-$withExtension"
    }

    private fun fullFileContextLines(leftText: String, rightText: String): Int {
        return maxOf(lineCount(leftText), lineCount(rightText))
    }

    private fun lineCount(text: String): Int {
        if (text.isEmpty()) {
            return 0
        }

        val newlineCount = text.count { it == '\n' }
        return newlineCount + if (text.endsWith('\n')) 0 else 1
    }
}

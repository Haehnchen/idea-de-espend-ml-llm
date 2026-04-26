package de.espend.ml.llm.tokscale

import de.espend.ml.llm.CommandPathUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class TokscalePeriod(val label: String, val flag: String) {
    WEEK("W", "--week"),
    MONTH("M", "--month")
}

data class TokscaleUsageStat(
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val cost: Double
)

data class TokscaleUsageResult(
    val stat: TokscaleUsageStat?,
    val error: String? = null
)

/**
 * Reads local Tokscale usage totals via the Tokscale CLI JSON output.
 *
 * Uses a locally installed `tokscale` binary when available, otherwise falls back to
 * `npx --package=tokscale@latest -c "tokscale ..."` so users do not need a global install.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
object TokscaleStatsReader {

    internal var commandRunnerOverride: ((TokscalePeriod) -> CommandOutput)? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<TokscalePeriod, CachedEntry>()

    fun getUsage(period: TokscalePeriod): TokscaleUsageResult {
        val result = fetchUsage(period)
        cache[period] = CachedEntry(result)
        return result
    }

    fun getCachedUsage(period: TokscalePeriod): TokscaleUsageResult? = cache[period]?.result

    fun areCachesValid(maxAgeMs: Long): Boolean =
        TokscalePeriod.entries.all { period ->
            val entry = cache[period] ?: return@all false
            System.currentTimeMillis() - entry.timestamp <= maxAgeMs
        }

    internal fun clearCache() {
        cache.clear()
    }

    private fun fetchUsage(period: TokscalePeriod): TokscaleUsageResult {
        val output = try {
            commandRunnerOverride?.invoke(period) ?: runTokscale(period)
        } catch (e: Exception) {
            return TokscaleUsageResult(null, e.message ?: "Tokscale failed")
        }
        if (output.exitCode != 0) {
            return TokscaleUsageResult(null, summarizeError(output.text))
        }

        return try {
            val stat = parseJsonOutput(output.text)
                ?: return TokscaleUsageResult(null, "No Tokscale data")
            TokscaleUsageResult(stat)
        } catch (_: Exception) {
            TokscaleUsageResult(null, "Could not parse Tokscale output")
        }
    }

    internal fun parseJsonOutput(output: String): TokscaleUsageStat? {
        val jsonText = extractJsonObject(output) ?: return null
        val root = json.parseToJsonElement(jsonText).jsonObject
        val input = root["totalInput"]?.jsonPrimitive?.longOrNull ?: return null
        val outputTokens = root["totalOutput"]?.jsonPrimitive?.longOrNull ?: return null
        val cost = root["totalCost"]?.jsonPrimitive?.doubleOrNull ?: return null

        return TokscaleUsageStat(
            inputTokens = input,
            outputTokens = outputTokens,
            totalTokens = input + outputTokens,
            cost = cost
        )
    }

    fun formatTokens(tokens: Long): String = when {
        tokens >= 1_000_000_000L -> formatScaled(tokens, 1_000_000_000.0, "B")
        tokens >= 1_000_000L -> formatScaled(tokens, 1_000_000.0, "M")
        tokens >= 1_000L -> formatScaled(tokens, 1_000.0, "K")
        else -> tokens.toString()
    }

    fun formatCost(cost: Double): String = when {
        cost <= 0.0 -> "$0"
        cost >= 1_000_000_000.0 -> "$" + formatScaled(cost, 1_000_000_000.0, "B")
        cost >= 1_000_000.0 -> "$" + formatScaled(cost, 1_000_000.0, "M")
        cost >= 1_000.0 -> "$" + formatScaled(cost, 1_000.0, "K")
        else -> "$" + String.format(formatLocale(), "%.0f", cost)
    }

    private fun runTokscale(period: TokscalePeriod): CommandOutput {
        val command = buildTokscaleCommand(period)
            ?: return CommandOutput(1, "tokscale or npx command not found")

        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            val readerThread = Thread {
                process.inputStream.bufferedReader().use { output.append(it.readText()) }
            }.apply {
                isDaemon = true
                start()
            }

            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                readerThread.join(1_000)
                return CommandOutput(1, "Tokscale timed out")
            }

            readerThread.join(1_000)
            CommandOutput(process.exitValue(), output.toString())
        } catch (e: IOException) {
            CommandOutput(1, e.message ?: "Could not run Tokscale")
        }
    }

    internal fun buildTokscaleCommand(period: TokscalePeriod): List<String>? {
        CommandPathUtils.findCommandPath("tokscale")?.let { tokscale ->
            return listOf(tokscale, period.flag, "--json", "--no-spinner")
        }

        val npx = CommandPathUtils.findCommandPath("npx") ?: return null
        return listOf(
            npx,
            "--package=tokscale@latest",
            "-c",
            "tokscale ${period.flag} --json --no-spinner"
        )
    }

    private fun extractJsonObject(output: String): String? {
        val start = output.indexOf('{')
        val end = output.lastIndexOf('}')
        if (start !in 0 until end) return null
        return output.substring(start, end + 1)
    }

    private fun summarizeError(output: String): String {
        val line = output
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return "Tokscale unavailable"

        return line
            .replace(Regex("\\s+"), " ")
            .take(ERROR_TEXT_LIMIT)
    }

    private fun formatScaled(tokens: Long, divisor: Double, suffix: String): String {
        return formatScaled(tokens.toDouble(), divisor, suffix)
    }

    private fun formatScaled(amount: Double, divisor: Double, suffix: String): String {
        val locale = formatLocale()
        val value = amount / divisor
        val text = if (value >= 100.0) {
            String.format(locale, "%.0f", value)
        } else {
            String.format(locale, "%.1f", value).removeSuffix("${DecimalFormatSymbols.getInstance(locale).decimalSeparator}0")
        }
        return "$text$suffix"
    }

    private fun formatLocale(): Locale = Locale.getDefault(Locale.Category.FORMAT)

    internal data class CommandOutput(
        val exitCode: Int,
        val text: String
    )

    private data class CachedEntry(
        val result: TokscaleUsageResult,
        val timestamp: Long = System.currentTimeMillis()
    )

    private const val COMMAND_TIMEOUT_SECONDS = 20L
    private const val ERROR_TEXT_LIMIT = 80
}

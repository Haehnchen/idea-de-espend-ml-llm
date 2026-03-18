package de.espend.ml.llm.rtk

import java.io.File
import java.sql.DriverManager
import java.time.LocalDate

data class RtkDayStat(
    val date: LocalDate,
    val inputTokens: Long,
    val outputTokens: Long,
    val savedTokens: Long,
    val savingsPct: Double
)

data class RtkWeekStat(
    val inputTokens: Long,
    val outputTokens: Long,
    val savedTokens: Long,
    val savingsPct: Double
)

/**
 * Reads RTK token-saving stats from the local SQLite history database.
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
object RtkStatsReader {

    /** Overrides the default DB path; intended for tests only. */
    internal var dbPathOverride: String? = null

    private val dbPath: String
        get() = dbPathOverride ?: (System.getProperty("user.home") + "/.local/share/rtk/history.db")

    fun getLastDays(n: Int): List<RtkDayStat> {
        val file = File(dbPath)
        if (!file.exists()) return emptyList()

        return try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
                val sql = """
                    SELECT date(timestamp, 'localtime') as day,
                           SUM(input_tokens) as input,
                           SUM(output_tokens) as output,
                           SUM(saved_tokens) as saved,
                           CASE WHEN SUM(input_tokens) > 0
                                THEN ROUND(SUM(saved_tokens) * 100.0 / SUM(input_tokens), 1)
                                ELSE 0 END as pct
                    FROM commands
                    WHERE date(timestamp, 'localtime') >= date('now', 'localtime', 'start of day', '-${n} days')
                    GROUP BY day
                    ORDER BY day DESC
                    LIMIT $n
                """.trimIndent()
                val result = mutableListOf<RtkDayStat>()
                conn.createStatement().executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        result.add(
                            RtkDayStat(
                                date = LocalDate.parse(rs.getString("day")),
                                inputTokens = rs.getLong("input"),
                                outputTokens = rs.getLong("output"),
                                savedTokens = rs.getLong("saved"),
                                savingsPct = rs.getDouble("pct")
                            )
                        )
                    }
                }
                result.reversed()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getLast7Days(): RtkWeekStat? {
        val file = File(dbPath)
        if (!file.exists()) return null

        return try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
                val sql = """
                    SELECT SUM(input_tokens) as input,
                           SUM(output_tokens) as output,
                           SUM(saved_tokens) as saved,
                           CASE WHEN SUM(input_tokens) > 0
                                THEN ROUND(SUM(saved_tokens) * 100.0 / SUM(input_tokens), 1)
                                ELSE 0 END as pct
                    FROM commands
                    WHERE date(timestamp, 'localtime') >= date('now', 'localtime', 'start of day', '-6 days')
                """.trimIndent()
                conn.createStatement().executeQuery(sql).use { rs ->
                    if (rs.next() && rs.getLong("input") > 0) {
                        RtkWeekStat(
                            inputTokens = rs.getLong("input"),
                            outputTokens = rs.getLong("output"),
                            savedTokens = rs.getLong("saved"),
                            savingsPct = rs.getDouble("pct")
                        )
                    } else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun formatTokens(tokens: Long): String = when {
        tokens >= 1_000_000L -> "${tokens / 1_000_000}M"
        tokens >= 1_000L -> "${tokens / 1_000}K"
        else -> tokens.toString()
    }
}

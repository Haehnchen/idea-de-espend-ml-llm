package de.espend.ml.llm.rtk

import java.io.File
import java.sql.Connection
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

    private const val RTK_DATA_DIR = "rtk"
    private const val HISTORY_DB = "history.db"
    private const val CONFIG_TOML = "config.toml"

    private enum class HostOs { WINDOWS, MAC, LINUX }

    internal fun dbPathCandidates(): List<String> {
        dbPathOverride?.let { return listOf(it) }

        return dbPathCandidates(
            osName = System.getProperty("os.name").orEmpty(),
            userHome = System.getProperty("user.home").orEmpty(),
            environment = System::getenv
        )
    }

    fun defaultDbPathForDisplay(): String {
        return defaultDbPathForDisplay(
            osName = System.getProperty("os.name").orEmpty(),
            userHome = System.getProperty("user.home").orEmpty(),
            environment = System::getenv
        )
    }

    internal fun defaultDbPathForDisplay(
        osName: String,
        userHome: String,
        environment: (String) -> String?
    ): String {
        val path = defaultDbPathCandidates(
            osName,
            userHome,
            environment
        ).firstOrNull() ?: return "RTK history database"

        return compactHomePath(path, userHome)
    }

    fun getLastDays(n: Int): List<RtkDayStat> {
        return getLastDays(n, dbPathCandidates())
    }

    internal fun getLastDays(n: Int, dbPathCandidates: List<String>): List<RtkDayStat> {
        for (dbPath in dbPathCandidates) {
            val stats = readLastDays(dbPath, n)
            if (!stats.isNullOrEmpty()) return stats
        }

        return emptyList()
    }

    private fun readLastDays(dbPath: String, n: Int): List<RtkDayStat>? {
        return withDb(dbPath) { conn ->
            val sql = """
                SELECT date(timestamp, 'localtime') as day,
                       SUM(input_tokens) as input,
                       SUM(output_tokens) as output,
                       SUM(saved_tokens) as saved
                FROM commands
                WHERE date(timestamp, 'localtime') >= date('now', 'localtime', 'start of day', '-${n} days')
                GROUP BY day
                ORDER BY day DESC
                LIMIT $n
            """.trimIndent()
            val result = mutableListOf<RtkDayStat>()
            conn.createStatement().executeQuery(sql).use { rs ->
                while (rs.next()) {
                    val inputTokens = rs.getLong("input")
                    val savedTokens = rs.getLong("saved")
                    result.add(
                        RtkDayStat(
                            date = LocalDate.parse(rs.getString("day")),
                            inputTokens = inputTokens,
                            outputTokens = rs.getLong("output"),
                            savedTokens = savedTokens,
                            savingsPct = savingsPct(savedTokens, inputTokens)
                        )
                    )
                }
            }
            result.reversed()
        }
    }

    fun getLast7Days(): RtkWeekStat? {
        return getLast7Days(dbPathCandidates())
    }

    internal fun getLast7Days(dbPathCandidates: List<String>): RtkWeekStat? {
        for (dbPath in dbPathCandidates) {
            readLast7Days(dbPath)?.let { return it }
        }

        return null
    }

    private fun readLast7Days(dbPath: String): RtkWeekStat? {
        return withDb(dbPath) { conn ->
            val sql = """
                SELECT SUM(input_tokens) as input,
                       SUM(output_tokens) as output,
                       SUM(saved_tokens) as saved
                FROM commands
                WHERE date(timestamp, 'localtime') >= date('now', 'localtime', 'start of day', '-6 days')
            """.trimIndent()
            conn.createStatement().executeQuery(sql).use { rs ->
                if (rs.next() && rs.getLong("input") > 0) {
                    val inputTokens = rs.getLong("input")
                    val savedTokens = rs.getLong("saved")
                    RtkWeekStat(
                        inputTokens = inputTokens,
                        outputTokens = rs.getLong("output"),
                        savedTokens = savedTokens,
                        savingsPct = savingsPct(savedTokens, inputTokens)
                    )
                } else null
            }
        }
    }

    internal fun dbPathCandidates(
        osName: String,
        userHome: String,
        environment: (String) -> String?,
        configFiles: List<File> = configPathCandidates(osName, userHome, environment).map(::File)
    ): List<String> {
        return buildList {
            environment("RTK_DB_PATH")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }

            configFiles
                .mapNotNull { readConfiguredDbPath(it) }
                .forEach { add(it) }

            addAll(defaultDbPathCandidates(osName, userHome, environment))
        }.distinct()
    }

    internal fun defaultDbPathCandidates(
        osName: String,
        userHome: String,
        environment: (String) -> String?
    ): List<String> {
        val home = userHome.trim()
        val candidates = mutableListOf<String>()

        when (detectOs(osName)) {
            HostOs.WINDOWS -> {
                environment("LOCALAPPDATA")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    candidates.add(joinWindowsPath(it, RTK_DATA_DIR, HISTORY_DB))
                }
                environment("APPDATA")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    candidates.add(joinWindowsPath(it, RTK_DATA_DIR, HISTORY_DB))
                }
                if (home.isNotEmpty()) {
                    candidates.add(joinWindowsPath(home, "AppData", "Local", RTK_DATA_DIR, HISTORY_DB))
                    candidates.add(joinWindowsPath(home, "AppData", "Roaming", RTK_DATA_DIR, HISTORY_DB))
                }
            }
            HostOs.MAC -> {
                if (home.isNotEmpty()) {
                    candidates.add(joinUnixPath(home, "Library", "Application Support", RTK_DATA_DIR, HISTORY_DB))
                    candidates.add(joinUnixPath(home, ".local", "share", RTK_DATA_DIR, HISTORY_DB))
                }
            }
            HostOs.LINUX -> {
                environment("XDG_DATA_HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    candidates.add(joinUnixPath(it, RTK_DATA_DIR, HISTORY_DB))
                }
                if (home.isNotEmpty()) {
                    candidates.add(joinUnixPath(home, ".local", "share", RTK_DATA_DIR, HISTORY_DB))
                }
            }
        }

        return candidates.distinct()
    }

    internal fun configPathCandidates(
        osName: String,
        userHome: String,
        environment: (String) -> String?
    ): List<String> {
        val home = userHome.trim()
        val candidates = mutableListOf<String>()

        when (detectOs(osName)) {
            HostOs.WINDOWS -> {
                environment("APPDATA")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    candidates.add(joinWindowsPath(it, RTK_DATA_DIR, CONFIG_TOML))
                }
                if (home.isNotEmpty()) {
                    candidates.add(joinWindowsPath(home, "AppData", "Roaming", RTK_DATA_DIR, CONFIG_TOML))
                }
            }
            HostOs.MAC -> {
                if (home.isNotEmpty()) {
                    candidates.add(joinUnixPath(home, "Library", "Application Support", RTK_DATA_DIR, CONFIG_TOML))
                    candidates.add(joinUnixPath(home, ".config", RTK_DATA_DIR, CONFIG_TOML))
                }
            }
            HostOs.LINUX -> {
                environment("XDG_CONFIG_HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    candidates.add(joinUnixPath(it, RTK_DATA_DIR, CONFIG_TOML))
                }
                if (home.isNotEmpty()) {
                    candidates.add(joinUnixPath(home, ".config", RTK_DATA_DIR, CONFIG_TOML))
                }
            }
        }

        return candidates.distinct()
    }

    private fun detectOs(osName: String): HostOs = when {
        osName.startsWith("Windows", ignoreCase = true) -> HostOs.WINDOWS
        osName.contains("Mac", ignoreCase = true) || osName.contains("Darwin", ignoreCase = true) -> HostOs.MAC
        else -> HostOs.LINUX
    }

    internal fun parseConfiguredDbPath(toml: String): String? {
        var section = ""

        for (rawLine in toml.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.startsWith("[") && line.contains("]")) {
                section = line.substringAfter("[").substringBefore("]").trim()
                continue
            }

            if (section != "tracking") continue

            val separator = line.indexOf('=')
            if (separator <= 0) continue

            val key = line.substring(0, separator).trim()
            if (key != "database_path") continue

            return parseTomlValue(line.substring(separator + 1))?.takeIf { it.isNotBlank() }
        }

        return null
    }

    private fun readConfiguredDbPath(configFile: File): String? {
        if (!configFile.exists()) return null

        return try {
            parseConfiguredDbPath(configFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun <T> withDb(dbPath: String, block: (Connection) -> T): T? {
        if (!File(dbPath).exists()) return null

        return try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:$dbPath").use(block)
        } catch (_: Exception) {
            null
        }
    }

    private fun savingsPct(savedTokens: Long, inputTokens: Long): Double {
        if (inputTokens <= 0L) return 0.0

        return kotlin.math.round(savedTokens * 1000.0 / inputTokens) / 10.0
    }

    private fun parseTomlValue(rawValue: String): String? {
        val value = stripTomlComment(rawValue).trim()
        if (value.isEmpty()) return null

        if (value.startsWith("'")) {
            return value.indexOf('\'', startIndex = 1)
                .takeIf { it > 0 }
                ?.let { value.substring(1, it) }
        }

        if (value.startsWith("\"")) {
            return parseTomlBasicString(value)
        }

        return value.takeIf { it.isNotBlank() }
    }

    private fun parseTomlBasicString(value: String): String? {
        val result = StringBuilder()
        var index = 1

        while (index < value.length) {
            val char = value[index]
            if (char == '"') {
                return result.toString()
            }

            if (char == '\\' && index + 1 < value.length) {
                index++
                result.append(
                    when (val escaped = value[index]) {
                        '\\' -> '\\'
                        '"' -> '"'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> escaped
                    }
                )
            } else {
                result.append(char)
            }
            index++
        }

        return null
    }

    private fun stripTomlComment(value: String): String {
        var inSingleQuote = false
        var inDoubleQuote = false
        var escaped = false

        for (index in value.indices) {
            val char = value[index]

            if (escaped) {
                escaped = false
                continue
            }

            if (inDoubleQuote && char == '\\') {
                escaped = true
                continue
            }

            when (char) {
                '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
                '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                '#' -> if (!inSingleQuote && !inDoubleQuote) return value.substring(0, index)
            }
        }

        return value
    }

    private fun joinUnixPath(first: String, vararg more: String): String =
        sequenceOf(first.trimEnd('/'), *more.map { it.trim('/') }.toTypedArray()).joinToString("/")

    private fun joinWindowsPath(first: String, vararg more: String): String =
        sequenceOf(first.trimEnd('\\', '/'), *more.map { it.trim('\\', '/') }.toTypedArray()).joinToString("\\")

    private fun compactHomePath(path: String, userHome: String): String {
        val home = userHome.trim().trimEnd('\\', '/')
        if (home.isEmpty()) return path

        return when {
            path == home -> "~"
            path.startsWith("$home/") -> "~/" + path.removePrefix("$home/")
            path.startsWith("$home\\") -> "~\\" + path.removePrefix("$home\\")
            else -> path
        }
    }
}

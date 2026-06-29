package de.espend.ml.llm.rtk

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.DriverManager
import java.time.LocalDate

class RtkStatsReaderTest {

    private lateinit var dbFile: File

    @Before
    fun setUp() {
        Class.forName("org.sqlite.JDBC")
        dbFile = File.createTempFile("rtk-test-", ".db")
        RtkStatsReader.dbPathOverride = dbFile.absolutePath

        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().execute(
                """CREATE TABLE commands (
                    id INTEGER PRIMARY KEY,
                    timestamp TEXT NOT NULL,
                    original_cmd TEXT NOT NULL DEFAULT '',
                    rtk_cmd TEXT NOT NULL DEFAULT '',
                    input_tokens INTEGER NOT NULL,
                    output_tokens INTEGER NOT NULL,
                    saved_tokens INTEGER NOT NULL,
                    savings_pct REAL NOT NULL,
                    exec_time_ms INTEGER DEFAULT 0,
                    project_path TEXT DEFAULT ''
                )"""
            )
        }
    }

    @After
    fun tearDown() {
        RtkStatsReader.dbPathOverride = null
        dbFile.delete()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Inserts a row using noon UTC on the given local date (safe across all timezones). */
    private fun insert(date: LocalDate, input: Long, output: Long, saved: Long) {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().execute(
                """INSERT INTO commands (timestamp, input_tokens, output_tokens, saved_tokens, savings_pct)
                   VALUES ('${date}T12:00:00+00:00', $input, $output, $saved, 0)"""
            )
        }
    }

    // ── path resolution ──────────────────────────────────────────────────────

    @Test
    fun `dbPathCandidates reads environment config and platform defaults in order`() {
        val homeDir = java.nio.file.Files.createTempDirectory("rtk-home-").toFile()
        val configuredDbPath = File(homeDir, "custom/history.db").absolutePath
        val configFile = File(homeDir, ".config/rtk/config.toml")

        try {
            configFile.parentFile.mkdirs()
            configFile.writeText(
                """
                [tracking]
                database_path = '$configuredDbPath'
                """.trimIndent()
            )

            val candidates = RtkStatsReader.dbPathCandidates(
                osName = "Linux",
                userHome = homeDir.absolutePath,
                environment = { name -> if (name == "RTK_DB_PATH") "/env/history.db" else null }
            )

            assertEquals("/env/history.db", candidates[0])
            assertEquals(configuredDbPath, candidates[1])
            assertEquals(File(homeDir, ".local/share/rtk/history.db").absolutePath, candidates[2])
        } finally {
            homeDir.deleteRecursively()
        }
    }

    @Test
    fun `defaultDbPathCandidates uses Windows LocalAppData and Roaming fallbacks`() {
        val environment = mapOf(
            "LOCALAPPDATA" to "C:\\Users\\Ada\\AppData\\Local",
            "APPDATA" to "C:\\Users\\Ada\\AppData\\Roaming"
        )

        val candidates = RtkStatsReader.defaultDbPathCandidates(
            osName = "Windows 11",
            userHome = "C:\\Users\\Ada",
            environment = { environment[it] }
        )

        assertEquals("C:\\Users\\Ada\\AppData\\Local\\rtk\\history.db", candidates[0])
        assertTrue(candidates.contains("C:\\Users\\Ada\\AppData\\Roaming\\rtk\\history.db"))
    }

    @Test
    fun `defaultDbPathCandidates uses macOS application support before legacy local share`() {
        val candidates = RtkStatsReader.defaultDbPathCandidates(
            osName = "Mac OS X",
            userHome = "/Users/ada",
            environment = { null }
        )

        assertEquals("/Users/ada/Library/Application Support/rtk/history.db", candidates[0])
        assertEquals("/Users/ada/.local/share/rtk/history.db", candidates[1])
    }

    @Test
    fun `defaultDbPathForDisplay compacts path below user home`() {
        assertEquals(
            "~/Library/Application Support/rtk/history.db",
            RtkStatsReader.defaultDbPathForDisplay(
                osName = "Mac OS X",
                userHome = "/Users/ada",
                environment = { null }
            )
        )
    }

    @Test
    fun `defaultDbPathCandidates uses XDG data home before Linux fallback`() {
        val candidates = RtkStatsReader.defaultDbPathCandidates(
            osName = "Linux",
            userHome = "/home/ada",
            environment = { name -> if (name == "XDG_DATA_HOME") "/custom/data" else null }
        )

        assertEquals("/custom/data/rtk/history.db", candidates[0])
        assertEquals("/home/ada/.local/share/rtk/history.db", candidates[1])
    }

    @Test
    fun `parseConfiguredDbPath reads tracking database path from literal string`() {
        val path = """C:\Users\Ada\AppData\Local\rtk\history.db"""

        val result = RtkStatsReader.parseConfiguredDbPath(
            """
            [display]
            colors = true

            [tracking]
            database_path = '$path' # comment
            """.trimIndent()
        )

        assertEquals(path, result)
    }

    @Test
    fun `parseConfiguredDbPath unescapes basic string backslashes`() {
        val result = RtkStatsReader.parseConfiguredDbPath(
            """
            [tracking]
            database_path = "C:\\Users\\Ada\\rtk\\history.db"
            """.trimIndent()
        )

        assertEquals("""C:\Users\Ada\rtk\history.db""", result)
    }

    @Test
    fun `getLastDays checks all candidate paths until populated database is found`() {
        val today = LocalDate.now()
        insert(today, 10_000, 1_000, 9_000)

        val result = RtkStatsReader.getLastDays(
            n = 1,
            dbPathCandidates = listOf(
                File(dbFile.parentFile, "missing-history.db").absolutePath,
                dbFile.absolutePath
            )
        )

        assertEquals(1, result.size)
        assertEquals(9_000L, result[0].savedTokens)
    }

    // ── formatTokens ─────────────────────────────────────────────────────────

    @Test
    fun `formatTokens returns raw number below 1000`() {
        assertEquals("0", RtkStatsReader.formatTokens(0))
        assertEquals("999", RtkStatsReader.formatTokens(999))
    }

    @Test
    fun `formatTokens returns K suffix for thousands`() {
        assertEquals("1K", RtkStatsReader.formatTokens(1_000))
        assertEquals("1K", RtkStatsReader.formatTokens(1_999))
        assertEquals("26K", RtkStatsReader.formatTokens(26_400))
        assertEquals("999K", RtkStatsReader.formatTokens(999_999))
    }

    @Test
    fun `formatTokens returns M suffix for millions`() {
        assertEquals("1M", RtkStatsReader.formatTokens(1_000_000))
        assertEquals("1M", RtkStatsReader.formatTokens(1_900_000))
        assertEquals("26M", RtkStatsReader.formatTokens(26_100_000))
    }

    // ── getLastDays ───────────────────────────────────────────────────────────

    @Test
    fun `getLastDays returns empty list when DB file does not exist`() {
        RtkStatsReader.dbPathOverride = "/nonexistent/path/history.db"
        assertTrue(RtkStatsReader.getLastDays(3).isEmpty())
    }

    @Test
    fun `getLastDays returns empty list when table has no rows`() {
        assertTrue(RtkStatsReader.getLastDays(3).isEmpty())
    }

    @Test
    fun `getLastDays returns today entry`() {
        val today = LocalDate.now()
        insert(today, 10_000, 1_000, 9_000)

        val result = RtkStatsReader.getLastDays(3)

        assertEquals(1, result.size)
        assertEquals(today, result[0].date)
        assertEquals(10_000L, result[0].inputTokens)
        assertEquals(1_000L, result[0].outputTokens)
        assertEquals(9_000L, result[0].savedTokens)
    }

    @Test
    fun `getLastDays aggregates multiple rows for the same day`() {
        val today = LocalDate.now()
        insert(today, 5_000, 500, 4_500)
        insert(today, 3_000, 300, 2_700)

        val result = RtkStatsReader.getLastDays(3)

        assertEquals(1, result.size)
        assertEquals(8_000L, result[0].inputTokens)
        assertEquals(800L, result[0].outputTokens)
        assertEquals(7_200L, result[0].savedTokens)
    }

    @Test
    fun `getLastDays returns days in ascending order`() {
        val today = LocalDate.now()
        insert(today, 1_000, 100, 900)
        insert(today.minusDays(1), 2_000, 200, 1_800)
        insert(today.minusDays(2), 3_000, 300, 2_700)

        val result = RtkStatsReader.getLastDays(3)

        assertEquals(3, result.size)
        assertEquals(today.minusDays(2), result[0].date)
        assertEquals(today.minusDays(1), result[1].date)
        assertEquals(today, result[2].date)
    }

    @Test
    fun `getLastDays respects the limit`() {
        val today = LocalDate.now()
        insert(today, 1_000, 100, 900)
        insert(today.minusDays(1), 2_000, 200, 1_800)
        insert(today.minusDays(2), 3_000, 300, 2_700)

        val result = RtkStatsReader.getLastDays(2)

        assertEquals(2, result.size)
        assertEquals(today.minusDays(1), result[0].date)
        assertEquals(today, result[1].date)
    }

    @Test
    fun `getLastDays excludes entries outside the date range`() {
        val today = LocalDate.now()
        insert(today.minusDays(10), 5_000, 500, 4_500)
        insert(today, 1_000, 100, 900)

        val result = RtkStatsReader.getLastDays(3)

        assertEquals(1, result.size)
        assertEquals(today, result[0].date)
    }

    @Test
    fun `getLastDays calculates savings percentage correctly`() {
        val today = LocalDate.now()
        insert(today, 10_000, 1_000, 9_000)

        val result = RtkStatsReader.getLastDays(1)

        assertEquals(1, result.size)
        assertEquals(90.0, result[0].savingsPct, 0.1)
    }

    // ── getLast7Days ──────────────────────────────────────────────────────────

    @Test
    fun `getLast7Days returns null when DB file does not exist`() {
        RtkStatsReader.dbPathOverride = "/nonexistent/path/history.db"
        assertNull(RtkStatsReader.getLast7Days())
    }

    @Test
    fun `getLast7Days returns null when table has no rows`() {
        assertNull(RtkStatsReader.getLast7Days())
    }

    @Test
    fun `getLast7Days aggregates all entries within the last 7 days`() {
        val today = LocalDate.now()
        insert(today, 10_000, 1_000, 9_000)
        insert(today.minusDays(3), 20_000, 2_000, 18_000)
        insert(today.minusDays(6), 5_000, 500, 4_500)

        val result = RtkStatsReader.getLast7Days()

        assertNotNull(result)
        assertEquals(35_000L, result!!.inputTokens)
        assertEquals(3_500L, result.outputTokens)
        assertEquals(31_500L, result.savedTokens)
    }

    @Test
    fun `getLast7Days excludes entries older than 7 days`() {
        val today = LocalDate.now()
        insert(today.minusDays(7), 50_000, 5_000, 45_000)
        insert(today, 10_000, 1_000, 9_000)

        val result = RtkStatsReader.getLast7Days()

        assertNotNull(result)
        assertEquals(10_000L, result!!.inputTokens)
    }

    @Test
    fun `getLast7Days calculates savings percentage correctly`() {
        val today = LocalDate.now()
        insert(today, 10_000, 2_000, 8_000)

        val result = RtkStatsReader.getLast7Days()

        assertNotNull(result)
        assertEquals(80.0, result!!.savingsPct, 0.1)
    }

    @Test
    fun `getLast7Days returns null when only old entries exist`() {
        val today = LocalDate.now()
        insert(today.minusDays(8), 10_000, 1_000, 9_000)

        assertNull(RtkStatsReader.getLast7Days())
    }
}

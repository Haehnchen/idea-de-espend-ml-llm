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

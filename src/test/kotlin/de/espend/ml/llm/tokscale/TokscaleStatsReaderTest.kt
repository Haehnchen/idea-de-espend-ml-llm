package de.espend.ml.llm.tokscale

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class TokscaleStatsReaderTest {

    private lateinit var previousFormatLocale: Locale

    @Before
    fun setUp() {
        previousFormatLocale = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(Locale.Category.FORMAT, previousFormatLocale)
        TokscaleStatsReader.commandRunnerOverride = null
        TokscaleStatsReader.clearCache()
    }

    @Test
    fun `parseJsonOutput reads Tokscale totals`() {
        val stat = TokscaleStatsReader.parseJsonOutput(
            """
            {
              "totalInput": 14719457,
              "totalOutput": 1182747,
              "totalCost": 258.7336788699999
            }
            """.trimIndent()
        )

        assertNotNull(stat)
        assertEquals(14_719_457L, stat!!.inputTokens)
        assertEquals(1_182_747L, stat.outputTokens)
        assertEquals(15_902_204L, stat.totalTokens)
        assertEquals(258.7336788699999, stat.cost, 0.000001)
    }

    @Test
    fun `parseJsonOutput tolerates surrounding npm output`() {
        val stat = TokscaleStatsReader.parseJsonOutput(
            """
            npm notice something
            {"totalInput":1000,"totalOutput":250,"totalCost":0.125}
            npm notice done
            """.trimIndent()
        )

        assertNotNull(stat)
        assertEquals(1_250L, stat!!.totalTokens)
    }

    @Test
    fun `parseJsonOutput returns null when totals are missing`() {
        assertNull(TokscaleStatsReader.parseJsonOutput("""{"entries":[]}"""))
    }

    @Test
    fun `getUsage returns parsed usage from command runner`() {
        TokscaleStatsReader.commandRunnerOverride = {
            TokscaleStatsReader.CommandOutput(
                0,
                """{"totalInput":2000,"totalOutput":3000,"totalCost":1.5}"""
            )
        }

        val result = TokscaleStatsReader.getUsage(TokscalePeriod.WEEK)

        assertNull(result.error)
        assertNotNull(result.stat)
        assertEquals(5_000L, result.stat!!.totalTokens)
        assertEquals(1.5, result.stat.cost, 0.000001)
    }

    @Test
    fun `getUsage reports command errors without growing data`() {
        TokscaleStatsReader.commandRunnerOverride = {
            TokscaleStatsReader.CommandOutput(1, "npm error registry unavailable")
        }

        val result = TokscaleStatsReader.getUsage(TokscalePeriod.MONTH)

        assertNull(result.stat)
        assertEquals("npm error registry unavailable", result.error)
    }

    @Test
    fun `formatTokens uses compact units`() {
        assertEquals("999", TokscaleStatsReader.formatTokens(999))
        assertEquals("1K", TokscaleStatsReader.formatTokens(1_000))
        assertEquals("9.5K", TokscaleStatsReader.formatTokens(9_500))
        assertEquals("14.7M", TokscaleStatsReader.formatTokens(14_719_457))
        assertEquals("1.5B", TokscaleStatsReader.formatTokens(1_533_409_133))
    }

    @Test
    fun `formatCost uses dollars`() {
        assertEquals("$0", TokscaleStatsReader.formatCost(0.0))
        assertEquals("$0", TokscaleStatsReader.formatCost(0.004))
        assertEquals("$259", TokscaleStatsReader.formatCost(258.7336788699999))
        assertEquals("$1.2K", TokscaleStatsReader.formatCost(1_234.56))
        assertEquals("$1.2M", TokscaleStatsReader.formatCost(1_234_567.0))
    }

    @Test
    fun `formatting uses system format locale`() {
        Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)

        assertEquals("9,5K", TokscaleStatsReader.formatTokens(9_500))
        assertEquals("14,7M", TokscaleStatsReader.formatTokens(14_719_457))
        assertEquals("1,5B", TokscaleStatsReader.formatTokens(1_533_409_133))
        assertEquals("$259", TokscaleStatsReader.formatCost(258.7336788699999))
        assertEquals("$1,2K", TokscaleStatsReader.formatCost(1_234.56))
    }

    @Test
    fun `getUsage stores command result in cache`() {
        TokscaleStatsReader.commandRunnerOverride = {
            TokscaleStatsReader.CommandOutput(
                0,
                """{"totalInput":4000,"totalOutput":1000,"totalCost":12.3}"""
            )
        }

        TokscaleStatsReader.getUsage(TokscalePeriod.WEEK)

        val cached = TokscaleStatsReader.getCachedUsage(TokscalePeriod.WEEK)
        assertNotNull(cached)
        assertEquals(5_000L, cached!!.stat!!.totalTokens)
    }

    @Test
    fun `areCachesValid requires both week and month cache entries`() {
        TokscaleStatsReader.commandRunnerOverride = {
            TokscaleStatsReader.CommandOutput(
                0,
                """{"totalInput":100,"totalOutput":50,"totalCost":1}"""
            )
        }

        TokscaleStatsReader.getUsage(TokscalePeriod.WEEK)
        assertEquals(false, TokscaleStatsReader.areCachesValid(15_000))

        TokscaleStatsReader.getUsage(TokscalePeriod.MONTH)
        assertEquals(true, TokscaleStatsReader.areCachesValid(15_000))
    }
}

package de.espend.ml.llm.difftastic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifftasticDiffToolOrderTest {
    @Test
    fun `normalizeOrder adds built in viewers before Difftastic for empty settings`() {
        val order = DifftasticDiffToolOrder.normalizeOrder(emptyList())

        assertEquals(DifftasticDiffToolOrder.difftasticClassName, order.last())
        assertEquals(DifftasticDiffToolOrder.defaultBuiltInOrder, order.take(DifftasticDiffToolOrder.defaultBuiltInOrder.size))
    }

    @Test
    fun `normalizeOrder moves Difftastic from first position to last`() {
        val customTool = "example.CustomDiffTool"
        val order = DifftasticDiffToolOrder.normalizeOrder(
            listOf(DifftasticDiffToolOrder.difftasticClassName, customTool)
        )

        assertEquals(customTool, order.first())
        assertEquals(DifftasticDiffToolOrder.difftasticClassName, order.last())
        assertTrue(order.containsAll(DifftasticDiffToolOrder.defaultBuiltInOrder))
    }
}

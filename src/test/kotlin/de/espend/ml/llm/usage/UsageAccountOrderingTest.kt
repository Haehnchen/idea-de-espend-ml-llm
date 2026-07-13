package de.espend.ml.llm.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAccountOrderingTest {

    @Test
    fun `accounts are sorted by weight and equal weights keep their stored order`() {
        val accounts = listOf(
            UsageAccountState(id = "second", weight = 20),
            UsageAccountState(id = "first", weight = 10),
            UsageAccountState(id = "third-a", weight = 30),
            UsageAccountState(id = "third-b", weight = 30)
        )

        assertEquals(
            listOf("first", "second", "third-a", "third-b"),
            accountsInDisplayOrder(accounts).map { it.id }
        )
    }
}

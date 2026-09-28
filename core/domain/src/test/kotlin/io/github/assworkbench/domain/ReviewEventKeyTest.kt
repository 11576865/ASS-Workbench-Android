package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReviewEventKeyTest {
    private fun event(id: Long, text: String, start: Long = 1000L) = AssEvent(
        id = id,
        start = SubTime(start),
        end = SubTime(start + 1000L),
        style = "Target",
        text = text,
    )

    @Test
    fun keysSurviveUnrelatedInsertionAndReordering() {
        val a = event(1, "A", 1000)
        val b = event(2, "B", 3000)
        val original = ReviewEventKey.keys(listOf(a, b))
        val reordered = ReviewEventKey.keys(
            listOf(
                event(99, "unrelated", 2000),
                b.copy(id = 20),
                a.copy(id = 10),
            )
        )
        assertEquals(original.getValue(1), reordered.getValue(10))
        assertEquals(original.getValue(2), reordered.getValue(20))
    }

    @Test
    fun semanticEventEditChangesKey() {
        val before = event(1, "Original")
        val after = before.copy(id = 9, text = "Edited")
        assertNotEquals(ReviewEventKey.key(before), ReviewEventKey.key(after))
    }

    @Test
    fun identicalDuplicatesReceiveOccurrenceSuffixes() {
        val first = event(1, "same")
        val second = first.copy(id = 2)
        val keys = ReviewEventKey.keys(listOf(first, second))
        assertEquals(ReviewEventKey.key(first), keys.getValue(1))
        assertEquals(ReviewEventKey.key(first) + ":1", keys.getValue(2))
    }
}

package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BilingualPairingTest {
    @Test
    fun pairsTwoStylesWithoutChangingEvents() {
        val doc = AssDocument(
            styles = listOf(AssStyle(name = "Source"), AssStyle(name = "Target")),
            events = listOf(
                AssEvent(1, start = SubTime(1000), end = SubTime(3000), style = "Source", text = "Hello"),
                AssEvent(2, start = SubTime(1100), end = SubTime(3050), style = "Target", text = "你好"),
                AssEvent(3, start = SubTime(4000), end = SubTime(5000), style = "Source", text = "Bye"),
                AssEvent(4, start = SubTime(6500), end = SubTime(7000), style = "Target", text = "孤立目标"),
            ),
        )
        val rows = BilingualPairing.pair(doc, "Source", "Target")
        assertEquals(3, rows.size)
        assertEquals(1L, rows[0].source?.id)
        assertEquals(2L, rows[0].target?.id)
        assertFalse(rows[0].timingMismatch)
        assertTrue(rows[1].missingSide)
        assertTrue(rows[2].missingSide)
        assertEquals(4, doc.events.size)
    }

    @Test
    fun marksLargerTimingDifference() {
        val doc = AssDocument(
            styles = listOf(AssStyle(name = "A"), AssStyle(name = "B")),
            events = listOf(
                AssEvent(1, start = SubTime(1000), end = SubTime(3000), style = "A", text = "A"),
                AssEvent(2, start = SubTime(1500), end = SubTime(3400), style = "B", text = "B"),
            ),
        )
        val row = BilingualPairing.pair(doc, "A", "B").single()
        assertTrue(row.timingMismatch)
    }
}

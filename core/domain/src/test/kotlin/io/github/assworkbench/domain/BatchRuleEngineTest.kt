package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class BatchRuleEngineTest {
    @Test
    fun filters_and_applies_multiple_actions_without_touching_other_events() {
        val doc = AssDocument(
            styles = listOf(AssStyle("Default"), AssStyle("Chinese")),
            events = listOf(
                AssEvent(1, start = SubTime(1000), end = SubTime(2000), style = "Chinese", text = "hello"),
                AssEvent(2, start = SubTime(3000), end = SubTime(4000), style = "Default", text = "hello"),
            ),
        )
        val preview = BatchRuleEngine.preview(
            doc,
            BatchRule(
                filters = listOf(BatchFilter.Style("Chinese"), BatchFilter.TextContains("hell")),
                actions = listOf(BatchAction.ShiftTime(100), BatchAction.SetLayer(3)),
            ),
        )
        assertEquals(setOf(1L), preview.affectedEventIds)
        assertEquals(1100L, preview.document.events[0].start.millis)
        assertEquals(3, preview.document.events[0].layer)
        assertEquals(doc.events[1], preview.document.events[1])
    }
}

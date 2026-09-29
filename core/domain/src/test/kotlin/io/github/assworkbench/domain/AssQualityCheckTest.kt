package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertTrue

class AssQualityCheckTest {
    @Test
    fun detects_timing_style_and_syntax_problems() {
        val document = AssDocument(
            styles = listOf(AssStyle(name = "Default")),
            events = listOf(
                AssEvent(
                    id = 1,
                    start = SubTime(1_000),
                    end = SubTime(1_100),
                    style = "Missing",
                    text = "{\\fs56 broken",
                ),
                AssEvent(
                    id = 2,
                    start = SubTime(1_050),
                    end = SubTime(2_000),
                    style = "Default",
                    text = "ok",
                ),
            ),
        )
        val issues = AssQualityCheck.inspect(document)
        assertTrue(issues.any { it.eventId == 1L && it.kind == AssQcKind.VERY_SHORT_DURATION })
        assertTrue(issues.any { it.eventId == 1L && it.kind == AssQcKind.UNKNOWN_STYLE })
        assertTrue(issues.any { it.eventId == 1L && it.kind == AssQcKind.INLINE_SYNTAX })
        assertTrue(issues.any { it.eventId == 1L && it.kind == AssQcKind.OVERLAP })
    }
    @Test
    fun detectsNestedOverlapAgainstOccupiedFrontier() {
        val document = AssDocument(events = listOf(
            AssEvent(id = 1, start = SubTime(0), end = SubTime(10_000), text = "long"),
            AssEvent(id = 2, start = SubTime(1_000), end = SubTime(2_000), text = "short"),
            AssEvent(id = 3, start = SubTime(3_000), end = SubTime(4_000), text = "inside"),
        ))
        val issues = AssQualityCheck.inspect(document)
        assertTrue(issues.any { it.eventId == 1L && it.kind == AssQcKind.OVERLAP && "#3" in it.message })
    }
    @Test
    fun detects_invalid_position_syntax() {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 9,
                    start = SubTime(0),
                    end = SubTime(1_000),
                    text = "{\\pos(nope,200)}Text",
                )
            )
        )
        val issues = AssQualityCheck.inspect(document)
        assertTrue(issues.any { it.eventId == 9L && it.kind == AssQcKind.INVALID_POSITION })
    }

}

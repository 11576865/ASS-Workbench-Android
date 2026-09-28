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
}

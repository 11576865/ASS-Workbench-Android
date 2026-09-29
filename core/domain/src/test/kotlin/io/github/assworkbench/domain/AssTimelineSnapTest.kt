package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AssTimelineSnapTest {
    @Test
    fun pointPrefersExplicitTargetInsideThreshold() {
        assertEquals(
            1_000,
            AssTimelineSnap.snapPoint(
                candidateMs = 1_032,
                targets = listOf(1_000, 2_000),
                thresholdMs = 40,
                gridMs = 100,
            ),
        )
    }

    @Test
    fun pointFallsBackToGridWhenNoExplicitTargetIsClose() {
        assertEquals(
            1_000,
            AssTimelineSnap.snapPoint(
                candidateMs = 1_041,
                targets = listOf(2_000),
                thresholdMs = 20,
                gridMs = 100,
            ),
        )
    }

    @Test
    fun movingSpanCanSnapEitherEdgeAndPreservesDuration() {
        val snapped = AssTimelineSnap.snapSpan(
            startMs = 1_020,
            endMs = 2_020,
            targets = listOf(2_000),
            thresholdMs = 30,
        )
        assertEquals(1_000, snapped.startMs)
        assertEquals(2_000, snapped.endMs)
        assertEquals(1_000, snapped.endMs - snapped.startMs)
    }

    @Test
    fun movingSpanUsesNearestGridEdgeWithoutChangingDuration() {
        val snapped = AssTimelineSnap.snapSpan(
            startMs = 1_060,
            endMs = 1_990,
            targets = emptyList(),
            thresholdMs = 0,
            gridMs = 100,
        )
        assertEquals(1_070, snapped.startMs)
        assertEquals(2_000, snapped.endMs)
        assertEquals(930, snapped.endMs - snapped.startMs)
    }
}

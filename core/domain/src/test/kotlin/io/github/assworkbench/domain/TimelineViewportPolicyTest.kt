package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineViewportPolicyTest {
    @Test
    fun followModeTracksPlayheadAndKeepsViewportAtOrAfterZero() {
        assertEquals(
            15_000L,
            TimelineViewportPolicy.resolveCenter(
                playheadMs = 2_000L,
                currentCenterMs = 80_000L,
                halfWindowMs = 15_000L,
                followPlayhead = true,
            ),
        )
        assertEquals(
            42_000L,
            TimelineViewportPolicy.resolveCenter(
                playheadMs = 42_000L,
                currentCenterMs = 80_000L,
                halfWindowMs = 15_000L,
                followPlayhead = true,
            ),
        )
    }

    @Test
    fun manualViewportDoesNotSnapBackWhenPlaybackAdvances() {
        assertEquals(
            80_000L,
            TimelineViewportPolicy.resolveCenter(
                playheadMs = 250_000L,
                currentCenterMs = 80_000L,
                halfWindowMs = 15_000L,
                followPlayhead = false,
            ),
        )
    }

    @Test
    fun zoomOnlyClampsManualViewportToNewMinimum() {
        assertEquals(
            60_000L,
            TimelineViewportPolicy.resolveCenter(
                playheadMs = 300_000L,
                currentCenterMs = 20_000L,
                halfWindowMs = 60_000L,
                followPlayhead = false,
            ),
        )
    }

    @Test
    fun panUsesTimelineScaleAndCannotMoveBeforeTimeZero() {
        assertEquals(
            65_000L,
            TimelineViewportPolicy.panCenter(
                currentCenterMs = 80_000L,
                dragAmountPx = 500f,
                widthPx = 1_000,
                windowDurationMs = 30_000L,
                halfWindowMs = 15_000L,
            ),
        )
        assertEquals(
            15_000L,
            TimelineViewportPolicy.panCenter(
                currentCenterMs = 16_000L,
                dragAmountPx = 500f,
                widthPx = 1_000,
                windowDurationMs = 30_000L,
                halfWindowMs = 15_000L,
            ),
        )
    }
}

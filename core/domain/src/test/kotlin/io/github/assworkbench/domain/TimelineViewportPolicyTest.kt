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
    @Test
    fun pinchZoomKeepsTouchedTimeUnderSameViewportFraction() {
        // 30 s window centered at 60 s => [45 s, 75 s].
        // Finger at 25% points at 52.5 s. Zooming to 10 s must keep 52.5 s at 25%.
        assertEquals(
            55_000L,
            TimelineViewportPolicy.zoomCenterAroundAnchor(
                currentCenterMs = 60_000L,
                oldWindowDurationMs = 30_000L,
                newWindowDurationMs = 10_000L,
                anchorFraction = 0.25f,
            ),
        )
    }

    @Test
    fun pinchZoomCannotExposeNegativeTimelineTime() {
        assertEquals(
            5_000L,
            TimelineViewportPolicy.zoomCenterAroundAnchor(
                currentCenterMs = 15_000L,
                oldWindowDurationMs = 30_000L,
                newWindowDurationMs = 10_000L,
                anchorFraction = 0f,
            ),
        )
    }
}

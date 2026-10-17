package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3 screenshot-derived geometry contract; native screenshot comparison is a
 * separate gate and cannot be replaced by these pure layout assertions.
 */
class R3FixedLayoutPolicyTest {
    @Test fun referenceCanvasKeepsStableLeftVideoTimeRightProportions() {
        // Reference screenshot 1600x960: 56 top + 50 task ≈854dp available.
        val d = R3FixedLayoutPolicy.resolve(
            widthDp = 1600f, heightDp = 854f,
            mediaAspect = 16f / 9f, preview = PreviewDensity.FULL,
        )
        assertEquals(267f, d.navigationWidthDp, 0.5f)
        assertEquals(427f, d.inspectorWidthDp, 0.5f)
        assertEquals(556f, d.mediaHeightDp, 3f)
        assertEquals(298f, d.timelineHeightDp, 3f)
        assertEquals(854f, d.mediaHeightDp + d.timelineHeightDp, 0.5f)
    }

    @Test fun mediaAspectAndTimelineSurviveFourByThreeAndPortraitReference() {
        val wide = R3FixedLayoutPolicy.resolve(1600f, 854f, 4f / 3f, PreviewDensity.FULL)
        assertTrue("4:3 image must not crop the timeline", wide.timelineHeightDp >= 247f)
        assertTrue(wide.mediaHeightDp > 0f)

        val tall = R3FixedLayoutPolicy.resolve(1180f, 620f, 9f / 16f, PreviewDensity.FULL)
        assertTrue("tall video cannot consume the permanent timeline", tall.timelineHeightDp >= 184f)
        assertEquals(620f, tall.mediaHeightDp + tall.timelineHeightDp, 0.5f)
    }

    @Test fun collapsedPreviewGivesAllSpaceToSameTimeline() {
        val d = R3FixedLayoutPolicy.resolve(1400f, 680f, 16f / 9f, PreviewDensity.HIDDEN)
        assertEquals(0f, d.mediaHeightDp, 0f)
        assertEquals(680f, d.timelineHeightDp, 0f)
    }


    @Test fun preciseTimingAllocatesMajorityToSameTimelineWithoutNewClockOrInspector() {
        val subtitle = R3FixedLayoutPolicy.resolve(
            widthDp = 1440f, heightDp = 740f,
            mediaAspect = 16f / 9f, preview = PreviewDensity.FULL,
        )
        val timing = R3FixedLayoutPolicy.resolve(
            widthDp = 1440f, heightDp = 740f,
            mediaAspect = 16f / 9f, preview = PreviewDensity.FULL,
            timingPriority = true,
        )
        assertEquals(subtitle.navigationWidthDp, timing.navigationWidthDp, 0f)
        assertEquals(subtitle.inspectorWidthDp, timing.inspectorWidthDp, 0f)
        assertTrue("Timing must expand the live timeline", timing.timelineHeightDp > subtitle.timelineHeightDp)
        assertTrue("Timing workspace should prioritize waveform and cues", timing.timelineHeightDp >= 740f * 0.65f)
        assertEquals(740f, timing.timelineHeightDp + timing.mediaHeightDp, 0.5f)
        assertTrue("Media preview remains present", timing.mediaHeightDp > 48f)
    }

    @Test fun timingModeNeverOverridesTheUsersHiddenPreviewPreference() {
        val hidden = R3FixedLayoutPolicy.resolve(
            1440f, 740f, 4f / 3f, PreviewDensity.HIDDEN,
            timingPriority = true,
        )
        assertEquals(0f, hidden.mediaHeightDp, 0f)
        assertEquals(740f, hidden.timelineHeightDp, 0f)
        val compact = R3FixedLayoutPolicy.resolve(
            1440f, 740f, 16f / 9f, PreviewDensity.COMPACT,
            timingPriority = true,
        )
        assertTrue(compact.mediaHeightDp <= 740f * 0.34f + 0.01f)
        assertEquals(740f, compact.mediaHeightDp + compact.timelineHeightDp, 0.5f)
    }

    @Test fun invalidNativeVideoRatioFallsBackWithoutNaNGeometry() {
        val invalid = R3FixedLayoutPolicy.resolve(1400f, 700f, Float.NaN, PreviewDensity.FULL)
        val fallback = R3FixedLayoutPolicy.resolve(1400f, 700f, 16f / 9f, PreviewDensity.FULL)
        assertEquals(fallback, invalid)
    }
}

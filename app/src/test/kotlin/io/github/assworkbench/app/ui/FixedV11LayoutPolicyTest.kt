package io.github.assworkbench.app.ui

import org.junit.Assert.*
import org.junit.Test

class FixedV11LayoutPolicyTest {
    @Test fun referenceColumnsMatchFrozen1440DesignIncludingPermanentRail() {
        val g = FixedV11LayoutPolicy.resolve(1440f, 752f, 16f/9f, PreviewDensity.FULL,
            textDockRequested = true)
        assertEquals(309f, g.navigationWidthDp, 0.1f)
        assertEquals(326f, g.inspectorWidthDp, 0.1f)
        assertTrue(g.mediaHeightDp > 250f)
        assertTrue(g.timelineHeightDp >= 184f)
        assertEquals(752f, g.mediaHeightDp + g.timelineHeightDp + g.textDockHeightDp + 2f, 0.1f)
    }

    @Test fun timingAndHiddenVideoKeepTheirOwnBudgetsWithoutDroppingTextDock() {
        val normal = FixedV11LayoutPolicy.resolve(1440f, 752f, 16f/9f, PreviewDensity.FULL)
        val timing = FixedV11LayoutPolicy.resolve(1440f, 752f, 16f/9f, PreviewDensity.FULL, true)
        assertTrue(timing.timelineHeightDp > normal.timelineHeightDp)
        val hidden = FixedV11LayoutPolicy.resolve(1440f, 752f, Float.NaN, PreviewDensity.HIDDEN,
            textDockRequested = true)
        assertEquals(0f, hidden.mediaHeightDp, 0f)
        assertTrue(hidden.textDockHeightDp > 0f)
        assertEquals(750f, hidden.timelineHeightDp + hidden.textDockHeightDp, 0.1f)
    }

    @Test fun veryShortBodyCannotProduceNegativeOrOverflowingVerticalGeometry() {
        for(height in listOf(0f, 100f, 420f, 560f, 900f)) {
            val g = FixedV11LayoutPolicy.resolve(1180f, height, 9f/16f, PreviewDensity.FULL,
                textDockRequested = true)
            assertTrue(g.mediaHeightDp >= 0f && g.timelineHeightDp >= 0f && g.textDockHeightDp >= 0f)
            assertTrue(g.mediaHeightDp + g.timelineHeightDp + g.textDockHeightDp <= height + 0.1f)
        }
    }
    @Test fun compactTabletReservesDesignInspectorAndKeepsCentralRawBesideIt() {
        val g = FixedV11LayoutPolicy.resolve(1024f, 629f, 16f/9f, PreviewDensity.FULL,
            textDockRequested = true, compact = true)
        assertEquals(0f, g.navigationWidthDp, 0f)
        assertEquals(310f, g.inspectorWidthDp, 0.1f)
        assertTrue(g.textDockHeightDp >= 184f)
        assertTrue(g.timelineHeightDp >= 184f)
        assertTrue(g.mediaHeightDp > 180f)
        assertEquals(629f, g.mediaHeightDp + g.timelineHeightDp + g.textDockHeightDp + 2f, 0.1f)
    }
}

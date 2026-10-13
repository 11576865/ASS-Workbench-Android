package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineDockPolicyTest {
    @Test
    fun resizeClampsToProfessionalWorkspaceBounds() {
        assertEquals(0.30f, TimelineDockPolicy.resize(0.32f, -0.20f), 0.0001f)
        assertEquals(0.72f, TimelineDockPolicy.resize(0.66f, 0.20f), 0.0001f)
        assertEquals(0.55f, TimelineDockPolicy.resize(0.50f, 0.05f), 0.0001f)
    }

    @Test
    fun snapUsesStableWorkingStops() {
        assertEquals(0.38f, TimelineDockPolicy.snap(0.34f), 0.0001f)
        assertEquals(0.50f, TimelineDockPolicy.snap(0.47f), 0.0001f)
        assertEquals(0.64f, TimelineDockPolicy.snap(0.69f), 0.0001f)
    }

    @Test
    fun verticalIntentExpandsUpAndCollapsesDown() {
        assertTrue(TimelineDockPolicy.expansionAfterDrag(false, -0.05f))
        assertFalse(TimelineDockPolicy.expansionAfterDrag(true, 0.09f))
        assertTrue(TimelineDockPolicy.expansionAfterDrag(true, 0.01f))
        assertFalse(TimelineDockPolicy.expansionAfterDrag(false, -0.01f))
    }
    @Test fun canvasDockReservesEditorSpaceAndSuspendsInShortViewport() {
        val normal = TimelineDockPolicy.canvasLayout(720f, true, 0.64f)
        assertEquals(460.8f, normal.heightDp, 0.001f)
        assertFalse(normal.compact)
        assertFalse(normal.suspended)
        val constrained = TimelineDockPolicy.canvasLayout(400f, true, 0.64f)
        assertEquals(192f, constrained.heightDp, 0.001f)
        assertTrue(constrained.compact)
        val short = TimelineDockPolicy.canvasLayout(240f, true, 0.64f)
        assertEquals(0f, short.heightDp, 0f)
        assertTrue(short.suspended)
        // Temporary projection never overwrites the preferred fraction.
        assertEquals(normal, TimelineDockPolicy.canvasLayout(720f, true, 0.64f))
    }

    @Test fun canvasDragStartsAtMeasuredHeightWithoutPresetJump() {
        val compact = TimelineDockPolicy.canvasLayout(720f, false, 0.42f)
        assertEquals(142f, compact.heightDp, 0f)
        val fraction = TimelineDockPolicy.canvasResizeExtent(720f, compact.heightDp, -10f)
        assertEquals(152f, TimelineDockPolicy.canvasLayout(720f, true, fraction).heightDp, 0.001f)
        assertEquals(512f / 720f, TimelineDockPolicy.canvasResizeExtent(720f, 142f, -9999f), 0.001f)
        assertEquals(142f / 720f, TimelineDockPolicy.canvasResizeExtent(720f, 300f, 9999f), 0.001f)
    }

    @Test fun canvasReleaseOnlySnapsNearWorkingStop() {
        assertEquals(0.38f, TimelineDockPolicy.canvasSettleExtent(720f, 0.39f), 0f)
        assertEquals(0.31f, TimelineDockPolicy.canvasSettleExtent(720f, 0.31f), 0f)
        assertEquals(142f / 720f, TimelineDockPolicy.canvasSettleExtent(720f, 150f / 720f), 0.001f)
    }

    @Test fun canvasProjectionRejectsNonfiniteOrEmptyViewport() {
        for (height in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
            assertTrue(TimelineDockPolicy.canvasLayout(height, true, 0.5f).suspended)
            assertTrue(TimelineDockPolicy.canvasResizeExtent(height, 142f, -10f).isFinite())
        }
        assertEquals(302.4f, TimelineDockPolicy.canvasLayout(720f, true, Float.NaN).heightDp, 0.001f)
    }
}

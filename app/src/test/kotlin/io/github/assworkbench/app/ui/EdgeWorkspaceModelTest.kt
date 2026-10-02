package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeWorkspaceModelTest {
    @Test
    fun panelExtentClampsAndSnapsToStableStops() {
        val expanded = EdgePanelState(extentFraction = 0.38f).withExtent(0.91f)
        assertEquals(0.62f, expanded.extentFraction)

        val snapped = EdgePanelState(extentFraction = 0.49f).snapped()
        assertEquals(0.52f, snapped.extentFraction)
    }

    @Test
    fun residentToggleKeepsPanelOpen() {
        val state = EdgeWorkspaceState()
            .toggleResident(EdgeDockSide.RIGHT)

        assertTrue(state.right.open)
        assertTrue(state.right.resident)
    }

    @Test
    fun bookmarkCanCrossSidesWithoutLosingIdentity() {
        val initial = EdgeWorkspaceState()
            .addOrActivateBookmark("STYLE:primary", "STYLE", EdgeDockSide.RIGHT)

        val moved = initial.moveBookmark("STYLE:primary", EdgeDockSide.LEFT)
        val bookmark = moved.bookmarks.single()

        assertEquals("STYLE:primary", bookmark.instanceId)
        assertEquals(EdgeDockSide.LEFT, bookmark.side)
        assertEquals("STYLE:primary", moved.activeBookmarkId)
    }

    @Test
    fun closingPanelDoesNotDeleteBookmarks() {
        val state = EdgeWorkspaceState(
            right = EdgePanelState(open = true),
        ).addOrActivateBookmark("POSITION:primary", "POSITION", EdgeDockSide.RIGHT)

        val closed = state.updatePanel(
            EdgeDockSide.RIGHT,
            state.right.copy(open = false),
        )

        assertFalse(closed.right.open)
        assertEquals(1, closed.bookmarks.size)
    }
}

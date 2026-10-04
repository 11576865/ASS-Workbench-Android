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
}

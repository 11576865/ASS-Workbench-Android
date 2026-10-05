package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceXYPadTest {
    @Test fun grabPreservesValueAndDragUsesIndependentScriptScales() {
        val drag = WorkspaceXYPadDrag.begin(500.0, 200.0, 10.0, 20.0, 200.0, 100.0, 1920.0, 1080.0)!!
        assertEquals(500.0, drag.move(10.0, 20.0)!!.x, 0.001)
        val next = drag.move(30.0, 30.0)!!
        assertEquals(692.0, next.x, 0.001)
        assertEquals(308.0, next.y, 0.001)
    }

    @Test fun boundsClampWithoutAccumulatedDriftWhenReturning() {
        val drag = WorkspaceXYPadDrag.begin(500.0, 200.0, 10.0, 20.0, 200.0, 100.0, 1920.0, 1080.0)!!
        assertEquals(1920.0, drag.move(1000.0, 1000.0)!!.x, 0.001)
        assertEquals(0.0, drag.move(-1000.0, -1000.0)!!.y, 0.001)
        assertEquals(500.0, drag.move(10.0, 20.0)!!.x, 0.001)
    }

    @Test fun invalidDimensionsAndCoordinatesAreRejected() {
        assertNull(WorkspaceXYPadDrag.begin(0.0, 0.0, 0.0, 0.0, 0.0, 100.0, 1920.0, 1080.0))
        assertNull(WorkspaceXYPadDrag.begin(Double.NaN, 0.0, 0.0, 0.0, 200.0, 100.0, 1920.0, 1080.0))
        assertNull(WorkspaceXYPadDrag.begin(0.0, 0.0, 0.0, 0.0, 200.0, 100.0, -1.0, 1080.0))
        val drag = WorkspaceXYPadDrag.begin(0.0, 0.0, 0.0, 0.0, 200.0, 100.0, 1920.0, 1080.0)!!
        assertNull(drag.move(Double.POSITIVE_INFINITY, 0.0))
    }
}

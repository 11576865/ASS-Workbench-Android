package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class WorkspaceAngleDialTest {
    private fun direction(degrees: Double): Pair<Double, Double> {
        val radians = Math.toRadians(degrees)
        return cos(radians) * 50.0 to -sin(radians) * 50.0
    }

    @Test fun grabDoesNotJumpAndCounterclockwiseAddsToExistingTurns() {
        val start = direction(90.0)
        val drag = WorkspaceAngleDialDrag.begin(725.0, start.first, start.second, 8.0)!!
        assertEquals(725.0, drag.value, 0.001)
        val next = direction(120.0)
        assertEquals(755.0, drag.move(next.first, next.second)!!.value, 0.001)
    }

    @Test fun crossingBranchCutStaysContinuousInBothDirections() {
        val start = direction(179.0)
        val drag = WorkspaceAngleDialDrag.begin(40.0, start.first, start.second, 8.0)!!
        val next = direction(-179.0)
        val moved = drag.move(next.first, next.second)!!
        assertEquals(42.0, moved.value, 0.001)
        assertEquals(40.0, moved.move(start.first, start.second)!!.value, 0.001)
    }

    @Test fun centerAndInvalidCoordinatesDoNotPublishValues() {
        assertNull(WorkspaceAngleDialDrag.begin(0.0, 0.0, 0.0, 8.0))
        assertNull(WorkspaceAngleDialDrag.begin(Double.NaN, 50.0, 0.0, 8.0))
        val drag = WorkspaceAngleDialDrag.begin(10.0, 50.0, 0.0, 8.0)!!
        assertNull(drag.move(0.0, 0.0))
        assertNull(drag.move(Double.POSITIVE_INFINITY, 0.0))
        assertEquals(100.0, drag.move(0.0, -50.0)!!.value, 0.001)
    }
}

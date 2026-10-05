package io.github.assworkbench.app.ui.workspace

import io.github.assworkbench.domain.AssPoint
import org.junit.Assert.*
import org.junit.Test

class WorkspaceTransformRangeTest {
    @Test fun shearPadPreservesNegativeValuesAndZeroCenter() {
        val range = WorkspaceTransformRange.shear
        assertEquals(AssPoint(0.0, 20.0), range.toPad(AssPoint(-10.0, 10.0)))
        assertEquals(AssPoint(0.0, 0.0), range.fromPad(AssPoint(10.0, 10.0)))
        assertEquals(AssPoint(-2.5, 3.0), range.fromPad(range.toPad(AssPoint(-2.5, 3.0))))
    }
    @Test fun scalePadPreservesPercentAndUsesCanonicalBounds() {
        val range = WorkspaceTransformRange.scale
        assertEquals(AssPoint(99.0, 149.0), range.toPad(AssPoint(100.0, 150.0)))
        assertEquals(AssPoint(1.0, 1000.0), range.fromPad(AssPoint(0.0, 999.0)))
        assertEquals(999.0, range.span, 0.0)
    }
    @Test fun invalidDraftsCannotPublishAndOutOfRangeDisplayClamps() {
        val range = WorkspaceTransformRange.scale
        assertNull(range.parse("NaN", "100"))
        assertNull(range.parse("0", "100"))
        assertNull(range.parse("100", "1001"))
        assertNull(WorkspaceTransformRange.shear.parse("-11", "0"))
        assertEquals(AssPoint(100.0, 150.0), range.parse("100", "150"))
        assertEquals(AssPoint(0.0, 999.0), range.toPad(AssPoint(-20.0, 2000.0)))
    }
}

package io.github.assworkbench.app.ui.workspace

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceParameterAngleDialTest {
    @Test
    fun dialUsesTopAsZeroAndClockwisePositive() {
        assertEquals(0f, rotationDialAngle(Offset(50f, 0f), 100f, 100f), 0.001f)
        assertEquals(90f, rotationDialAngle(Offset(100f, 50f), 100f, 100f), 0.001f)
        assertEquals(-90f, rotationDialAngle(Offset(0f, 50f), 100f, 100f), 0.001f)
        assertEquals(180f, kotlin.math.abs(rotationDialAngle(Offset(50f, 100f), 100f, 100f)), 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun dialRejectsZeroSizedSurface() {
        rotationDialAngle(Offset.Zero, 0f, 100f)
    }
}

package io.github.assworkbench.app.ui.interaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class FixedRodTest {
    @Test fun fullOrbitDoesNotMoveSubtitle() {
        var previous = 0f
        for (degree in 0..360) {
            val angle = (degree * PI / 180).toFloat()
            val step = FixedRod.advance(1900f, 1000f, 1900f + cos(angle) * 112f,
                1000f + sin(angle) * 112f, 112f, previous)
            assertEquals(0f, step.dx, 0.0002f)
            assertEquals(0f, step.dy, 0.0002f)
            previous = step.angle
        }
    }
    @Test fun pushAndPullWorkOnEverySideAndKeepLength() {
        for (degree in 0 until 360 step 15) {
            val angle = (degree * PI / 180).toFloat()
            for (distance in listOf(72f, 112f, 152f)) {
                val px = cos(angle) * distance
                val py = sin(angle) * distance
                val step = FixedRod.advance(0f, 0f, px, py, 112f, angle)
                assertEquals(cos(angle) * (distance - 112f), step.dx, 0.0001f)
                assertEquals(sin(angle) * (distance - 112f), step.dy, 0.0001f)
                assertEquals(112f, hypot(px - step.dx, py - step.dy), 0.0001f)
            }
        }
    }
    @Test fun rotationCrossesAngleSeamWithoutJump() {
        val delta = FixedRod.angularDelta((179 * PI / 180).toFloat(), (-179 * PI / 180).toFloat())
        assertEquals((2 * PI / 180).toFloat(), delta, 0.000001f)
    }
    @Test fun fingerAtAnchorRetainsDirectionAndFiniteValues() {
        val step = FixedRod.advance(42f, 42f, 42f, 42f, 112f, 1f)
        assertEquals(1f, step.angle, 0f)
        assertTrue(step.dx.isFinite() && step.dy.isFinite())
        assertEquals(112f, hypot(step.dx, step.dy), 0.0001f)
    }
}

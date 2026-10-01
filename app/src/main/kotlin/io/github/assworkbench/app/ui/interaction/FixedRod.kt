package io.github.assworkbench.app.ui.interaction

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Screen-space geometry. Orbit changes the tool, never the subtitle. */
internal object FixedRod {
    data class Step(val dx: Float, val dy: Float, val angle: Float, val radial: Float)

    fun advance(anchorX: Float, anchorY: Float, fingerX: Float, fingerY: Float,
                length: Float, previousAngle: Float): Step {
        require(length > 0f && length.isFinite())
        val vx = fingerX - anchorX
        val vy = fingerY - anchorY
        val distance = hypot(vx, vy)
        val angle = if (distance > 0.001f) atan2(vy, vx) else previousAngle
        val radial = distance - length
        return Step(cos(angle) * radial, sin(angle) * radial, angle, radial)
    }

    fun angularDelta(previous: Float, next: Float): Float =
        atan2(sin(next - previous), cos(next - previous))
}

package io.github.assworkbench.app.ui.workspace

import kotlin.math.atan2
import kotlin.math.hypot

/** Relative, counterclockwise-positive rotation; preserves complete ASS turns. */
internal class WorkspaceAngleDialDrag private constructor(
    val value: Double,
    private val direction: Double,
    private val deadRadius: Double,
) {
    fun move(x: Double, y: Double): WorkspaceAngleDialDrag? {
        val next = direction(x, y, deadRadius) ?: return null
        var delta = next - direction
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        val nextValue = value + delta
        return if (nextValue.isFinite()) WorkspaceAngleDialDrag(nextValue, next, deadRadius) else null
    }

    companion object {
        fun begin(value: Double, x: Double, y: Double, deadRadius: Double): WorkspaceAngleDialDrag? {
            if (!value.isFinite() || !deadRadius.isFinite() || deadRadius <= 0.0) return null
            val direction = direction(x, y, deadRadius) ?: return null
            return WorkspaceAngleDialDrag(value, direction, deadRadius)
        }

        private fun direction(x: Double, y: Double, deadRadius: Double): Double? {
            if (!x.isFinite() || !y.isFinite() || hypot(x, y) < deadRadius) return null
            return Math.toDegrees(atan2(-y, x))
        }
    }
}

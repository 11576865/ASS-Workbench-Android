package io.github.assworkbench.app.ui.workspace

import io.github.assworkbench.domain.AssPoint

/** Relative pointer displacement mapped to ASS script coordinates, without accumulated clamp drift. */
internal class WorkspaceXYPadDrag private constructor(
    private val originX: Double, private val originY: Double,
    private val pointerX: Double, private val pointerY: Double,
    private val scaleX: Double, private val scaleY: Double,
    private val scriptWidth: Double, private val scriptHeight: Double,
) {
    fun move(x: Double, y: Double): AssPoint? {
        if (!x.isFinite() || !y.isFinite()) return null
        val nextX = originX + (x - pointerX) * scaleX
        val nextY = originY + (y - pointerY) * scaleY
        if (!nextX.isFinite() || !nextY.isFinite()) return null
        return AssPoint(nextX.coerceIn(0.0, scriptWidth), nextY.coerceIn(0.0, scriptHeight))
    }

    companion object {
        fun begin(x: Double, y: Double, pointerX: Double, pointerY: Double,
                  width: Double, height: Double, scriptWidth: Double, scriptHeight: Double): WorkspaceXYPadDrag? {
            if (!listOf(x, y, pointerX, pointerY, width, height, scriptWidth, scriptHeight).all(Double::isFinite)) return null
            if (width <= 0.0 || height <= 0.0 || scriptWidth <= 0.0 || scriptHeight <= 0.0) return null
            return WorkspaceXYPadDrag(x, y, pointerX, pointerY, scriptWidth / width, scriptHeight / height,
                scriptWidth, scriptHeight)
        }
    }
}

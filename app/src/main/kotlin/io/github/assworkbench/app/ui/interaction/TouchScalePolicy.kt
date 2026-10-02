package io.github.assworkbench.app.ui.interaction

internal data class TouchScaleValue(
    val x: Double,
    val y: Double,
)

internal object TouchScalePolicy {
    private const val MIN = 1.0
    private const val MAX = 1000.0

    fun xy(baseX: Double, baseY: Double, deltaX: Double, deltaY: Double, locked: Boolean): TouchScaleValue {
        val x0 = baseX.coerceIn(MIN, MAX)
        val y0 = baseY.coerceIn(MIN, MAX)
        val x1 = (x0 + deltaX).coerceIn(MIN, MAX)
        val y1 = (y0 + deltaY).coerceIn(MIN, MAX)
        if (!locked) return TouchScaleValue(x1, y1)

        val factor = ((x1 / x0) + (y1 / y0)) / 2.0
        return scaled(x0, y0, factor)
    }

    fun x(baseX: Double, baseY: Double, deltaX: Double, locked: Boolean): TouchScaleValue {
        val x0 = baseX.coerceIn(MIN, MAX)
        val y0 = baseY.coerceIn(MIN, MAX)
        val x1 = (x0 + deltaX).coerceIn(MIN, MAX)
        return if (locked) scaled(x0, y0, x1 / x0) else TouchScaleValue(x1, y0)
    }

    fun y(baseX: Double, baseY: Double, deltaY: Double, locked: Boolean): TouchScaleValue {
        val x0 = baseX.coerceIn(MIN, MAX)
        val y0 = baseY.coerceIn(MIN, MAX)
        val y1 = (y0 + deltaY).coerceIn(MIN, MAX)
        return if (locked) scaled(x0, y0, y1 / y0) else TouchScaleValue(x0, y1)
    }

    private fun scaled(baseX: Double, baseY: Double, factor: Double): TouchScaleValue =
        TouchScaleValue(
            x = (baseX * factor.coerceIn(0.01, 10.0)).coerceIn(MIN, MAX),
            y = (baseY * factor.coerceIn(0.01, 10.0)).coerceIn(MIN, MAX),
        )
}

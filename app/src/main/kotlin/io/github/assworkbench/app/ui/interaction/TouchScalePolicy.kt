package io.github.assworkbench.app.ui.interaction

internal data class TouchScaleValue(
    val x: Double,
    val y: Double,
)

internal object TouchScalePolicy {
    private const val MIN = 1.0
    private const val MAX = 1000.0

    fun xy(
        baseX: Double,
        baseY: Double,
        deltaX: Double,
        deltaY: Double,
        locked: Boolean,
        snapStep: Double? = null,
    ): TouchScaleValue {
        val x0 = baseX.coerceIn(MIN, MAX)
        val y0 = baseY.coerceIn(MIN, MAX)
        val x1 = (x0 + deltaX).coerceIn(MIN, MAX)
        val y1 = (y0 + deltaY).coerceIn(MIN, MAX)
        if (!locked) {
            return TouchScaleValue(
                x = snap(x1, snapStep),
                y = snap(y1, snapStep),
            )
        }

        val factor = ((x1 / x0) + (y1 / y0)) / 2.0
        val raw = scaled(x0, y0, factor)
        val step = validStep(snapStep) ?: return raw
        val normalizedDx = kotlin.math.abs((x1 - x0) / x0)
        val normalizedDy = kotlin.math.abs((y1 - y0) / y0)
        return if (normalizedDx >= normalizedDy) {
            scaled(x0, y0, snap(raw.x, step) / x0)
        } else {
            scaled(x0, y0, snap(raw.y, step) / y0)
        }
    }

    fun x(
        baseX: Double,
        baseY: Double,
        deltaX: Double,
        locked: Boolean,
        snapStep: Double? = null,
    ): TouchScaleValue {
        val x0 = baseX.coerceIn(MIN, MAX)
        val y0 = baseY.coerceIn(MIN, MAX)
        val x1 = (x0 + deltaX).coerceIn(MIN, MAX)
        if (!locked) return TouchScaleValue(snap(x1, snapStep), y0)

        val driver = snap(x1, snapStep)
        return scaled(x0, y0, driver / x0)
    }

    fun y(
        baseX: Double,
        baseY: Double,
        deltaY: Double,
        locked: Boolean,
        snapStep: Double? = null,
    ): TouchScaleValue {
        val x0 = baseX.coerceIn(MIN, MAX)
        val y0 = baseY.coerceIn(MIN, MAX)
        val y1 = (y0 + deltaY).coerceIn(MIN, MAX)
        if (!locked) return TouchScaleValue(x0, snap(y1, snapStep))

        val driver = snap(y1, snapStep)
        return scaled(x0, y0, driver / y0)
    }

    private fun snap(value: Double, step: Double?): Double {
        val valid = validStep(step) ?: return value.coerceIn(MIN, MAX)
        return (kotlin.math.round(value / valid) * valid).coerceIn(MIN, MAX)
    }

    private fun validStep(step: Double?): Double? =
        step?.takeIf { it.isFinite() && it > 0.0 }

    private fun scaled(baseX: Double, baseY: Double, factor: Double): TouchScaleValue {
        val minFactor = maxOf(MIN / baseX, MIN / baseY)
        val maxFactor = minOf(MAX / baseX, MAX / baseY)
        val safeFactor = factor.coerceIn(minFactor, maxFactor)
        return TouchScaleValue(
            x = baseX * safeFactor,
            y = baseY * safeFactor,
        )
    }
}

package io.github.assworkbench.app.ui.workspace

import io.github.assworkbench.domain.AssPoint

internal data class WorkspaceTransformRange(val min: Double, val max: Double) {
    val span: Double get() = max - min
    fun toPad(point: AssPoint) = AssPoint(point.x.coerceIn(min, max) - min, point.y.coerceIn(min, max) - min)
    fun fromPad(point: AssPoint) = AssPoint(point.x + min, point.y + min)
    fun parse(x: String, y: String): AssPoint? {
        val first = x.toDoubleOrNull()?.takeIf { it.isFinite() && it in min..max } ?: return null
        val second = y.toDoubleOrNull()?.takeIf { it.isFinite() && it in min..max } ?: return null
        return AssPoint(first, second)
    }
    companion object {
        val scale = WorkspaceTransformRange(1.0, 1000.0)
        val shear = WorkspaceTransformRange(-10.0, 10.0)
    }
}

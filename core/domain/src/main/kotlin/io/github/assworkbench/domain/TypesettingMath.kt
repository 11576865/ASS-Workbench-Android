package io.github.assworkbench.domain

import kotlin.math.roundToInt

data class Bilingual6040Layout(
    val playResX: Int,
    val playResY: Int,
    val marginHorizontal: Int,
    val marginVertical: Int,
    val sourceBoundaryY: Int,
    val targetBoundaryY: Int,
    val sourceMarginV: Int,
    val targetMarginV: Int,
    val centralGap: Int,
)

object TypesettingMath {
    fun bilingual6040(
        playResX: Int,
        playResY: Int,
        marginHorizontalPercent: Double = 0.03,
        marginVerticalPercent: Double = 0.05,
        centralGap: Int = 20,
    ): Bilingual6040Layout {
        val width = playResX.coerceAtLeast(1)
        val height = playResY.coerceAtLeast(1)
        val marginH = (width * marginHorizontalPercent.coerceIn(0.0, 0.40)).roundToInt()
        val marginV = (height * marginVerticalPercent.coerceIn(0.0, 0.40)).roundToInt()
        val gap = centralGap.coerceIn(0, height)
        val safeHeight = (height - marginV * 2).coerceAtLeast(0)
        val available = (safeHeight - gap).coerceAtLeast(0)
        val sourceHeight = (available * 0.60).roundToInt()
        val sourceBoundary = (marginV + sourceHeight).coerceIn(0, height)
        val targetBoundary = (sourceBoundary + gap).coerceIn(0, height)
        return Bilingual6040Layout(
            playResX = width,
            playResY = height,
            marginHorizontal = marginH,
            marginVertical = marginV,
            sourceBoundaryY = sourceBoundary,
            targetBoundaryY = targetBoundary,
            sourceMarginV = (height - sourceBoundary).coerceAtLeast(0),
            targetMarginV = targetBoundary,
            centralGap = gap,
        )
    }
}

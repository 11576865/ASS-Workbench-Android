package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TypesettingMathTest {
    @Test
    fun hsrStyle6040GeometryMatchesReferenceCanvas() {
        val layout = TypesettingMath.bilingual6040(1920, 1080)
        assertEquals(58, layout.marginHorizontal)
        assertEquals(54, layout.marginVertical)
        assertEquals(625, layout.sourceBoundaryY)
        assertEquals(645, layout.targetBoundaryY)
        assertEquals(455, layout.sourceMarginV)
        assertEquals(645, layout.targetMarginV)
    }
}

package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameTimeMapTest {
    @Test
    fun maps_cfr_with_rational_rate() {
        val map = FrameTimeMap.Cfr(60_000, 1001)
        assertEquals(59L, map.frameAtOrBefore(1000))
        assertEquals(1001L, map.timeForFrame(60))
    }

    @Test
    fun maps_vfr_from_explicit_boundaries() {
        val map = FrameTimeMap.Vfr(longArrayOf(0, 17, 33, 51, 68))
        assertEquals(2L, map.frameAtOrBefore(50))
        assertEquals(51L, map.timeForFrame(3))
    }
}

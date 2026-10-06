package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class CanvasOverviewTest {
    @Test fun mapRetainsFarAndHiddenNodesWithoutChangingWorldGeometry() {
        val nodes = listOf(
            InfiniteCanvasNode("near", x = -100f, y = -100f),
            InfiniteCanvasNode("far", x = 9900f, y = -100f, hidden = true),
        )
        val map = canvasOverview(nodes)
        assertEquals(listOf("near", "far"), map.map { it.id })
        assertEquals(0f, map.first().left, 0.00001f)
        assertEquals(1f, map.last().right, 0.00001f)
        assertTrue(map.last().hidden)
        assertEquals(9900f, nodes.last().x, 0f)
        assertTrue(map.all { it.top >= 0f && it.bottom <= 1f })
    }

    @Test fun extremeFiniteCoordinatesDoNotOverflowTheMap() {
        val map = canvasOverview(listOf(
            InfiniteCanvasNode("left", x = -Float.MAX_VALUE),
            InfiniteCanvasNode("right", x = Float.MAX_VALUE),
        ))
        assertEquals(2, map.size)
        assertEquals(0f, map.first().left, 0f)
        assertEquals(1f, map.last().right, 0f)
        assertTrue(map.all { listOf(it.left, it.top, it.right, it.bottom).all(Float::isFinite) })
    }

    @Test fun overlappingMarkersChooseHighestLayerAndEmptySpaceDoesNotSelect() {
        val map = canvasOverview(listOf(
            InfiniteCanvasNode("back", z = 2),
            InfiniteCanvasNode("front", z = 8),
        ))
        assertEquals("front", hitCanvasOverview(map, 0.5f, 0.5f))
        assertNull(hitCanvasOverview(map, -0.5f, 0.5f))
        assertNull(hitCanvasOverview(map, Float.NaN, 0.5f))
    }

    @Test fun tinyFarMarkersHaveASelectableNeighborhood() {
        val map = canvasOverview(listOf(
            InfiniteCanvasNode("near"), InfiniteCanvasNode("far", x = 100_000f),
        ))
        assertEquals("far", hitCanvasOverview(map, 0.98f, 0.5f, tolerance = 0.03f))
        assertNull(hitCanvasOverview(map, 0.5f, 0.9f, tolerance = 0.03f))
    }

    @Test fun invalidGeometryIsExcludedAndEmptyMapIsSafe() {
        assertTrue(canvasOverview(emptyList()).isEmpty())
        val map = canvasOverview(listOf(
            InfiniteCanvasNode("valid"), InfiniteCanvasNode("nan", x = Float.NaN),
            InfiniteCanvasNode("zero", width = 0f),
        ))
        assertEquals(listOf("valid"), map.map { it.id })
        assertNull(hitCanvasOverview(emptyList(), 0.5f, 0.5f))
    }
}

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


    @Test fun searchMatchesTitleSubtitleAndIdentityWithoutChangingSourceNodes() {
        val entries = listOf(
            InfiniteCanvasEntry("preview", "视频", "预览画面"),
            InfiniteCanvasEntry("TEXT:primary", "正文工具", "Follow Focus"),
            InfiniteCanvasEntry("POSITION:primary", "位置", "固定字幕"),
            InfiniteCanvasEntry("orphan", "离线工具"),
        )
        val nodes = listOf(
            InfiniteCanvasNode("preview"),
            InfiniteCanvasNode("TEXT:primary", x = 3000f, hidden = true),
            InfiniteCanvasNode("POSITION:primary", x = -500f, hidden = true),
        )
        val original = nodes.toList()
        assertEquals(listOf("TEXT:primary"),
            filterCanvasOverviewEntries(entries, nodes, "  FOLLOW FOCUS  ").map { it.first.id })
        assertEquals(listOf("TEXT:primary"),
            filterCanvasOverviewEntries(entries, nodes, "text:PRIMARY").map { it.first.id })
        assertEquals(listOf("POSITION:primary"),
            filterCanvasOverviewEntries(entries, nodes, "位置", hiddenOnly = true).map { it.first.id })
        assertEquals(listOf("TEXT:primary", "POSITION:primary"),
            filterCanvasOverviewEntries(entries, nodes, "", hiddenOnly = true).map { it.first.id })
        assertTrue(filterCanvasOverviewEntries(entries, nodes, "missing").isEmpty())
        assertEquals(original, nodes)
        assertEquals(3, filterCanvasOverviewEntries(entries, nodes, " ").size)
    }

    @Test fun birdseyeSearchSelectionKeepsFullMapProjectionAndHitIsolation() {
        val entries = listOf(
            InfiniteCanvasEntry("near", "附近预览"),
            InfiniteCanvasEntry("far", "远端音频"),
        )
        val nodes = listOf(
            InfiniteCanvasNode("near", x = 0f),
            InfiniteCanvasNode("far", x = 10_000f, hidden = true),
        )
        val full = canvasOverview(nodes)
        val filteredIds = filterCanvasOverviewEntries(entries, nodes, "远端", hiddenOnly = true)
            .mapTo(mutableSetOf()) { it.first.id }
        val selectable = full.filter { it.id in filteredIds }
        assertEquals(2, full.size)
        assertEquals(listOf("far"), selectable.map { it.id })
        val near = full.first { it.id == "near" }
        val far = full.first { it.id == "far" }
        assertNull(hitCanvasOverview(selectable, (near.left + near.right) / 2f,
            (near.top + near.bottom) / 2f, tolerance = 0.01f))
        assertEquals("far", hitCanvasOverview(selectable, (far.left + far.right) / 2f,
            (far.top + far.bottom) / 2f, tolerance = 0.01f))
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

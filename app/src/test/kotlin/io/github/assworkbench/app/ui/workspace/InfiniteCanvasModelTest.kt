package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class InfiniteCanvasModelTest {
    @Test fun zoomKeepsTheWorldPointUnderTheAnchor() {
        val camera = InfiniteCanvasCamera(80f, -40f, 0.7f)
        val next = camera.zoomAt(330f, 240f, 1.4f)
        assertEquals((330f - camera.x) / camera.scale, (330f - next.x) / next.scale, 0.001f)
        assertEquals((240f - camera.y) / camera.scale, (240f - next.y) / next.scale, 0.001f)
    }
    @Test fun nodesCanMoveOutsideTheViewportAndResizeHasOnlyMinimumBounds() {
        val node = InfiniteCanvasNode("video", 20f, 30f, 480f, 300f)
        assertEquals(-1980f, node.move(-2000f, 0f).x, 0f)
        assertEquals(540f, node.resize(60f, 0f).width, 0f)
        assertEquals(220f, node.resize(-2000f, 0f).width, 0f)
    }
    @Test fun explicitRaisePreservesGeometryAndOtherLayers() {
        val nodes = listOf(InfiniteCanvasNode("video", z = 1), InfiniteCanvasNode("audio", z = 2))
        assertEquals(nodes.last(), raiseCanvasNode(nodes, "video").last())
        assertEquals(nodes.first().x, raiseCanvasNode(nodes, "video").first().x, 0f)
        assertTrue(raiseCanvasNode(nodes, "video").first { it.id == "video" }.z > 2)
    }
    @Test fun persistencePreservesHiddenOverlayAndCamera() {
        val camera = InfiniteCanvasCamera(-800f, 720f, 0.6f)
        val nodes = listOf(InfiniteCanvasNode("audio", -450f, 680f, alpha = 0.2f, passthrough = true, hidden = true))
        val restored = InfiniteCanvasPersistence.decode(InfiniteCanvasPersistence.encode(camera, nodes))
        assertEquals(camera, restored.first)
        assertEquals(nodes, restored.second)
    }
    @Test fun invalidNumbersCannotPoisonProjectionOrRestore() {
        val c = InfiniteCanvasCamera()
        assertEquals(c, c.zoomAt(Float.NaN, 30f, 1f))
        assertEquals(c, c.pan(Float.POSITIVE_INFINITY, 0f))
        assertEquals(c, InfiniteCanvasPersistence.decode(listOf("invalid")).first)
    }
}

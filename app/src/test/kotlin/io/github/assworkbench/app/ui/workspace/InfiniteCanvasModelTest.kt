package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class InfiniteCanvasModelTest {
    @Test fun recalledAudioReservesUsableScreenHeightForSignalAndControls() {
        val node = expandAudioCanvasForFocus(InfiniteCanvasNode("audio", width = 650f, height = 190f), 0.5f, 700f)
        assertEquals(480f, node.height, 0f)
        assertEquals(650f, node.width, 0f)
        assertEquals(1, node.z)
        val restored = InfiniteCanvasPersistence.decode(InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(), listOf(node)))
        assertEquals(480f, restored.second.single().height, 0f)
    }
    @Test fun explicitFocusShowsContentAtPhoneFitScale() {
        assertTrue(showCanvasContent(0.43f, "preview", "preview"))
        assertFalse(showCanvasContent(0.43f, "preview", null))
        assertTrue(showCanvasContent(0.8f, "preview", null))
    }
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

    @Test fun totalOverviewCanFitWidelySeparatedNodesBelowLegacyQuarterScale() {
        val nodes = listOf(
            InfiniteCanvasNode("preview", x = 0f, width = 400f),
            InfiniteCanvasNode("subtitles", x = 8_000f, width = 400f),
        )
        val camera = fitCanvasCamera(nodes, 360f, 700f)!!
        assertTrue(camera.scale < 0.25f)
        assertTrue(camera.scale >= 0.025f)
        assertTrue(camera.x + 8_400f * camera.scale <= 360f)
        val restored = InfiniteCanvasPersistence.decode(
            InfiniteCanvasPersistence.encode(camera, nodes))
        assertEquals(camera, restored.first)
        assertEquals(nodes, restored.second)
    }

    @Test fun approachingToolMovesCameraWithoutMovingWorldGeometry() {
        val target = InfiniteCanvasNode("style", x = 8_000f, y = -1_200f, width = 480f, height = 450f)
        val camera = canvasCameraForNode(target, 420f, 860f)
        assertTrue(camera.scale in 0.025f..1f)
        val projectedX = camera.x + target.x * camera.scale
        val projectedY = camera.y + target.y * camera.scale
        assertTrue(projectedX in 0f..420f)
        assertTrue(projectedY in 0f..860f)
        assertEquals(8_000f, target.x, 0f)
    }

    @Test fun fitIgnoresNonFiniteNodesAndInvalidViewport() {
        val valid = InfiniteCanvasNode("preview", x = 100f)
        val invalid = InfiniteCanvasNode("broken", x = Float.NaN)
        assertEquals(
            fitCanvasCamera(listOf(valid), 400f, 800f),
            fitCanvasCamera(listOf(valid, invalid), 400f, 800f),
        )
        assertNull(fitCanvasCamera(listOf(invalid), 400f, 800f))
        assertNull(fitCanvasCamera(listOf(valid), 0f, 800f))
        // Do not label an enormous layout "fit" when the camera cannot shrink enough.
        assertNull(fitCanvasCamera(
            listOf(InfiniteCanvasNode("left", x = -100_000_000f),
                InfiniteCanvasNode("right", x = 100_000_000f)),
            400f, 800f,
        ))
    }
}

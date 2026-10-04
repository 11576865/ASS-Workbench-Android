package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class InfiniteCanvasModelTest {
    @Test fun lockedNodeCannotMoveResizeOrExpandButCanBeRecalled() {
        val locked = InfiniteCanvasNode("audio", 900f, -500f, 650f, 190f, hidden = true, layoutLocked = true)
        assertEquals(locked, locked.move(30f, 40f))
        assertEquals(locked, locked.resize(30f, 40f))
        assertEquals(locked, expandAudioCanvasForFocus(locked, 0.5f, 700f))
        val (camera, shown) = approachCanvasNode(locked, 400f, 700f)
        assertFalse(shown.hidden)
        assertEquals(locked.copy(hidden = false), shown)
        assertEquals(200f, camera.x + (shown.x + shown.width / 2f) * camera.scale, 0.001f)
        assertEquals(64f, camera.y + shown.y * camera.scale, 0.001f)
    }

    @Test fun persistenceRestoresLockAndReadsPreviousNineFieldRows() {
        val locked = InfiniteCanvasNode("POSITION:primary", layoutLocked = true)
        assertEquals(locked, InfiniteCanvasPersistence.decode(InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(), listOf(locked))).second.single())
        val old = listOf("infinite-v1", "0\u001f0\u001f1", "audio\u001f10\u001f20\u001f300\u001f200\u001f3\u001f0.2\u001ftrue\u001ffalse")
        assertEquals(InfiniteCanvasNode("audio", 10f, 20f, 300f, 200f, 3, 0.2f, true, false), InfiniteCanvasPersistence.decode(old).second.single())
    }

    @Test fun closedToolGeometryIsRemovedBeforeItsIdCanBeReused() {
        val nodes = listOf(InfiniteCanvasNode("preview"), InfiniteCanvasNode("POSITION:2", hidden = true), InfiniteCanvasNode("STYLE:primary"))
        assertEquals(listOf(nodes[0], nodes[2]), retainCanvasNodes(nodes, setOf("preview", "STYLE:primary")))
    }

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
}

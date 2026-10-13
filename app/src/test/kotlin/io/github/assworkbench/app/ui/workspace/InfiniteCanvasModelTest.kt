package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class InfiniteCanvasModelTest {

    @Test fun canvasSaveableSlotsCannotReuseKeysAcrossConcurrentRepresentations() {
        val session = 86L
        val keys = listOf(
            canvasSaveableContentKey(session, CanvasContentRole.BOARD, "preview"),
            canvasSaveableContentKey(session, CanvasContentRole.FOCUSED, "preview"),
            canvasSaveableContentKey(session, CanvasContentRole.FOCUSED, "audio"),
            canvasSaveableContentKey(session, CanvasContentRole.FOCUSED_LAYER, "audio", "preview"),
            canvasSaveableContentKey(session, CanvasContentRole.FOCUSED_LAYER, "preview", "audio"),
            canvasSaveableContentKey(session, CanvasContentRole.REFERENCE, "preview", "TEXT:primary"),
            canvasSaveableContentKey(session, CanvasContentRole.REFERENCE, "preview", "STYLE:primary"),
            canvasSaveableContentKey(session, CanvasContentRole.FOCUSED, "TEXT:primary"),
        )
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(keys[0], canvasSaveableContentKey(session, CanvasContentRole.BOARD, "preview"))
        assertNotEquals(keys[0], canvasSaveableContentKey(session + 1, CanvasContentRole.BOARD, "preview"))
        assertNotEquals(keys[1], canvasSaveableContentKey(session, CanvasContentRole.FOCUSED, "audio"))
        assertNotEquals(
            canvasSaveableContentKey(session, CanvasContentRole.FOCUSED_LAYER, "preview", "audio"),
            canvasSaveableContentKey(session, CanvasContentRole.FOCUSED_LAYER, "preview", "subtitles"))
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
    @Test fun zoomAcrossOppositeFiniteFloatOffsetsDoesNotOverflowIntermediateArithmetic() {
        val camera = InfiniteCanvasCamera(x = -Float.MAX_VALUE, y = 0f, scale = 1f)
        val next = camera.zoomAt(Float.MAX_VALUE, 0f, 0.025f)
        assertEquals(0.025f, next.scale, 0f)
        assertTrue(next.x.isFinite())
        assertTrue(next.x > 0f)
        assertTrue(next.x < Float.MAX_VALUE)
        // A mathematical result outside Float camera storage must still fail closed.
        val unrepresentable = InfiniteCanvasCamera(x = Float.MAX_VALUE, scale = 1f)
        assertEquals(unrepresentable, unrepresentable.zoomAt(-Float.MAX_VALUE, 0f, 2f))
    }

    @Test fun overviewCentersNativeScaleToolInsideReservedNavigationArea() {
        val node = InfiniteCanvasNode("editor", x = 120f, y = -90f, width = 320f, height = 240f)
        val camera = fitCanvasCamera(listOf(node), viewportWidth = 900f, viewportHeight = 720f)!!
        assertEquals(1f, camera.scale, 0f)
        val centerX = camera.x + (node.x + node.width / 2f) * camera.scale
        val centerY = camera.y + (node.y + node.height / 2f) * camera.scale
        assertEquals(450f, centerX, 0.001f)
        assertEquals(72f + (720f - 160f) / 2f, centerY, 0.001f)
        assertEquals(120f, node.x, 0f)
        assertEquals(-90f, node.y, 0f)
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
    @Test fun offscreenLiveCompositionUsesCameraProjectionWithPrefetch() {
        val near = InfiniteCanvasNode("preview", x = 0f, y = 0f, width = 300f, height = 220f)
        val far = InfiniteCanvasNode("audio", x = 10_000f, y = 0f, width = 300f, height = 220f)
        val camera = InfiniteCanvasCamera(0f, 0f, 1f)
        assertTrue(shouldComposeCanvasTool(camera, near, 420f, 800f))
        assertFalse(shouldComposeCanvasTool(camera, far, 420f, 800f))
        assertTrue(shouldComposeCanvasTool(camera.copy(x = -9_950f), far, 420f, 800f))
        assertFalse(shouldComposeCanvasTool(camera.copy(x = 1_000f), near, 420f, 800f))
    }

    @Test fun offscreenVisibilityRejectsInvalidInputsAndHandlesExtremeWorldPositions() {
        val camera = InfiniteCanvasCamera(0f, 0f, 1f)
        val node = InfiniteCanvasNode("tool")
        assertFalse(shouldComposeCanvasTool(camera, node, Float.NaN, 800f))
        assertFalse(shouldComposeCanvasTool(camera, node, 420f, 800f, -1f))
        assertFalse(shouldComposeCanvasTool(camera, node.copy(x = Float.POSITIVE_INFINITY), 420f, 800f))
        assertFalse(shouldComposeCanvasTool(camera, node.copy(x = Float.MAX_VALUE), 420f, 800f))
        assertFalse(shouldComposeCanvasTool(camera, node.copy(x = -Float.MAX_VALUE), 420f, 800f))
        assertTrue(shouldComposeCanvasTool(camera, node.copy(x = -128f), 420f, 800f))
        assertFalse(shouldComposeCanvasTool(camera, node.copy(x = -600f), 420f, 800f))
    }

    @Test fun layoutLockSurvivesSceneRoundTripAndFreezesGeometry() {
        val locked = InfiniteCanvasNode("preview", x = -500f, y = 1200f, layoutLocked = true)
        assertEquals(locked, locked.move(50f, -30f))
        assertEquals(locked, locked.resize(100f, 100f))
        val restored = InfiniteCanvasPersistence.decode(
            InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(), listOf(locked)))
        assertEquals(locked, restored.second.single())
    }

    @Test fun legacyV1RowsRestoreAsUnlockedAndInvalidV2RowsFailClosed() {
        val separator = "\u001f"
        val legacy = listOf("infinite-v1", "0${separator}0${separator}1",
            "preview${separator}0${separator}0${separator}400${separator}320${separator}1${separator}1${separator}false${separator}false")
        val scene = InfiniteCanvasPersistence.decode(legacy)
        assertEquals(1, scene.second.size)
        assertFalse(scene.second.single().layoutLocked)
        val malformed = InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(), scene.second).toMutableList()
        malformed[2] = malformed[2].substringBeforeLast(separator) + separator + "invalid"
        assertTrue(InfiniteCanvasPersistence.decode(malformed).second.isEmpty())
    }

    @Test fun arrangementPreservesLockHiddenIdentityAndZOrderWithoutOverlappingMovableCards() {
        val nodes = listOf(
            InfiniteCanvasNode("fixed", 100f, 100f, width = 400f, height = 350f, layoutLocked = true),
            InfiniteCanvasNode("a", x = -200f, width = 500f, height = 300f, z = 3),
            InfiniteCanvasNode("hidden", x = 9000f, hidden = true),
            InfiniteCanvasNode("b", width = 300f, height = 600f, z = 5),
            InfiniteCanvasNode("c", width = 250f, height = 200f),
        )
        val arranged = arrangeCanvasNodes(nodes, columns = 2)
        assertEquals(nodes[0], arranged[0])
        assertEquals(nodes[2], arranged[2])
        assertEquals(nodes.map { it.z }, arranged.map { it.z })
        assertTrue(arranged[1].y >= nodes[0].y + nodes[0].height)
        assertTrue(arranged[3].x >= arranged[1].x + arranged[1].width + 40f)
        assertTrue(arranged[4].y >= arranged[1].y + maxOf(arranged[1].height, arranged[3].height) + 40f)
        assertEquals(nodes, arrangeCanvasNodes(nodes, 0))
    }

    @Test fun glassOpacityCyclesThroughReadablePersistenceValues() {
        assertEquals(0.75f, nextCanvasOpacity(1f))
        assertEquals(0.50f, nextCanvasOpacity(0.75f))
        assertEquals(0.25f, nextCanvasOpacity(0.50f))
        assertEquals(1f, nextCanvasOpacity(0.25f))
        assertEquals(0.75f, nextCanvasOpacity(Float.NaN))
        val node = InfiniteCanvasNode("STYLE:primary", alpha = 0.50f)
        assertEquals(node, InfiniteCanvasPersistence.decode(
            InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(), listOf(node))).second.single())
    }

    @Test fun viewportHistoryMovesBackAndForwardWithoutTouchingCanvasGeometry() {
        val initial = InfiniteCanvasCamera(16f, 60f, 0.85f)
        val distant = InfiniteCanvasCamera(-1800f, 420f, 0.5f)
        val overview = InfiniteCanvasCamera(200f, 110f, 0.25f)
        val first = CanvasViewportHistory().visit(initial, distant).visit(distant, overview)
        assertTrue(first.canBack)
        assertFalse(first.canForward)
        val (afterBack, returned) = first.goBack(overview)!!
        assertEquals(distant, returned)
        assertTrue(afterBack.canForward)
        val (afterSecondBack, original) = afterBack.goBack(distant)!!
        assertEquals(initial, original)
        val (afterForward, approached) = afterSecondBack.goForward(initial)!!
        assertEquals(distant, approached)
        val changedDestination = InfiniteCanvasCamera(90f, 100f, 1f)
        val branched = afterForward.visit(approached, changedDestination)
        assertFalse(branched.canForward)
        assertEquals(approached, branched.goBack(changedDestination)!!.second)
        assertFalse(CanvasViewportHistory().canBack)
        assertNull(CanvasViewportHistory().goBack(initial))
    }

    @Test fun viewportHistoryIsBoundedAndRejectsCorruptRestoredFrames() {
        val frames = (0..30).map { InfiniteCanvasCamera(it.toFloat(), (-it).toFloat(), 0.85f) }
        var history = CanvasViewportHistory()
        frames.zipWithNext().forEach { (from, to) -> history = history.visit(from, to) }
        assertEquals(CanvasViewportHistory.LIMIT, history.past.size)
        val roundTrip = CanvasViewportHistoryPersistence.decode(
            CanvasViewportHistoryPersistence.encode(history))
        assertEquals(history, roundTrip)
        assertEquals(frames[14], roundTrip.past.first())
        val corrupt = CanvasViewportHistoryPersistence.encode(history).toMutableList()
        corrupt[1] = "NaN,Infinity,0.5"
        assertEquals(CanvasViewportHistory(), CanvasViewportHistoryPersistence.decode(corrupt))
        assertEquals(CanvasViewportHistory(), CanvasViewportHistoryPersistence.decode(listOf("bogus")))
        val invalid = InfiniteCanvasCamera(Float.NaN, 0f, 1f)
        assertEquals(CanvasViewportHistory(), CanvasViewportHistory().visit(invalid, frames[0]))
        assertEquals(CanvasViewportHistory(), CanvasViewportHistory().visit(frames[0], invalid))
    }

    @Test fun edgeCuesChooseNearestVisibleWorldToolPerDirection() {
        val nodes = listOf(
            InfiniteCanvasNode("left", -600f, 250f),
            InfiniteCanvasNode("right-nearest", 700f, 250f),
            InfiniteCanvasNode("right-far", 7000f, 250f),
            InfiniteCanvasNode("top", 100f, -800f),
            InfiniteCanvasNode("bottom", 100f, 1200f),
            InfiniteCanvasNode("partly-visible", 350f, 80f),
            InfiniteCanvasNode("hidden", 800f, hidden = true),
        )
        val cues = canvasEdgeCues(InfiniteCanvasCamera(), nodes, 400f, 700f)
        assertEquals(CanvasEdge.entries.toList(), cues.map { it.side })
        assertEquals("left", cues.first { it.side == CanvasEdge.LEFT }.nearestNodeId)
        val right = cues.first { it.side == CanvasEdge.RIGHT }
        assertEquals("right-nearest", right.nearestNodeId)
        assertEquals(2, right.count)
        assertEquals("top", cues.first { it.side == CanvasEdge.TOP }.nearestNodeId)
        assertEquals("bottom", cues.first { it.side == CanvasEdge.BOTTOM }.nearestNodeId)
        assertEquals(4, cues.size)
    }

    @Test fun edgeCuesRejectInvalidViewportAndHandleExtremeWorldCoordinates() {
        val node = InfiniteCanvasNode("far", x = Float.MAX_VALUE)
        val camera = InfiniteCanvasCamera(0f, 0f, 1f)
        assertEquals(CanvasEdge.RIGHT,
            canvasEdgeCues(camera, listOf(node), 400f, 700f).single().side)
        assertTrue(canvasEdgeCues(camera, listOf(node), Float.NaN, 700f).isEmpty())
        assertTrue(canvasEdgeCues(InfiniteCanvasCamera(scale = Float.NaN),
            listOf(node), 400f, 700f).isEmpty())
        assertTrue(canvasEdgeCues(camera,
            listOf(node.copy(hidden = true)), 400f, 700f).isEmpty())
    }

}

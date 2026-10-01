package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class WorkbenchSurfaceControllerTest {
    private val fallback = SurfaceGeometry(20f, 30f)

    @Test fun gestureCandidatesAreNotSavedAndCancellationRestoresLayout() {
        val controller = WorkbenchSurfaceController()
        controller.ensure("A", fallback)
        val saved = controller.save()
        controller.begin("A", fallback, fallback)
        repeat(100) { controller.moveBy("A", 1f, 1f, 1200f, 900f) }
        assertEquals(saved, controller.save())
        controller.cancel("A")
        assertNull(controller.candidate("A").value)
        assertEquals(fallback, controller.state("A", fallback).geometry)
        controller.begin("A", fallback, fallback)
        controller.moveBy("A", 40f, 50f, 1200f, 900f)
        controller.commit("A", fallback)
        assertEquals(fallback.copy(x = 60f, y = 80f), controller.state("A", fallback).geometry)
    }

    @Test fun movingProjectedWindowPreservesDesiredDimensions() {
        val large = SurfaceGeometry(700f, 40f, 486f, 590f)
        val controller = WorkbenchSurfaceController(listOf(WorkspaceSurfaceState("A", large)))
        controller.begin("A", large, large.inViewport(360f, 700f))
        controller.moveBy("A", 0f, 20f, 360f, 700f)
        controller.commit("A", large)
        val moved = controller.state("A", large).geometry
        assertEquals(486f, moved.width)
        assertEquals(590f, moved.height)
        assertEquals(60f, moved.y)
    }

    @Test fun narrowSplitScreenStillSupportsVerticalMovement() {
        val controller = WorkbenchSurfaceController()
        controller.ensure("A", fallback)
        controller.begin("A", fallback, fallback.inViewport(180f, 800f))
        controller.moveBy("A", 0f, 25f, 180f, 800f)
        controller.commit("A", fallback)
        assertEquals(55f, controller.state("A", fallback).geometry.y)
        assertEquals(374f, controller.state("A", fallback).geometry.width)
    }

    @Test fun resizeCommitsOnceAndLayoutLockCancelsGesture() {
        val controller = WorkbenchSurfaceController()
        controller.ensure("A", fallback)
        controller.begin("A", fallback, fallback, resize = true)
        controller.resizeBy("A", -88f, -220f, 1200f, 900f)
        assertEquals(fallback, controller.state("A", fallback).geometry)
        controller.commit("A", fallback)
        val compact = controller.state("A", fallback)
        assertEquals(SurfaceSizeClass.COMPACT, compact.sizeClass)
        assertEquals(286f, compact.geometry.width)
        controller.begin("A", fallback, compact.geometry)
        controller.toggleLayoutLock("A", fallback)
        assertNull(controller.candidate("A").value)
        controller.begin("A", fallback, compact.geometry, resize = true)
        controller.resizeBy("A", 100f, 100f, 1200f, 900f)
        controller.cycleSize("A", fallback)
        controller.commit("A", fallback)
        assertEquals(compact.geometry, controller.state("A", fallback).geometry)
        assertTrue(controller.state("A", fallback).layoutLocked)
    }

    @Test fun restoredStackingDoesNotResetOnMountAndInstancesRemainIndependent() {
        val controller = WorkbenchSurfaceController(listOf(
            WorkspaceSurfaceState("A", fallback, zOrder = 10),
            WorkspaceSurfaceState("B", SurfaceGeometry(60f, 70f), zOrder = 2),
        ))
        controller.ensure("A", SurfaceGeometry())
        controller.ensure("B", SurfaceGeometry())
        assertTrue(controller.z("A") > controller.z("B"))
        controller.bringToFront("B")
        assertTrue(controller.z("B") > controller.z("A"))
        assertEquals(fallback, controller.state("A", fallback).geometry)
        val restored = WorkbenchSurfaceController(WorkspaceSurfacePersistence.decode(controller.save()))
        assertEquals(controller.save(), restored.save())
    }
    @Test fun dockMinimizeAndTabStackPersistIndependently() {
        val controller = WorkbenchSurfaceController()
        controller.ensure("A", fallback)
        controller.ensure("B", SurfaceGeometry(80f, 90f))

        controller.cycleDock("A", fallback)
        assertEquals(SurfaceDock.LEFT, controller.state("A", fallback).dock)
        controller.stackWithFront("B", SurfaceGeometry(80f, 90f))
        val group = controller.state("B", fallback).tabGroupId
        assertNotNull(group)
        assertEquals(group, controller.state("A", fallback).tabGroupId)
        assertEquals(2, controller.tabGroup("A").size)
        controller.toggleMinimized("A", fallback)
        assertTrue(controller.state("A", fallback).minimized)

        val restored = WorkbenchSurfaceController(WorkspaceSurfacePersistence.decode(controller.save()))
        assertEquals(SurfaceDock.LEFT, restored.state("A", fallback).dock)
        assertTrue(restored.state("A", fallback).minimized)
        assertEquals(group, restored.state("B", fallback).tabGroupId)

        restored.unstack("B", fallback)
        assertNull(restored.state("B", fallback).tabGroupId)
        assertEquals(group, restored.state("A", fallback).tabGroupId)
    }

}

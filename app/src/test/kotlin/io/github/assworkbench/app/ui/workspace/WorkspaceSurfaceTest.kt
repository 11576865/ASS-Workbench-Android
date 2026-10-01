package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class WorkspaceSurfaceTest {
    @Test fun viewportProjectionKeepsCanonicalLayoutForRotationAndInsets() {
        val landscape = SurfaceGeometry(820f, 90f, 374f, 430f)
        val portrait = landscape.inViewport(360f, 620f)
        assertEquals(0f, portrait.x)
        assertEquals(360f, portrait.width)
        assertEquals(landscape, landscape.inViewport(1280f, 800f))
        assertEquals(820f, landscape.x)
        val inset = landscape.inViewport(360f, 100f)
        assertEquals(0f, inset.y)
        assertEquals(100f, inset.height)
    }

    @Test fun extremeDragsKeepWholeTitleAndWindowReachable() {
        val geometry = SurfaceGeometry(-50_000f, 50_000f)
        val visible = geometry.inViewport(800f, 600f)
        assertEquals(0f, visible.x)
        assertEquals(170f, visible.y)
        assertTrue(visible.x + visible.width <= 800f)
        assertTrue(visible.y + visible.height <= 600f)
        assertEquals(SurfaceGeometry(0f, 0f, 1f, 1f), geometry.inViewport(0f, Float.NaN))
    }

    @Test fun layoutLockRejectsGeometryButDoesNotOwnBinding() {
        val state = WorkspaceSurfaceState("POSITION:2", layoutLocked = true)
        assertEquals(state, state.withGeometry(SurfaceGeometry(10f, 20f)))
        assertEquals(SurfaceGeometry(10f, 20f),
            state.copy(layoutLocked = false).withGeometry(SurfaceGeometry(10f, 20f)).geometry)
    }

    @Test fun codecRestoresIndependentInstancesAndGeometry() {
        val surfaces = listOf(
            WorkspaceSurfaceState("POSITION:primary", SurfaceGeometry(12f, 35f), zOrder = 4),
            WorkspaceSurfaceState("POSITION:2", SurfaceGeometry(830f, 40f, 486f, 590f),
                SurfaceSizeClass.PRECISION, layoutLocked = true, zOrder = 8),
        )
        assertEquals(surfaces, WorkspaceSurfacePersistence.decode(WorkspaceSurfacePersistence.encode(surfaces)))
    }

    @Test fun codecRejectsCorruptRowsIndividuallyAndUnknownVersion() {
        val good = WorkspaceSurfaceState("STYLE:primary")
        val rows = WorkspaceSurfacePersistence.encode(listOf(good)) + listOf(
            "corrupt", "BAD\u001fNaN\u001f0\u001f374\u001f430\u001fSTANDARD\u001ffalse\u001f1",
            "BAD\u001f0\u001f0\u001f-1\u001f430\u001fSTANDARD\u001ffalse\u001f1",
        )
        assertEquals(listOf(good), WorkspaceSurfacePersistence.decode(rows))
        assertTrue(WorkspaceSurfacePersistence.decode(listOf("future-v2")).isEmpty())
    }

    @Test fun freeResizeResolvesSizeClassOnlyFromCommittedGeometry() {
        assertEquals(SurfaceSizeClass.COMPACT, SurfaceSizeClass.forGeometry(SurfaceGeometry(width = 286f, height = 210f)))
        assertEquals(SurfaceSizeClass.STANDARD, SurfaceSizeClass.forGeometry(SurfaceGeometry()))
        assertEquals(SurfaceSizeClass.PRECISION, SurfaceSizeClass.forGeometry(SurfaceGeometry(width = 486f, height = 590f)))
        assertEquals(374f, WorkspaceSurfaceState("STYLE:primary")
            .withGeometry(SurfaceGeometry(width = Float.POSITIVE_INFINITY)).geometry.width)
    }
}

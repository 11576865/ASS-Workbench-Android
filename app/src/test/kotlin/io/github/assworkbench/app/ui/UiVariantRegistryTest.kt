package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UiVariantRegistryTest {
    @Test fun defaultIsTheUnifiedInfiniteCanvas() {
        assertSame(WorkspacePresentationMode.SPATIAL_EXPERIMENTAL, UiVariantRegistry.default)
    }

    @Test fun onlyStableMainAndUnifiedCanvasAreSelectable() {
        assertEquals(
            listOf(WorkspacePresentationMode.FIXED, WorkspacePresentationMode.SPATIAL_EXPERIMENTAL),
            UiVariantRegistry.entries,
        )
    }

    @Test fun legacyExperimentNamesMigrateWithoutRevivingRetiredLayouts() {
        assertSame(WorkspacePresentationMode.FIXED, UiVariantRegistry.resolve("FIXED"))
        assertSame(WorkspacePresentationMode.SPATIAL_EXPERIMENTAL, UiVariantRegistry.resolve("SPATIAL_EXPERIMENTAL"))
        listOf(
            "CANVAS_EXPERIMENTAL", "PAGER_EXPERIMENTAL", "PRECISION_LENS_EXPERIMENTAL",
            "TOOL_INSTANCES_EXPERIMENTAL", "GLASS_LAYERED_EXPERIMENTAL",
            "SUBTITLE_OBJECT_EXPERIMENTAL", "EDGE_BOOKMARK_EXPERIMENTAL",
            "TIMELINE_DOCK_EXPERIMENTAL", "FUTURE_REMOVED_UI",
        ).forEach { legacy ->
            assertSame(WorkspacePresentationMode.SPATIAL_EXPERIMENTAL, UiVariantRegistry.resolve(legacy))
        }
    }
}

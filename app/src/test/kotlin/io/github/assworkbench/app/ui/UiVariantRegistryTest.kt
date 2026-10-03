package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UiVariantRegistryTest {
    @Test fun newWorkspacesDefaultToInfiniteCanvas() {
        assertSame(WorkspacePresentationMode.SPATIAL_EXPERIMENTAL, UiVariantRegistry.default)
    }

    @Test
    fun registryContainsExistingPresentations() {
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.FIXED))
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.CANVAS_EXPERIMENTAL))
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.PAGER_EXPERIMENTAL))
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.SPATIAL_EXPERIMENTAL))
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.PRECISION_LENS_EXPERIMENTAL))
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.TOOL_INSTANCES_EXPERIMENTAL))
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.GLASS_LAYERED_EXPERIMENTAL))
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.SUBTITLE_OBJECT_EXPERIMENTAL))
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.EDGE_BOOKMARK_EXPERIMENTAL))
    }

    @Test
    fun persistedNamesRemainCompatible() {
        assertSame(
            WorkspacePresentationMode.FIXED,
            UiVariantRegistry.resolve("FIXED"),
        )
        assertSame(
            WorkspacePresentationMode.CANVAS_EXPERIMENTAL,
            UiVariantRegistry.resolve("CANVAS_EXPERIMENTAL"),
        )
        assertSame(
            WorkspacePresentationMode.PAGER_EXPERIMENTAL,
            UiVariantRegistry.resolve("PAGER_EXPERIMENTAL"),
        )
        assertSame(
            WorkspacePresentationMode.SPATIAL_EXPERIMENTAL,
            UiVariantRegistry.resolve("SPATIAL_EXPERIMENTAL"),
        )
        assertSame(
            WorkspacePresentationMode.PRECISION_LENS_EXPERIMENTAL,
            UiVariantRegistry.resolve("PRECISION_LENS_EXPERIMENTAL"),
        )
        assertSame(
            WorkspacePresentationMode.TOOL_INSTANCES_EXPERIMENTAL,
            UiVariantRegistry.resolve("TOOL_INSTANCES_EXPERIMENTAL"),
        )
        assertSame(
            WorkspacePresentationMode.GLASS_LAYERED_EXPERIMENTAL,
            UiVariantRegistry.resolve("GLASS_LAYERED_EXPERIMENTAL"),
        )
        assertSame(
            WorkspacePresentationMode.SUBTITLE_OBJECT_EXPERIMENTAL,
            UiVariantRegistry.resolve("SUBTITLE_OBJECT_EXPERIMENTAL"),
        )
        assertSame(
            WorkspacePresentationMode.EDGE_BOOKMARK_EXPERIMENTAL,
            UiVariantRegistry.resolve("EDGE_BOOKMARK_EXPERIMENTAL"),
        )
    }

    @Test
    fun unknownPresentationFallsBackToDefault() {
        assertEquals(UiVariantRegistry.default, UiVariantRegistry.resolve("FUTURE_REMOVED_UI"))
    }
}

package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UiVariantRegistryTest {
    @Test
    fun registryContainsExistingPresentations() {
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.FIXED))
        assertTrue(UiVariantRegistry.entries.contains(WorkspacePresentationMode.CANVAS_EXPERIMENTAL))
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
    }

    @Test
    fun unknownPresentationFallsBackToDefault() {
        assertEquals(UiVariantRegistry.default, UiVariantRegistry.resolve("FUTURE_REMOVED_UI"))
    }
}

package io.github.assworkbench.fonts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RendererLogParserTest {
    @Test
    fun parsesProviderAndFontSelection() {
        val snapshot = RendererLogParser.parse(
            lines = listOf(
                "[sub/ass] libass API version: 0x1705000",
                "[sub/ass] Setting up fonts...",
                "[sub/ass] Using font provider fontconfig",
                "[sub/ass] fontselect: (Source Han Sans SC, 400, 0) -> /data/user/0/app/files/mpv/fonts/SourceHanSansSC.otf, 0, SourceHanSansSC-Regular",
                "[sub/ass] Done.",
            ),
            requestedProvider = "fontconfig",
        )

        assertEquals("fontconfig", snapshot.detectedProvider)
        assertTrue(snapshot.setupStarted)
        assertTrue(snapshot.setupCompleted)
        assertTrue(snapshot.providerReady)
        assertEquals(1, snapshot.fontSelections.size)
        assertEquals("Source Han Sans SC", snapshot.fontSelections.single().requestedFamily)
        assertEquals(0, snapshot.fontSelections.single().faceIndex)
        assertEquals("SourceHanSansSC-Regular", snapshot.fontSelections.single().selectedName)
    }

    @Test
    fun recordsGlyphFallbackAndFailure() {
        val snapshot = RendererLogParser.parse(
            lines = listOf(
                "[sub/ass] Setting up fonts...",
                "[sub/ass] Using font provider fontconfig",
                "[sub/ass] Done.",
                "[sub/ass] Glyph 0x4E2D not found, selecting one more font for (Missing CJK, 400, 0)",
                "[sub/ass] fontselect: failed to find any fallback with glyph 0x4E2D for font: (Missing CJK, 400, 0)",
            ),
            requestedProvider = "fontconfig",
        )

        assertEquals(1, snapshot.glyphFallbackRequests)
        assertEquals(1, snapshot.fallbackFailures)
        assertTrue(snapshot.warnings.single().contains("failed to find any fallback"))
    }

    @Test
    fun requestedFontconfigIsNotReadyWhenProviderIsMissing() {
        val snapshot = RendererLogParser.parse(
            lines = listOf(
                "[sub/ass] Setting up fonts...",
                "[sub/ass] can't find selected font provider",
                "[sub/ass] Done.",
            ),
            requestedProvider = "fontconfig",
        )

        assertFalse(snapshot.providerReady)
        assertEquals(null, snapshot.detectedProvider)
    }
}

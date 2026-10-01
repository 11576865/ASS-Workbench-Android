package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveWorkspacePolicyTest {
    @Test
    fun compactWindowUsesInlineHostAndKeepsRequestedPreviewReachable() {
        val decision = AdaptiveWorkspacePolicy.resolve(
            widthDp = 412f,
            heightDp = 760f,
            imeVisible = false,
            requestedPreview = PreviewDensity.FULL,
        )
        assertEquals(WorkbenchLayoutProfile.COMPACT, decision.profile)
        assertEquals(ToolHostHint.INLINE, decision.hostHint)
        assertEquals(PreviewDensity.FULL, decision.effectivePreview)
        assertTrue(decision.previewHeightDp in 160f..240f)
    }

    @Test
    fun imeCompactsPreviewWithoutChangingRequestedMode() {
        val decision = AdaptiveWorkspacePolicy.resolve(
            widthDp = 800f,
            heightDp = 600f,
            imeVisible = true,
            requestedPreview = PreviewDensity.FULL,
        )
        assertEquals(WorkbenchLayoutProfile.DUAL_PANE, decision.profile)
        assertEquals(PreviewDensity.COMPACT, decision.effectivePreview)
        assertTrue(decision.previewHeightDp <= 136f)
    }

    @Test
    fun wideWindowUsesThreePaneAndHiddenPreviewStaysHidden() {
        val decision = AdaptiveWorkspacePolicy.resolve(
            widthDp = 1400f,
            heightDp = 900f,
            imeVisible = false,
            requestedPreview = PreviewDensity.HIDDEN,
        )
        assertEquals(WorkbenchLayoutProfile.THREE_PANE, decision.profile)
        assertEquals(PreviewDensity.HIDDEN, decision.effectivePreview)
        assertEquals(0f, decision.previewHeightDp)
    }
}

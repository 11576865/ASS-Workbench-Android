package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveWorkspacePolicyTest {
    @org.junit.Test fun compactBodyCanExtendBeyondViewportWithoutUnboundedEditor() {
        val layout = CompactWorkspaceBody.resolve(260f, 196f, false)
        org.junit.Assert.assertEquals(360f, layout.editorHeightDp)
        org.junit.Assert.assertTrue(layout.editorHeightDp + layout.previewHeightDp > 260f)
        val hidden = CompactWorkspaceBody.resolve(600f, 0f, false)
        org.junit.Assert.assertEquals(600f, hidden.editorHeightDp)
    }

    @org.junit.Test fun previewResizeAndImePressurePreserveUsableEditingSpace() {
        val small = CompactWorkspaceBody.resolve(600f, 196f, false, 0.75f)
        val large = CompactWorkspaceBody.resolve(600f, 196f, false, 1.5f)
        org.junit.Assert.assertTrue(large.previewHeightDp > small.previewHeightDp)
        org.junit.Assert.assertEquals(600f, large.editorHeightDp)
        val ime = CompactWorkspaceBody.resolve(220f, 112f, true, 1.5f)
        org.junit.Assert.assertTrue(ime.previewHeightDp <= 112f)
        org.junit.Assert.assertEquals(240f, ime.editorHeightDp)
    }

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

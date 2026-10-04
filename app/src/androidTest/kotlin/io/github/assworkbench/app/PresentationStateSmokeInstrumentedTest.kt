package io.github.assworkbench.app

import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cross-presentation smoke contract.
 *
 * This test deliberately owns no presentation-specific business logic. It verifies
 * that switching presentation is a view concern: canonical document state, focus,
 * and undo/redo history remain owned by the editor session.
 */
@RunWith(AndroidJUnit4::class)
class PresentationStateSmokeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<EditorRegressionHostActivity>()

    private val viewModel: EditorViewModel
        get() = composeRule.activity.editorViewModel

    @Test
    fun canvasPreservesCanonicalStateAndExistingHistory() =
        assertPresentationInvariant("CANVAS_EXPERIMENTAL", "canvas-workspace")

    @Test
    fun pagerPreservesCanonicalStateAndExistingHistory() =
        assertPresentationInvariant("PAGER_EXPERIMENTAL", "pager-workspace")

    @Test
    fun spatialPreservesCanonicalStateAndExistingHistory() =
        assertPresentationInvariant("SPATIAL_EXPERIMENTAL", "spatial-workspace")

    @Test fun spatialCameraAndOverlayDoNotEditDocument() {
        composeRule.activityRule.scenario.onActivity { it.restoreDeterministicFixture() }
        composeRule.waitUntil(10_000) { viewModel.state.value.document.events.size == 2 }
        viewModel.focusEvent(1L, seek = false)
        viewModel.updateEventText(1L, "Overlay invariant")
        switchPresentation("SPATIAL_EXPERIMENTAL", "spatial-workspace")
        val document = viewModel.state.value.document
        val selection = viewModel.state.value.focusedEventId
        composeRule.onNodeWithTag("spatial-zoom-out").performClick()
        composeRule.onNodeWithTag("spatial-zoom-in").performClick()
        composeRule.onNodeWithText("召回").performClick()
        composeRule.onNodeWithTag("spatial-recall-audio").performClick()
        composeRule.onNodeWithTag("spatial-audio-evidence").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-menu-audio").performClick()
        composeRule.onNodeWithText("恢复实底").performClick()
        composeRule.onNodeWithTag("spatial-menu-audio").performClick()
        composeRule.onNodeWithText("透明叠加").performClick()
        composeRule.onNodeWithTag("spatial-menu-audio").performClick()
        composeRule.onNodeWithText("穿透操作视频").performClick()
        composeRule.waitForIdle()
        assertEquals(document, viewModel.state.value.document)
        assertEquals(selection, viewModel.state.value.focusedEventId)
        viewModel.undo()
        composeRule.waitUntil(5_000) { eventText(1L) == "Recovered line" }
    }

    @Test fun spatialToolsCanPinDuplicateCloseAndReopenWithoutDocumentEdits() {
        composeRule.activityRule.scenario.onActivity { it.restoreDeterministicFixture() }
        composeRule.waitUntil(10_000) { viewModel.state.value.document.events.size == 2 }
        viewModel.focusEvent(1L, seek = false)
        viewModel.updateEventText(1L, "Lifecycle invariant")
        switchPresentation("SPATIAL_EXPERIMENTAL", "spatial-workspace")
        val document = viewModel.state.value.document
        composeRule.onNodeWithTag("spatial-add-tool").performClick()
        composeRule.onNodeWithTag("spatial-open-POSITION").performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-menu-POSITION-primary").performClick()
        composeRule.onNodeWithTag("spatial-pin-POSITION-primary").performClick()
        composeRule.runOnIdle { viewModel.focusEvent(2L, seek = false) }
        composeRule.onNodeWithTag("spatial-binding-POSITION-primary").assertTextContains("#1")
        composeRule.onNodeWithTag("spatial-menu-POSITION-primary").performClick()
        composeRule.onNodeWithTag("spatial-duplicate-follow-POSITION-primary").performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-binding-POSITION-2").assertTextContains("#2")
        composeRule.onNodeWithTag("spatial-menu-POSITION-2").performClick()
        composeRule.onNodeWithTag("spatial-close-POSITION-2").performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-node-POSITION-2").assertDoesNotExist()
        composeRule.onNodeWithText("召回").performClick()
        composeRule.onNodeWithTag("spatial-recall-POSITION-primary").performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-menu-POSITION-primary").performClick()
        composeRule.onNodeWithTag("spatial-hide-POSITION-primary").performClick()
        composeRule.onNodeWithTag("spatial-node-POSITION-primary").assertDoesNotExist()
        composeRule.onNodeWithTag("spatial-add-tool").performClick()
        composeRule.onNodeWithTag("spatial-open-POSITION").performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-node-POSITION-primary").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-binding-POSITION-primary").assertTextContains("#1")
        assertEquals(document, viewModel.state.value.document)
        assertEquals(2L, viewModel.state.value.focusedEventId)
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitUntil(5_000) { eventText(1L) == "Recovered line" }
    }

    @Test fun closingUnownedPositionPanePreservesOtherWritersPreview() {
        composeRule.activityRule.scenario.onActivity { it.restoreDeterministicFixture() }
        composeRule.waitUntil(10_000) { viewModel.state.value.document.events.size == 2 }
        viewModel.focusEvent(1L, seek = false)
        switchPresentation("SPATIAL_EXPERIMENTAL", "spatial-workspace")
        composeRule.onNodeWithTag("spatial-add-tool").performClick()
        composeRule.onNodeWithTag("spatial-open-POSITION").performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-menu-POSITION-primary").performClick()
        composeRule.onNodeWithTag("spatial-duplicate-POSITION-primary").performScrollTo().performClick()
        composeRule.runOnIdle { viewModel.previewEventPosition(1L, 125.0, 240.0) }
        val preview = viewModel.state.value.previewDocument
        assertTrue(preview != null)
        composeRule.onNodeWithText("召回").performClick()
        composeRule.onNodeWithTag("spatial-recall-POSITION-primary").performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-menu-POSITION-primary").performClick()
        composeRule.onNodeWithTag("spatial-close-POSITION-primary").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(preview, viewModel.state.value.previewDocument)
        composeRule.runOnIdle { viewModel.clearTransientPreview("geometry:1") }
    }

    @Test fun switchingPositionTargetCancelsItsOwnedPendingPreview() {
        composeRule.activityRule.scenario.onActivity { it.restoreDeterministicFixture() }
        composeRule.waitUntil(10_000) { viewModel.state.value.document.events.size == 2 }
        viewModel.focusEvent(1L, seek = false)
        switchPresentation("SPATIAL_EXPERIMENTAL", "spatial-workspace")
        composeRule.onNodeWithTag("spatial-add-tool").performClick()
        composeRule.onNodeWithTag("spatial-open-POSITION").performScrollTo().performClick()
        composeRule.onNodeWithTag("position-section-TRANSFORM").performScrollTo().performClick()
        val document = viewModel.state.value.document
        composeRule.onNodeWithTag("geometry-rotation-x-1-value").performScrollTo().performTextReplacement("12.5")
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        composeRule.runOnIdle { viewModel.focusEvent(2L, seek = false) }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument == null }
        val deadline = android.os.SystemClock.uptimeMillis() + 400L
        composeRule.waitUntil(2_000) { android.os.SystemClock.uptimeMillis() >= deadline }
        assertEquals(document, viewModel.state.value.document)
    }

    @Test
    fun toolInstancesPreserveCanonicalStateAndExistingHistory() =
        assertPresentationInvariant("TOOL_INSTANCES_EXPERIMENTAL", "tool-instance-workspace")

    @Test
    fun glassLayeredPreservesCanonicalStateAndExistingHistory() =
        assertPresentationInvariant("GLASS_LAYERED_EXPERIMENTAL", "glass-layered-workspace")

    @Test
    fun precisionLensPreservesCanonicalStateAndExistingHistory() =
        assertPresentationInvariant("PRECISION_LENS_EXPERIMENTAL", "precision-lens-workspace")

    @Test
    fun subtitleObjectPreservesCanonicalStateAndExistingHistory() =
        assertPresentationInvariant("SUBTITLE_OBJECT_EXPERIMENTAL", "subtitle-object-workspace")

    @Test
    fun edgeBookmarkPreservesCanonicalStateAndExistingHistory() =
        assertPresentationInvariant("EDGE_BOOKMARK_EXPERIMENTAL", "edge-bookmark-workspace")

    @Test
    fun timelineDockPreservesCanonicalStateAndExistingHistory() =
        assertPresentationInvariant("TIMELINE_DOCK_EXPERIMENTAL", "timeline-dock-workspace")

    private fun assertPresentationInvariant(variant: String, rootTag: String) {
        composeRule.onNodeWithTag("recovery-restore")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.state.value.subtitleLoaded &&
                viewModel.state.value.document.events.size == 2
        }

        viewModel.focusEvent(1L, seek = false)
        viewModel.updateEventText(1L, "Presentation invariant")
        composeRule.waitUntil(5_000) {
            eventText(1L) == "Presentation invariant" &&
                viewModel.state.value.focusedEventId == 1L &&
                viewModel.state.value.canUndo
        }

        // Document edits schedule recovery asynchronously. Wait for the durable
        // journal to observe the edit before opening popup UI so the test does
        // not conflate recovery IO with presentation navigation.
        val recoveryStore = RecoveryStore(composeRule.activity.application)
        composeRule.waitUntil(10_000) {
            recoveryStore.read()
                ?.document
                ?.events
                ?.firstOrNull { it.id == 1L }
                ?.text == "Presentation invariant"
        }

        switchPresentation(variant, rootTag)

        assertEquals(
            "presentation $variant must not mutate canonical Event text",
            "Presentation invariant",
            eventText(1L),
        )
        assertEquals(
            "presentation $variant must preserve Focus identity",
            1L,
            viewModel.state.value.focusedEventId,
        )
        assertTrue(
            "presentation $variant must preserve pre-existing Undo history",
            viewModel.state.value.canUndo,
        )

        viewModel.undo()
        composeRule.waitUntil(5_000) {
            eventText(1L) == "Recovered line" && viewModel.state.value.canRedo
        }
        viewModel.redo()
        composeRule.waitUntil(5_000) {
            eventText(1L) == "Presentation invariant"
        }
    }

    private fun switchPresentation(variant: String, rootTag: String) {
        hideKeyboard()
        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        composeRule.onNodeWithTag("ui-variant-lab").assertIsDisplayed()
        composeRule.onNodeWithTag("ui-variant-use-$variant")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(rootTag, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(rootTag, useUnmergedTree = true).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun hideKeyboard() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        composeRule.waitUntil(5_000) {
            composeRule.activity.window.decorView.rootWindowInsets
                ?.isVisible(android.view.WindowInsets.Type.ime()) != true
        }
        composeRule.waitForIdle()
    }

    private fun eventText(id: Long): String =
        viewModel.state.value.document.events.first { it.id == id }.text
}

package io.github.assworkbench.app

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import io.github.assworkbench.app.ui.WorkbenchTool
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.SubtitleProject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorRegressionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<EditorRegressionHostActivity>()

    private lateinit var application: Application
    private lateinit var recoveryStore: RecoveryStore
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        recoveryStore = RecoveryStore(application)
        viewModel = composeRule.activity.editorViewModel
    }

    @After
    fun tearDown() {
        recoveryStore.clear()
    }

    private fun selectUiVariant(tag: String) {
        val choices = composeRule.onAllNodesWithTag(tag)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (choices.isEmpty() && tag == "ui-variant-use-SPATIAL_EXPERIMENTAL") {
            // Already selected: the two-layout chooser does not offer "Use" twice.
            composeRule.onNodeWithText("关闭").performClick()
        } else {
            composeRule.onNodeWithTag(tag)
                .performScrollTo().assertIsDisplayed().performClick()
        }
        composeRule.waitForIdle()
    }

    @Test fun fixedNavigationAndExternalToolCommandKeepTheRequestedGroupConsistent() {
        restoreRecovery()
        if (composeRule.onAllNodesWithTag("spatial-workspace", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
            composeRule.onNodeWithTag("workspace-direct-mode-toggle").performClick()
        }
        eventRow(1L).performClick()
        openTool("STYLE")
        composeRule.onNodeWithTag("fixed-preview-density").assertIsDisplayed()
        val before = viewModel.state.value.document
        composeRule.onNodeWithTag("fixed-group-NAVIGATION").performScrollTo().performClick()
        composeRule.onNodeWithTag("fixed-group-NAVIGATION").assertIsSelected()
        composeRule.onNodeWithTag("fixed-tool-SUBTITLES").assertIsSelected()
        composeRule.onNodeWithTag("fixed-tool-STYLE").assertDoesNotExist()
        composeRule.onNodeWithTag("fixed-preview-density").assertIsDisplayed()
        // Global commands must leave the list navigation group too.
        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithText("字体管理").performClick()
        composeRule.onNodeWithTag("fixed-group-RESOURCES").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("fixed-tool-FONTS").assertIsSelected()
        composeRule.onNodeWithTag("fixed-tool-SUBTITLES").assertDoesNotExist()
        openTool("TEXT")
        composeRule.onNodeWithTag("event-collapse-1").performClick()
        composeRule.onNodeWithTag("fixed-group-NAVIGATION").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("fixed-tool-SUBTITLES").assertIsSelected()
        assertEquals(before, viewModel.state.value.document)
    }

    @Test
    fun recoveryEntryRestoresAndCollapsedRawDraftSurvives() {
        restoreRecovery()

        // Restoring must not consume the journal. A second process death before
        // explicit save/discard should still have something to recover.
        assertTrue(recoveryStore.exists())

        eventRow(1L)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag("event-raw-1")
            .assertIsDisplayed()
            .performTextInput(" DRAFT")

        composeRule.onNodeWithTag("event-collapse-1")
            .performClick()

        eventRow(1L)
            .performClick()

        composeRule.onNodeWithTag("event-raw-1")
            .assertIsDisplayed()

        // The canonical Event is still untouched after collapse/re-open. Applying
        // the visible editor now must commit the draft that survived disposal.
        assertFalse(eventText(1L).contains("DRAFT"))
        composeRule.onNodeWithTag("event-apply-text-1")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            eventText(1L) == "Recovered line DRAFT"
        }
    }

    @Test
    fun mainLayoutSwitchAlwaysReachableAndDoesNotReplaceCanonicalDocument() {
        restoreRecovery()
        val original = viewModel.state.value.document
        // Layout chrome must stay present even if WorkspaceState hides surfaces.
        if (composeRule.onAllNodesWithTag("spatial-workspace", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
            composeRule.onNodeWithContentDescription("切换至标准工作台").performClick()
        }
        composeRule.onNodeWithTag("fixed-workspace").assertIsDisplayed()
        assertEquals(original, viewModel.state.value.document)
        composeRule.onNodeWithContentDescription("切换至无限画布").performClick()
        composeRule.onNodeWithTag("spatial-workspace").assertIsDisplayed()
        assertEquals(original, viewModel.state.value.document)
        composeRule.onNodeWithContentDescription("切换至标准工作台").performClick()
        composeRule.onNodeWithTag("fixed-workspace").assertIsDisplayed()
        assertEquals(original, viewModel.state.value.document)
    }

    @Test
    fun unifiedCanvasSwitchesDirectlyBetweenRealPreviewAndSubtitleAuthoring() {
        restoreRecovery()
        switchToUnifiedCanvas()
        if (composeRule.onAllNodesWithTag("spatial-tool-switcher")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()) {
            composeRule.onNodeWithTag("spatial-quick-preview").performClick()
        }
        composeRule.onNodeWithTag("spatial-tool-switcher").performClick()
        composeRule.onNodeWithTag("spatial-switch-to-subtitles").performClick()
        composeRule.onNodeWithTag("spatial-native-content-subtitles").assertExists()
        composeRule.onNodeWithTag("spatial-reference-preview").assertExists()
        composeRule.onNodeWithTag("spatial-tool-switcher").performClick()
        composeRule.onNodeWithTag("spatial-switch-to-preview").performClick()
        composeRule.onNodeWithTag("spatial-native-content-preview").assertExists()
    }

    @Test
    fun spatialWorkspaceExposesBoardAndNativeEditorWithoutMovingWorldNodes() {
        restoreRecovery()
        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        selectUiVariant("ui-variant-use-SPATIAL_EXPERIMENTAL")

        composeRule.onNodeWithTag("spatial-workspace").assertIsDisplayed()
        // A first-time session may open the video at native size; the board is one tap away.
        if (composeRule.onAllNodesWithTag("spatial-return-to-board")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
            composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        }
        composeRule.onNodeWithTag("spatial-overview").performClick()
        composeRule.onNodeWithTag("spatial-node-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-node-subtitles").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-node-audio").assertIsDisplayed()

        val previewBeforeRail = composeRule.onNodeWithTag("spatial-node-preview")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("spatial-edge-handle-left").performClick()
        composeRule.onNodeWithTag("spatial-edge-rail-left").assertIsDisplayed()
        val previewAfterRail = composeRule.onNodeWithTag("spatial-node-preview")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(previewBeforeRail, previewAfterRail)

        composeRule.onNodeWithTag("spatial-edge-pin-left").performClick()
        composeRule.onNodeWithTag("spatial-edge-entry-subtitles").performClick()
        composeRule.onNodeWithTag("spatial-focused-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-native-content-subtitles").assertExists()
        composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        composeRule.onNodeWithTag("spatial-edge-rail-left").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-edge-close-left").performClick()
        composeRule.onNodeWithTag("spatial-node-subtitles").assertIsDisplayed()

        composeRule.onNodeWithText("召回").performClick()
        composeRule.onNodeWithTag("spatial-recall-subtitles").performClick()
        composeRule.onNodeWithTag("spatial-native-content-subtitles").assertExists()
    }

    @Test fun birdseyeNavigationPreservesSubtitleDocumentAndUndoHistory() {
        restoreRecovery()
        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        selectUiVariant("ui-variant-use-SPATIAL_EXPERIMENTAL")
        val before = viewModel.state.value.document
        val undoBefore = viewModel.state.value.canUndo
        val redoBefore = viewModel.state.value.canRedo
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.onNodeWithTag("spatial-birdseye-node-audio").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertEquals(undoBefore, viewModel.state.value.canUndo)
        assertEquals(redoBefore, viewModel.state.value.canRedo)
        composeRule.onNodeWithTag("spatial-native-content-audio").assertExists()
    }

    @Test fun spatialWorkspaceLongPressDragExtractsRotationWithoutEditingAss() =
        verifySpatialParameterExtraction("event.rotation.z", "rotation-z", "TRANSFORM", "slider")

    @Test fun spatialWorkspaceLongPressDragExtractsPositionWithoutEditingAss() =
        verifySpatialParameterExtraction("event.position.xy", "position-xy", "PLACEMENT", "xy")

    @Test fun spatialWorkspaceLongPressDragExtractsScaleWithoutEditingAss() =
        verifySpatialParameterExtraction("event.scale.xy", "scale-xy", "TRANSFORM", "slider-x")

    @Test fun spatialWorkspaceLongPressDragExtractsShearWithoutEditingAss() =
        verifySpatialParameterExtraction("event.shear.xy", "shear-xy", "TRANSFORM", "xy")

    private fun verifySpatialParameterExtraction(key: String, extractionTag: String, section: String, control: String) {
        restoreRecovery()

        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        selectUiVariant("ui-variant-use-SPATIAL_EXPERIMENTAL")

        composeRule.onNodeWithTag("spatial-add-tool").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(
                "spatial-native-content-CAPABILITIES-primary",
                useUnmergedTree = true,
            ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        composeRule.onNodeWithTag("tool-search")
            .performTextReplacement("POSITION")
        hideKeyboard()
        composeRule.onNodeWithTag("tool-POSITION")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(
                "spatial-native-content-POSITION-primary",
                useUnmergedTree = true,
            ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }

        composeRule.onNodeWithTag("position-section-$section")
            .performScrollTo()
            .performClick()

        val before = viewModel.state.value.document
        assertFalse(viewModel.state.value.canUndo)

        composeRule.onNodeWithTag("position-parameter-list")
            .performScrollToNode(hasTestTag("extract-$extractionTag-1"))
        composeRule.onNodeWithTag("extract-$extractionTag-1")
            .assertIsDisplayed()
            .performTouchInput {
                down(center)
                advanceEventTime(800)
                moveBy(Offset(72f, 24f))
                up()
            }

        // A projection is a real ToolInstance, reachable through birdseye even
        // when the user remains focused in the source parameter editor.
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.onNodeWithTag("spatial-birdseye-node-parameter-${key}-1")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-native-content-parameter-${key}-1").assertExists()
        composeRule.onNodeWithTag(
            "parameter-projection-${control}-parameter-${key}-1"
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        composeRule.onNodeWithTag("spatial-node-parameter-${key}-1").assertExists()

        assertEquals(before, viewModel.state.value.document)
        assertFalse("Extracting a workspace projection must not enter ASS Undo", viewModel.state.value.canUndo)
    }

    @Test
    fun unifiedCanvasHidesAndRecallsNativeToolDirectory() {
        restoreRecovery()
        switchToUnifiedCanvas()
        composeRule.onNodeWithTag("spatial-add-tool").performClick()
        composeRule.onNodeWithTag("spatial-native-content-CAPABILITIES-primary").assertExists()
        composeRule.onNodeWithTag("spatial-menu-CAPABILITIES-primary").performClick()
        composeRule.onNodeWithText("收回工具").performClick()
        composeRule.onNodeWithText("召回").performClick()
        composeRule.onNodeWithTag("spatial-recall-CAPABILITIES-primary").performClick()
        composeRule.onNodeWithTag("spatial-native-content-CAPABILITIES-primary").assertExists()
    }

    @Test
    fun unifiedCanvasPreservesPersistentTimelineAndToolNavigation() {
        restoreRecovery()
        switchToUnifiedCanvas()
        composeRule.onNodeWithTag("spatial-timeline-toggle").performClick()
        composeRule.onNodeWithTag("spatial-timeline-dock").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-timeline-content").assertExists()
        composeRule.onNodeWithTag("spatial-timeline-expand").performClick()
        composeRule.onNodeWithTag("spatial-timeline-dock").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-timeline-close").performClick()
        composeRule.onNodeWithTag("spatial-timeline-dock").assertDoesNotExist()
    }

    @Test
    fun unifiedCanvasRetainsTransparentAudioAndInputPassthrough() {
        restoreRecovery()
        switchToUnifiedCanvas()
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.onNodeWithTag("spatial-birdseye-node-audio").performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-native-content-audio").assertExists()
        composeRule.onNodeWithTag("spatial-menu-audio").performClick()
        composeRule.onNodeWithText("恢复实底").performClick()
        composeRule.onNodeWithTag("spatial-menu-audio").performClick()
        composeRule.onNodeWithText("透明叠加").performClick()
        composeRule.onNodeWithTag("spatial-menu-audio").performClick()
        composeRule.onNodeWithText("穿透操作视频").performClick()
    }

    @Test
    fun unifiedCanvasCanOpenNativePositionEditorWithoutASecondPrecisionUi() {
        restoreRecovery()
        switchToUnifiedCanvas()
        composeRule.onNodeWithTag("spatial-add-tool").performClick()
        composeRule.onNodeWithTag("tool-search").performTextReplacement("POSITION")
        hideKeyboard()
        composeRule.onNodeWithTag("tool-POSITION").performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("spatial-native-content-POSITION-primary")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        composeRule.onNodeWithTag("spatial-reference-preview").assertExists()
        composeRule.onNodeWithTag("spatial-precision-toggle").performClick()
        composeRule.onNodeWithTag("precision-controls").assertExists()
        composeRule.onNodeWithTag("precision-gain-FINE").assertExists()
        composeRule.onNodeWithTag("precision-lens-FLOATING_LENS").assertExists()
        composeRule.onNodeWithTag("spatial-precision-toggle").performClick()
    }

    @Test
    fun rawDraftSurvivesSwitchingBetweenEvents() {
        restoreRecovery()

        eventRow(1L)
            .performClick()
        composeRule.onNodeWithTag("event-raw-1")
            .performTextInput(" SWITCH")

        openTool("SUBTITLES")
        eventRow(2L)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("event-raw-2")
            .assertIsDisplayed()

        openTool("SUBTITLES")
        eventRow(1L)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("event-raw-1")
            .assertIsDisplayed()

        assertFalse(eventText(1L).contains("SWITCH"))
        composeRule.onNodeWithTag("event-apply-text-1")
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            eventText(1L) == "Recovered line SWITCH"
        }
        assertEquals("Second recovered line", eventText(2L))
    }

    @Test
    fun rawDraftFromPreviousWorkspaceSessionMustNotLeakIntoSameEventId() {
        restoreRecovery()
        val firstSession = viewModel.state.value.workspaceSessionId
        eventRow(1L).performClick()
        composeRule.onNodeWithTag("event-raw-1").performTextInput(" PRIOR SESSION")
        composeRule.onNodeWithTag("event-apply-text-1").assertExists()
        assertEquals("Recovered line", eventText(1L))

        // Reload an identical fixture as a different project session. The Event
        // identifier remains 1, but the old project owns its uncommitted draft.
        composeRule.activityRule.scenario.onActivity { it.restoreDeterministicFixture() }
        composeRule.waitUntil(10_000) {
            viewModel.state.value.workspaceSessionId != firstSession &&
                viewModel.state.value.document.events.size == 2 &&
                eventText(1L) == "Recovered line"
        }
        eventRow(1L).performClick()
        composeRule.onNodeWithTag("event-raw-1")
            .assertIsDisplayed()
            .assertTextContains("Recovered line")
        // No stale Apply action: the new project's editor is clean, even though
        // its event ID and canonical text equal those from the previous session.
        composeRule.onNodeWithTag("event-apply-text-1").assertDoesNotExist()
    }

    @Test
    fun rawDraftSurvivesActivityRecreation() {
        restoreRecovery()
        // Global search requests LIST once; recreation must restore the later EDITOR page.
        composeRule.onNodeWithContentDescription("搜索").performClick()

        eventRow(1L)
            .performClick()
        composeRule.onNodeWithTag("event-raw-1")
            .performTextInput(" ROTATED")

        // Recreate the Activity rather than merely recomposing. This exercises
        // rememberSaveable + SaveableStateHolder across Android state restoration.
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("event-raw-1")
            .assertIsDisplayed()
        assertFalse(eventText(1L).contains("ROTATED"))

        composeRule.onNodeWithTag("event-apply-text-1")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            eventText(1L) == "Recovered line ROTATED"
        }
    }

    private fun assertInspectorDraftStillPresent(stage: String) {
        Log.i("AsswbRegression", "rotation:assert-draft:$stage")
        // Check both the unsaved payload and the Apply action. Earlier CI logs
        // ended at "apply-draft" without an assertion exception, obscuring whether
        // draft loss or commit failure caused this connected-test failure.
        val draftText = composeRule.onNodeWithTag("event-raw-1")
            .fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.EditableText
            ].text
        assertTrue("Uncommitted raw draft must still contain WORKBENCH after $stage: $draftText",
            draftText?.contains("WORKBENCH") == true)
        composeRule.onNodeWithTag("event-apply-text-1").assertExists()
    }


    @Test
    fun fixedInspectorSharesUncommittedEventDraftAcrossToolInstances() {
        restoreRecovery()
        eventRow(1L).performClick()
        composeRule.onNodeWithTag("event-raw-1").performTextInput(" SWITCH")
        composeRule.onNodeWithTag("event-raw-1").assertTextContains("SWITCH")
        // Switching to Effects removes the TEXT provider but must not
        // create a new draft owner for the same fixed Event inspector.
        openTool("EFFECTS")
        composeRule.onNodeWithTag("fixed-inspector").assertIsDisplayed()
        assertEquals("Recovered line", eventText(1L))
        openTool("TEXT")
        composeRule.onNodeWithTag("event-raw-1").assertTextContains("SWITCH")
        composeRule.onNodeWithTag("event-apply-text-1").assertExists()
        assertEquals("Recovered line", eventText(1L))
        composeRule.onNodeWithTag("event-apply-text-1").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { eventText(1L) == "Recovered line SWITCH" }
    }

    @Test
    fun inspectorDraftSurvivesToolSwitchAndRotation() {
        Log.i("AsswbRegression", "rotation:restore-recovery")
        restoreRecovery()
        val draftSessionId = viewModel.state.value.workspaceSessionId
        Log.i("AsswbRegression", "rotation:open-event")
        eventRow(1L).performClick()
        Log.i("AsswbRegression", "rotation:type-draft")
        composeRule.onNodeWithTag("event-raw-1").performTextInput(" WORKBENCH")
        Log.i("AsswbRegression", "rotation:switch-effects")
        openTool("EFFECTS")
        composeRule.onNodeWithTag("fixed-inspector").assertIsDisplayed()
        Log.i("AsswbRegression", "rotation:switch-text")
        openTool("TEXT")
        composeRule.onNodeWithTag("event-raw-1").assertIsDisplayed()
        assertInspectorDraftStillPresent("after-tool-switch")
        Log.i("AsswbRegression", "rotation:request-landscape")
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        Log.i("AsswbRegression", "rotation:wait-landscape")
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        Log.i("AsswbRegression", "rotation:check-landscape")
        val previewNodes = composeRule.onAllNodesWithTag("preview-workspace").fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (previewNodes.isNotEmpty()) composeRule.onNodeWithTag("preview-workspace").assertIsDisplayed()
        val listNodes = composeRule.onAllNodesWithTag("subtitle-navigation").fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (listNodes.isNotEmpty()) composeRule.onNodeWithTag("subtitle-navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("event-inspector").assertIsDisplayed()
        assertInspectorDraftStillPresent("landscape")
        Log.i("AsswbRegression", "rotation:capture-landscape")
        captureLayout("landscape")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            Log.i("AsswbRegression", "rotation:resize-tablet")
            automation.executeShellCommand("wm size 1920x1200").close()
            Log.i("AsswbRegression", "rotation:set-tablet-density")
            automation.executeShellCommand("wm density 160").close()
            Log.i("AsswbRegression", "rotation:request-tablet-landscape")
            composeRule.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            Log.i("AsswbRegression", "rotation:wait-tablet-config")
            composeRule.waitUntil(10_000) {
                val configuration = composeRule.activity.resources.configuration
                configuration.screenWidthDp >= 1600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            Log.i("AsswbRegression", "rotation:wait-tablet-idle")
            composeRule.waitForIdle()
            Log.i("AsswbRegression", "rotation:check-tablet-bounds")
            val preview = composeRule.onNodeWithTag("preview-workspace").fetchSemanticsNode().boundsInRoot
            val navigation = composeRule.onNodeWithTag("subtitle-navigation").fetchSemanticsNode().boundsInRoot
            val inspector = composeRule.onNodeWithTag("fixed-inspector").fetchSemanticsNode().boundsInRoot
            val fixed = composeRule.onNodeWithTag("fixed-workspace").fetchSemanticsNode().boundsInRoot
            assertTrue("Preview must remain inside fixed workspace", preview.left >= fixed.left && preview.right <= fixed.right)
            assertTrue("Navigation and inspector must not overlap", navigation.right <= inspector.left || navigation.bottom <= inspector.top)
            composeRule.onNodeWithTag("canvas-workspace").assertDoesNotExist()
            composeRule.onNodeWithTag("preview-divider").assertDoesNotExist()
            assertEquals("Viewport resizing must not replace the editor document session",
                draftSessionId, viewModel.state.value.workspaceSessionId)
            assertInspectorDraftStillPresent("tablet-landscape")
            Log.i("AsswbRegression", "rotation:capture-tablet")
            captureLayout("tablet-landscape")
        } finally {
            Log.i("AsswbRegression", "rotation:reset-display-size")
            automation.executeShellCommand("wm size reset").close()
            Log.i("AsswbRegression", "rotation:reset-display-density")
            automation.executeShellCommand("wm density reset").close()
        }
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.screenWidthDp < 1200
        }

        // wm size/density reset can recreate the responsive host while preserving the
        // inspector page. Request the navigation domain through the persistent FIXED
        // workspace semantics instead of relying on a transient compact-page chip.
        Log.i("AsswbRegression", "rotation:find-list-page")
        showFixedSubtitleList()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("event-row-1", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        Log.i("AsswbRegression", "rotation:select-event")
        eventRow(1L)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("event-raw-1", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        assertInspectorDraftStillPresent("after-tablet-reset-and-list-return")
        Log.i("AsswbRegression", "rotation:apply-draft")
        composeRule.onNodeWithTag("event-apply-text-1").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { eventText(1L) == "Recovered line WORKBENCH" }
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        captureLayout("portrait")
    }

    @Test
    fun groupedNavigationAndGeometrySectionsPreserveDocument() {
        restoreRecovery()
        eventRow(1L).performClick()
        val before = viewModel.state.value.document
        for (name in listOf("FRAMES", "FONT_REQUIREMENTS", "QC", "PROJECT", "POSITION")) {
            openTool(name)
            composeRule.onNodeWithTag("fixed-inspector").assertIsDisplayed()
        }
        for (section in listOf("TRANSFORM", "CLIP", "STYLE_LAYOUT", "PLACEMENT")) {
            composeRule.onNodeWithTag("position-section-$section").performScrollTo().performClick()
            composeRule.waitForIdle()
            assertEquals(before, viewModel.state.value.document)
        }
        captureLayout("grouped-geometry")
        openTool("TEXT")
        val compactList = composeRule.onAllNodesWithTag("fixed-page-list").fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (compactList.isNotEmpty()) {
            composeRule.onNodeWithTag("fixed-page-list").performClick()
            eventRow(1L).assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("event-raw-1").assertIsDisplayed()
        }
        assertEquals(before, viewModel.state.value.document)
    }

    @Test fun positionFieldsFollowTransientPreviewAndCancelRestoresTypedDraft() {
        restoreRecovery()
        viewModel.focusEvent(1L, seek = false)
        viewModel.setEventPosition(1L, 100.0, 200.0)
        openTool("POSITION")
        val before = viewModel.state.value.document
        composeRule.onNodeWithTag("position-value-x-1").performScrollTo().performTextReplacement("333")
        composeRule.runOnIdle { viewModel.previewEventPosition(1L, 125.0, 240.0) }
        composeRule.onNodeWithTag("position-value-x-1").assertTextContains("125.0")
        composeRule.onNodeWithTag("position-value-y-1").assertTextContains("240.0")
        assertEquals(before, viewModel.state.value.document)
        composeRule.runOnIdle { viewModel.clearTransientPreview("geometry:1") }
        composeRule.onNodeWithTag("position-value-x-1").assertTextContains("333")
        composeRule.onNodeWithTag("position-value-y-1").assertTextContains("200.0")
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitUntil(5_000) { eventText(1L) == "Recovered line" }
    }

    @Test fun equalGeometryPublicationStillAdvancesWriterRevision() {
        restoreRecovery()
        val vm = viewModel
        composeRule.runOnIdle {
            vm.previewEventRotationX(1L, 12.5)
            val first = vm.state.value
            vm.previewEventRotationX(1L, 12.5)
            val second = vm.state.value
            assertEquals(first.previewDocument, second.previewDocument)
            assertEquals(first.geometryPreviewRevision + 1L, second.geometryPreviewRevision)
            vm.clearTransientPreview("geometry:1")
        }
    }

    @Test fun transformFieldsProjectExternalPreviewAndRestoreDrafts() {
        restoreRecovery()
        viewModel.focusEvent(1L, seek = false)
        openTool("POSITION")
        composeRule.onNodeWithTag("position-section-TRANSFORM").performScrollTo().performClick()
        val before = viewModel.state.value.document
        val cases = listOf<Triple<String, String, () -> Unit>>(
            Triple("rotation-x", "12.5") { viewModel.previewEventRotationX(1L, 12.5) },
            Triple("rotation-y", "23.5") { viewModel.previewEventRotationY(1L, 23.5) },
            Triple("rotation-z", "34.5") { viewModel.previewEventRotationZ(1L, 34.5) },
            Triple("scale-x", "125.0") { viewModel.previewEventScale(1L, 125.0, 175.0) },
            Triple("scale-y", "175.0") { viewModel.previewEventScale(1L, 125.0, 175.0) },
            Triple("shear-x", "0.25") { viewModel.previewEventShear(1L, 0.25, -0.5) },
            Triple("shear-y", "-0.5") { viewModel.previewEventShear(1L, 0.25, -0.5) },
        )
        for ((parameter, value, preview) in cases) {
            val fieldTag = "geometry-$parameter-1-value"
            composeRule.onNodeWithTag("position-parameter-list")
                .performScrollToNode(hasTestTag(fieldTag))
            val field = composeRule.onNodeWithTag(fieldTag)
            field.performTextReplacement("unfinished")
            composeRule.runOnIdle(preview)
            field.assertTextContains(value)
            composeRule.onNodeWithTag("geometry-$parameter-1-slider").assertIsNotEnabled()
            assertEquals(before, viewModel.state.value.document)
            composeRule.runOnIdle { viewModel.clearTransientPreview("geometry:1") }
            field.assertTextContains("unfinished")
            composeRule.onNodeWithTag("geometry-$parameter-1-slider").assertIsEnabled()
        }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test fun equalExternalTakeoverCancelsPendingNumericCommit() {
        restoreRecovery()
        viewModel.focusEvent(1L, seek = false)
        openTool("POSITION")
        composeRule.onNodeWithTag("position-section-TRANSFORM").performScrollTo().performClick()
        val before = viewModel.state.value.document
        val field = composeRule.onNodeWithTag("geometry-rotation-x-1-value")
        field.performScrollTo().performTextReplacement("12.5")
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        composeRule.runOnIdle { viewModel.previewEventRotationX(1L, 12.5) }
        composeRule.onNodeWithTag("geometry-rotation-x-1-slider").assertIsNotEnabled()
        var deadline = android.os.SystemClock.uptimeMillis() + 400L
        composeRule.waitUntil(2_000) { android.os.SystemClock.uptimeMillis() >= deadline }
        assertEquals(before, viewModel.state.value.document)
        composeRule.runOnIdle { viewModel.clearTransientPreview("geometry:1") }
        field.assertTextContains("12.5")
        composeRule.onNodeWithTag("geometry-rotation-x-1-slider").assertIsEnabled()
        deadline = android.os.SystemClock.uptimeMillis() + 400L
        composeRule.waitUntil(2_000) { android.os.SystemClock.uptimeMillis() >= deadline }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test fun transformSliderStillOwnsItsPreviewAndCommitsOneUndoStep() {
        restoreRecovery()
        viewModel.focusEvent(1L, seek = false)
        openTool("POSITION")
        composeRule.onNodeWithTag("position-section-TRANSFORM").performScrollTo().performClick()
        val before = viewModel.state.value.document
        composeRule.onNodeWithTag("geometry-rotation-x-1-slider").performScrollTo().performTouchInput {
            swipe(start = center, end = androidx.compose.ui.geometry.Offset(width * 0.75f, center.y), durationMillis = 700)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.document != before }
        composeRule.onNodeWithTag("geometry-rotation-x-1-slider").assertIsEnabled()
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitUntil(5_000) { viewModel.state.value.document == before }
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test fun moveEndpointFieldsProjectPreviewAndRestoreDrafts() {
        restoreRecovery()
        composeRule.runOnIdle {
            viewModel.updateEventText(1L, "{\\move(100,200,300,400,50,1500)}Recovered line")
            viewModel.focusEvent(1L, seek = false)
        }
        openTool("POSITION")
        val before = viewModel.state.value.document
        val values = listOf("start-x" to "125.0", "start-y" to "240.0", "end-x" to "350.0", "end-y" to "460.0")
        for ((name, value) in values) {
            val field = composeRule.onNodeWithTag("geometry-move-$name-1-value")
            field.performScrollTo().performTextReplacement("unfinished")
            composeRule.runOnIdle { viewModel.previewEventMove(1L, 125.0, 240.0, 350.0, 460.0) }
            field.assertTextContains(value)
            composeRule.onNodeWithTag("geometry-move-1-apply").performScrollTo().assertIsNotEnabled()
            assertEquals(before, viewModel.state.value.document)
            composeRule.runOnIdle { viewModel.clearTransientPreview("geometry:1") }
            field.performScrollTo().assertTextContains("unfinished")
        }
        assertEquals(before, viewModel.state.value.document)
    }

    @Test fun originFieldsProjectPreviewAndRestoreDrafts() {
        restoreRecovery()
        composeRule.runOnIdle {
            viewModel.setEventOrigin(1L, 100.0, 200.0)
            viewModel.focusEvent(1L, seek = false)
        }
        openTool("POSITION")
        composeRule.onNodeWithTag("position-section-TRANSFORM").performScrollTo().performClick()
        val before = viewModel.state.value.document
        for ((axis, value) in listOf("x" to "125.0", "y" to "240.0")) {
            val field = composeRule.onNodeWithTag("geometry-origin-$axis-1-value")
            field.performScrollTo().performTextReplacement("unfinished")
            composeRule.runOnIdle { viewModel.previewEventOrigin(1L, 125.0, 240.0) }
            field.assertTextContains(value)
            composeRule.onNodeWithTag("geometry-origin-1-apply").assertIsNotEnabled()
            composeRule.runOnIdle { viewModel.clearTransientPreview("geometry:1") }
            field.assertTextContains("unfinished")
        }
        assertEquals(before, viewModel.state.value.document)
    }

    @Test fun rectClipExternalTakeoverCancelsPendingDraftAndRestoresAllCoordinates() {
        restoreRecovery()
        composeRule.runOnIdle {
            viewModel.setEventRectClip(1L, 100.0, 200.0, 300.0, 400.0, true)
            viewModel.focusEvent(1L, seek = false)
        }
        openTool("POSITION")
        composeRule.onNodeWithTag("position-section-CLIP").performScrollTo().performClick()
        val before = viewModel.state.value.document
        val field = composeRule.onNodeWithTag("geometry-clip-left-1-value")
        field.performScrollTo().performTextReplacement("120")
        composeRule.waitUntil(5_000) { vi…98032 tokens truncated…         .testTag("spatial-live-" + entry.id.replace(':', '-'))) {
                                        contentState.SaveableStateProvider(canvasSaveableContentKey(sessionId, CanvasContentRole.BOARD, entry.id)) {
                                            content(entry.id, !node.passthrough)
                                        }
                                    }
                                } else {
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        CanvasToolSummary(entry, width, height,
                                            enabled = !gestureOwned, onOpen = { focus(entry.id) })
                                    }
                                }
                                Icon(Icons.Filled.OpenInFull, "调整 " + entry.title + " 大小",
                                    Modifier.align(Alignment.End).size(44.dp)
                                        .testTag("spatial-resize-" + entry.id.replace(':', '-'))
                                        .pointerInput(entry.id, gestureOwned, node.layoutLocked) {
                                            detectDragGestures(
                                                onDragStart = { if (!gestureOwned && !liveNode.layoutLocked) moving = liveNode },
                                                onDragEnd = {
                                                    if (!gestureOwned) moving?.let(::updateNode)
                                                    moving = null
                                                },
                                                onDragCancel = { moving = null },
                                                onDrag = { change, delta ->
                                                    if (!gestureOwned && !liveNode.layoutLocked) {
                                                        change.consume()
                                                        moving = liveNode.resize(
                                                            delta.x / density / scale,
                                                            delta.y / density / scale,
                                                        )
                                                    }
                                                },
                                            )
                                        }.padding(12.dp))
                            }
                        }
                    }
                }
            }
            // Reusable tool access does not require travelling across world space.
            Surface(Modifier.align(Alignment.BottomCenter)
                .padding(bottom = dockHeight + 8.dp, start = 8.dp, end = 8.dp)
                .zIndex(1_000_010f), shape = MaterialTheme.shapes.large, shadowElevation = 3.dp) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // Keep the only creation affordance inside the visible viewport.
                    // Instance tabs may scroll without pushing the directory off-screen.
                    TextButton(onClick = ::showToolPicker, enabled = !gestureOwned,
                        modifier = Modifier.testTag("spatial-add-tool")) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Text("工具")
                    }
                    VerticalDivider(Modifier.height(26.dp).padding(horizontal = 4.dp))
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically) {
                        entries.forEach { entry ->
                            val node = nodes.firstOrNull { it.id == entry.id }
                            TextButton(
                                enabled = node != null && !gestureOwned,
                                onClick = { focus(entry.id) },
                                modifier = Modifier.testTag("spatial-quick-" + entry.id.replace(':', '-')),
                            ) {
                                Icon(if (node?.hidden == true) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Tab, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(entry.title, maxLines = 1)
                            }
                        }
                    }
                }
            }
            // Compact discoverability handle; the drawer is an optional list, not a second editor.
            if (!toolDrawerOpen && !toolDrawerResident) FilledTonalIconButton(
                onClick = { toolDrawerOpen = true },
                modifier = Modifier.align(Alignment.CenterStart).zIndex(1_000_012f)
                    .testTag("spatial-edge-handle-left"),
            ) { Icon(Icons.Filled.ChevronRight, "展开工具目录") }

        } else {
            // No camera transform reaches this subtree: editors keep native-size hit areas.
            val (entry, node) = focused!!
            Surface(Modifier.fillMaxSize()
                .padding(bottom = dockHeight)
                .padding(start = if (toolDrawerResident) railWidth else 0.dp)
                // Bookmark rail owns a real touch region; the focused editor
                // must not place sliders/fields beneath it.
                .padding(end = if (entries.any { it.bookmarked }) 68.dp else 0.dp)
                .testTag("spatial-focused-editor"),
                color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = ::returnToBoard, enabled = !gestureOwned,
                            modifier = Modifier.testTag("spatial-return-to-board")) {
                            Icon(Icons.Filled.ArrowBack, null, Modifier.size(18.dp))
                            Text("画布")
                        }
                        Box(Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth()
                                .clickable(enabled = !gestureOwned) { switcherOpen = true }
                                .testTag("spatial-tool-switcher")) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.title, style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1)
                                    Icon(Icons.Filled.ArrowDropDown, "切换工具", Modifier.size(18.dp))
                                }
                                if (entry.subtitle.isNotBlank()) Text(entry.subtitle,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            DropdownMenu(expanded = switcherOpen,
                                onDismissRequest = { switcherOpen = false }) {
                                entries.forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text((if (target.id == entry.id) "✓ " else "") + target.title) },
                                        enabled = !gestureOwned,
                                        modifier = Modifier.testTag(
                                            "spatial-switch-to-" + target.id.replace(':', '-')),
                                        onClick = {
                                            focus(target.id)
                                            switcherOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { timelineDockVisible = !timelineDockVisible },
                            enabled = !gestureOwned, modifier = Modifier.testTag("spatial-timeline-toggle")) {
                            Icon(Icons.Filled.ViewTimeline, if (timelineDockVisible) "收起时间轴" else "展开时间轴")
                        }
                        if (entry.id.startsWith("POSITION:")) IconButton(
                            onClick = onTogglePrecision, enabled = !gestureOwned,
                            modifier = Modifier.testTag("spatial-precision-toggle"),
                        ) {
                            Icon(if (precisionEnabled) Icons.Filled.CenterFocusStrong
                                else Icons.Filled.CenterFocusWeak,
                                if (precisionEnabled) "关闭精密操控" else "开启精密操控")
                        }
                        IconButton(onClick = ::showToolPicker, enabled = !gestureOwned,
                            modifier = Modifier.testTag("spatial-add-tool")) {
                            Icon(Icons.Filled.Add, "添加工具")
                        }
                        IconButton(onClick = { birdseyeOpen = true }, enabled = !gestureOwned,
                            modifier = Modifier.testTag("spatial-birdseye")) {
                            Icon(Icons.Filled.Apps, "切换工具")
                        }
                        Box {
                            IconButton(onClick = { cardMenuId = entry.id }, enabled = !gestureOwned,
                                modifier = Modifier.testTag("spatial-menu-" + entry.id.replace(':', '-'))) {
                                Icon(Icons.Filled.MoreHoriz, "管理 " + entry.title)
                            }
                            DropdownMenu(expanded = cardMenuId == entry.id,
                                onDismissRequest = { cardMenuId = null }) {
                                DropdownMenuItem(text = { Text("收回工具") },
                                    modifier = Modifier.testTag("spatial-hide-" + entry.id.replace(':', '-')),
                                    onClick = {
                                        updateNode(node.copy(hidden = true))
                                        focusedId = null
                                        cardMenuId = null
                                    })
                                if (viewportW < 460f) {
                                    DropdownMenuItem(text = { Text("撤销字幕编辑") }, enabled = canUndo,
                                        onClick = { onUndo(); cardMenuId = null })
                                    DropdownMenuItem(text = { Text("重做字幕编辑") }, enabled = canRedo,
                                        onClick = { onRedo(); cardMenuId = null })
                                    HorizontalDivider()
                                }
                                if (entry.canClose) DropdownMenuItem(
                                    text = { Text(if (entry.bookmarked) "取消侧书签" else "放入侧书签") },
                                    modifier = Modifier.testTag("spatial-bookmark-" + entry.id.replace(':', '-')),
                                    onClick = { onToggleBookmark(entry.id); cardMenuId = null },
                                )
                                DropdownMenuItem(text = { Text(if (node.layoutLocked) "解除布局锁" else "锁定布局") },
                                    modifier = Modifier.testTag("spatial-layout-lock-" + entry.id.replace(':', '-')),
                                    onClick = {
                                        updateNode(node.copy(layoutLocked = !node.layoutLocked))
                                        cardMenuId = null
                                    })
                                if (entry.canBindEvent) DropdownMenuItem(
                                    text = { Text(if (entry.pinnedEvent) "解除对象固定（跟随焦点）"
                                        else "固定读取对象 #" + entry.focusEventId) },
                                    enabled = entry.pinnedEvent || entry.focusEventId != null,
                                    onClick = { onToggleEventBinding(entry.id); cardMenuId = null })
                                if (entry.canDuplicate) {
                                    DropdownMenuItem(text = { Text("复制工具（保留绑定）") }, onClick = {
                                        pendingToolSelection = entries.mapTo(mutableSetOf()) { it.id }
                                        onDuplicateTool(entry.id, false)
                                        cardMenuId = null
                                    })
                                    DropdownMenuItem(text = { Text("复制工具（跟随焦点）") }, onClick = {
                                        pendingToolSelection = entries.mapTo(mutableSetOf()) { it.id }
                                        onDuplicateTool(entry.id, true)
                                        cardMenuId = null
                                    })
                                }
                                if (entry.canClose) DropdownMenuItem(text = { Text("关闭工具实例") },
                                    modifier = Modifier.testTag("spatial-close-" + entry.id.replace(':', '-')),
                                    onClick = {
                                        onCloseTool(entry.id)
                                        focusedId = null
                                        cardMenuId = null
                                        pendingToolSelection = null
                                    })

                                DropdownMenuItem(text = { Text(if (node.alpha < 1f) "恢复实底" else "透明叠加") },
                                    onClick = {
                                        updateNode(node.copy(alpha = if (node.alpha < 1f) 1f else 0.2f))
                                        cardMenuId = null
                                    })
                                if (entry.id == "audio") DropdownMenuItem(
                                    text = { Text(if (node.passthrough) "操作波形" else "穿透操作视频") },
                                    onClick = {
                                        updateNode(node.copy(passthrough = !node.passthrough))
                                        cardMenuId = null
                                    })
                            }
                        }
                        if (viewportW >= 460f) {
                            IconButton(onClick = onUndo, enabled = canUndo) {
                                Icon(Icons.Filled.Undo, "撤销字幕编辑")
                            }
                            IconButton(onClick = onRedo, enabled = canRedo) {
                                Icon(Icons.Filled.Redo, "重做字幕编辑")
                            }
                        }
                    }
                    HorizontalDivider()
                    val liveNode by rememberUpdatedState(node)
                    val liveNodes by rememberUpdatedState(nodes)
                    val liveContent by rememberUpdatedState(content)
                    // Preserve ordinary remember() drafts while a pane is moved
                    // between split and fullscreen layouts (or resized).
                    val productionEditor = remember(entry.id, sessionId) {
                        movableContentOf<Modifier> { paneModifier ->
                        Box(paneModifier.testTag("spatial-native-content-" + entry.id.replace(':', '-'))) {
                            val preview = liveNodes.firstOrNull { it.id == "preview" && !it.hidden }
                            val audio = liveNodes.firstOrNull { it.id == "audio" && !it.hidden }
                            // Audio/video is a real layered editing surface, not a flattened card.
                            // Keep actual renderer and waveform on their existing clock and callbacks.
                            if (entry.id == "audio" && preview != null) {
                                contentState.SaveableStateProvider(canvasSaveableContentKey(sessionId, CanvasContentRole.FOCUSED_LAYER, "preview", entry.id)) {
                                    liveContent("preview", true)
                                }
                                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .height(liveNode.height.coerceIn(160f, 460f).dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = liveNode.alpha))) {
                                    contentState.SaveableStateProvider(canvasSaveableContentKey(sessionId, CanvasContentRole.FOCUSED_LAYER, "audio", entry.id)) {
                                        liveContent("audio", !liveNode.passthrough)
                                    }
                                }
                            } else {
                                contentState.SaveableStateProvider(canvasSaveableContentKey(sessionId, CanvasContentRole.FOCUSED, entry.id)) {
                                    liveContent(entry.id, !liveNode.passthrough)
                                }
                                if (entry.id == "preview" && audio != null) {
                                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                        .height(audio.height.coerceIn(160f, 460f).dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = audio.alpha))) {
                                        contentState.SaveableStateProvider(canvasSaveableContentKey(sessionId, CanvasContentRole.FOCUSED_LAYER, "audio", entry.id)) {
                                            liveContent("audio", !audio.passthrough)
                                        }
                                    }
                                }
                            }
                        
                        }
                        }
                    }
                    val reference = nodes.firstOrNull { it.id == "preview" && !it.hidden }
                    val showReferenceOption = entry.id != "preview" && entry.id != "audio" && reference != null
                    // Give the text/parameter editor the full available height
                    // while the soft keyboard is visible. The user's split choice
                    // remains intact and automatically returns after IME closes.
                    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    val referenceFraction = when (referencePreviewSize) {
                        0 -> 0.25f
                        2 -> 0.54f
                        else -> 0.40f
                    }
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        if (showReferenceOption) Row(
                            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { referencePreviewVisible = !referencePreviewVisible },
                                enabled = !gestureOwned,
                                modifier = Modifier.testTag("spatial-preview-toggle"),
                            ) {
                                Icon(if (referencePreviewVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (referencePreviewVisible) "隐藏参考画面" else "显示参考画面")
                            }
                            if (referencePreviewVisible) TextButton(
                                onClick = { referencePreviewSize = (referencePreviewSize + 1) % 3 },
                                enabled = !gestureOwned,
                                modifier = Modifier.testTag("spatial-preview-size"),
                            ) { Text(when (referencePreviewSize) {
                                0 -> "预览 25%"
                                2 -> "预览 54%"
                                else -> "预览 40%"
                            }) }
                        }
                        if (showReferenceOption && referencePreviewVisible && !imeVisible) {
                            // The reference is the real video/ASS renderer and shares the
                            // current media clock and document focus with this editor.
                            // SaveableStateProvider keys are unique within the stage.
                            val referencePane: @Composable (Modifier) -> Unit = { paneModifier ->
                                Box(paneModifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .testTag("spatial-reference-preview")) {
                                    contentState.SaveableStateProvider(canvasSaveableContentKey(sessionId, CanvasContentRole.REFERENCE, "preview", entry.id)) {
                                        content("preview", true)
                                    }
                                }
                            }
                            if (viewportW >= 840f) {
                                Row(Modifier.fillMaxSize()) {
                                    referencePane(Modifier.weight(referenceFraction).fillMaxHeight())
                                    VerticalDivider()
                                    productionEditor(Modifier.weight(1f - referenceFraction).fillMaxHeight())
                                }
                            } else {
                                Column(Modifier.fillMaxSize()) {
                                    referencePane(Modifier.fillMaxWidth().fillMaxHeight(referenceFraction))
                                    HorizontalDivider()
                                    productionEditor(Modifier.weight(1f).fillMaxWidth())
                                }
                            }
                        } else {
                            productionEditor(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }

        // A resident tool directory is a shared overlay, not a board-only
        // surface: pinning it keeps tool switching available while editing.
            if (toolDrawerOpen || toolDrawerResident) Surface(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight()
                    .padding(bottom = dockHeight).width(railWidth)
                    .zIndex(1_000_020f).testTag("spatial-edge-rail-left"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { toolDrawerResident = !toolDrawerResident },
                            modifier = Modifier.size(40.dp).testTag("spatial-edge-pin-left")) {
                            Icon(Icons.Filled.PushPin, if (toolDrawerResident) "取消驻留" else "驻留")
                        }
                        IconButton(onClick = { toolDrawerOpen = false; toolDrawerResident = false },
                            modifier = Modifier.size(40.dp).testTag("spatial-edge-close-left")) {
                            Icon(Icons.Filled.ChevronLeft, "收起目录")
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        entries.forEach { entry ->
                            TextButton(onClick = { focus(entry.id) },
                                enabled = !gestureOwned,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("spatial-edge-entry-" + entry.id.replace(':', '-'))) {
                                Text(entry.title, maxLines = 1)
                            }
                        }
                    }
                    IconButton(onClick = ::showToolPicker,
                        modifier = Modifier.testTag("spatial-edge-add-tool")) {
                        Icon(Icons.Filled.Add, "添加工具")
                    }
                }
            }

        // Side bookmarks are live ToolInstance identities, not cloned editors.
        // The world node remains saved while BOOKMARKED, so recall is reversible.
        val bookmarked = entries.filter { it.bookmarked }
        if (bookmarked.isNotEmpty()) Surface(
            Modifier.align(Alignment.CenterEnd).padding(bottom = dockHeight).width(68.dp)
                .zIndex(1_000_022f).testTag("spatial-bookmark-rail"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                bookmarked.forEach { item ->
                    TextButton(onClick = { focus(item.id) }, enabled = !gestureOwned,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("spatial-bookmark-open-" + item.id.replace(':', '-'))) {
                        Text(item.title.take(4), maxLines = 2)
                    }
                }
            }
        }

        // Minimal direction-aware edge navigation: no constellation of
        // overlapping offscreen buttons and no invisible-world traversal.
        // Hidden tools remain in Recall; BOOKMARKED tools live in their rail.
        if (boardMode) {
            val cues = canvasEdgeCues(
                camera,
                nodes.filter { node ->
                    entries.any { entry -> entry.id == node.id && !entry.bookmarked }
                },
                viewportW, viewportH,
            )
            cues.forEach { cue ->
                if (cue.side != CanvasEdge.LEFT || !toolDrawerOpen) {
                    val target = entries.firstOrNull { it.id == cue.nearestNodeId }
                    val align = when (cue.side) {
                        CanvasEdge.LEFT -> Alignment.CenterStart
                        CanvasEdge.RIGHT -> Alignment.CenterEnd
                        CanvasEdge.TOP -> Alignment.TopCenter
                        CanvasEdge.BOTTOM -> Alignment.BottomCenter
                    }
                    val margins = when (cue.side) {
                        CanvasEdge.LEFT -> Modifier.padding(start = 8.dp)
                        CanvasEdge.RIGHT -> Modifier.padding(
                            end = if (entries.any { it.bookmarked }) 76.dp else 8.dp)
                        CanvasEdge.TOP -> Modifier.padding(top = 64.dp)
                        CanvasEdge.BOTTOM -> Modifier.padding(bottom = dockHeight + 68.dp)
                    }
                    FilledTonalButton(
                        onClick = { focus(cue.nearestNodeId) },
                        enabled = !gestureOwned && target != null,
                        modifier = Modifier.align(align).then(margins)
                            .zIndex(1_000_018f)
                            .testTag("spatial-offscreen-" + cue.side.name.lowercase()),
                    ) {
                        Icon(when (cue.side) {
                            CanvasEdge.LEFT -> Icons.Filled.ArrowBack
                            CanvasEdge.RIGHT -> Icons.Filled.ArrowForward
                            CanvasEdge.TOP -> Icons.Filled.ArrowUpward
                            CanvasEdge.BOTTOM -> Icons.Filled.ArrowDownward
                        }, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text((target?.title ?: "工具") +
                            if (cue.count > 1) " +${cue.count - 1}" else "",
                            maxLines = 1)
                    }
                }
            }
        }

        // The timeline is a persistent *real* editor surface owned by this
        // workspace, not a separate presentation mode or a detached media clock.
        if (timelineDockVisible) Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(dockHeight)
                .zIndex(1_000_040f).testTag("spatial-timeline-dock"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 5.dp,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DragHandle, null, Modifier.size(18.dp))
                    Text("时间轴", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { timelineDockExpanded = !timelineDockExpanded },
                        modifier = Modifier.testTag("spatial-timeline-expand")) {
                        Text(if (timelineDockExpanded) "紧凑" else "展开")
                    }
                    IconButton(onClick = { timelineDockVisible = false },
                        modifier = Modifier.testTag("spatial-timeline-close")) {
                        Icon(Icons.Filled.Close, "关闭常驻时间轴")
                    }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth().testTag("spatial-timeline-content")) {
                    renderTimeline(!timelineDockExpanded)
                }
            }
        }

        // Workspace actions remain separate from ASS document Undo/Redo.
        if (boardMode) Surface(Modifier.align(Alignment.TopCenter).widthIn(max = 900.dp).fillMaxWidth().padding(8.dp).zIndex(1_000_025f),
            shape = MaterialTheme.shapes.large, shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // Birdseye is the recovery route for a lost/offscreen workspace.
                // Keep it outside the scrolling actions at every phone width.
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (boardMode) {
                        Text("空间", Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = ::overview, enabled = !gestureOwned,
                            modifier = Modifier.testTag("spatial-overview")) { Text("总览") }
                        IconButton(onClick = ::goToPreviousViewport,
                            enabled = !gestureOwned && viewportHistory.canBack,
                            modifier = Modifier.testTag("spatial-viewport-back")) {
                            Icon(Icons.Filled.ArrowBack, "返回上一画布视角")
                        }
                        IconButton(onClick = ::goToNextViewport,
                            enabled = !gestureOwned && viewportHistory.canForward,
                            modifier = Modifier.testTag("spatial-viewport-forward")) {
                            Icon(Icons.Filled.ArrowForward, "前进下一画布视角")
                        }
                        TextButton(onClick = { timelineDockVisible = !timelineDockVisible },
                            enabled = !gestureOwned, modifier = Modifier.testTag("spatial-timeline-toggle")) {
                            Text(if (timelineDockVisible) "收起时间轴" else "时间轴")
                        }
                        Box {
                            TextButton(onClick = { arrangeOpen = true }, enabled = !gestureOwned,
                                modifier = Modifier.testTag("spatial-arrange")) { Text("整理") }
                            DropdownMenu(expanded = arrangeOpen, onDismissRequest = { arrangeOpen = false }) {
                                DropdownMenuItem(text = { Text("纵向排列") },
                                    onClick = { arrange(1) }, modifier = Modifier.testTag("spatial-arrange-column"))
                                DropdownMenuItem(text = { Text("两列排列") },
                                    onClick = { arrange(2) }, modifier = Modifier.testTag("spatial-arrange-two"))
                            }
                        }
                        Box {
                            TextButton(onClick = { recallOpen = true }, enabled = !gestureOwned) { Text("召回") }
                            DropdownMenu(expanded = recallOpen, onDismissRequest = { recallOpen = false }) {
                                entries.forEach { entry ->
                                    DropdownMenuItem(text = { Text(entry.title) },
                                        enabled = !gestureOwned,
                                        modifier = Modifier.testTag("spatial-recall-" + entry.id.replace(':', '-')),
                                        onClick = { focus(entry.id); recallOpen = false })
                                }
                            }
                        }
                        IconButton(enabled = !gestureOwned,
                            onClick = {
                                navigateToCamera(scene.first.zoomAt(viewportW / 2f, viewportH / 2f,
                                    scene.first.scale / 1.2f))
                            }, modifier = Modifier.testTag("spatial-zoom-out")) {
                            Icon(Icons.Filled.Remove, "缩小画布")
                        }
                        Text((camera.scale * 100f).toInt().toString() + "%",
                            style = MaterialTheme.typography.labelSmall)
                        IconButton(enabled = !gestureOwned,
                            onClick = {
                                navigateToCamera(scene.first.zoomAt(viewportW / 2f, viewportH / 2f,
                                    scene.first.scale * 1.2f))
                            }, modifier = Modifier.testTag("spatial-zoom-in")) {
                            Icon(Icons.Filled.Add, "放大画布")
                        }
                    }
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Icon(Icons.Filled.Undo, "撤销字幕编辑")
                    }
                    IconButton(onClick = onRedo, enabled = canRedo) {
                        Icon(Icons.Filled.Redo, "重做字幕编辑")
                    }
                }
                VerticalDivider(Modifier.height(26.dp).padding(horizontal = 4.dp))
                TextButton(onClick = { birdseyeOpen = true }, enabled = !gestureOwned,
                    modifier = Modifier.testTag("spatial-birdseye")) {
                    Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("鸟瞰")
                }
            }
        }
    }
}

/** Compact LOD visual; the native editor remains independent of camera scale. */
@Composable
private fun CanvasToolSummary(
    entry: InfiniteCanvasEntry,
    width: Float,
    height: Float,
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    Box(Modifier.fillMaxSize().clickable(enabled = enabled, onClick = onOpen)
        .testTag("spatial-open-" + entry.id.replace(':', '-'))) {
        Column(Modifier.align(Alignment.Center).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                when (entry.id) {
                    "preview" -> Icons.Filled.Movie
                    "subtitles" -> Icons.Filled.Subtitles
                    "audio" -> Icons.Filled.GraphicEq
                    else -> Icons.Filled.Tune
                },
                contentDescription = "打开 " + entry.title,
                tint = MaterialTheme.colorScheme.primary,
            )
            if (width >= 148f && height >= 140f) {
                Spacer(Modifier.height(6.dp))
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                if (entry.subtitle.isNotBlank()) Text(entry.subtitle,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/** Measure summary cards without constraining their world size to the phone viewport. */
@Composable
private fun UnboundedBoardLayer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val unbounded = Constraints(0, Constraints.Infinity, 0, Constraints.Infinity)
        val children = measurables.map { it.measure(unbounded) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            children.forEach { it.place(0, 0) }
        }
    }
}

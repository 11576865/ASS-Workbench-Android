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

    @Test
    fun inspectorDraftSurvivesToolSwitchAndRotation() {
        Log.i("AsswbRegression", "rotation:restore-recovery")
        restoreRecovery()
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
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        composeRule.runOnIdle { viewModel.previewEventRectClip(1L, 125.0, 240.0, 350.0, 460.0, false) }
        for ((edge, value) in listOf("left" to "125.0", "top" to "240.0", "right" to "350.0", "bottom" to "460.0")) {
            composeRule.onNodeWithTag("geometry-clip-$edge-1-value").performScrollTo().assertTextContains(value)
        }
        composeRule.onNodeWithTag("geometry-clip-normal-1").performScrollTo().assertIsSelected().assertIsNotEnabled()
        composeRule.onNodeWithTag("geometry-clip-1-apply").performScrollTo().assertIsNotEnabled()
        var deadline = android.os.SystemClock.uptimeMillis() + 400L
        composeRule.waitUntil(2_000) { android.os.SystemClock.uptimeMillis() >= deadline }
        assertEquals(before, viewModel.state.value.document)
        composeRule.runOnIdle { viewModel.clearTransientPreview("geometry:1") }
        field.performScrollTo().assertTextContains("120")
        composeRule.onNodeWithTag("geometry-clip-inverted-1").performScrollTo().assertIsSelected()
        deadline = android.os.SystemClock.uptimeMillis() + 400L
        composeRule.waitUntil(2_000) { android.os.SystemClock.uptimeMillis() >= deadline }
        assertEquals(before, viewModel.state.value.document)
        composeRule.onNodeWithTag("geometry-clip-1-apply").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { viewModel.state.value.document != before }
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitUntil(5_000) { viewModel.state.value.document == before }
    }

    private fun captureLayout(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val scaled = Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * 900f / bitmap.width).toInt(), true)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP).chunked(2800).forEachIndexed { index, chunk ->
            Log.i("AsswbVisual", "UI_CAPTURE:$name:$index:$chunk")
        }
        Log.i("AsswbVisual", "UI_CAPTURE_END:$name")
    }

    @Test
    fun unifiedCanvasHidesAndRestoresWorldNodeWithoutDeletingToolState() {
        restoreRecovery()
        switchToUnifiedCanvas()
        if (composeRule.onAllNodesWithTag("spatial-return-to-board")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
            composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        }
        val before = viewModel.state.value.document
        composeRule.onNodeWithTag("spatial-menu-preview").performClick()
        composeRule.onNodeWithText("收回工具").performClick()
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.onNodeWithTag("spatial-birdseye-node-preview").performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-native-content-preview").assertExists()
        assertEquals(before, viewModel.state.value.document)
    }

    @Test
    fun unifiedCanvasLayoutLockSurvivesRecreationWithoutTouchingSubtitle() {
        restoreRecovery()
        switchToUnifiedCanvas()
        val canonical = viewModel.state.value.document
        if (composeRule.onAllNodesWithTag("spatial-return-to-board")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
            composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        }
        composeRule.onNodeWithTag("spatial-menu-preview").performClick()
        composeRule.onNodeWithTag("spatial-layout-lock-preview").performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        viewModel = composeRule.activity.editorViewModel
        if (composeRule.onAllNodesWithTag("spatial-return-to-board")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
            composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        }
        composeRule.onNodeWithTag("spatial-menu-preview").performClick()
        composeRule.onNodeWithText("解除布局锁").assertIsDisplayed()
        assertEquals(canonical, viewModel.state.value.document)
    }

    @Test
    fun recoveryDiscardDeletesJournalWithoutLoadingIt() {
        composeRule.onNodeWithTag("recovery-discard")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            !viewModel.state.value.recoveryAvailable
        }
        assertFalse(recoveryStore.exists())
        assertFalse(viewModel.state.value.subtitleLoaded)
    }

    @Test
    fun latestEditIsRecoverableByFreshViewModel() {
        restoreRecovery()

        viewModel.updateEventText(1L, "Recovered line UPDATED")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            recoveryStore.read()
                ?.document
                ?.events
                ?.firstOrNull { it.id == 1L }
                ?.text == "Recovered line UPDATED"
        }

        // A fresh ViewModel approximates the next process launch: it must discover
        // the journal from storage rather than inheriting the live editor state.
        val fresh = EditorViewModel(application)
        assertTrue(fresh.state.value.recoveryAvailable)
        fresh.restoreRecovery()
        assertEquals(
            "Recovered line UPDATED",
            fresh.state.value.document.events.first { it.id == 1L }.text,
        )
        assertTrue(fresh.state.value.dirty)
    }

    @Test
    fun normalSaveFailureDoesNotEscapeAndKeepsDirtyRecovery() {
        restoreRecovery()

        val handled = viewModel.saveTo(
            Uri.parse("content://io.github.assworkbench.invalid-provider/unwritable.ass")
        )

        assertFalse(handled)
        assertTrue(viewModel.state.value.dirty)
        assertTrue(recoveryStore.exists())
        assertTrue(viewModel.state.value.status.startsWith("字幕保存失败："))
    }

    @Test
    fun dirtyCanonicalSurvivesBackgroundResume() {
        restoreRecovery()
        viewModel.updateEventText(1L, "Recovered line BACKGROUND")
        val before = viewModel.state.value.document

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        viewModel = composeRule.activity.editorViewModel
        assertEquals(before, viewModel.state.value.document)
        assertTrue(viewModel.state.value.dirty)
        composeRule.waitUntil(10_000) {
            recoveryStore.read()?.document?.events?.firstOrNull { it.id == 1L }?.text ==
                "Recovered line BACKGROUND"
        }
    }

    @Test
    fun semanticSearchInvalidAuxiliaryFiltersStayFailClosed() {
        restoreRecovery()
        val before = viewModel.state.value.document

        openTool("BATCH")
        composeRule.onNodeWithTag("search-style-regex")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextReplacement("[")
        composeRule.onNodeWithTag("search-replace-error")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(before, viewModel.state.value.document)

        composeRule.onNodeWithTag("search-style-regex")
            .performTextReplacement("")
        composeRule.onNodeWithTag("search-forbidden-tag")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextReplacement("pos(")
        composeRule.onNodeWithTag("search-replace-error")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(before, viewModel.state.value.document)
    }

    @Test
    fun karaokeFxPreviewIsNonDestructiveAndBatchApplyCommitsAtomically() {
        restoreRecovery()

        viewModel.updateEventText(1L, "{\\k20}Ka{\\k20}ra")
        viewModel.updateEventText(2L, "{\\k20}O{\\k20}ke")
        viewModel.focusEvent(1L, seek = false)
        viewModel.toggleSelected(1L)
        viewModel.toggleSelected(2L)

        val canonicalBeforePreview = viewModel.state.value.document
        openTool("KARAOKE")

        composeRule.onNodeWithTag("karaoke-fx-author")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("karaoke-fx-summary")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("karaoke-fx-preview")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(10_000) {
            val state = viewModel.state.value
            state.previewOwnerId == "karaoke-fx" &&
                state.previewDocument?.events?.firstOrNull { it.id == 1L }?.text?.contains("\\fscy") == true &&
                state.previewDocument?.events?.firstOrNull { it.id == 2L }?.text?.contains("\\fscy") == true
        }
        assertEquals(canonicalBeforePreview, viewModel.state.value.document)

        composeRule.onNodeWithTag("karaoke-fx-apply")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(10_000) {
            val state = viewModel.state.value
            state.previewDocument == null &&
                state.document.events.firstOrNull { it.id == 1L }?.text?.contains("\\fscy") == true &&
                state.document.events.firstOrNull { it.id == 2L }?.text?.contains("\\fscy") == true
        }
        assertTrue(viewModel.state.value.dirty)
    }

    @Test
    fun repeatedActivityRecreationDoesNotMutateDirtyDocument() {
        restoreRecovery()
        viewModel.updateEventText(1L, "{\\bord3}Recovered line RECREATE")
        viewModel.setEventPosition(1L, 640.0, 360.0)
        val before = viewModel.state.value.document

        repeat(3) {
            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            viewModel = composeRule.activity.editorViewModel
            assertEquals(before, viewModel.state.value.document)
            assertTrue(viewModel.state.value.dirty)
        }
    }

    @Test
    fun batchUnknownStyleAndNegativeMarginFailBeforePreview() {
        restoreRecovery()
        val before = viewModel.state.value.document

        openTool("BATCH")
        composeRule.onNodeWithTag("batch-style-action")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextReplacement("Missing")
        composeRule.onNodeWithTag("batch-preview-error")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(before, viewModel.state.value.document)

        composeRule.onNodeWithTag("batch-style-action")
            .performTextReplacement("")
        composeRule.onNodeWithTag("batch-margin-l")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextReplacement("-1")
        composeRule.onNodeWithTag("batch-preview-error")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(before, viewModel.state.value.document)
    }

    @Test
    fun batchInvalidRegexFailsClosedInsteadOfDroppingTheFilter() {
        restoreRecovery()
        val before = viewModel.state.value.document

        openTool("BATCH")
        composeRule.onNodeWithTag("batch-preview-pending")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("batch-raw-regex-filter")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextReplacement("[")
        composeRule.onNodeWithTag("batch-preview-error")
            .performScrollTo()
            .assertIsDisplayed()

        assertEquals(before, viewModel.state.value.document)
    }

    @Test
    fun semanticSearchInvalidAuxiliaryFiltersFailClosedInUi() {
        restoreRecovery()
        val before = viewModel.state.value.document

        openTool("BATCH")
        composeRule.onNodeWithTag("search-style-regex")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextReplacement("[")
        composeRule.onNodeWithTag("search-replace-error")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(before, viewModel.state.value.document)

        composeRule.onNodeWithTag("search-style-regex")
            .performTextReplacement("")
        composeRule.onNodeWithTag("search-forbidden-tag")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextReplacement("pos(")
        composeRule.onNodeWithTag("search-replace-error")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(before, viewModel.state.value.document)
    }

    @Test
    fun batchKaraokeRevealControlsStayPreviewOnlyUntilCommit() {
        restoreRecovery()
        val before = viewModel.state.value.document

        openTool("BATCH")
        composeRule.onNodeWithTag("batch-karaoke-reveal")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("batch-karaoke-reveal-enabled")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("batch-karaoke-reveal-ms")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("batch-karaoke-reveal-blur")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("batch-karaoke-reveal-accel")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("batch-preview-explicit")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("batch-preview-pending")
            .performScrollTo()
            .assertIsDisplayed()

        assertEquals(before, viewModel.state.value.document)

        composeRule.onNodeWithTag("batch-karaoke-reveal-ms")
            .performTextReplacement("-1")
        composeRule.onNodeWithTag("batch-preview-error")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(before, viewModel.state.value.document)
    }

    @Test
    fun effectsPaneExposesSpatialFadeAuthoringWithoutMutatingDocument() {
        restoreRecovery()
        eventRow(1L).performClick()
        val before = viewModel.state.value.document

        openTool("EFFECTS")
        composeRule.onNodeWithTag("fx-composition-pane")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("fx-reflection-with-fade")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("fx-fade-bands")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("fx-fade-depth")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("fx-fade-far-opacity")
            .assertIsDisplayed()

        assertEquals(before, viewModel.state.value.document)
    }

    private fun restoreRecovery() {
        composeRule.onNodeWithTag("recovery-restore")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.state.value.subtitleLoaded &&
                viewModel.state.value.document.events.size == 2
        }
        composeRule.onNodeWithTag("surface-SUBTITLES").assertDoesNotExist()
        captureLayout("clean-canvas")
        openTool("SUBTITLES")
    }

    private fun eventRow(id: Long): SemanticsNodeInteraction {
        showFixedSubtitleList()
        return composeRule.onNodeWithTag("event-row-$id")
    }

    private fun showFixedSubtitleList() {
        hideKeyboard()
        val fixedWorkspace = composeRule.onAllNodesWithTag("fixed-workspace", useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (fixedWorkspace.isEmpty()) return

        val navigationGroups = composeRule.onAllNodesWithTag("fixed-group-NAVIGATION", useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (navigationGroups.isNotEmpty()) {
            composeRule.onNodeWithTag("fixed-group-NAVIGATION").performScrollTo().performClick()
        }
        val pages = composeRule.onAllNodesWithTag("fixed-page-list", useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (pages.isNotEmpty()) {
            composeRule.onNodeWithTag("fixed-page-list").performClick()
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("subtitle-navigation", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
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

    private fun switchToUnifiedCanvas() {
        hideKeyboard()
        if (composeRule.onAllNodesWithTag("spatial-workspace", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) return
        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        composeRule.onNodeWithTag("ui-variant-lab").assertIsDisplayed()
        selectUiVariant("ui-variant-use-SPATIAL_EXPERIMENTAL")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("spatial-workspace", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun openTool(name: String) {
        hideKeyboard()
        val fixedWorkspace = composeRule.onAllNodesWithTag("fixed-workspace", useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
        if (fixedWorkspace) {
            if (name == "SUBTITLES") {
                showFixedSubtitleList()
                return
            }
            val group = WorkbenchTool.valueOf(name).group
            composeRule.onNodeWithTag("fixed-group-${group.name}").performScrollTo().performClick()
            val fixedTool = composeRule.onAllNodesWithTag("fixed-tool-$name", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
            if (fixedTool.isNotEmpty()) {
                composeRule.onNodeWithTag("fixed-tool-$name").performScrollTo().performClick()
                composeRule.waitForIdle()
                return
            }
        }
        composeRule.onNodeWithTag("workspace-tools").performClick()
        composeRule.onNodeWithTag("tool-search").performTextReplacement(name)
        hideKeyboard()
        composeRule.onNodeWithTag("tool-$name").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun eventText(id: Long): String =
        viewModel.state.value.document.events.first { it.id == id }.text
}

private fun seedRecovery(store: RecoveryStore) {
    val document = AssDocument(
        events = listOf(
            AssEvent(
                id = 1L,
                start = SubTime(1_000),
                end = SubTime(3_000),
                text = "Recovered line",
            ),
            AssEvent(
                id = 2L,
                start = SubTime(3_200),
                end = SubTime(5_000),
                text = "Second recovered line",
            ),
        )
    )
    store.write(
        project = SubtitleProject(title = "Recovery regression.ass"),
        document = document,
        textEncoding = AssTextEncoding.UTF8,
    )
}

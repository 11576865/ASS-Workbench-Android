package io.github.assworkbench.app

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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        composeRule.onNodeWithTag(tag)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
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
        composeRule.onNodeWithText("应用正文")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            eventText(1L) == "Recovered line DRAFT"
        }
    }

    @Test
    fun pagerWorkspaceSwitchesBetweenRealSubtitlePreviewAndToolPages() {
        restoreRecovery()

        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        selectUiVariant("ui-variant-use-PAGER_EXPERIMENTAL")

        composeRule.onNodeWithTag("pager-workspace").assertIsDisplayed()
        composeRule.onNodeWithTag("pager-page-preview").assertIsDisplayed()

        composeRule.onNodeWithTag("pager-nav-SUBTITLES").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("pager-page-subtitles", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("event-row-1").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("pager-page-tool", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("event-inspector").assertIsDisplayed()

        composeRule.onNodeWithTag("pager-nav-PREVIEW").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("pager-page-preview", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    @Test
    fun spatialWorkspaceExposesRealNodesAndNavigationControls() {
        restoreRecovery()

        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        selectUiVariant("ui-variant-use-SPATIAL_EXPERIMENTAL")

        composeRule.onNodeWithTag("spatial-workspace").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-overview").performClick()
        composeRule.onNodeWithTag("spatial-node-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-node-subtitles").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-node-tool").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-navigation-mode").performClick()
        composeRule.onNodeWithTag("spatial-navigation-overlay").assertIsDisplayed()

        composeRule.onNodeWithTag("spatial-navigation-mode").performClick()
        composeRule.onNodeWithTag("spatial-focus-subtitles").performClick()
        composeRule.onNodeWithTag("spatial-node-subtitles").assertIsDisplayed()
    }

    @Test
    fun toolInstanceWorkspaceSupportsHideAndRestore() {
        restoreRecovery()

        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        selectUiVariant("ui-variant-use-TOOL_INSTANCES_EXPERIMENTAL")

        composeRule.onNodeWithTag("tool-instance-workspace").assertIsDisplayed()
        composeRule.onNodeWithTag("tool-instance-directory").assertIsDisplayed()

        composeRule.onNodeWithTag("tool-instance-directory").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("surface-CAPABILITIES-primary", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("tool-hide-temporary").performClick()
        composeRule.onNodeWithTag("tool-hidden-CAPABILITIES-primary").assertIsDisplayed()
        composeRule.onNodeWithTag("tool-hidden-CAPABILITIES-primary").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("surface-CAPABILITIES-primary", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    @Test
    fun edgeBookmarkWorkspaceOpensFourEdgeLayersAndBookmarks() {
        restoreRecovery()

        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        selectUiVariant("ui-variant-use-EDGE_BOOKMARK_EXPERIMENTAL")

        composeRule.onNodeWithTag("edge-bookmark-workspace").assertIsDisplayed()
        composeRule.onNodeWithTag("edge-bookmark-left").assertIsDisplayed()
        composeRule.onNodeWithTag("edge-bookmark-right").assertIsDisplayed()
        composeRule.onNodeWithTag("edge-handle-top").assertIsDisplayed()
        composeRule.onNodeWithTag("edge-handle-bottom").assertIsDisplayed()
        composeRule.onNodeWithTag("edge-handle-left").assertIsDisplayed()
        composeRule.onNodeWithTag("edge-handle-right").assertIsDisplayed()

        composeRule.onNodeWithTag("edge-handle-top").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("edge-layer-top", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("edge-handle-bottom").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("edge-layer-bottom", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    @Test
    fun glassLayeredWorkspaceExposesMaterialAndPerformanceControls() {
        restoreRecovery()

        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        selectUiVariant("ui-variant-use-GLASS_LAYERED_EXPERIMENTAL")

        composeRule.onNodeWithTag("glass-layered-workspace").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-control-deck").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-performance-QUALITY").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("glass-performance-LOW_COST").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("glass-alpha").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-blur").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-layer-overview").assertIsDisplayed()
    }

    @Test
    fun precisionLensWorkspaceShowsPrecisionControls() {
        restoreRecovery()

        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        selectUiVariant("ui-variant-use-PRECISION_LENS_EXPERIMENTAL")

        composeRule.onNodeWithTag("precision-lens-workspace").assertIsDisplayed()
        composeRule.onNodeWithTag("precision-controls").assertIsDisplayed()
        composeRule.onNodeWithTag("precision-gain-COARSE").assertIsDisplayed()
        composeRule.onNodeWithTag("precision-gain-FINE").assertIsDisplayed()
        composeRule.onNodeWithTag("precision-lens-LOCAL_FOCUS").assertIsDisplayed()
        composeRule.onNodeWithTag("precision-lens-FLOATING_LENS").assertIsDisplayed()
        composeRule.onNodeWithTag("precision-snap-toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("precision-snap-bypass").assertIsDisplayed()
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
        composeRule.onNodeWithText("应用正文")
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

        composeRule.onNodeWithText("应用正文")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            eventText(1L) == "Recovered line ROTATED"
        }
    }

    @Test
    fun inspectorDraftSurvivesToolSwitchAndRotation() {
        restoreRecovery()
        eventRow(1L).performClick()
        composeRule.onNodeWithTag("event-raw-1").performTextInput(" WORKBENCH")
        openTool("EFFECTS")
        composeRule.onNodeWithTag("fixed-inspector").assertIsDisplayed()
        openTool("TEXT")
        composeRule.onNodeWithTag("event-raw-1").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        val previewNodes = composeRule.onAllNodesWithTag("preview-workspace").fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (previewNodes.isNotEmpty()) composeRule.onNodeWithTag("preview-workspace").assertIsDisplayed()
        val listNodes = composeRule.onAllNodesWithTag("subtitle-navigation").fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (listNodes.isNotEmpty()) composeRule.onNodeWithTag("subtitle-navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("event-inspector").assertIsDisplayed()
        captureLayout("landscape")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.executeShellCommand("wm size 1920x1200").close()
            automation.executeShellCommand("wm density 160").close()
            composeRule.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeRule.waitUntil(10_000) {
                val configuration = composeRule.activity.resources.configuration
                configuration.screenWidthDp >= 1600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            composeRule.waitForIdle()
            val preview = composeRule.onNodeWithTag("preview-workspace").fetchSemanticsNode().boundsInRoot
            val navigation = composeRule.onNodeWithTag("subtitle-navigation").fetchSemanticsNode().boundsInRoot
            val inspector = composeRule.onNodeWithTag("fixed-inspector").fetchSemanticsNode().boundsInRoot
            val fixed = composeRule.onNodeWithTag("fixed-workspace").fetchSemanticsNode().boundsInRoot
            assertTrue("Preview must remain inside fixed workspace", preview.left >= fixed.left && preview.right <= fixed.right)
            assertTrue("Navigation and inspector must not overlap", navigation.right <= inspector.left || navigation.bottom <= inspector.top)
            composeRule.onNodeWithTag("canvas-workspace").assertDoesNotExist()
            composeRule.onNodeWithTag("preview-divider").assertDoesNotExist()
            captureLayout("tablet-landscape")
        } finally {
            automation.executeShellCommand("wm size reset").close()
            automation.executeShellCommand("wm density reset").close()
        }
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.screenWidthDp < 1200
        }
        composeRule.onNodeWithText("应用正文").performScrollTo().performClick()
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
    fun canvasWorkspaceSupportsStackHideRestoreWithoutPreviewModes() {
        restoreRecovery()
        switchToCanvas()

        fun waitForSurface(tag: String) {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    composeRule.onNodeWithTag(tag).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
        }

        openTool("POSITION")
        waitForSurface("surface-POSITION")
        val dragHandle = composeRule.onNodeWithTag("surface-drag-POSITION")
        val before = dragHandle.fetchSemanticsNode().boundsInRoot
        dragHandle.performTouchInput {
            swipe(
                start = center,
                end = center + androidx.compose.ui.geometry.Offset(140f, 90f),
                durationMillis = 350,
            )
        }
        composeRule.waitForIdle()
        val after = dragHandle.fetchSemanticsNode().boundsInRoot
        assertTrue("Floating surface drag handle should move the surface", after.left > before.left || after.top > before.top)

        openTool("STYLE")
        waitForSurface("surface-STYLE")

        openTool("FONTS")
        openTool("QC")
        waitForSurface("surface-FONTS")
        waitForSurface("surface-QC")

        composeRule.onNodeWithContentDescription("隐藏全部浮层").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("surface-drag-POSITION")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        }

        composeRule.onNodeWithContentDescription("呼回全部浮层").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("surface-drag-POSITION")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("surface-POSITION").assertIsDisplayed()
        composeRule.onNodeWithTag("surface-FONTS").assertIsDisplayed()

        composeRule.onNodeWithTag("preview-workspace").assertIsDisplayed()
        listOf("NORMAL", "FOCUS", "FLOATING", "MANIPULATION").forEach {
            composeRule.onNodeWithTag("preview-mode-$it").assertDoesNotExist()
        }
        composeRule.onNodeWithTag("floating-preview").assertDoesNotExist()
        composeRule.onNodeWithText("跟随系统").assertDoesNotExist()
        composeRule.onNodeWithText("白天").assertDoesNotExist()
        captureLayout("floating-workspace")

    }

    @Test
    fun surfaceResizeAndLayoutLockSurviveRecreationWithoutChangingSubtitle() {
        restoreRecovery()
        switchToCanvas()
        val canonical = viewModel.state.value.document
        openTool("POSITION")
        composeRule.waitForIdle()
        val surface = composeRule.onNodeWithTag("surface-POSITION")
        val before = surface.fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("surface-resize-POSITION").performTouchInput {
            swipe(start = center, end = center - androidx.compose.ui.geometry.Offset(80f, 100f), durationMillis = 350)
        }
        composeRule.waitForIdle()
        val resized = surface.fetchSemanticsNode().boundsInRoot
        assertTrue("Resize must reduce the committed window size", resized.width < before.width || resized.height < before.height)
        composeRule.onNodeWithTag("surface-lock-POSITION").performClick()
        val handle = composeRule.onNodeWithTag("surface-drag-POSITION")
        val locked = handle.fetchSemanticsNode().boundsInRoot
        handle.performTouchInput {
            swipe(start = center, end = center + androidx.compose.ui.geometry.Offset(60f, 60f), durationMillis = 350)
        }
        composeRule.waitForIdle()
        assertEquals(locked, handle.fetchSemanticsNode().boundsInRoot)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        viewModel = composeRule.activity.editorViewModel
        composeRule.onNodeWithContentDescription("解除布局锁定").assertIsDisplayed()
        val restored = composeRule.onNodeWithTag("surface-POSITION").fetchSemanticsNode().boundsInRoot
        assertEquals(resized.width, restored.width, 1f)
        assertEquals(resized.height, restored.height, 1f)
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
        hideKeyboard()
        val pages = composeRule.onAllNodesWithTag("fixed-page-list").fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (pages.isNotEmpty()) composeRule.onNodeWithTag("fixed-page-list").performClick()
        return composeRule.onNodeWithTag("event-row-$id")
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

    private fun switchToCanvas() {
        hideKeyboard()
        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        composeRule.onNodeWithTag("ui-variant-lab").assertIsDisplayed()
        selectUiVariant("ui-variant-use-CANVAS_EXPERIMENTAL")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("canvas-workspace", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun openTool(name: String) {
        hideKeyboard()
        val fixedWorkspace = composeRule.onAllNodesWithTag("fixed-workspace", useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
        if (fixedWorkspace) {
            if (name == "SUBTITLES") {
                val pages = composeRule.onAllNodesWithTag("fixed-page-list").fetchSemanticsNodes(atLeastOneRootRequired = false)
                if (pages.isNotEmpty()) composeRule.onNodeWithTag("fixed-page-list").performClick()
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

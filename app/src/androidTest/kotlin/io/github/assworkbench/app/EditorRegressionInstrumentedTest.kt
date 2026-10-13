package io.github.assworkbench.app

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
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
import androidx.compose.ui.test.click
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
import io.github.assworkbench.domain.AssTransform
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

    @Test fun invalidEventFieldsExplainErrorsAndCannotCommitAcrossHosts() {
        restoreRecovery()
        composeRule.runOnIdle { viewModel.focusEvent(1L, seek = false) }
        for (canvas in listOf(false, true)) {
            if (canvas) switchToUnifiedCanvas()
            val before = viewModel.state.value.document
            openTool("TEXT")
            composeRule.onNodeWithTag("event-start-1").performScrollTo().performTextReplacement("broken")
            hideKeyboard()
            composeRule.onNodeWithTag("event-apply-time-1").assertIsNotEnabled()
            composeRule.onNodeWithText("输入 ASS 时间或非负毫秒数").assertExists()
            composeRule.runOnIdle { assertEquals(before, viewModel.state.value.document) }
            composeRule.onNodeWithTag("event-start-1").performTextReplacement("2000")
            composeRule.onNodeWithTag("event-end-1").performTextReplacement("1000")
            hideKeyboard()
            composeRule.onNodeWithTag("event-apply-time-1").assertIsNotEnabled()
            composeRule.onNodeWithText("End 不能早于 Start").assertExists()
            composeRule.onNodeWithTag("event-end-1").performTextReplacement("3000")
            hideKeyboard()
            composeRule.onNodeWithTag("event-apply-time-1").performScrollTo().assertIsEnabled().performClick()
            composeRule.runOnIdle {
                assertEquals(2000L, viewModel.state.value.document.events.first { it.id == 1L }.start.millis)
                assertEquals(3000L, viewModel.state.value.document.events.first { it.id == 1L }.end.millis)
                viewModel.undo()
            }
            composeRule.runOnIdle { assertEquals(before, viewModel.state.value.document) }
            openTool("EVENT")
            composeRule.onNodeWithTag("event-layer-1").performScrollTo().performTextReplacement("oops")
            hideKeyboard()
            composeRule.onNodeWithTag("event-apply-metadata-1").assertIsNotEnabled()
            composeRule.onNodeWithText("输入有效整数").assertExists()
            composeRule.runOnIdle { assertEquals(before, viewModel.state.value.document) }
            composeRule.onNodeWithTag("event-layer-1").performTextReplacement("1000")
            hideKeyboard()
            composeRule.onNodeWithTag("event-apply-metadata-1").assertIsNotEnabled()
            composeRule.onNodeWithText("范围 -999～999").assertExists()
            composeRule.runOnIdle { assertEquals(before, viewModel.state.value.document) }
            composeRule.onNodeWithTag("event-layer-1").performTextReplacement("-3")
            hideKeyboard()
            composeRule.onNodeWithTag("event-apply-metadata-1").performScrollTo().assertIsEnabled().performClick()
            composeRule.runOnIdle {
                assertEquals(-3, viewModel.state.value.document.events.first { it.id == 1L }.layer)
                viewModel.undo()
            }
            composeRule.runOnIdle { assertEquals(before, viewModel.state.value.document) }
        }
    }

    @Test fun metadataDraftFollowsHostAndRetainsExternalConflictUntilExplicitApply() {
        restoreRecovery()
        composeRule.runOnIdle { viewModel.focusEvent(1L, seek = false) }
        val before = viewModel.state.value.document
        openTool("EVENT")
        composeRule.onNodeWithTag("event-actor-1").performScrollTo().performTextReplacement("Local actor")
        composeRule.onNodeWithTag("event-layer-1").performScrollTo().performTextReplacement("-03")
        hideKeyboard()
        composeRule.onNodeWithTag("event-comment-1").performClick()
        composeRule.onNodeWithTag("workspace-direct-mode-toggle").performClick()
        openTool("EVENT")
        composeRule.onNodeWithTag("event-actor-1").assertTextContains("Local actor")
        composeRule.onNodeWithTag("event-layer-1").assertTextContains("-03")
        composeRule.onNodeWithTag("event-comment-1").assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(before, viewModel.state.value.document)
            viewModel.updateFocusedMetadata(0, "External actor", false)
        }
        val external = viewModel.state.value.document
        composeRule.onNodeWithTag("event-metadata-conflict-1").assertExists()
        composeRule.onNodeWithTag("event-actor-1").assertTextContains("Local actor")
        composeRule.onNodeWithTag("event-apply-metadata-1").performScrollTo().performClick()
        composeRule.onNodeWithTag("event-metadata-conflict-1").assertDoesNotExist()
        composeRule.onNodeWithTag("event-layer-1").assertTextContains("-3")
        composeRule.runOnIdle {
            val event = viewModel.state.value.document.events.first { it.id == 1L }
            assertEquals(-3, event.layer)
            assertEquals("Local actor", event.name)
            assertTrue(event.comment)
            viewModel.undo()
        }
        composeRule.runOnIdle { assertEquals(external, viewModel.state.value.document) }
    }

    @Test fun timingDraftFollowsHostAndExplicitOverwriteKeepsMillisecondValue() {
        restoreRecovery()
        composeRule.runOnIdle { viewModel.focusEvent(1L, seek = false) }
        val before = viewModel.state.value.document
        openTool("TEXT")
        composeRule.onNodeWithTag("event-start-1").performScrollTo().performTextReplacement("1251")
        composeRule.onNodeWithTag("event-end-1").performTextReplacement("4000")
        hideKeyboard()
        composeRule.onNodeWithTag("workspace-direct-mode-toggle").performClick()
        openTool("TEXT")
        composeRule.onNodeWithTag("event-start-1").assertTextContains("1251")
        composeRule.onNodeWithTag("event-end-1").assertTextContains("4000")
        composeRule.runOnIdle {
            assertEquals(before, viewModel.state.value.document)
            viewModel.setEventTiming(1L, 1500, 4500)
        }
        val external = viewModel.state.value.document
        composeRule.onNodeWithTag("event-timing-conflict-1").assertExists()
        composeRule.onNodeWithTag("event-start-1").assertTextContains("1251")
        composeRule.onNodeWithTag("event-apply-time-1").performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithTag("event-timing-conflict-1").assertDoesNotExist()
        composeRule.onNodeWithTag("event-start-1").assertTextContains("0:00:01.25")
        composeRule.runOnIdle {
            val event = viewModel.state.value.document.events.first { it.id == 1L }
            assertEquals(1251L, event.start.millis)
            assertEquals(4000L, event.end.millis)
            viewModel.undo()
        }
        composeRule.runOnIdle { assertEquals(external, viewModel.state.value.document) }
    }

    @Test fun directoryDialogKeyboardClosesWithoutDismissingOrSubmittingDraft() {
        restoreRecovery()
        // Establish the host through the real two-layout switch, independent of
        // whichever presentation a preceding fixture persisted.
        if (composeRule.onAllNodesWithTag("fixed-workspace", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()) {
            composeRule.onNodeWithTag("workspace-direct-mode-toggle").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("fixed-workspace", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
        }
        composeRule.runOnIdle { viewModel.focusEvent(1L, seek = false) }
        openTool("TEXT")
        composeRule.onNodeWithTag("event-raw-1").performScrollTo().performTextInput(" DIALOG_DRAFT")
        hideKeyboard()
        val before = viewModel.state.value.document
        composeRule.onNodeWithTag("fixed-tool-CAPABILITIES").performClick()
        composeRule.onNodeWithTag("tool-search").performTextReplacement("STYLE")
        var dialogRoot: View? = null
        performInFocusedWindow("Show IME in the directory dialog") { controller, root ->
            dialogRoot = root
            val focused = checkNotNull(root.findFocus()) { "Directory dialog must own text focus" }
            focused.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .showSoftInput(focused, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            controller.loopMainThreadUntilIdle()
        }
        composeRule.waitUntil(5_000) {
            dialogRoot?.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
        }
        hideKeyboard()
        composeRule.onNodeWithTag("fixed-tool-directory").assertIsDisplayed()
        composeRule.onNodeWithTag("tool-search").assertTextContains("STYLE")
        composeRule.onNodeWithTag("fixed-directory-close").performClick()
        assertEquals("Recovered line DIALOG_DRAFT", rawDraftText(1L))
        composeRule.runOnIdle { assertEquals(before, viewModel.state.value.document) }
    }

    @Test fun sharedSubtitleFilterNavigatesResultsWithoutChangingSelectionOrDocumentAcrossHosts() {
        restoreRecovery()
        val before = viewModel.state.value
        composeRule.runOnIdle {
            viewModel.focusEvent(1L, seek = false)
            viewModel.toggleSelected(1L)
        }
        composeRule.onNodeWithContentDescription("搜索").performClick()
        if (composeRule.onAllNodesWithTag("subtitle-filter-open-dialog").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("subtitle-filter-open-dialog").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("subtitle-filter-quick").performTextReplacement("Second")
        hideKeyboard()
        if (composeRule.onAllNodesWithTag("subtitle-filter-dialog-done").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("subtitle-filter-dialog-done").performClick()
        }
        composeRule.onNodeWithTag("subtitle-focus-excluded").assertExists()
        composeRule.runOnIdle {
            assertEquals(listOf(2L), viewModel.state.value.filteredEvents.map { it.id })
            assertEquals(1L, viewModel.state.value.focusedEventId)
        }
        composeRule.onNodeWithTag("subtitle-result-next").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(2L, viewModel.state.value.focusedEventId)
            assertEquals(setOf(1L), viewModel.state.value.selectedEventIds)
        }
        composeRule.onNodeWithTag("subtitle-result-select-all").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(setOf(2L), viewModel.state.value.selectedEventIds) }
        composeRule.onNodeWithContentDescription("关闭搜索").performClick()
        composeRule.onNodeWithTag("workspace-direct-mode-toggle").assertIsDisplayed()
        switchToUnifiedCanvas()
        composeRule.runOnIdle {
            assertEquals(setOf(2L), viewModel.state.value.selectedEventIds)
            assertEquals("Second", viewModel.state.value.query)
            assertEquals(before.document, viewModel.state.value.document)
        }
        openTool("SUBTITLES")
        composeRule.onNodeWithTag("subtitle-results").assertExists()
        composeRule.runOnIdle {
            assertEquals("Second", viewModel.state.value.query)
            assertEquals(listOf(2L), viewModel.state.value.filteredEvents.map { it.id })
            viewModel.focusEvent(1L, seek = false)
        }
        composeRule.onNodeWithTag("subtitle-focus-excluded").assertExists()
        composeRule.onNodeWithTag("subtitle-result-locate").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(io.github.assworkbench.domain.SubtitleFilterState(), viewModel.state.value.subtitleFilter)
            assertEquals(1L, viewModel.state.value.focusedEventId)
            assertEquals(setOf(2L), viewModel.state.value.selectedEventIds)
            assertEquals(before.document, viewModel.state.value.document)
            assertEquals(before.dirty, viewModel.state.value.dirty)
            assertEquals(before.canUndo, viewModel.state.value.canUndo)
        }
    }

    @Test fun batchReviewStaysExpiredAfterEditUndoEvenWhenUiSkipsIntermediateFrames() {
        restoreRecovery()
        composeRule.runOnIdle {
            viewModel.focusEvent(1L, seek = false)
            val before = viewModel.state.value
            val review = io.github.assworkbench.domain.AssBatchReview.create(before.document,
                io.github.assworkbench.domain.AssBatchRecipe("review", actions = listOf(
                    io.github.assworkbench.domain.AssBatchAction.SetActor("ReviewedActor"))),
                before.workspaceSessionId, before.documentRevision)
            val store = io.github.assworkbench.app.ui.BatchAuthoringDraftStore(before.workspaceSessionId)
            val draft = store.obtain("BATCH:primary")
            val ids = before.document.events.mapTo(linkedSetOf()) { it.id }
            draft.record("rule", before.document, ids, Result.success(review), before.documentRevision)

            viewModel.focusEvent(2L, seek = false)
            viewModel.setQuery("Second")
            assertEquals(before.documentRevision, viewModel.state.value.documentRevision)
            viewModel.focusEvent(1L, seek = false)
            viewModel.updateFocusedText("intermediate edit")
            viewModel.undo()
            val restored = viewModel.state.value
            assertEquals(before.document, restored.document)
            assertEquals(before.documentRevision + 2L, restored.documentRevision)
            assertEquals(null, draft.preview("rule", restored.document, ids, restored.documentRevision))
            viewModel.applyBatchReview(review)
            assertEquals(before.document, viewModel.state.value.document)
            assertTrue(viewModel.state.value.status.contains("已过期"))
            assertTrue(viewModel.state.value.canRedo)

            val fresh = io.github.assworkbench.domain.AssBatchReview.create(restored.document,
                io.github.assworkbench.domain.AssBatchRecipe("review", actions = listOf(
                    io.github.assworkbench.domain.AssBatchAction.SetActor("ReviewedActor"))),
                restored.workspaceSessionId, restored.documentRevision)
            viewModel.applyBatchReview(fresh)
            assertTrue(viewModel.state.value.document.events.all { it.name == "ReviewedActor" })
            assertEquals(restored.documentRevision + 1L, viewModel.state.value.documentRevision)
            viewModel.undo()
            assertEquals(before.document, viewModel.state.value.document)
        }
    }

    @Test fun subtitleFilterSavedStateRestoresConditionsAndProjectReplacementClearsThem() {
        restoreRecovery()
        composeRule.runOnIdle {
            val handle = androidx.lifecycle.SavedStateHandle()
            val first = EditorViewModel(application, handle)
            val filter = io.github.assworkbench.domain.SubtitleFilterState(
                quickText = "line", actor = "Alice", kind = io.github.assworkbench.domain.SubtitleEventKind.COMMENT,
                selectedOnly = true)
            first.setSubtitleFilter(filter)
            val restoredHandle = androidx.lifecycle.SavedStateHandle(mapOf(
                "subtitle-filter" to handle.get<ArrayList<String>>("subtitle-filter")))
            val restored = EditorViewModel(application, restoredHandle)
            assertEquals(filter, restored.state.value.subtitleFilter)
            restored.newSubtitleProject()
            assertEquals(io.github.assworkbench.domain.SubtitleFilterState(), restored.state.value.subtitleFilter)
            assertEquals(io.github.assworkbench.domain.SubtitleFilterState(), EditorViewModel(application, restoredHandle).state.value.subtitleFilter)
        }
    }

    @Test fun fixedTaskNavigationAndExternalToolCommandKeepTheRequestedEditorConsistent() {
        restoreRecovery()
        eventRow(1L).performClick()
        openTool("STYLE")
        composeRule.onNodeWithTag("fixed-preview-density").assertIsDisplayed()
        val before = viewModel.state.value.document
        composeRule.onNodeWithTag("fixed-tool-SUBTITLES").performScrollTo().performClick()
        composeRule.onNodeWithTag("fixed-tool-SUBTITLES").assertIsSelected()
        composeRule.onNodeWithTag("fixed-tool-STYLE").assertIsNotSelected()
        composeRule.onNodeWithTag("fixed-preview-density").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithText("字体管理").performClick()
        composeRule.onNodeWithTag("fixed-inspector").assertIsDisplayed()
        composeRule.onNodeWithTag("fixed-tool-SUBTITLES").assertIsNotSelected()
        openTool("TEXT")
        composeRule.onNodeWithTag("event-collapse-1").performClick()
        composeRule.onNodeWithTag("fixed-tool-SUBTITLES").assertIsSelected()
        assertEquals(before, viewModel.state.value.document)
    }

    @Test fun eventTextDraftFollowsTheSameToolAcrossMainAndCanvasWithoutImplicitCommit() {
        restoreRecovery()
        eventRow(1L).performClick()
        val before = viewModel.state.value.document
        composeRule.onNodeWithTag("event-raw-1").performTextInput(" CROSS")
        hideKeyboard()
        composeRule.onNodeWithTag("workspace-direct-mode-toggle").performClick()
        openTool("TEXT")
        assertEquals("Recovered line CROSS", rawDraftText(1L))
        assertEquals(before, viewModel.state.value.document)
        composeRule.onNodeWithTag("event-raw-1").performTextInput(" HOST")
        hideKeyboard()
        composeRule.onNodeWithTag("workspace-direct-mode-toggle").performClick()
        openTool("TEXT")
        assertEquals("Recovered line CROSS HOST", rawDraftText(1L))
        assertEquals(before, viewModel.state.value.document)
        composeRule.onNodeWithTag("event-apply-text-1").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { eventText(1L) == "Recovered line CROSS HOST" }
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitUntil(10_000) { viewModel.state.value.document == before }
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
        composeRule.onNodeWithTag("spatial-overview").performScrollTo().assertIsDisplayed().performClick()
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
        val railBounds = composeRule.onNodeWithTag("spatial-edge-rail-left").fetchSemanticsNode().boundsInRoot
        val pinBounds = composeRule.onNodeWithTag("spatial-edge-pin-left").fetchSemanticsNode().boundsInRoot
        val closeBounds = composeRule.onNodeWithTag("spatial-edge-close-left").fetchSemanticsNode().boundsInRoot
        assertTrue("Pin must fit inside the rail", pinBounds.left >= railBounds.left && pinBounds.right <= railBounds.right)
        assertTrue("Close must fit inside the rail", closeBounds.left >= railBounds.left && closeBounds.right <= railBounds.right)
        assertTrue("Rail actions must not overlap", pinBounds.bottom <= closeBounds.top || pinBounds.right <= closeBounds.left)

        composeRule.onNodeWithTag("spatial-edge-pin-left").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("取消驻留").assertIsDisplayed()
        Log.i("AsswbRegression", "resident-rail:pinned-on-board")
        composeRule.onNodeWithTag("spatial-edge-entry-subtitles").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("spatial-focused-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-native-content-subtitles").assertExists()
        composeRule.onNodeWithTag("spatial-edge-rail-left").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("取消驻留").assertIsDisplayed()
        Log.i("AsswbRegression", "resident-rail:retained-in-editor")
        composeRule.onNodeWithTag("spatial-return-to-board").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("spatial-edge-rail-left").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("取消驻留").assertIsDisplayed()
        Log.i("AsswbRegression", "resident-rail:retained-on-return")
        composeRule.onNodeWithTag("spatial-edge-close-left").performClick()
        composeRule.onNodeWithTag("spatial-node-subtitles").assertIsDisplayed()

        composeRule.onNodeWithText("召回").performScrollTo().assertIsDisplayed().performClick()
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
        composeRule.onNodeWithText("召回").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("spatial-recall-CAPABILITIES-primary").performClick()
        composeRule.onNodeWithTag("spatial-native-content-CAPABILITIES-primary").assertExists()
    }

    @Test fun productionTimelineDockResizeDoesNotChangeSubtitleEditingState() {
        restoreRecovery()
        switchToUnifiedCanvas()
        val before = viewModel.state.value
        composeRule.onNodeWithTag("spatial-timeline-toggle").performClick()
        val originalHeight = composeRule.onNodeWithTag("spatial-timeline-dock").fetchSemanticsNode().boundsInRoot.height
        val density = composeRule.activity.resources.displayMetrics.density
        composeRule.onNodeWithTag("spatial-timeline-resize").performTouchInput {
            down(center)
            moveBy(Offset(0f, -100f * density))
            up()
        }
        val newHeight = composeRule.onNodeWithTag("spatial-timeline-dock").fetchSemanticsNode().boundsInRoot.height
        assertTrue(newHeight > originalHeight + 32f * density)
        composeRule.onNodeWithTag("timeline-temporal-canvas").assertExists()
        composeRule.runOnIdle {
            val after = viewModel.state.value
            assertEquals(before.document, after.document)
            assertEquals(before.focusedEventId, after.focusedEventId)
            assertEquals(before.selectedEventIds, after.selectedEventIds)
            assertEquals(before.dirty, after.dirty)
            assertEquals(before.canUndo, after.canUndo)
        }
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
        assertEquals("Recovered line SWITCH", rawDraftText(1L))
        // Switching to Effects removes the TEXT provider but must not
        // create a new draft owner for the same fixed Event inspector.
        openTool("EFFECTS")
        composeRule.onNodeWithTag("fixed-inspector").assertIsDisplayed()
        assertEquals("Recovered line", eventText(1L))
        openTool("TEXT")
        assertEquals("Recovered line SWITCH", rawDraftText(1L))
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
        composeRule.waitForIdle()
        val compactLandscape = composeRule.onAllNodesWithTag("fixed-content-flow", useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        val previewNodes = composeRule.onAllNodesWithTag("preview-workspace").fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (previewNodes.isNotEmpty()) {
            // Short landscape windows can remain COMPACT. Their saved outer
            // scroll may intentionally hide the preview while editing.
            if (compactLandscape) composeRule.onNodeWithTag("preview-workspace").performScrollTo()
            composeRule.onNodeWithTag("preview-workspace").assertIsDisplayed()
        }
        if (compactLandscape) {
            composeRule.onNodeWithTag("fixed-inspector").performScrollTo().assertIsDisplayed()
        }
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

    @Test fun compactContentFlowCanScrollPreviewOutWithoutLosingDirtyText() {
        restoreRecovery()
        eventRow(1L).performScrollTo().performClick()
        openTool("TEXT")
        val before = viewModel.state.value.document
        composeRule.onNodeWithTag("event-raw-1").performScrollTo().performTextReplacement("flow draft")
        hideKeyboard()
        val compact = composeRule.onAllNodesWithTag("fixed-content-flow")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (compact.isNotEmpty()) {
            composeRule.onNodeWithTag("fixed-preview-size").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("fixed-inspector").performScrollTo().assertIsDisplayed()
            val preview = composeRule.onNodeWithTag("preview-workspace").fetchSemanticsNode().boundsInRoot
            val flow = composeRule.onNodeWithTag("fixed-content-flow").fetchSemanticsNode().boundsInRoot
            assertTrue("Preview can leave the editor viewport", preview.bottom <= flow.top + 1f)
        }
        assertEquals("flow draft", rawDraftText(1L))
        assertEquals(before, viewModel.state.value.document)
    }

    @Test fun independentDirectoryDismissalPreservesDirtyTextAndTaskContext() {
        restoreRecovery()
        eventRow(1L).performScrollTo().performClick()
        openTool("TEXT")
        val before = viewModel.state.value.document
        composeRule.onNodeWithTag("event-raw-1").performScrollTo().performTextReplacement("directory draft")
        hideKeyboard()
        composeRule.onNodeWithTag("fixed-tool-CAPABILITIES").performClick()
        composeRule.onNodeWithTag("fixed-tool-directory").assertIsDisplayed()
        composeRule.onNodeWithTag("fixed-directory-close").performClick()
        composeRule.onNodeWithTag("event-raw-1").performScrollTo().assertIsDisplayed()
        assertEquals("directory draft", rawDraftText(1L))
        assertEquals(before, viewModel.state.value.document)
        openTool("POSITION")
        composeRule.onNodeWithTag("fixed-tool-STYLE").assertIsSelected().performClick()
        composeRule.onNodeWithTag("position-section-PLACEMENT").performScrollTo().assertIsDisplayed()
        assertEquals(before, viewModel.state.value.document)
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
        showFixedSubtitleList()
        eventRow(1L).performScrollTo().performClick()
        composeRule.onNodeWithTag("event-raw-1").performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithTag("spatial-quick-preview").performClick()
        composeRule.onNodeWithTag("spatial-menu-preview").performClick()
        composeRule.onNodeWithTag("spatial-hide-preview").performClick()
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
        composeRule.onNodeWithTag("spatial-quick-preview").performClick()
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
        composeRule.onNodeWithTag("batch-mode-search").performClick()
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

    @Test fun batchActionsRemainAboveVisibleKeyboardAcrossMainAndCanvas() {
        restoreRecovery()
        composeRule.activityRule.scenario.onActivity { activity ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        repeat(2) { host ->
            if (host == 1) switchToUnifiedCanvas()
            openTool("BATCH")
            composeRule.onNodeWithTag("batch-actor-action").performScrollTo().assertIsDisplayed()
                .performTextReplacement("KEYBOARD_BATCH")
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("batch-actor-action").assertIsDisplayed().performClick()
            var keyboardRoot: View? = null
            var showEvidence = "No focused root selected"
            try {
                performInFocusedWindow("Show batch IME in the focused window") { controller, root ->
                    keyboardRoot = root
                    val focused = checkNotNull(root.findFocus()) { "Batch field must own input focus" }
                    val accepted = focused.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .showSoftInput(focused, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    showEvidence = "host=$host accepted=$accepted windowFocused=${root.hasWindowFocus()} " +
                        "focusClass=${focused.javaClass.simpleName} hasToken=${focused.windowToken != null}"
                    controller.loopMainThreadUntilIdle()
                }
                composeRule.waitUntil(5_000) {
                    keyboardRoot?.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
                }
            } catch (error: Exception) {
                throw AssertionError("Batch IME did not become visible: $showEvidence", error)
            }
            composeRule.waitForIdle()
            val decor = checkNotNull(keyboardRoot)
            val keyboardTop = decor.height - decor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            val preview = composeRule.onNodeWithTag("batch-preview-explicit")
            val apply = composeRule.onNodeWithTag("batch-review-commit")
            preview.assertIsDisplayed()
            apply.assertIsDisplayed()
            assertTrue("Preview must stay above IME", preview.fetchSemanticsNode().boundsInWindow.bottom <= keyboardTop + 1f)
            assertTrue("Apply must stay above IME", apply.fetchSemanticsNode().boundsInWindow.bottom <= keyboardTop + 1f)
            preview.performTouchInput { click() }
            apply.assertIsEnabled()
            hideKeyboard()
        }
    }

    @Test fun batchDraftAndReviewedPreviewFollowSameInstanceAcrossMainAndCanvas() {
        restoreRecovery()
        val before = viewModel.state.value.document
        composeRule.runOnIdle { viewModel.setQuery("Second") }
        openTool("BATCH")
        composeRule.onNodeWithTag("batch-scope-RESULTS").performClick()
        composeRule.onNodeWithTag("batch-actor-action").performScrollTo().performTextReplacement("SHARED_BATCH")
        hideKeyboard()
        visibleBatchAction("batch-preview-explicit").performClick()
        composeRule.onNodeWithTag("batch-review-commit").assertIsEnabled()
        composeRule.onNodeWithTag("batch-mode-search").performClick()
        composeRule.onNodeWithTag("batch-mode-rule").performClick()
        composeRule.onNodeWithTag("batch-review-commit").assertIsEnabled()
        switchToUnifiedCanvas()
        openTool("BATCH")
        composeRule.onNodeWithTag("batch-scope-RESULTS").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("batch-scope-count").assertTextContains("作用范围 1 条")
        composeRule.onNodeWithTag("batch-actor-action").performScrollTo().assertTextContains("SHARED_BATCH")
        composeRule.onNodeWithTag("batch-review-commit").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(before, viewModel.state.value.document) }
        visibleBatchAction("batch-review-commit").performClick()
        composeRule.runOnIdle {
            assertEquals("SHARED_BATCH", viewModel.state.value.document.events.first { it.id == 2L }.name)
            viewModel.undo()
            assertEquals(before, viewModel.state.value.document)
        }
    }

    @Test fun batchSearchLiteralInputsRestoreButReviewedCommitRequiresFreshPrecheckAfterRecreation() {
        restoreRecovery()
        openTool("BATCH")
        composeRule.onNodeWithTag("batch-mode-search").performClick()
        composeRule.onNodeWithTag("batch-scope-DOCUMENT").performClick()
        composeRule.onNodeWithTag("search-pattern").performScrollTo().performTextReplacement("Second")
        composeRule.onNodeWithTag("search-replacement").performScrollTo().performTextReplacement("RESTORED_SEARCH")
        hideKeyboard()
        visibleBatchAction("search-review-preview").performClick()
        composeRule.onNodeWithTag("search-review-commit").assertIsEnabled()
        val before = viewModel.state.value.document
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        viewModel = composeRule.activity.editorViewModel
        openTool("BATCH")
        composeRule.onNodeWithTag("batch-mode-search").performClick()
        composeRule.onNodeWithTag("search-pattern").performScrollTo().assertTextContains("Second")
        composeRule.onNodeWithTag("search-replacement").performScrollTo().assertTextContains("RESTORED_SEARCH")
        composeRule.onNodeWithTag("search-review-commit").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(before, viewModel.state.value.document) }
    }

    @Test fun reviewedBatchCommitsOneUndoAndRejectsStaleBaselineAndSession() {
        restoreRecovery()
        composeRule.runOnIdle {
            val before = viewModel.state.value.document
            val recipe = io.github.assworkbench.domain.AssBatchRecipe("reviewed", actions = listOf(
                io.github.assworkbench.domain.AssBatchAction.SetActor("Reviewed")))
            val review = io.github.assworkbench.domain.AssBatchReview.create(before, recipe,
                viewModel.state.value.workspaceSessionId, viewModel.state.value.documentRevision)
            viewModel.applyBatchReview(review)
            assertTrue(viewModel.state.value.document.events.all { it.name == "Reviewed" })
            viewModel.undo()
            assertEquals(before, viewModel.state.value.document)
            viewModel.updateEventText(1L, "external mutation")
            val changed = viewModel.state.value.document
            viewModel.applyBatchReview(review)
            assertEquals(changed, viewModel.state.value.document)
            assertTrue(viewModel.state.value.status.contains("过期"))
            val oldSession = io.github.assworkbench.domain.AssBatchReview.create(changed, recipe,
                viewModel.state.value.workspaceSessionId, viewModel.state.value.documentRevision)
            viewModel.newSubtitleProject()
            val replacement = viewModel.state.value.document
            viewModel.applyBatchReview(oldSession)
            assertEquals(replacement, viewModel.state.value.document)
            assertFalse(viewModel.state.value.canUndo)
        }
    }

    @Test fun batchUsesSharedResultsAndRequiresFreshPreviewAfterScopeChangeAcrossHosts() {
        restoreRecovery()
        composeRule.runOnIdle { viewModel.setQuery("Second") }
        openTool("BATCH")
        composeRule.onNodeWithTag("batch-scope-RESULTS").performClick()
        composeRule.onNodeWithTag("batch-scope-count").assertTextContains("作用范围 1 条")
        composeRule.onNodeWithTag("batch-actor-action").performScrollTo().performTextReplacement("Reviewed")
        hideKeyboard()
        visibleBatchAction("batch-preview-explicit").performClick()
        composeRule.onNodeWithTag("batch-review-commit").assertIsEnabled()
        val beforeLocate = viewModel.state.value.document
        val selectionBeforeLocate = viewModel.state.value.selectedEventIds
        composeRule.onNodeWithTag("batch-preview-locate-2").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(2L, viewModel.state.value.focusedEventId)
            assertEquals(selectionBeforeLocate, viewModel.state.value.selectedEventIds)
            assertEquals(beforeLocate, viewModel.state.value.document)
        }
        composeRule.onNodeWithTag("batch-scope-DOCUMENT").performScrollTo().performClick()
        composeRule.onNodeWithTag("batch-review-commit").assertIsNotEnabled()
        switchToUnifiedCanvas()
        openTool("BATCH")
        composeRule.onNodeWithTag("batch-scope-RESULTS").performScrollTo().performClick()
        composeRule.onNodeWithTag("batch-scope-count").assertTextContains("作用范围 1 条")
        composeRule.onNodeWithTag("batch-actor-action").performScrollTo().performTextReplacement("Reviewed")
        hideKeyboard()
        visibleBatchAction("batch-preview-explicit").performClick()
        visibleBatchAction("batch-review-commit").performClick()
        composeRule.runOnIdle {
            assertEquals("Reviewed", viewModel.state.value.document.events.first { it.id == 2L }.name)
            assertFalse(viewModel.state.value.document.events.first { it.id == 1L }.name == "Reviewed")
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
        composeRule.onNodeWithTag("batch-mode-search").performClick()
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
    fun transformDraftSurvivesTaskAndHostSwitchAndBlocksExternalRawConflict() {
        restoreRecovery()
        composeRule.runOnIdle {
            viewModel.updateEventText(1L, "{\\t(120,780,\\fscx140)}Recovered line")
        }
        eventRow(1L).performClick()
        openTool("EFFECTS")
        composeRule.onNodeWithTag("effect-transform-remove-0")
            .performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("effect-transform-start-0")
            .performScrollTo().performTextReplacement("251")
        composeRule.onNodeWithTag("effect-transform-remove-0")
            .performScrollTo().assertIsNotEnabled()
        hideKeyboard()
        openTool("TEXT")
        openTool("EFFECTS")
        composeRule.onNodeWithTag("effect-transform-start-0")
            .performScrollTo().assertTextContains("251")

        switchToUnifiedCanvas()
        openTool("EFFECTS")
        composeRule.onNodeWithTag("effect-transform-start-0")
            .performScrollTo().assertTextContains("251")
        val original = viewModel.state.value.document.events.first { it.id == 1L }.text
        assertTrue(original.contains("\\t(120,780,"))

        composeRule.runOnIdle {
            viewModel.updateEventText(1L, "{\\pos(12,34)\\t(120,780,\\fscx140)}Recovered line")
        }
        composeRule.onNodeWithTag("effect-transform-conflict-0")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("effect-transform-remove-0")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("effect-transform-start-0")
            .assertTextContains("251")
        composeRule.onNodeWithTag("effect-transform-reload-0")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("effect-transform-start-0")
            .assertTextContains("120.0")
    }

    @Test
    fun staleTransformSourceIsRejectedWithoutOverwritingOtherRawEdits() {
        restoreRecovery()
        val baseline = "{\\t(120,780,\\fscx140)}Recovered line"
        val externallyChanged = "{\\pos(50,60)\\t(120,780,\\fscx140)}Recovered line"
        composeRule.runOnIdle {
            viewModel.updateEventText(1L, baseline)
            viewModel.updateEventText(1L, externallyChanged)
            val before = viewModel.state.value.document
            val candidate = AssTransform(150.0, 750.0, null, "\\fscx190")
            assertFalse(viewModel.setEventTransformIfSourceMatches(1L, 0, baseline, candidate))
            assertEquals(before, viewModel.state.value.document)
            assertTrue(viewModel.setEventTransformIfSourceMatches(1L, 0, externallyChanged, candidate))
            assertTrue(viewModel.state.value.document.events.first { it.id == 1L }.text.contains("\\pos(50,60)"))
            viewModel.undo()
            assertEquals(before, viewModel.state.value.document)
            assertFalse(viewModel.removeEventTransformIfSourceMatches(1L, 0, baseline))
            assertEquals(before, viewModel.state.value.document)
            assertTrue(viewModel.removeEventTransformIfSourceMatches(1L, 0, externallyChanged))
            assertTrue(viewModel.state.value.document.events.first { it.id == 1L }.text.contains("\\pos(50,60)"))
            viewModel.undo()
            assertEquals(before, viewModel.state.value.document)
        }
    }

    @Test
    fun transformTimingHandlesAreDraftOnlyAndKeepExactFieldFallback() {
        restoreRecovery()
        composeRule.runOnIdle {
            viewModel.updateEventText(1L, "{\\t(120,780,\\fscx140)}Recovered line")
        }
        composeRule.waitUntil(5_000) {
            viewModel.state.value.document.events.first { it.id == 1L }.text
                .contains("\\t(120,780,")
        }
        val before = viewModel.state.value.document
        eventRow(1L).performClick()
        openTool("EFFECTS")
        composeRule.onNodeWithTag("effect-transform-entry")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("effect-transform-range-0")
            .performScrollTo().assertIsDisplayed()
        assertEquals(before, viewModel.state.value.document)

        // Fractional timestamps remain editable in the exact fields, not
        // silently rounded or represented by a one-millisecond slider.
        composeRule.runOnIdle {
            viewModel.updateEventText(1L, "{\\t(120.5,780.5,\\fscx140)}Recovered line")
        }
        composeRule.waitUntil(5_000) {
            viewModel.state.value.document.events.first { it.id == 1L }.text
                .contains("\\t(120.5,780.5,")
        }
        composeRule.onNodeWithTag("effect-transform-start-0")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("effect-transform-end-0")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("effect-transform-range-0").assertDoesNotExist()
    }

    @Test
    fun effectsPaneExposesSpatialFadeAuthoringWithoutMutatingDocument() {
        restoreRecovery()
        eventRow(1L).performClick()
        val before = viewModel.state.value.document

        openTool("EFFECTS")
        // Daily authoring controls remain reachable above the advanced composition pane.
        composeRule.onNodeWithTag("effect-blur-value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("effect-fade-header").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("effect-transform-entry").performScrollTo().assertIsDisplayed()
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

        composeRule.onNodeWithTag("fixed-tool-SUBTITLES").performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("subtitle-navigation", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun visibleBatchAction(tag: String): SemanticsNodeInteraction {
        val action = composeRule.onNodeWithTag(tag)
        // Fixed actions are outside the tool's inner scroll body. In the compact
        // main host the entire finite tool pane still belongs to the outer flow.
        // Canvas/tablet footers have no such scrolling ancestor.
        if (composeRule.onAllNodesWithTag("fixed-content-flow", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
            action.performScrollTo()
        }
        return action.assertIsDisplayed()
    }

    private fun performInFocusedWindow(description: String, action: (UiController, View) -> Unit) {
        // Window focus differs from a child View's input focus. Espresso does
        // not expose a ViewMatchers.hasWindowFocus API in the pinned version.
        val focusedDecor = object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("decor view with window focus")
            }
            override fun matchesSafely(view: View): Boolean = view.hasWindowFocus()
        }
        onView(isRoot()).inRoot(withDecorView(focusedDecor)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription(): String = description
            override fun perform(controller: UiController, view: View) = action(controller, view.rootView)
        })
    }

    private fun hideKeyboard() {
        var imeRoot: View? = null
        var requestEvidence = "No focused root selected"
        try {
            performInFocusedWindow("Close IME in the currently focused window") { controller, root ->
                imeRoot = root
                requestEvidence = "rootClass=${root.javaClass.simpleName} windowFocused=${root.hasWindowFocus()} " +
                    "focusClass=${root.findFocus()?.javaClass?.simpleName} " +
                    "beforeVisible=${root.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())}"
                ViewActions.closeSoftKeyboard().perform(controller, root)
            }
            composeRule.waitUntil(5_000) {
                imeRoot?.rootWindowInsets
                    ?.isVisible(android.view.WindowInsets.Type.ime()) == false
            }
        } catch (error: Exception) {
            throw AssertionError("IME hide did not complete: $requestEvidence", error)
        }
        composeRule.waitForIdle()
    }

    private fun switchToUnifiedCanvas() {
        hideKeyboard()
        if (composeRule.onAllNodesWithTag("spatial-workspace", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) return
        composeRule.onNodeWithTag("workspace-direct-mode-toggle")
            .assertIsDisplayed().performClick()
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
            composeRule.onNodeWithTag("fixed-tool-CAPABILITIES").performClick()
            composeRule.onNodeWithTag("tool-search").performTextReplacement(name)
            hideKeyboard()
            composeRule.onNodeWithTag("tool-$name").performScrollTo().assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            // Inner performScrollTo only reaches the nearest scroll ancestor.
            // Expose the finite inspector in the compact outer flow first;
            // subsequent field scroll/display assertions remain strict.
            if (composeRule.onAllNodesWithTag("fixed-content-flow", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
                composeRule.onNodeWithTag("fixed-inspector").performScrollTo().assertIsDisplayed()
            }
            return
        }
        composeRule.onNodeWithTag("spatial-add-tool").assertIsDisplayed().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("tool-search")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        composeRule.onNodeWithTag("tool-search").performTextReplacement(name)
        hideKeyboard()
        composeRule.onNodeWithTag("tool-$name").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun rawDraftText(id: Long): String =
        composeRule.onNodeWithTag("event-raw-$id").fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.EditableText
        ].text

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

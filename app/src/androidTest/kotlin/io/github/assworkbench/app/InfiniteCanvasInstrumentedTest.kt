package io.github.assworkbench.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.runtime.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.workspace.InfiniteCanvasCamera
import io.github.assworkbench.app.ui.workspace.InfiniteCanvasEntry
import io.github.assworkbench.app.ui.workspace.InfiniteCanvasHost
import io.github.assworkbench.app.ui.workspace.InfiniteCanvasNode
import io.github.assworkbench.app.ui.workspace.InfiniteCanvasPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class InfiniteCanvasInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<EditorRegressionHostActivity>()

    @Test fun birdseyeRecallsHiddenFarNodeWithoutChangingItsGeometry() {
        val fixture = OverviewFixture(listOf(
            InfiniteCanvasNode("preview"), InfiniteCanvasNode("subtitles", x = 10_000f, hidden = true, z = 9)))
        showOverviewFixture(fixture)
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.onNodeWithTag("spatial-birdseye-node-subtitles").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("spatial-native-content-subtitles").assertExists()
        val recalled = fixture.saved.second.first { it.id == "subtitles" }
        assertEquals(fixture.nodes.last().copy(hidden = false), recalled)
        assertFalse(recalled.hidden)
        assertNotEquals(InfiniteCanvasCamera(0f, 0f, 0.25f), fixture.saved.first)
        composeRule.onNodeWithTag("spatial-birdseye-dialog").assertDoesNotExist()
    }

    @Test fun clickingMapMarkerApproachesTheNode() {
        val fixture = OverviewFixture(listOf(InfiniteCanvasNode("preview", x = 10_000f)))
        showOverviewFixture(fixture)
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.onNodeWithTag("spatial-birdseye-map").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("spatial-native-content-preview").assertExists()
        assertEquals(fixture.nodes, fixture.saved.second)
        assertNotEquals(InfiniteCanvasCamera(0f, 0f, 0.25f), fixture.saved.first)
        composeRule.onNodeWithTag("overview-content-preview").assertExists()
    }

    @Test fun ownedRodGestureBlocksMapAndListNavigation() {
        val fixture = OverviewFixture(listOf(InfiniteCanvasNode("preview")))
        showOverviewFixture(fixture)
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.runOnIdle { fixture.owned = true }
        composeRule.onNodeWithTag("spatial-birdseye-node-preview").assertIsNotEnabled()
        val before = fixture.saved
        composeRule.onNodeWithTag("spatial-birdseye-map").performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(before, fixture.saved) }
        composeRule.onNodeWithTag("spatial-birdseye-dialog").assertExists()
    }

    @Test fun replacementSessionDoesNotInheritDetailedNodeOrOpenBirdseye() {
        val fixture = OverviewFixture(listOf(InfiniteCanvasNode("preview", width = 2400f, height = 1800f)))
        showOverviewFixture(fixture)
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.onNodeWithTag("spatial-birdseye-node-preview").performScrollTo().performClick()
        composeRule.onNodeWithTag("overview-content-preview").assertExists()
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.runOnIdle { fixture.session = 2L }
        composeRule.onNodeWithTag("spatial-birdseye-dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("overview-content-preview").assertDoesNotExist()
    }

    private class OverviewFixture(val nodes: List<InfiniteCanvasNode>) {
        val initial = InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(0f, 0f, 0.25f), nodes)
        var saved = InfiniteCanvasPersistence.decode(initial)
        var session by mutableStateOf(1L)
        var owned by mutableStateOf(false)
    }

    private fun showOverviewFixture(fixture: OverviewFixture) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = fixture.session, savedScene = fixture.initial,
                        onSaveScene = { fixture.saved = InfiniteCanvasPersistence.decode(it) },
                        entries = fixture.nodes.map { InfiniteCanvasEntry(it.id, it.id) },
                        gestureOwned = fixture.owned, onAddTool = {}, onActivate = {},
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("overview-content-" + id)) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun overviewCardsOpenNativeSizedEditorAndReturnWithoutChangingNodes() {
        val fixture = OverviewFixture(listOf(
            InfiniteCanvasNode("preview", width = 900f, height = 300f),
        ))
        showOverviewFixture(fixture)
        val original = fixture.saved.second
        composeRule.onNodeWithTag("spatial-open-preview").performClick()
        composeRule.onNodeWithTag("spatial-focused-editor").assertExists()
        composeRule.onNodeWithTag("spatial-native-content-preview").assertExists()
        composeRule.onNodeWithTag("overview-content-preview").assertExists()
        composeRule.onNodeWithTag("spatial-node-preview").assertDoesNotExist()
        composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        composeRule.onNodeWithTag("spatial-node-preview").assertExists()
        composeRule.onNodeWithTag("spatial-native-content-preview").assertDoesNotExist()
        assertEquals(original, fixture.saved.second)
    }

    @Test fun closeUpBoardKeepsMultipleRealToolsAndOverviewUsesSummaries() {
        val fixture = OverviewFixture(listOf(
            InfiniteCanvasNode("preview", width = 360f, height = 320f),
            InfiniteCanvasNode("subtitles", x = 440f, width = 360f, height = 320f),
        ))
        val boardAtOne = InfiniteCanvasPersistence.encode(
            InfiniteCanvasCamera(0f, 0f, 1f), fixture.nodes)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 3L, savedScene = boardAtOne,
                        onSaveScene = { fixture.saved = InfiniteCanvasPersistence.decode(it) },
                        entries = fixture.nodes.map { InfiniteCanvasEntry(it.id, it.id) },
                        gestureOwned = false, onAddTool = {}, onActivate = {},
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("overview-content-" + id)) }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("spatial-live-preview").assertExists()
        composeRule.onNodeWithTag("spatial-live-subtitles").assertExists()
        composeRule.onNodeWithTag("overview-content-preview").assertExists()
        composeRule.onNodeWithTag("overview-content-subtitles").assertExists()

        composeRule.onNodeWithTag("spatial-zoom-out").performClick()
        composeRule.onNodeWithTag("spatial-live-preview").assertDoesNotExist()
        composeRule.onNodeWithTag("spatial-open-preview").assertExists()
        composeRule.onNodeWithTag("spatial-zoom-in").performClick()
        composeRule.onNodeWithTag("spatial-live-preview").assertExists()
        assertEquals(fixture.nodes, fixture.saved.second)
    }

    @Test fun pickerSwitchesToAnAlreadyExistingToolInstance() {
        val nodes = listOf(
            InfiniteCanvasNode("CAPABILITIES:primary"),
            InfiniteCanvasNode("POSITION:primary", x = 460f),
        )
        val initial = InfiniteCanvasPersistence.encode(
            InfiniteCanvasCamera(0f, 0f, 0.85f), nodes)
        var requestedId by mutableStateOf<String?>(null)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 4L, savedScene = initial, onSaveScene = {},
                        entries = nodes.map { InfiniteCanvasEntry(it.id, it.id) },
                        gestureOwned = false,
                        onAddTool = { requestedId = "CAPABILITIES:primary" },
                        onActivate = {},
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        requestedActiveToolId = requestedId,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("overview-content-" + id)) }
                }
            }
        }
        composeRule.onNodeWithTag("spatial-add-tool").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("spatial-native-content-CAPABILITIES-primary").assertExists()
        composeRule.runOnIdle { requestedId = "POSITION:primary" }
        composeRule.onNodeWithTag("spatial-native-content-POSITION-primary").assertExists()
    }


    @Test fun explicitBirdseyeNavigationCancelsAbandonedToolPickerSelection() {
        val nodes = listOf(
            InfiniteCanvasNode("CAPABILITIES:primary"),
            InfiniteCanvasNode("POSITION:primary", x = 460f),
            InfiniteCanvasNode("STYLE:primary", x = 920f),
        )
        val initial = InfiniteCanvasPersistence.encode(
            InfiniteCanvasCamera(0f, 0f, 0.85f), nodes)
        var requestedId by mutableStateOf<String?>(null)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 5L, savedScene = initial, onSaveScene = {},
                        entries = nodes.map { InfiniteCanvasEntry(it.id, it.id) },
                        gestureOwned = false,
                        onAddTool = { requestedId = "CAPABILITIES:primary" },
                        onActivate = {},
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        requestedActiveToolId = requestedId,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("overview-content-" + id)) }
                }
            }
        }
        composeRule.onNodeWithTag("spatial-add-tool").performClick()
        composeRule.onNodeWithTag("spatial-native-content-CAPABILITIES-primary").assertExists()
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.onNodeWithTag("spatial-birdseye-node-POSITION-primary")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-native-content-POSITION-primary").assertExists()
        composeRule.runOnIdle { requestedId = "STYLE:primary" }
        composeRule.onNodeWithTag("spatial-native-content-POSITION-primary").assertExists()
        composeRule.onNodeWithTag("spatial-native-content-STYLE-primary").assertDoesNotExist()
    }


    @Test fun offscreenMediaSuspendsWithoutUnmountingGeneralEditors() {
        val nodes = listOf(
            InfiniteCanvasNode("audio", width = 280f, height = 220f),
            InfiniteCanvasNode("subtitles", x = 1_000f, width = 280f, height = 220f),
            InfiniteCanvasNode("preview", x = 8_000f, width = 280f, height = 220f),
        )
        val initial = InfiniteCanvasPersistence.encode(
            InfiniteCanvasCamera(0f, 0f, 1f), nodes)
        var saved = InfiniteCanvasPersistence.decode(initial)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 6L, savedScene = initial,
                        onSaveScene = { saved = InfiniteCanvasPersistence.decode(it) },
                        entries = nodes.map { InfiniteCanvasEntry(it.id, it.id) },
                        gestureOwned = false, onAddTool = {}, onActivate = {},
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("overview-content-" + id)) }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("spatial-live-audio").assertExists()
        composeRule.onNodeWithTag("spatial-live-preview").assertDoesNotExist()
        // Unproven draft-bearing tools remain mounted even while offscreen.
        composeRule.onNodeWithTag("spatial-live-subtitles").assertExists()
        composeRule.onNodeWithTag("overview-content-subtitles").assertExists()
        composeRule.onNodeWithTag("spatial-birdseye").performClick()
        composeRule.onNodeWithTag("spatial-birdseye-node-preview")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("spatial-native-content-preview").assertExists()
        composeRule.onNodeWithTag("overview-content-preview").assertExists()
        composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        composeRule.onNodeWithTag("spatial-live-preview").assertExists()
        composeRule.onNodeWithTag("spatial-live-audio").assertDoesNotExist()
        composeRule.onNodeWithTag("spatial-live-subtitles").assertExists()
        assertEquals(nodes, saved.second)
    }


    @Test fun focusedPreviewHasDirectAddToolEntryAndOpensNativeToolDirectory() {
        var directoryAdded by mutableStateOf(false)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 77L, savedScene = emptyList(),
                        onSaveScene = {},
                        entries = listOf(InfiniteCanvasEntry("preview", "视频")) +
                            if (directoryAdded) listOf(
                                InfiniteCanvasEntry("CAPABILITIES:primary", "工具目录")) else emptyList(),
                        gestureOwned = false, onAddTool = { directoryAdded = true },
                        onActivate = {}, onUndo = {}, onRedo = {},
                        canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("overview-content-" + id)) }
                }
            }
        }
        composeRule.onNodeWithTag("spatial-native-content-preview").assertExists()
        composeRule.onNodeWithTag("spatial-add-tool").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("spatial-native-content-CAPABILITIES-primary").assertExists()
        composeRule.onNodeWithTag("overview-content-CAPABILITIES:primary").assertExists()
    }

    @Test fun contextualToolLifecycleAndLayoutActionsAreReachableInFocusedAndBoardModes() {
        val nodes = listOf(
            InfiniteCanvasNode("preview"),
            InfiniteCanvasNode("POSITION:primary", x = 560f),
        )
        val initial = InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(0f, 0f, 0.85f), nodes)
        var saved = InfiniteCanvasPersistence.decode(initial)
        var closeCount by mutableIntStateOf(0)
        var cloneCount by mutableIntStateOf(0)
        var pinCount by mutableIntStateOf(0)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 78L, savedScene = initial,
                        onSaveScene = { saved = InfiniteCanvasPersistence.decode(it) },
                        entries = listOf(
                            InfiniteCanvasEntry("preview", "视频"),
                            InfiniteCanvasEntry("POSITION:primary", "位置",
                                canClose = true, canDuplicate = true, canBindEvent = true,
                                focusEventId = 4L),
                        ),
                        gestureOwned = false, onAddTool = {}, onActivate = {},
                        onCloseTool = { closeCount++ },
                        onDuplicateTool = { _, _ -> cloneCount++ },
                        onToggleEventBinding = { pinCount++ },
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("overview-content-" + id)) }
                }
            }
        }
        composeRule.onNodeWithTag("spatial-quick-POSITION-primary").performClick()
        composeRule.onNodeWithTag("spatial-menu-POSITION-primary").performClick()
        composeRule.onNodeWithTag("spatial-layout-lock-POSITION-primary").performClick()
        composeRule.waitForIdle()
        assertTrue(saved.second.single { it.id == "POSITION:primary" }.layoutLocked)
        composeRule.onNodeWithTag("spatial-menu-POSITION-primary").performClick()
        composeRule.onNodeWithText("固定读取对象 #4").performClick()
        composeRule.onNodeWithTag("spatial-menu-POSITION-primary").performClick()
        composeRule.onNodeWithText("复制工具（保留绑定）").performClick()
        composeRule.runOnIdle {
            assertEquals(1, pinCount)
            assertEquals(1, cloneCount)
        }
        composeRule.onNodeWithTag("spatial-menu-POSITION-primary").performClick()
        composeRule.onNodeWithTag("spatial-close-POSITION-primary").performClick()
        composeRule.runOnIdle { assertEquals(1, closeCount) }
        composeRule.onNodeWithTag("spatial-return-to-board").assertDoesNotExist()
    }

    @Test fun boardArrangeDoesNotAlterHiddenOrLockedWorldNodes() {
        val nodes = listOf(
            InfiniteCanvasNode("preview", x = 900f, y = 700f, layoutLocked = true),
            InfiniteCanvasNode("subtitles", x = -900f, y = 500f),
            InfiniteCanvasNode("audio", x = 500f, y = -500f, hidden = true),
        )
        val initial = InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(0f, 0f, 0.85f), nodes)
        var saved = InfiniteCanvasPersistence.decode(initial)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 79L, savedScene = initial,
                        onSaveScene = { saved = InfiniteCanvasPersistence.decode(it) },
                        entries = nodes.map { InfiniteCanvasEntry(it.id, it.id) },
                        gestureOwned = false, onAddTool = {}, onActivate = {},
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("overview-content-" + id)) }
                }
            }
        }
        composeRule.onNodeWithTag("spatial-arrange").performClick()
        composeRule.onNodeWithTag("spatial-arrange-column").performClick()
        composeRule.waitForIdle()
        assertEquals(nodes[0], saved.second.first())
        assertEquals(nodes[2], saved.second.last())
        assertTrue(saved.second[1].y > nodes[0].y + nodes[0].height)
        assertEquals(nodes.map { it.z }, saved.second.map { it.z })
    }


    @Test fun semanticZoomDoesNotDestroyNonSaveableEditorDraft() {
        val initial = InfiniteCanvasPersistence.encode(
            InfiniteCanvasCamera(0f, 0f, 1f),
            listOf(InfiniteCanvasNode("subtitles", width = 400f, height = 400f)))
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 80L, savedScene = initial, onSaveScene = {},
                        entries = listOf(InfiniteCanvasEntry("subtitles", "字幕")),
                        gestureOwned = false, onAddTool = {}, onActivate = {},
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { _, _ ->
                        var draft by remember { mutableIntStateOf(0) }
                        Button(onClick = { draft++ }, modifier = Modifier.testTag("editor-draft-increment")) {
                            Text("Draft ${draft}", Modifier.testTag("editor-draft-value"))
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("editor-draft-increment").performClick()
        composeRule.onNodeWithText("Draft 1").assertExists()
        composeRule.onNodeWithTag("spatial-zoom-out").performClick()
        composeRule.onNodeWithTag("spatial-open-subtitles").assertExists()
        composeRule.onNodeWithTag("spatial-zoom-in").performClick()
        composeRule.onNodeWithText("Draft 1").assertExists()
    }


    @Test fun focusedToolTitleSwitchesDirectlyToAnotherToolWithoutLosingSpatialLayout() {
        val nodes = listOf(
            InfiniteCanvasNode("preview", width = 420f, height = 360f),
            InfiniteCanvasNode("subtitles", x = 700f, y = 400f),
        )
        val initial = InfiniteCanvasPersistence.encode(
            InfiniteCanvasCamera(0f, 0f, 0.7f), nodes)
        var saved = InfiniteCanvasPersistence.decode(initial)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 81L, savedScene = initial,
                        onSaveScene = { saved = InfiniteCanvasPersistence.decode(it) },
                        entries = nodes.map { InfiniteCanvasEntry(it.id, it.id) },
                        gestureOwned = false, onAddTool = {}, onActivate = {},
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("overview-content-" + id)) }
                }
            }
        }
        composeRule.onNodeWithTag("spatial-quick-preview").performClick()
        composeRule.onNodeWithTag("spatial-tool-switcher").performClick()
        composeRule.onNodeWithTag("spatial-switch-to-subtitles").performClick()
        composeRule.onNodeWithTag("spatial-native-content-subtitles").assertExists()
        composeRule.onNodeWithTag("spatial-native-content-preview").assertDoesNotExist()
        composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        assertEquals(nodes, saved.second)
    }


    @Test fun referencePreviewAndNativeEditorAreVisibleTogetherWithoutDestroyingDraft() {
        val nodes = listOf(InfiniteCanvasNode("preview", width = 400f, height = 320f),
            InfiniteCanvasNode("subtitles", x = 480f, width = 400f, height = 360f))
        val initial = InfiniteCanvasPersistence.encode(
            InfiniteCanvasCamera(0f, 0f, 0.85f), nodes)
        var saved = InfiniteCanvasPersistence.decode(initial)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 82L, savedScene = initial,
                        onSaveScene = { saved = InfiniteCanvasPersistence.decode(it) },
                        entries = nodes.map { InfiniteCanvasEntry(it.id, it.id) },
                        gestureOwned = false, onAddTool = {}, onActivate = {},
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ ->
                        if (id == "preview") Text("Live ASS video", Modifier.testTag("reference-video"))
                        else {
                            var unsavedDraft by remember { mutableIntStateOf(0) }
                            Button(onClick = { unsavedDraft++ },
                                modifier = Modifier.testTag("split-editor-draft")) {
                                Text("Unsaved ${unsavedDraft}")
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("spatial-quick-subtitles").performClick()
        composeRule.onNodeWithTag("spatial-reference-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-native-content-subtitles").assertIsDisplayed()
        composeRule.onNodeWithTag("reference-video").assertExists()
        composeRule.onNodeWithTag("split-editor-draft").performClick()
        composeRule.onNodeWithText("Unsaved 1").assertExists()
        composeRule.onNodeWithTag("spatial-preview-size").performClick()
        composeRule.onNodeWithText("Unsaved 1").assertExists()
        composeRule.onNodeWithTag("spatial-preview-toggle").performClick()
        composeRule.onNodeWithTag("spatial-reference-preview").assertDoesNotExist()
        composeRule.onNodeWithText("Unsaved 1").assertExists()
        composeRule.onNodeWithTag("spatial-preview-toggle").performClick()
        composeRule.onNodeWithTag("spatial-reference-preview").assertExists()
        composeRule.onNodeWithText("Unsaved 1").assertExists()
        assertEquals(nodes, saved.second)
    }

    @Test fun unifiedCanvasTimelineDockWorksInsideFocusedEditorAndBoard() {
        val nodes = listOf(InfiniteCanvasNode("preview"), InfiniteCanvasNode("subtitles", x = 500f))
        val scene = InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(0f, 0f, 0.85f), nodes)
        var renderCompact by mutableStateOf(true)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 83L, savedScene = scene, onSaveScene = {},
                        entries = nodes.map { InfiniteCanvasEntry(it.id, it.id) },
                        gestureOwned = false, onAddTool = {}, onActivate = {},
                        renderTimeline = { compact ->
                            renderCompact = compact
                            Text("Real timeline pane", Modifier.testTag("test-timeline-content"))
                        },
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("content-" + id)) }
                }
            }
        }
        composeRule.onNodeWithTag("spatial-quick-preview").performClick()
        composeRule.onNodeWithTag("spatial-timeline-toggle").performClick()
        composeRule.onNodeWithTag("spatial-timeline-dock").assertIsDisplayed()
        composeRule.onNodeWithTag("test-timeline-content").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(renderCompact) }
        composeRule.onNodeWithTag("spatial-timeline-expand").performClick()
        composeRule.runOnIdle { assertFalse(renderCompact) }
        composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        composeRule.onNodeWithTag("spatial-timeline-dock").assertExists()
        composeRule.onNodeWithTag("spatial-timeline-close").performClick()
        composeRule.onNodeWithTag("spatial-timeline-dock").assertDoesNotExist()
    }

    @Test fun unifiedCanvasSideBookmarksReuseToolIdentityAndPersistWorldGeometry() {
        val nodes = listOf(
            InfiniteCanvasNode("preview"),
            InfiniteCanvasNode("STYLE:primary", x = 200f, y = 180f),
        )
        val initial = InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(0f, 0f, 0.85f), nodes)
        var bookmarked by mutableStateOf(false)
        var saved = InfiniteCanvasPersistence.decode(initial)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    InfiniteCanvasHost(
                        sessionId = 84L, savedScene = initial,
                        onSaveScene = { saved = InfiniteCanvasPersistence.decode(it) },
                        entries = listOf(
                            InfiniteCanvasEntry("preview", "视频"),
                            InfiniteCanvasEntry("STYLE:primary", "样式",
                                canClose = true, bookmarked = bookmarked),
                        ),
                        gestureOwned = false, onAddTool = {}, onActivate = {},
                        onToggleBookmark = { id ->
                            if (id == "STYLE:primary") bookmarked = !bookmarked
                        },
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { id, _ -> Text(id, Modifier.testTag("content-" + id)) }
                }
            }
        }
        composeRule.onNodeWithTag("spatial-quick-STYLE-primary").performClick()
        composeRule.onNodeWithTag("spatial-menu-STYLE-primary").performClick()
        composeRule.onNodeWithTag("spatial-bookmark-STYLE-primary").performClick()
        composeRule.onNodeWithTag("spatial-bookmark-rail").assertExists()
        composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        composeRule.onNodeWithTag("spatial-node-STYLE-primary").assertDoesNotExist()
        composeRule.onNodeWithTag("spatial-bookmark-open-STYLE-primary").performClick()
        composeRule.onNodeWithTag("spatial-native-content-STYLE-primary").assertExists()
        assertEquals(nodes, saved.second)
    }


    @Test fun externalToolbarFocusRequestOpensAndReopensAnExistingNativeInstance() {
        val nodes = listOf(InfiniteCanvasNode("preview"),
            InfiniteCanvasNode("STYLE:primary", x = 800f, y = 120f))
        val initial = InfiniteCanvasPersistence.encode(
            InfiniteCanvasCamera(0f, 0f, 0.7f), nodes)
        var activeId by mutableStateOf<String?>(null)
        var revision by mutableIntStateOf(0)
        var saved = InfiniteCanvasPersistence.decode(initial)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize()) {
                        Button(onClick = {
                            activeId = "STYLE:primary"
                            revision++
                        }, modifier = Modifier.testTag("test-main-open-style")) {
                            Text("Open style in unified canvas")
                        }
                        InfiniteCanvasHost(
                            sessionId = 86L, savedScene = initial,
                            onSaveScene = { saved = InfiniteCanvasPersistence.decode(it) },
                            entries = listOf(InfiniteCanvasEntry("preview", "视频"),
                                InfiniteCanvasEntry("STYLE:primary", "样式")),
                            gestureOwned = false, onAddTool = {},
                            onActivate = { activeId = it },
                            onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                            requestedActiveToolId = activeId,
                            requestedFocusRevision = revision,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) { id, _ -> Text(id, Modifier.testTag("content-" + id)) }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("test-main-open-style").performClick()
        composeRule.onNodeWithTag("spatial-native-content-STYLE-primary").assertExists()
        composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        composeRule.onNodeWithTag("spatial-native-content-STYLE-primary").assertDoesNotExist()
        // The identity did not change: only a new explicit user action can
        // re-open the existing tool rather than relying on ID-change effects.
        composeRule.onNodeWithTag("test-main-open-style").performClick()
        composeRule.onNodeWithTag("spatial-native-content-STYLE-primary").assertExists()
        assertEquals(nodes, saved.second)
    }

    @Test fun focusedEditorIsNotShrunkByBoardCamera() {
        val fixture = OverviewFixture(listOf(
            InfiniteCanvasNode("preview", x = 8000f, width = 900f),
        ))
        showOverviewFixture(fixture)
        composeRule.onNodeWithTag("spatial-quick-preview").performClick()
        val editorWidth = composeRule.onNodeWithTag("spatial-focused-editor")
            .fetchSemanticsNode().layoutInfo.width
        assertTrue(editorWidth > 0)
        assertTrue(editorWidth < (900f * composeRule.activity.resources.displayMetrics.density).roundToInt())
        composeRule.onNodeWithTag("overview-content-preview").assertExists()
    }
}

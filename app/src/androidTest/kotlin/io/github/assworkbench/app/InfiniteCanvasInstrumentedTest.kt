package io.github.assworkbench.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
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

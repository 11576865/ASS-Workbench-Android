package io.github.assworkbench.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsNotEnabled
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

    @Test fun surfaceMeasurementIsNotClampedToThePhoneViewport() {
        var expectedWidth = 0
        val scene = InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(),
            listOf(InfiniteCanvasNode("preview", width = 900f, height = 300f)))
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    expectedWidth = (900f * LocalDensity.current.density).roundToInt()
                    InfiniteCanvasHost(
                        sessionId = 1L, savedScene = scene, onSaveScene = {},
                        entries = listOf(InfiniteCanvasEntry("preview", "Video")),
                        gestureOwned = false, onAddTool = {}, onActivate = {},
                        onUndo = {}, onRedo = {}, canUndo = false, canRedo = false,
                        modifier = Modifier.fillMaxSize(),
                    ) { _, _ -> Text("Real-sized surface") }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(expectedWidth,
            composeRule.onNodeWithTag("spatial-node-preview", useUnmergedTree = true)
                .fetchSemanticsNode().layoutInfo.width)
    }
}

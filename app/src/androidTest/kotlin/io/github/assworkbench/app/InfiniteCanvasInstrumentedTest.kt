package io.github.assworkbench.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.workspace.InfiniteCanvasCamera
import io.github.assworkbench.app.ui.workspace.InfiniteCanvasEntry
import io.github.assworkbench.app.ui.workspace.InfiniteCanvasHost
import io.github.assworkbench.app.ui.workspace.InfiniteCanvasNode
import io.github.assworkbench.app.ui.workspace.InfiniteCanvasPersistence
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InfiniteCanvasInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<EditorRegressionHostActivity>()

    @Test fun surfaceMeasurementIsNotClampedToThePhoneViewport() {
        var expectedWidth = 0
        val scene = InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(),
            listOf(InfiniteCanvasNode("preview", width = 900f, height = 300f)))
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    expectedWidth = (900f * LocalDensity.current.density).toInt()
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

package io.github.assworkbench.app

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.assworkbench.app.ui.WorkbenchTool
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Production-rendered evidence for the one supported infinite-canvas experiment.
 *
 * Runtime-backed renderer/interaction evidence and the richer ToolInstance
 * binding/geometry evidence are owned by their dedicated capture tests on main.
 * Archived Canvas presentation is not an available UI; capture the spatial board.
 */
@RunWith(AndroidJUnit4::class)
class UigsAdditionalVisualCaptureInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<EditorRegressionHostActivity>()

    private val viewModel: EditorViewModel
        get() = composeRule.activity.editorViewModel

    @Test
    fun captureCanvasWorkspaceFixtureLandscape() {
        restoreFixture()
        openFixedTool(WorkbenchTool.POSITION)
        switchPresentation("SPATIAL_EXPERIMENTAL", "spatial-workspace")
        if (composeRule.onAllNodesWithTag("spatial-return-to-board")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
            composeRule.onNodeWithTag("spatial-return-to-board").performClick()
        }
        composeRule.onNodeWithTag("spatial-overview").performClick()
        composeRule.onNodeWithTag("spatial-node-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-node-subtitles").assertIsDisplayed()
        captureDisplay("ASS.INFINITE_CANVAS.WORKSPACE.FIXTURE_LANDSCAPE.png")
    }

    private fun restoreFixture() {
        composeRule.runOnUiThread {
            composeRule.activity.restoreDeterministicFixture()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val state = viewModel.state.value
            state.subtitleLoaded &&
                state.document.events.size == 2 &&
                state.document.events.firstOrNull { it.id == 1L }?.text == "Recovered line"
        }
        viewModel.focusEvent(1L, seek = false)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.focusedEventId == 1L
        }
        composeRule.onNodeWithTag("fixed-workspace", useUnmergedTree = true).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun openFixedTool(tool: WorkbenchTool) {
        composeRule.onNodeWithTag("fixed-group-${tool.group.name}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fixed-tool-${tool.name}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fixed-inspector", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun switchPresentation(variant: String, rootTag: String) {
        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        composeRule.onNodeWithTag("ui-variant-lab").assertIsDisplayed()
        composeRule.onNodeWithTag("ui-variant-use-$variant")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(rootTag, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(rootTag, useUnmergedTree = true).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun captureDisplay(fileName: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val target = instrumentation.targetContext.filesDir.resolve(fileName)
        FileOutputStream(target).use { stream ->
            assertTrue(
                "UIGS compositor screenshot must encode as PNG",
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
            )
        }
        bitmap.recycle()
        assertTrue(
            "UIGS capture must produce a non-empty app-private PNG",
            target.isFile && target.length() > 0L,
        )
    }
}

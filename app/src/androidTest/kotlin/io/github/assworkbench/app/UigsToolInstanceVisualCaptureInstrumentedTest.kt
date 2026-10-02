package io.github.assworkbench.app

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deterministic visual evidence for the real ToolInstance workspace.
 *
 * This deliberately exercises binding and floating-surface presentation through
 * production UI entrypoints instead of constructing a synthetic surface.
 */
@RunWith(AndroidJUnit4::class)
class UigsToolInstanceVisualCaptureInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<EditorRegressionHostActivity>()

    private val viewModel: EditorViewModel
        get() = composeRule.activity.editorViewModel

    @Test
    fun capturePinnedDuplicatedAndResizedPositionSurfaceLandscape() {
        composeRule.runOnUiThread {
            composeRule.activity.restoreDeterministicFixture()
        }
        composeRule.waitUntil(10_000) {
            viewModel.state.value.subtitleLoaded && viewModel.state.value.document.events.size == 2
        }
        viewModel.focusEvent(1L, seek = false)
        composeRule.waitUntil(5_000) { viewModel.state.value.focusedEventId == 1L }

        composeRule.onNodeWithContentDescription("工具和更多操作").performClick()
        composeRule.onNodeWithTag("workspace-mode-toggle").performClick()
        composeRule.onNodeWithTag("ui-variant-use-TOOL_INSTANCES_EXPERIMENTAL")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("tool-instance-workspace", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("tool-instance-directory").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("tool-search", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("tool-search").performTextReplacement("POSITION")
        composeRule.onNodeWithTag("tool-POSITION").performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("surface-POSITION-primary", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("固定到当前字幕").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("复制工具实例并沿用绑定").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("surface-POSITION-2", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("surface-resize-POSITION-2").performTouchInput {
            swipe(start = center, end = center + Offset(90f, 60f), durationMillis = 300)
        }
        composeRule.onNodeWithTag("surface-POSITION-primary").assertIsDisplayed()
        composeRule.onNodeWithTag("surface-POSITION-2").assertIsDisplayed()
        composeRule.waitForIdle()

        captureDisplay("ASS.TOOL_INSTANCE_WORKSPACE.FIXTURE_LANDSCAPE.png")
    }

    private fun captureDisplay(fileName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val target = instrumentation.targetContext.filesDir.resolve(fileName)
        FileOutputStream(target).use { stream ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
        assertTrue(target.isFile && target.length() > 0L)
    }
}

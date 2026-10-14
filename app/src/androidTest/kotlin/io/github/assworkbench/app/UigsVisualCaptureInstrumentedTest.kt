package io.github.assworkbench.app

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.assworkbench.app.ui.WorkbenchTool
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deterministic production-UI visual capture fixture for UIGS.
 *
 * The debug-only host injects repeatable project data, but the rendered tree is
 * the real ModernEditorScreen + EditorViewModel used by the application.
 * UiAutomation.takeScreenshot() captures the Android display/compositor rather
 * than a Compose node bitmap.
 */
@RunWith(AndroidJUnit4::class)
class UigsVisualCaptureInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<EditorRegressionHostActivity>()

    private val viewModel: EditorViewModel
        get() = composeRule.activity.editorViewModel

    @Test
    fun captureFixedToolFixturesLandscape() {
        composeRule.runOnUiThread {
            composeRule.activity.restoreDeterministicFixture()
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            val state = viewModel.state.value
            state.subtitleLoaded &&
                state.document.events.size == 2 &&
                state.document.events.firstOrNull { it.id == 1L }?.text == "Recovered line" &&
                state.document.events.firstOrNull { it.id == 2L }?.text == "Second recovered line"
        }

        viewModel.focusEvent(1L, seek = false)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.focusedEventId == 1L
        }
        composeRule.onNodeWithTag("fixed-workspace", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.waitForIdle()

        captureDisplay("ASS.STYLE_TOOL.FIXTURE_LANDSCAPE.png")

        val tools = listOf(
            WorkbenchTool.TEXT,
            WorkbenchTool.EVENT,
            WorkbenchTool.BATCH,
            WorkbenchTool.TIMELINE,
            WorkbenchTool.FRAMES,
            WorkbenchTool.POSITION,
            WorkbenchTool.VECTOR_CLIP,
            WorkbenchTool.EFFECTS,
            WorkbenchTool.KARAOKE,
            WorkbenchTool.FONTS,
            WorkbenchTool.FONT_REQUIREMENTS,
            WorkbenchTool.QC,
            WorkbenchTool.COMPATIBILITY,
            WorkbenchTool.DIAGNOSTICS,
            WorkbenchTool.PROJECT,
            WorkbenchTool.CAPABILITIES,
        )
        for (tool in tools) {
            openFixedTool(tool)
            captureDisplay("ASS.${tool.name}_TOOL.FIXTURE_LANDSCAPE.png")
        }

    }

    /**
     * A compositor screenshot of the real R3 window, for visual diff against
     * ASS_Workbench_R3_FIXED.png. A phone emulator must not masquerade as the
     * 1600x960 reference, so only a usable expanded window can capture this.
     */
    @Test
    fun captureR3FixedReferenceWhenExpanded() {
        val config = composeRule.activity.resources.configuration
        assumeTrue("R3 three-column reference requires expanded window",
            config.screenWidthDp >= 1180 && config.screenHeightDp >= 560)

        composeRule.runOnUiThread { composeRule.activity.restoreDeterministicFixture() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.state.value.subtitleLoaded &&
                viewModel.state.value.document.events.size == 2
        }
        viewModel.focusEvent(1L, seek = false)
        composeRule.waitForIdle()

        val nav = composeRule.onNodeWithTag("r3-event-navigation", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val media = composeRule.onNodeWithTag("r3-video", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val timeline = composeRule.onNodeWithTag("r3-timeline-dock", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val inspector = composeRule.onNodeWithTag("r3-parameter-inspector", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        assertTrue("R3 navigator must precede video", nav.right <= media.left + 2f)
        assertTrue("R3 video must be above the same live timeline", media.bottom <= timeline.top + 2f)
        assertTrue("R3 timeline must precede inspector", timeline.right <= inspector.left + 2f)
        assertTrue("R3 media should lead the center region", media.height > 0f)
        composeRule.onNodeWithTag("r3-inspector-context", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("r3-parameter-provenance", useUnmergedTree = true)
            .assertIsDisplayed()
        captureDisplay("ASS.R3.FIXED.PRODUCTION_REFERENCE.png")
    }

    private fun openFixedTool(tool: WorkbenchTool) {
        composeRule.onNodeWithTag("fixed-tool-CAPABILITIES").performClick()
        composeRule.waitForIdle()
        if (tool != WorkbenchTool.CAPABILITIES) {
            composeRule.onNodeWithTag("tool-search").performTextReplacement(tool.name)
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("tool-${tool.name}").performScrollTo().performClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(if (tool == WorkbenchTool.CAPABILITIES) "fixed-tool-directory"
            else "fixed-inspector", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun captureDisplay(fileName: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val target = instrumentation.targetContext.filesDir.resolve(fileName)
        FileOutputStream(target).use { stream ->
            assertTrue(
                "Android compositor screenshot must encode as PNG",
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

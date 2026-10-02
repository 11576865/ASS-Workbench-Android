package io.github.assworkbench.app

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    private fun openFixedTool(tool: WorkbenchTool) {
        composeRule.onNodeWithTag("fixed-group-${tool.group.name}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        if (tool == WorkbenchTool.CAPABILITIES) {
            composeRule.onNodeWithTag("fixed-inspector", useUnmergedTree = true)
                .assertIsDisplayed()
            composeRule.onNodeWithTag("tool-search")
                .assertIsDisplayed()
            return
        }

        composeRule.onNodeWithTag("fixed-tool-${tool.name}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fixed-inspector", useUnmergedTree = true)
            .assertIsDisplayed()
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

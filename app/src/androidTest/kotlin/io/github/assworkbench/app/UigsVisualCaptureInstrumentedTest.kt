package io.github.assworkbench.app

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.assworkbench.app.ui.WorkbenchTool
import java.io.ByteArrayOutputStream
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

    /**
     * A compositor screenshot of the real R3 window, for visual diff against
     * ASS_Workbench_R3_FIXED.png. A phone emulator must not masquerade as the
     * 1600x960 reference, so only a usable expanded window can capture this.
     */
    @Test
    fun captureR3FixedReferenceWhenExpanded() {
        // Emulator wm-size overrides may be expressed in the device's natural
        // portrait orientation. Explicitly request a landscape Activity here;
        // otherwise 1600x960 override can still render as 960x1600 dp.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            val config = composeRule.activity.resources.configuration
            config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        val config = composeRule.activity.resources.configuration
        val expanded = config.screenWidthDp >= 1180 && config.screenHeightDp >= 560
        // This class also participates in the normal, narrow Pixel 6 suite.
        // Only the dedicated expanded-screen test invocation has a strict gate:
        // the native screenshot validator independently requires the PNG file.
        val strict = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString("r3StrictCapture") == "true"
        if (!expanded && !strict) return
        check(expanded) {
            "R3_NATIVE_LAYOUT_UNAVAILABLE: observed ${config.screenWidthDp}x${config.screenHeightDp}dp " +
                "orientation=${config.orientation}; expected expanded landscape host"
        }

        composeRule.runOnUiThread { composeRule.activity.restoreDeterministicFixture() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.state.value.subtitleLoaded &&
                viewModel.state.value.document.events.size == 2
        }
        viewModel.focusEvent(1L, seek = false)
        composeRule.waitForIdle()
        // The R3 drawing shows an Event-local POSITION inspector. The default
        // Style editor is valid but would be the wrong visual comparison subject.
        composeRule.onNodeWithTag("fixed-context-tools").performClick()
        composeRule.onNodeWithTag("fixed-context-POSITION").performClick()
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
        composeRule.onNodeWithTag("fixed-r3-main-host", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("fixed-r3-spatial-switch", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("r3-subtitle-search", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("r3-event-filter", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("r3-inspector-context", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("r3-parameter-provenance", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("r3-raw-event-value", useUnmergedTree = true)
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
        if (fileName.startsWith("ASS.R3.")) {
            // A compositor image with a system ANR/permission dialog is genuine
            // PNG but not a valid screenshot of the target product. An observed
            // Pixel Launcher ANR obscured the earlier R3 evidence thumbnail.
            val activeRoot = instrumentation.uiAutomation.rootInActiveWindow
            val activePackage = activeRoot?.packageName?.toString()
            check(activePackage == instrumentation.targetContext.packageName) {
                "R3_NATIVE_SCREEN_OCCLUDED: foreground window belongs to " +
                    "${activePackage ?: "<none>"} instead of " +
                    instrumentation.targetContext.packageName
            }
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val target = instrumentation.targetContext.filesDir.resolve(fileName)
        FileOutputStream(target).use { stream ->
            assertTrue(
                "Android compositor screenshot must encode as PNG",
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
            )
        }
        // Debug-only R3 evidence mirror. Android 15's run-as may refuse access
        // to the app-private file even when test pixels were captured correctly.
        // App-specific external storage is a separate adb-readable extraction path.
        // The Base64 thumbnail is supplemental review evidence from the very same
        // compositor bitmap; it is never a substitute for the full PNG gate.
        if (fileName.startsWith("ASS.R3.")) {
            val mirrorFolder = checkNotNull(instrumentation.targetContext.getExternalFilesDir("uigs-capture")) {
                "R3 external capture directory is unavailable"
            }
            check(mirrorFolder.isDirectory || mirrorFolder.mkdirs()) {
                "Cannot create R3 adb-readable evidence directory"
            }
            val mirror = mirrorFolder.resolve(fileName)
            target.copyTo(mirror, overwrite = true)
            assertTrue("R3 mirrored compositor PNG must be nonempty",
                mirror.isFile && mirror.length() == target.length())
            Log.i("AsswbVisual",
                "R3_NATIVE_FILE:${mirror.absolutePath}:bytes=${mirror.length()}")

            // Gradle's connectedAndroidTest may uninstall the test/debug host
            // before the shell regains control. Stream the compositor PNG into
            // a bounded, indexed Android logcat evidence envelope while this
            // instrumentation process is still alive. Never depend on run-as.
            val pngBytes = target.readBytes()
            check(pngBytes.size in 1024..5_000_000) {
                "R3 screenshot has unexpected PNG size: ${pngBytes.size}"
            }
            val pngSha = java.security.MessageDigest.getInstance("SHA-256")
                .digest(pngBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            val pngChunks = Base64.encodeToString(pngBytes, Base64.NO_WRAP).chunked(2800)
            Log.i("AsswbVisual", "R3_PNG_BEGIN:$fileName:${pngBytes.size}:$pngSha:${pngChunks.size}")
            pngChunks.forEachIndexed { index, part ->
                Log.i("AsswbVisual", "R3_PNG:$index:$part")
            }
            Log.i("AsswbVisual", "R3_PNG_END:$fileName")

            val thumbnailWidth = 900
            val thumbnailHeight = (bitmap.height * (thumbnailWidth.toFloat() / bitmap.width)).toInt()
            val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbnailWidth, thumbnailHeight, true)
            val thumbOutput = ByteArrayOutputStream()
            check(thumbnail.compress(Bitmap.CompressFormat.JPEG, 70, thumbOutput)) {
                "Could not encode R3 compositor preview"
            }
            thumbnail.recycle()
            Base64.encodeToString(thumbOutput.toByteArray(), Base64.NO_WRAP)
                .chunked(2800)
                .forEachIndexed { index, part ->
                    Log.i("AsswbVisual", "R3_PREVIEW_JPEG:$index:$part")
                }
            Log.i("AsswbVisual", "R3_PREVIEW_JPEG_END")
        }
        bitmap.recycle()
        assertTrue(
            "UIGS capture must produce a non-empty app-private PNG",
            target.isFile && target.length() > 0L,
        )
    }
}

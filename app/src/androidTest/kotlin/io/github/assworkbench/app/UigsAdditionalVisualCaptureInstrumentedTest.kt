package io.github.assworkbench.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.assworkbench.app.ui.VideoPreview
import io.github.assworkbench.app.ui.WorkbenchTool
import io.github.assworkbench.app.ui.interaction.InteractionOverlayRegistry
import io.github.assworkbench.app.ui.interaction.InteractionProxySpec
import io.github.assworkbench.app.ui.interaction.WindowInteractionOverlay
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssStyle
import io.github.assworkbench.domain.SubTime
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
        switchPresentation("CANVAS_EXPERIMENTAL", "canvas-workspace")
        captureDisplay("ASS.CANVAS.WORKSPACE.FIXTURE_LANDSCAPE.png")
    }

    @Test
    fun captureToolInstancesWorkspaceFixtureLandscape() {
        restoreFixture()
        switchPresentation("TOOL_INSTANCES_EXPERIMENTAL", "tool-instance-workspace")
        composeRule.onNodeWithTag("tool-instance-directory").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("surface-CAPABILITIES-primary", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        captureDisplay("ASS.TOOL_INSTANCES.WORKSPACE.FIXTURE_LANDSCAPE.png")
    }

    @Test
    fun captureRendererBackedPreviewLandscape() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val video = targetContext.filesDir.resolve("uigs-renderer-backed-fixture.y4m")
        writeDeterministicVideo(video)

        val configDir = targetContext.filesDir.resolve("uigs-renderer-capture/config").apply {
            deleteRecursively()
            mkdirs()
        }
        val fontsDir = targetContext.filesDir.resolve("uigs-renderer-capture/fonts").apply {
            deleteRecursively()
            mkdirs()
        }
        val document = AssDocument(
            styles = listOf(
                AssStyle(
                    name = "Default",
                    fontName = "sans-serif",
                    fontSize = 64.0,
                    alignment = 5,
                    outline = 3.0,
                    shadow = 0.0,
                ),
            ),
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(0),
                    end = SubTime(2_000),
                    style = "Default",
                    text = "{\\pos(960,540)}UIGS runtime-backed libass",
                ),
            ),
        )
        val diagnostics = AtomicReference<List<String>>(emptyList())
        val registry = InteractionOverlayRegistry()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.fillMaxSize()) {
                        VideoPreview(
                            videoUri = video.absolutePath,
                            document = document,
                            renderDocument = document,
                            seekRequestMs = 500L,
                            seekRequestNonce = 1L,
                            onPosition = {},
                            onRendererDiagnostics = { diagnostics.set(it) },
                            configDir = configDir,
                            fontsDir = fontsDir,
                            fontRevision = 0L,
                            initialPositionMs = 500L,
                            focusedEventId = 1L,
                            positionEditEventId = 1L,
                            onPreviewEventPosition = { _, _ -> },
                            onSetEventPosition = { _, _ -> },
                            onPreviewEventMove = { _, _, _, _ -> },
                            onSetEventMove = { _, _, _, _ -> },
                            onPreviewEventOrigin = { _, _ -> },
                            onSetEventOrigin = { _, _ -> },
                            onPreviewEventRotation = {},
                            onSetEventRotation = {},
                            scaleLocked = true,
                            onPreviewEventScale = { _, _ -> },
                            onSetEventScale = { _, _ -> },
                            onPreviewEventShear = { _, _ -> },
                            onSetEventShear = { _, _ -> },
                            onPreviewEventClip = { _, _, _, _, _ -> },
                            onSetEventClip = { _, _, _, _, _ -> },
                            onCancelEventPositionPreview = {},
                            onFocusEvent = {},
                            onSetEventTiming = { _, _, _ -> },
                            onOpenVideo = {},
                            rendererEnabled = true,
                            fillViewport = true,
                            interactionRegistry = registry,
                            modifier = Modifier.fillMaxSize(),
                        )
                        WindowInteractionOverlay(
                            registry = registry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 30_000) {
            diagnostics.get().any {
                it.contains("Preview subtitle：sid=") && !it.contains("sid=unknown")
            }
        }
        composeRule.waitForIdle()
        captureDisplay("ASS.RENDERER_BACKED.PREVIEW_LANDSCAPE.png")
    }

    @Test
    fun captureInteractionOverlayFixtureLandscape() {
        val registry = InteractionOverlayRegistry()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.fillMaxSize()) {
                        WindowInteractionOverlay(
                            registry = registry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        composeRule.runOnUiThread {
            registry.publish(
                "position-1",
                listOf(
                    InteractionProxySpec(
                        id = "position-1-pos",
                        label = "位置",
                        targetInWindow = Offset(800f, 500f),
                        preferredOffsetPx = Offset(120f, -120f),
                        onDragDelta = {},
                        onCommit = {},
                        onCancel = {},
                    ),
                ),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("rod-handle-position-1-pos", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("rod-handle-position-1-pos", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.waitForIdle()
        captureDisplay("ASS.INTERACTION_OVERLAY.FIXTURE_LANDSCAPE.png")
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

    private fun writeDeterministicVideo(target: File) {
        val width = 320
        val height = 180
        val ySize = width * height
        val chromaSize = (width / 2) * (height / 2)
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            out.write("YUV4MPEG2 W320 H180 F2:1 Ip A1:1 C420jpeg\n".toByteArray(Charsets.US_ASCII))
            repeat(4) { frame ->
                out.write("FRAME\n".toByteArray(Charsets.US_ASCII))
                val luma = if (frame % 2 == 0) 48 else 72
                out.write(ByteArray(ySize) { luma.toByte() })
                out.write(ByteArray(chromaSize) { 128.toByte() })
                out.write(ByteArray(chromaSize) { 128.toByte() })
            }
        }
        assertTrue("Renderer fixture video must be non-empty", target.isFile && target.length() > 0L)
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

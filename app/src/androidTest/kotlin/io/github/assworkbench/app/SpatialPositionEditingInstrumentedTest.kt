package io.github.assworkbench.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.VideoPreview
import io.github.assworkbench.app.ui.interaction.InteractionOverlayRegistry
import io.github.assworkbench.app.ui.interaction.WindowInteractionOverlay
import io.github.assworkbench.app.ui.interaction.rememberInteractionOverlayRegistry
import io.github.assworkbench.app.ui.workspace.*
import io.github.assworkbench.domain.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native rod -> transient ASS -> one history commit, inside a real canvas surface. */
@RunWith(AndroidJUnit4::class)
class SpatialPositionEditingInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: EditorViewModel
    private lateinit var video: File
    private lateinit var configDir: File
    private lateinit var fontsDir: File
    private val registryRef = AtomicReference<InteractionOverlayRegistry?>()
    private val diagnostics = AtomicReference<List<String>>(emptyList())

    @Before fun mount() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        video = NativePreviewFixture.create(app.filesDir, "spatial-position-fixture.png")
        configDir = File(app.filesDir, "spatial-position/config").apply { mkdirs() }
        fontsDir = File(app.filesDir, "spatial-position/fonts").apply { mkdirs() }
        vm = ViewModelProvider(composeRule.activity)[EditorViewModel::class.java]
        val document = AssDocument(
            scriptInfo = linkedMapOf("ScriptType" to "v4.00+", "PlayResX" to "1920", "PlayResY" to "1080"),
            styles = listOf(AssStyle(fontName = "sans-serif")),
            events = listOf(
                AssEvent(id = 1L, start = SubTime(0), end = SubTime(30000), text = "{\\pos(960,540)}A"),
                AssEvent(id = 2L, start = SubTime(0), end = SubTime(30000), text = "{\\pos(800,500)}B"),
            ),
        )
        composeRule.runOnUiThread {
            vm.loadProjectSnapshot(AssWorkbenchProjectSnapshot(
                title = "Spatial position", document = document, videoUri = video.absolutePath,
                subtitleUri = null, containerUri = null, containerTrackNumber = null,
                textEncoding = AssTextEncoding.UTF8, sourceFormat = SubtitleSourceFormat.ASS,
                focusedEventId = 1L, selectedEventIds = emptySet(),
                workspaceMode = "SPATIAL_EXPERIMENTAL", workspaceState = emptyList(), surfaceState = emptyList(),
            ))
        }
        val scene = InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(16f, 64f, 1f),
            listOf(InfiniteCanvasNode("preview", width = 330f, height = 340f)))
        composeRule.setContent {
            MaterialTheme {
                val state by vm.state.collectAsState()
                val targetId = state.focusedEventId
                val registry = rememberInteractionOverlayRegistry()
                SideEffect { registryRef.set(registry) }
                Box(Modifier.fillMaxSize()) {
                    InfiniteCanvasHost(
                        sessionId = state.workspaceSessionId, savedScene = scene, onSaveScene = {},
                        entries = listOf(InfiniteCanvasEntry("preview", "视频")),
                        gestureOwned = registry.activeHandleId != null,
                        onAddTool = {}, onActivate = {}, onUndo = vm::undo, onRedo = vm::redo,
                        canUndo = state.canUndo, canRedo = state.canRedo,
                        modifier = Modifier.fillMaxSize(),
                    ) { _, _ ->
                        VideoPreview(
                            videoUri = video.absolutePath,
                            document = state.document,
                            renderDocument = state.previewDocument ?: state.document,
                            seekRequestMs = null,
                            seekRequestNonce = 0L,
                            onPosition = {},
                            onRendererDiagnostics = { diagnostics.set(it) },
                            configDir = configDir,
                            fontsDir = fontsDir,
                            fontRevision = 0L,
                            initialPositionMs = 1_000L,
                            focusedEventId = state.focusedEventId,
                            positionEditEventId = state.focusedEventId,
                            onPreviewEventPosition = { x, y -> targetId?.let { vm.previewEventPosition(it, x, y) } },
                            onSetEventPosition = { x, y -> targetId?.let { vm.setEventPosition(it, x, y) } },
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
                            onCancelEventPositionPreview = { targetId?.let { vm.clearTransientPreview("geometry:$it") } },
                            onFocusEvent = {},
                            onSetEventTiming = { _, _, _ -> },
                            onOpenVideo = {},
                            rendererEnabled = true,
                            interactionRegistry = registry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    WindowInteractionOverlay(registry)
                }
            }
        }
        composeRule.waitUntil(30_000) {
            diagnostics.get().any { it.contains("Preview subtitle") }
        }
        composeRule.waitUntil(30_000) {
            registryRef.get()?.handles?.any { it.id == "position-1-pos" } == true
        }
        composeRule.onNodeWithTag("rod-handle-position-1-pos").assertIsDisplayed()
    }

    @Test fun draggingPositionPreviewsThenCommitsExactlyOneUndoStep() {
        val before = vm.state.value.document
        assertFalse(vm.state.value.canUndo)
        composeRule.onNodeWithTag("rod-handle-position-1-pos").performTouchInput {
            down(center)
            moveBy(Offset(70f, 45f), delayMillis = 120)
        }
        composeRule.waitForIdle()
        assertEquals(before, vm.state.value.document)
        assertNotNull(vm.state.value.previewDocument)
        assertEquals("geometry:1", vm.state.value.previewOwnerId)
        assertFalse(vm.state.value.canUndo)
        composeRule.onRoot().performTouchInput { up() }
        composeRule.onNodeWithTag("spatial-quick-preview").performClick()
        composeRule.waitUntil(5_000) { vm.state.value.document != before }
        val changed = vm.state.value.document
        assertNull(vm.state.value.previewDocument)
        assertEquals(before.events[1], changed.events[1])
        composeRule.runOnIdle { vm.undo() }
        composeRule.waitForIdle()
        assertEquals(before, vm.state.value.document)
        assertFalse("No intermediate rod samples in history", vm.state.value.canUndo)
        composeRule.runOnIdle { vm.redo() }
        composeRule.waitForIdle()
        assertEquals(changed, vm.state.value.document)
    }

    @Test fun changingTargetDuringDragCancelsWithoutWritingEitherEvent() {
        val before = vm.state.value.document
        composeRule.onNodeWithTag("rod-handle-position-1-pos").performTouchInput {
            down(center)
            moveBy(Offset(70f, 45f), delayMillis = 120)
        }
        composeRule.waitForIdle()
        assertNotNull(vm.state.value.previewDocument)
        composeRule.runOnIdle { vm.focusEvent(2L, seek = false) }
        composeRule.waitUntil(5_000) { vm.state.value.previewDocument == null }
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(before, vm.state.value.document)
        assertFalse(vm.state.value.canUndo)
        assertEquals(2L, vm.state.value.focusedEventId)
        composeRule.waitUntil(5_000) {
            registryRef.get()?.handles?.any { it.id == "position-2-pos" } == true
        }
    }
}

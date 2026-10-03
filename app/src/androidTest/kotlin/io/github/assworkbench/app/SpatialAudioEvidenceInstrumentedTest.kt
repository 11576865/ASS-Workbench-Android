package io.github.assworkbench.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.InfiniteAudioEvidence
import io.github.assworkbench.app.ui.workspace.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Production host + waveform input routing. The lower hit target is diagnostic, not a video decoder. */
@RunWith(AndroidJUnit4::class)
class SpatialAudioEvidenceInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: EditorViewModel
    private val lowerTaps = AtomicInteger()
    private val saved = AtomicReference<List<String>>(emptyList())

    @Before fun mount() {
        vm = ViewModelProvider(composeRule.activity)[EditorViewModel::class.java]
        composeRule.runOnUiThread { vm.setPlaybackPosition(10_000L) }
        val scene = InfiniteCanvasPersistence.encode(InfiniteCanvasCamera(16f, 64f, 1f), listOf(
            InfiniteCanvasNode("preview", width = 300f, height = 330f, z = 1),
            InfiniteCanvasNode("audio", width = 300f, height = 260f, z = 2, alpha = 0.2f),
        ))
        composeRule.setContent {
            MaterialTheme {
                val state by vm.state.collectAsState()
                InfiniteCanvasHost(
                    sessionId = 1L, savedScene = scene, onSaveScene = { saved.set(it) },
                    entries = listOf(InfiniteCanvasEntry("preview", "视频"), InfiniteCanvasEntry("audio", "波形")),
                    gestureOwned = false, onAddTool = {}, onActivate = {},
                    onUndo = vm::undo, onRedo = vm::redo, canUndo = state.canUndo, canRedo = state.canRedo,
                    modifier = Modifier.fillMaxSize(),
                ) { id, interactive ->
                    if (id == "audio") InfiniteAudioEvidence(state, vm, interactive)
                    else Box(Modifier.fillMaxSize().testTag("diagnostic-lower-input").clickable { lowerTaps.incrementAndGet() })
                }
            }
        }
        composeRule.onNodeWithTag("spatial-audio-start").assertTextEquals("6000 ms")
    }

    @Test fun dragKeepsDisplayedRangeUntilReleaseWithoutDocumentUndo() {
        val document = vm.state.value.document
        val waveform = composeRule.onNodeWithTag("spatial-audio-evidence")
        waveform.performTouchInput {
            down(Offset(width * 0.4f, height * 0.5f))
            moveTo(Offset(width * 0.65f, height * 0.5f), delayMillis = 100)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("spatial-audio-start").assertTextEquals("6000 ms")
        composeRule.onNodeWithTag("spatial-audio-end").assertTextEquals("14000 ms")
        assertTrue(vm.state.value.seekRequestNonce > 0L)
        val seek = vm.playbackPositionMs.value
        waveform.performTouchInput { up() }
        composeRule.onNodeWithTag("spatial-audio-start").assertTextEquals("${seek - 4000L} ms")
        assertEquals(document, vm.state.value.document)
        assertFalse(vm.state.value.canUndo)
        assertEquals(0, lowerTaps.get())
    }

    @Test fun passthroughRoutesToLowerSurfaceWithoutRaisingIt() {
        val document = vm.state.value.document
        val originalNodes = InfiniteCanvasPersistence.decode(saved.get()).second
        val waveform = composeRule.onNodeWithTag("spatial-audio-evidence")
        waveform.performTouchInput { click(Offset(width * 0.25f, height * 0.5f)) }
        composeRule.waitForIdle()
        val nonce = vm.state.value.seekRequestNonce
        assertTrue(nonce > 0L)
        assertEquals(0, lowerTaps.get())
        composeRule.onNodeWithTag("spatial-menu-audio").performClick()
        composeRule.onNodeWithText("穿透操作视频").performClick()
        waveform.performTouchInput { click(Offset(width * 0.25f, height * 0.5f)) }
        composeRule.waitForIdle()
        assertEquals(1, lowerTaps.get())
        assertEquals(nonce, vm.state.value.seekRequestNonce)
        val nodes = InfiniteCanvasPersistence.decode(saved.get()).second
        assertEquals(originalNodes.map { it.z }, nodes.map { it.z })
        assertEquals(0.2f, nodes.first { it.id == "audio" }.alpha, 0f)
        composeRule.onNodeWithTag("spatial-menu-audio").performClick()
        composeRule.onNodeWithText("操作波形").performClick()
        waveform.performTouchInput { click(Offset(width * 0.75f, height * 0.5f)) }
        composeRule.waitForIdle()
        assertTrue(vm.state.value.seekRequestNonce > nonce)
        assertEquals(1, lowerTaps.get())
        assertEquals(document, vm.state.value.document)
        assertFalse(vm.state.value.canUndo)
    }

    @Test fun cancellingDragReleasesCapturedRange() {
        val waveform = composeRule.onNodeWithTag("spatial-audio-evidence")
        waveform.performTouchInput {
            down(Offset(width * 0.4f, height * 0.5f))
            moveTo(Offset(width * 0.7f, height * 0.5f), delayMillis = 100)
        }
        composeRule.onNodeWithTag("spatial-audio-start").assertTextEquals("6000 ms")
        val seek = vm.playbackPositionMs.value
        waveform.performTouchInput { cancel() }
        composeRule.onNodeWithTag("spatial-audio-start").assertTextEquals("${seek - 4000L} ms")
        assertFalse(vm.state.value.canUndo)
    }
}

package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.InfiniteAudioEvidence
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real MediaExtractor/MediaCodec PCM analysis of a deterministic tone, not a painted spectrum. */
@RunWith(AndroidJUnit4::class)
class SpectrogramInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun evidenceModeAnalyzesDecodedPcmWithoutDocumentEdits() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = toneWave(app.cacheDir)
        try {
            val vm = ViewModelProvider(composeRule.activity)[EditorViewModel::class.java]
            val document = vm.state.value.document
            composeRule.runOnUiThread { vm.attachVideo(Uri.fromFile(file)) }
            composeRule.setContent {
                MaterialTheme {
                    val state by vm.state.collectAsState()
                    InfiniteAudioEvidence(state, vm, interactive = true)
                }
            }
            composeRule.waitUntil(30_000) { vm.state.value.audioTracks.isNotEmpty() }
            composeRule.onNodeWithTag("spatial-mode-spectrogram").performClick()
            composeRule.waitUntil(30_000) {
                vm.state.value.spectrogram.status in listOf(WaveformLiteStatus.READY, WaveformLiteStatus.UNAVAILABLE)
            }
            assertEquals(vm.state.value.spectrogram.error, WaveformLiteStatus.READY, vm.state.value.spectrogram.status)
            val data = vm.state.value.spectrogram.data!!
            composeRule.runOnUiThread { vm.selectAudioTrack(vm.state.value.selectedAudioTrackIndex!!) }
            assertSame(data, vm.state.value.spectrogram.data)
            val peak = (0 until data.bandCount).maxBy { data.levelAt(0, it) }
            assertEquals(1000.0, data.frequencyAt(peak), 80.0)
            composeRule.onNodeWithTag("spatial-audio-evidence").performTouchInput { click(center) }
            assertTrue(vm.state.value.seekRequestNonce > 0L)
            assertEquals(document, vm.state.value.document)
            assertFalse(vm.state.value.canUndo)
            composeRule.onNodeWithTag("spatial-mode-waveform").performClick()
        } finally { file.delete() }
    }

    private fun toneWave(dir: File): File {
        val samples = 8000
        val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(16000).putInt(32000)
        buffer.putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { i -> buffer.putShort((sin(2 * PI * 1000 * i / 16000) * 16000).toInt().toShort()) }
        return File.createTempFile("spectrogram-tone-", ".wav", dir).apply { writeBytes(buffer.array()) }
    }
}

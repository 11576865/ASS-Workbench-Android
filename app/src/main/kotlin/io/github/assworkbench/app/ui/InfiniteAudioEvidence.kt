package io.github.assworkbench.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.WaveformLiteStatus
import io.github.assworkbench.domain.WaveformViewportSampler
import kotlin.math.roundToInt

/** Waveform and PCM-derived STFT share one media clock and one seeking gesture. */
@Composable
internal fun InfiniteAudioEvidence(state: EditorState, viewModel: EditorViewModel, interactive: Boolean) {
    val playhead by viewModel.playbackPositionMs.collectAsState()
    var spectrumMode by rememberSaveable(state.workspaceSessionId) { mutableStateOf(false) }
    LaunchedEffect(spectrumMode, state.project.videoUri, state.selectedAudioTrackIndex, state.spectrogram.status == WaveformLiteStatus.IDLE) {
        if (spectrumMode && state.spectrogram.status == WaveformLiteStatus.IDLE) viewModel.requestSpectrogram()
    }
    var capturedRange by remember(state.project.videoUri, state.selectedAudioTrackIndex, spectrumMode, interactive) { mutableStateOf<Pair<Long, Long>?>(null) }
    val range = audioEvidenceRange(playhead, capturedRange)
    val windowStart = range.first
    val windowEnd = range.second
    val latestRange by rememberUpdatedState(windowStart to windowEnd)
    val envelope = state.waveform.envelope
    val signal = MaterialTheme.colorScheme.primary
    val cursor = MaterialTheme.colorScheme.tertiary
    val analysisStatus = if (spectrumMode) state.spectrogram.status else state.waveform.status
    val analysisError = if (spectrumMode) state.spectrogram.error else state.waveform.error
    Column(Modifier.fillMaxSize().padding(4.dp)) {
        if (interactive) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { spectrumMode = false }, modifier = Modifier.height(32.dp).testTag("spatial-mode-waveform")) { Text(if (!spectrumMode) "● 波形" else "波形") }
            TextButton(onClick = { spectrumMode = true }, modifier = Modifier.height(32.dp).testTag("spatial-mode-spectrogram")) { Text(if (spectrumMode) "● 声谱图" else "声谱图") }
            if (spectrumMode && analysisStatus == WaveformLiteStatus.UNAVAILABLE)
                TextButton(onClick = viewModel::requestSpectrogram, modifier = Modifier.height(32.dp)) { Text("重试") }
        } else Text(if (spectrumMode) "声谱图 · 观察" else "波形 · 观察", modifier = Modifier.height(32.dp), style = MaterialTheme.typography.labelSmall)
        Text(
            when (analysisStatus) {
                WaveformLiteStatus.READY -> if (interactive) "拖动定位 · ${playhead} ms" else "观察叠层 · 触摸穿透到视频"
                WaveformLiteStatus.ANALYZING -> "正在分析真实音轨…"
                WaveformLiteStatus.UNAVAILABLE -> "分析不可用 · " + analysisError.orEmpty()
                WaveformLiteStatus.IDLE -> "打开视频后显示真实音轨"
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)).padding(4.dp),
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val samples = remember(envelope, windowStart, windowEnd, maxWidth) {
                envelope?.let { WaveformViewportSampler.sample(it, windowStart, windowEnd,
                    (maxWidth.value * 2).roundToInt().coerceIn(1, 1200)) }.orEmpty()
            }
            val data = state.spectrogram.data.takeIf { spectrumMode }
            val image = remember(data, windowStart, windowEnd, maxWidth) {
                data?.let { spectrum ->
                    val columns = maxWidth.value.roundToInt().coerceIn(1, 600)
                    val pixels = IntArray(columns * spectrum.bandCount)
                    for (x in 0 until columns) {
                        val frame = spectrum.frameAt(windowStart + (windowEnd - windowStart) * x / columns)
                        if (frame < 0) continue
                        for (band in 0 until spectrum.bandCount) {
                            val level = spectrum.levelAt(frame, band)
                            // Silence is transparent; stronger evidence is visible without an opaque black field.
                            val alpha = (level * 0.8f).roundToInt()
                            val red = level; val green = (level * 0.85f).roundToInt(); val blue = 255 - level
                            pixels[(spectrum.bandCount - 1 - band) * columns + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                        }
                    }
                    android.graphics.Bitmap.createBitmap(pixels, columns, spectrum.bandCount, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
                }
            }
            val touch = if (interactive) Modifier
                .pointerInput(state.project.videoUri, state.selectedAudioTrackIndex, spectrumMode) {
                    detectTapGestures { position ->
                        val range = latestRange
                        viewModel.seekPreviewTo(audioEvidenceTimeAt(range, position.x, size.width.toFloat()))
                    }
                }
                .pointerInput(state.project.videoUri, state.selectedAudioTrackIndex, spectrumMode) {
                    try {
                        detectDragGestures(
                            onDragStart = { capturedRange = latestRange },
                            onDragEnd = { capturedRange = null },
                            onDragCancel = { capturedRange = null },
                            onDrag = { change, _ ->
                                change.consume()
                                capturedRange?.let { viewModel.seekPreviewTo(audioEvidenceTimeAt(it, change.position.x, size.width.toFloat())) }
                            })
                    } finally {
                        capturedRange = null
                    }
                } else Modifier
            Canvas(Modifier.fillMaxSize().then(touch).testTag("spatial-audio-evidence")) {
                if (image != null) drawImage(image, dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)))
                if (!spectrumMode && samples.isNotEmpty()) {
                    val step = size.width / samples.size
                    samples.forEachIndexed { index, bucket ->
                        val x = (index + 0.5f) * step
                        val top = Offset(x, size.height / 2 - bucket.maximum.toFloat() / Short.MAX_VALUE * size.height * 0.45f)
                        val bottom = Offset(x, size.height / 2 - bucket.minimum.toFloat() / Short.MAX_VALUE * size.height * 0.45f)
                        val stroke = maxOf(1f, step.coerceAtMost(2f))
                        drawLine(Color.Black.copy(alpha = 0.85f), top, bottom, strokeWidth = stroke + 2.dp.toPx())
                        drawLine(signal, top, bottom, strokeWidth = stroke)
                    }
                }
                val x = size.width * ((playhead - windowStart).toFloat() / (windowEnd - windowStart)).coerceIn(0f, 1f)
                drawLine(Color.Black.copy(alpha = 0.85f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 4.dp.toPx())
                drawLine(cursor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val label = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)).padding(4.dp)
            Text("${windowStart} ms", modifier = label.testTag("spatial-audio-start"), style = MaterialTheme.typography.labelSmall)
            if (spectrumMode) Text("0–${state.spectrogram.data?.maxFrequencyHz?.toInt() ?: 8000} Hz · −80–0 dBFS", modifier = label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
            Text("${windowEnd} ms", modifier = label.testTag("spatial-audio-end"), style = MaterialTheme.typography.labelSmall)
        }
    }
}

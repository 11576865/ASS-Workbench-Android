package io.github.assworkbench.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.WaveformLiteStatus
import io.github.assworkbench.domain.WaveformViewportSampler
import kotlin.math.roundToInt

/** Real waveform evidence. No synthetic spectrum and no independent playback clock. */
@Composable
internal fun InfiniteAudioEvidence(state: EditorState, viewModel: EditorViewModel, interactive: Boolean) {
    val playhead by viewModel.playbackPositionMs.collectAsState()
    var capturedRange by remember(state.waveform.sourceUri, interactive) { mutableStateOf<Pair<Long, Long>?>(null) }
    val range = audioEvidenceRange(playhead, capturedRange)
    val windowStart = range.first
    val windowEnd = range.second
    val latestRange by rememberUpdatedState(windowStart to windowEnd)
    val envelope = state.waveform.envelope
    val signal = MaterialTheme.colorScheme.primary
    val cursor = MaterialTheme.colorScheme.tertiary
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            when (state.waveform.status) {
                WaveformLiteStatus.READY -> if (interactive) "拖动波形定位 · 播放头 ${playhead} ms" else "观察叠层 · 触摸穿透到视频"
                WaveformLiteStatus.ANALYZING -> "正在分析真实音轨…"
                WaveformLiteStatus.UNAVAILABLE -> "波形不可用 · " + state.waveform.error.orEmpty()
                WaveformLiteStatus.IDLE -> "打开视频后显示真实音轨波形"
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)).padding(4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val samples = remember(envelope, windowStart, windowEnd, maxWidth) {
                envelope?.let { WaveformViewportSampler.sample(it, windowStart, windowEnd,
                    (maxWidth.value * 2).roundToInt().coerceIn(1, 1200)) }.orEmpty()
            }
            val touch = if (interactive) Modifier
                .pointerInput(state.waveform.sourceUri) {
                    detectTapGestures { position ->
                        val range = latestRange
                        viewModel.seekPreviewTo(audioEvidenceTimeAt(range, position.x, size.width.toFloat()))
                    }
                }
                .pointerInput(state.waveform.sourceUri) {
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
                if (samples.isNotEmpty()) {
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
            Text("${windowEnd} ms", modifier = label.testTag("spatial-audio-end"), style = MaterialTheme.typography.labelSmall)
        }
    }
}

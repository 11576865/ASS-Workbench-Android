package io.github.assworkbench.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    val windowStart = (playhead - 4000L).coerceAtLeast(0L)
    val windowEnd = windowStart + 8000L
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
                        val t = range.first + ((range.second - range.first) * (position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)).toLong()
                        viewModel.seekPreviewTo(t)
                    }
                }
                .pointerInput(state.waveform.sourceUri) {
                    var start = 0L; var end = 0L; var x = 0f
                    detectDragGestures(
                        onDragStart = { p -> start = latestRange.first; end = latestRange.second; x = p.x },
                        onDrag = { change, delta ->
                            change.consume()
                            x += delta.x
                            viewModel.seekPreviewTo(start + ((end - start) * (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)).toLong())
                        })
                } else Modifier
            Canvas(Modifier.fillMaxSize().then(touch).testTag("spatial-audio-evidence")) {
                if (samples.isNotEmpty()) {
                    val step = size.width / samples.size
                    samples.forEachIndexed { index, bucket ->
                        val x = (index + 0.5f) * step
                        drawLine(signal,
                            Offset(x, size.height / 2 - bucket.maximum.toFloat() / Short.MAX_VALUE * size.height * 0.45f),
                            Offset(x, size.height / 2 - bucket.minimum.toFloat() / Short.MAX_VALUE * size.height * 0.45f),
                            strokeWidth = maxOf(1f, step.coerceAtMost(2f)))
                    }
                }
                val x = size.width * ((playhead - windowStart).toFloat() / (windowEnd - windowStart)).coerceIn(0f, 1f)
                drawLine(cursor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${windowStart} ms", style = MaterialTheme.typography.labelSmall)
            Text("${windowEnd} ms", style = MaterialTheme.typography.labelSmall)
        }
    }
}

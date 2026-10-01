package io.github.assworkbench.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*

@Composable
internal fun KaraokePane(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val event = state.document.events.firstOrNull { it.id == state.focusedEventId }
    if (event == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("先选择一条字幕") }
        return
    }
    val segments = remember(event.text) { AssKaraokeCodec.parse(event.text) }
    fun commit(next: List<AssKaraokeSegment>) {
        viewModel.updateEventText(event.id, AssKaraokeCodec.write(next))
    }

    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Karaoke · #${event.id}", style = MaterialTheme.typography.titleMedium)
        Text(
            "Event ${event.end.millis - event.start.millis} ms · Karaoke ${AssKaraokeCodec.totalDurationMs(segments)} ms",
            style = MaterialTheme.typography.bodySmall,
        )
        if (segments.isEmpty()) {
            Text("当前 Event 没有 \\k / \\kf / \\ko / \\kt。")
            val initialSegments = remember(event.text) { AssKaraokeCodec.initializePlainText(event.text) }
            if (initialSegments == null) Text(
                "当前正文含 ASS 标签或转义。自动分词不会清除它们；请在正文中明确添加 Karaoke 标签。",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { initialSegments?.takeIf { it.isNotEmpty() }?.let(::commit) },
                enabled = !initialSegments.isNullOrEmpty(),
            ) { Text("按词建立 20cs 初始音节") }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(segments) { index, segment ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AssistChip(
                            onClick = {
                                val modes = AssKaraokeMode.entries
                                val nextMode = modes[(segment.mode.ordinal + 1) % modes.size]
                                commit(segments.toMutableList().also { it[index] = segment.copy(mode = nextMode) })
                            },
                            label = { Text("\\${segment.mode.tag}") },
                        )
                        IconButton(
                            onClick = {
                                commit(
                                    segments.toMutableList().also {
                                        it[index] = segment.copy(
                                            centiseconds = (segment.centiseconds - 1).coerceAtLeast(0)
                                        )
                                    }
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Remove, "-1cs")
                        }
                        Text("${segment.centiseconds}cs", modifier = Modifier.width(54.dp))
                        IconButton(
                            onClick = {
                                commit(
                                    segments.toMutableList().also {
                                        it[index] = segment.copy(centiseconds = segment.centiseconds + 1)
                                    }
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Add, "+1cs")
                        }
                        OutlinedTextField(
                            value = segment.text,
                            onValueChange = { value ->
                                commit(
                                    segments.toMutableList().also {
                                        it[index] = segment.copy(text = value)
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VectorClipPane(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val event = state.document.events.firstOrNull { it.id == state.focusedEventId }
    if (event == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("先选择一条字幕") }
        return
    }
    val clip = remember(event.text) { AssVectorClipCodec.inspect(event.text) }

    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Vector Clip · #${event.id}", style = MaterialTheme.typography.titleMedium)
        if (clip == null) {
            Text("当前字幕没有可编辑的矢量裁剪路径。Drawing 仍可在下方创建和编辑。矩形裁剪位于“位置与几何 → 矩形裁剪”。")
            DrawingAuthorPane(event, viewModel)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("反向 iclip")
            Switch(
                checked = clip.inverted,
                onCheckedChange = { inverted ->
                    viewModel.updateEventText(
                        event.id,
                        AssVectorClipCodec.patch(event.text, clip.copy(inverted = inverted)),
                    )
                },
            )
            Spacer(Modifier.width(12.dp))
            Text("scale=${clip.scale ?: 1}")
        }
        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            val numbers = clip.tokens.mapNotNull { it.number }
            val pairs = numbers.chunked(2).filter { it.size == 2 }
            if (pairs.isNotEmpty()) {
                val xs = pairs.map { it[0] }
                val ys = pairs.map { it[1] }
                val minX = xs.minOrNull() ?: 0.0
                val maxX = xs.maxOrNull() ?: 1.0
                val minY = ys.minOrNull() ?: 0.0
                val maxY = ys.maxOrNull() ?: 1.0
                val dx = (maxX - minX).takeUnless { it == 0.0 } ?: 1.0
                val dy = (maxY - minY).takeUnless { it == 0.0 } ?: 1.0
                fun point(pair: List<Double>): Offset = Offset(
                    (((pair[0] - minX) / dx) * size.width).toFloat(),
                    (((pair[1] - minY) / dy) * size.height).toFloat(),
                )
                pairs.zipWithNext().forEach { (a, b) ->
                    drawLine(Color.White.copy(alpha = 0.55f), point(a), point(b), strokeWidth = 2.dp.toPx())
                }
                pairs.forEach { drawCircle(Color.White, 4.dp.toPx(), point(it)) }
            }
        }
        Text("已有路径控制点 · 数字可直接修改", style = MaterialTheme.typography.labelMedium)
        DrawingAuthorPane(event, viewModel)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(clip.tokens) { index, token ->
                if (token.command != null) {
                    Text(token.command.toString(), style = MaterialTheme.typography.labelLarge)
                } else {
                    OutlinedTextField(
                        value = token.number?.toString().orEmpty(),
                        onValueChange = { raw ->
                            raw.toDoubleOrNull()?.let { number ->
                                val next = clip.tokens.toMutableList()
                                next[index] = token.copy(number = number)
                                viewModel.updateEventText(
                                    event.id,
                                    AssVectorClipCodec.patch(event.text, clip.copy(tokens = next)),
                                )
                            }
                        },
                        label = { Text("token #${index}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

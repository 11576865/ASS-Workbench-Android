package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
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
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Karaoke", style = MaterialTheme.typography.titleSmall)
        if (event == null) {
            Text("先选择一条字幕。")
            return@Column
        }
        val segments = remember(event.text) { KaraokeSemantic.inspect(event.text) }
        var values by remember(event.id, event.text) {
            mutableStateOf(segments.map { it.valueCentiseconds.toString() })
        }
        if (segments.isEmpty()) {
            Text("当前 Event 没有 \\k / \\K / \\kf / \\ko / \\kt。")
            Text(
                "Karaoke 工作区只编辑已有的 Karaoke timing；Raw ASS 仍是逃生口。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val eventDuration = event.end.millis - event.start.millis
        val karaokeDuration = values.mapNotNull(String::toIntOrNull).sumOf { it.toLong() * 10L }
        Text(
            "#${event.id} · Event ${eventDuration} ms · Karaoke ${karaokeDuration} ms",
            style = MaterialTheme.typography.labelSmall,
        )
        if (karaokeDuration != eventDuration) {
            Text(
                "Karaoke 总时长与 Event 时长相差 ${kotlin.math.abs(eventDuration - karaokeDuration)} ms。",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        segments.forEachIndexed { index, segment ->
            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.small) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        when (segment.kind) {
                            KaraokeKind.K -> "\\k"
                            KaraokeKind.KF -> "\\kf"
                            KaraokeKind.KO -> "\\ko"
                            KaraokeKind.KT -> "\\kt"
                        },
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(42.dp),
                    )
                    OutlinedTextField(
                        value = values.getOrElse(index) { "" },
                        onValueChange = { next ->
                            values = values.toMutableList().also { list ->
                                if (index in list.indices) list[index] = next.filter { ch -> ch.isDigit() }.take(6)
                            }
                        },
                        label = { Text("cs") },
                        singleLine = true,
                        modifier = Modifier.width(92.dp),
                    )
                    Text(segment.text.ifBlank { "（空音节）" }, modifier = Modifier.weight(1f))
                }
            }
        }

        Button(
            onClick = {
                val parsed = values.mapNotNull(String::toIntOrNull)
                if (parsed.size == segments.size) viewModel.setKaraokeTimings(event.id, parsed)
            },
            enabled = values.size == segments.size && values.all { it.toIntOrNull() != null },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("应用音节时间 · 一个 Undo")
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
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Vector Clip", style = MaterialTheme.typography.titleSmall)
        if (event == null) {
            Text("先选择一条字幕。")
            return@Column
        }
        val initial = remember(event.text) { AssVectorClipSemantic.inspectLeading(event.text) }
        if (initial == null) {
            Text("当前 Event 没有可结构化编辑的 leading vector \\clip / \\iclip。")
            Text(
                "矩形 clip 继续由 Position 工具负责；Malformed / Raw-only path 不会被这里重写。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        var inverted by remember(event.id, event.text) { mutableStateOf(initial.inverted) }
        var rawPath by remember(event.id, event.text) { mutableStateOf(AssVectorPathCodec.write(initial.path)) }
        val parsed = remember(rawPath) { runCatching { AssVectorPathCodec.parse(rawPath) }.getOrNull() }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = !inverted, onClick = { inverted = false }, label = { Text("\\clip") })
            FilterChip(selected = inverted, onClick = { inverted = true }, label = { Text("\\iclip") })
            parsed?.scale?.let { Text("scale $it", style = MaterialTheme.typography.labelSmall) }
        }

        OutlinedTextField(
            value = rawPath,
            onValueChange = { rawPath = it },
            label = { Text("ASS vector path") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = 3,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(
                    if (parsed == null) "路径无法解析；不会写入。"
                    else "${parsed.commands.size} commands · ${parsed.commands.sumOf { it.coordinates.size / 2 }} coordinate pairs"
                )
            },
            isError = parsed == null,
        )

        parsed?.commands?.forEachIndexed { index, command ->
            Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(
                        "${index + 1}. ${command.command} · ${command.coordinates.joinToString(", ")}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        Button(
            onClick = {
                val path = parsed ?: return@Button
                viewModel.setVectorClip(event.id, AssVectorClip(inverted, path))
            },
            enabled = parsed != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("应用 Vector Clip · 一个 Undo")
        }
        Text(
            "当前第一阶段提供 lossless path model + exact path editing。直接点/Bezier handle 操作可在此模型上继续接入 Canvas manipulator。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

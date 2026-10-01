package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*
import io.github.assworkbench.fonts.FontMatchStatus

@Composable
internal fun FrameTimingPane(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val event = state.document.events.firstOrNull { it.id == state.focusedEventId }
    var mode by rememberSaveable { mutableStateOf("CFR") }
    var fpsText by rememberSaveable { mutableStateOf("60") }
    var timecodes by rememberSaveable {
        mutableStateOf("# timecode format v2\n0\n16.667\n33.333\n50")
    }
    val timebase = remember(mode, fpsText, timecodes) {
        if (mode == "VFR") {
            runCatching { VfrTimecodes.parseV2(timecodes) }.getOrNull()
        } else {
            fpsText.toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                FrameTimebase.Cfr((it * 1000.0).toLong(), 1000L)
            }
        }
    }

    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("帧级时间", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = mode == "CFR", onClick = { mode = "CFR" }, label = { Text("CFR") })
            FilterChip(selected = mode == "VFR", onClick = { mode = "VFR" }, label = { Text("VFR / timecodes v2") })
        }
        if (mode == "CFR") {
            OutlinedTextField(
                fpsText,
                { fpsText = it },
                label = { Text("FPS") },
                singleLine = true,
            )
        } else {
            OutlinedTextField(
                timecodes,
                { timecodes = it },
                label = { Text("逐帧 PTS（ms）") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when {
            event == null -> Text("先选择一条字幕。")
            timebase == null -> Text("时间基无效。", color = MaterialTheme.colorScheme.error)
            else -> {
                val start = FrameTimingTools.timing(event.start, timebase)
                val end = FrameTimingTools.timing(event.end, timebase)
                Text("Start: ${event.start.millis} ms → frame ${start.frame} @ ${start.timeMs} ms")
                Text("End: ${event.end.millis} ms → frame ${end.frame} @ ${end.timeMs} ms")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            val s = FrameTimingTools.shiftFrames(event.start, -1, timebase)
                            val e = FrameTimingTools.shiftFrames(event.end, -1, timebase)
                            viewModel.setEventTiming(event.id, s.millis, e.millis.coerceAtLeast(s.millis))
                        },
                    ) { Text("整体 -1f") }
                    Button(
                        onClick = {
                            val s = FrameTimingTools.shiftFrames(event.start, 1, timebase)
                            val e = FrameTimingTools.shiftFrames(event.end, 1, timebase)
                            viewModel.setEventTiming(event.id, s.millis, e.millis.coerceAtLeast(s.millis))
                        },
                    ) { Text("整体 +1f") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val s = timebase.snap(event.start.millis)
                            viewModel.setEventTiming(event.id, s, event.end.millis.coerceAtLeast(s))
                        },
                    ) { Text("Start 吸附帧") }
                    OutlinedButton(
                        onClick = {
                            val e = timebase.snap(event.end.millis).coerceAtLeast(event.start.millis)
                            viewModel.setEventTiming(event.id, event.start.millis, e)
                        },
                    ) { Text("End 吸附帧") }
                }
                Text(
                    "ASS 最终仍保存时间值；Frame 是编辑量化层，不把关键帧误当作字幕边界。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun FontRequirementsPane(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val requirements = remember(state.document) { FontRequirementResolver.resolve(state.document) }
    val diagnostics = state.fontDiagnostics.associateBy { it.requestedFamily.trim().lowercase() }

    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("字体依赖", style = MaterialTheme.typography.titleMedium)
        Text(
            "从实际引用的 Style、inline \\fn 与 \\rStyle 自动推导。这里不做字体子集化。",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = viewModel::selectRequestedFontsForPackaging,
            enabled = state.container.uri != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("为 MKV 写回自动选择所需已导入字体")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(requirements, key = { it.family.lowercase() }) { req ->
                val diagnostic = diagnostics[req.family.trim().lowercase()]
                ListItem(
                    headlineContent = {
                        Text(req.family, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(req.sources.joinToString(" + ") + " · " + req.eventIds.size + " events")
                    },
                    trailingContent = {
                        Text(
                            when (diagnostic?.status) {
                                FontMatchStatus.EXACT_IMPORTED -> "EXACT"
                                FontMatchStatus.METADATA_ALIAS -> "ALIAS"
                                FontMatchStatus.FALLBACK_ONLY -> "FALLBACK"
                                FontMatchStatus.MISSING, null -> "MISSING"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    }
}

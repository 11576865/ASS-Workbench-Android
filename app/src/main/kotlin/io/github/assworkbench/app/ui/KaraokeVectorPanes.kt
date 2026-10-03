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
import androidx.compose.runtime.saveable.rememberSaveable
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
    val fxTargetEventIds = state.selectedEventIds.ifEmpty { setOf(event.id) }
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
            var revealMs by rememberSaveable(event.id) { mutableStateOf("160") }
            var revealBlur by rememberSaveable(event.id) { mutableStateOf("3.5") }
            var revealAccel by rememberSaveable(event.id) { mutableStateOf("") }
            var withFlip by rememberSaveable(event.id) { mutableStateOf(true) }
            var flipStartScale by rememberSaveable(event.id) { mutableStateOf("12") }
            var flipOvershoot by rememberSaveable(event.id) { mutableStateOf("118") }
            var flipRotationX by rememberSaveable(event.id) { mutableStateOf("86") }

            val parsedRevealMs = revealMs.toLongOrNull()
            val parsedRevealBlur = revealBlur.toDoubleOrNull()
            val parsedRevealAccel = revealAccel.takeIf { it.isNotBlank() }?.toDoubleOrNull()
            val parsedFlipStartScale = flipStartScale.toDoubleOrNull()
            val parsedFlipOvershoot = flipOvershoot.toDoubleOrNull()
            val parsedFlipRotationX = flipRotationX.toDoubleOrNull()

            val revealSpec = if (
                parsedRevealMs != null && parsedRevealMs >= 0L &&
                parsedRevealBlur?.let { it.isFinite() && it in 0.0..20.0 } == true &&
                (revealAccel.isBlank() || parsedRevealAccel?.let { it.isFinite() && it > 0.0 } == true) &&
                (!withFlip || (
                    parsedFlipStartScale?.let { it.isFinite() && it > 0.0 } == true &&
                        parsedFlipOvershoot?.let { it.isFinite() && it > 0.0 } == true &&
                        parsedFlipRotationX?.isFinite() == true
                    ))
            ) {
                AssKaraokeRevealFxSpec(
                    revealMs = parsedRevealMs,
                    startBlur = parsedRevealBlur,
                    accel = parsedRevealAccel,
                    flip = if (withFlip) {
                        AssKaraokeFlipFxSpec(
                            startScalePercent = requireNotNull(parsedFlipStartScale),
                            overshootScalePercent = requireNotNull(parsedFlipOvershoot),
                            startRotationXDegrees = requireNotNull(parsedFlipRotationX),
                        )
                    } else null,
                )
            } else null
            val revealCompatibility = remember(state.document, fxTargetEventIds, revealSpec) {
                revealSpec?.let { spec ->
                    runCatching {
                        AssKaraokeFxAuthoring.planProgressiveRevealBatch(
                            state.document,
                            fxTargetEventIds,
                            spec,
                        )
                    }
                }
            }
            val revealError = revealCompatibility?.exceptionOrNull()?.message

            DisposableEffect(event.id, fxTargetEventIds) {
                onDispose { viewModel.clearTransientPreview("karaoke-fx") }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Karaoke FX · 逐音节显现", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "按 Karaoke 累计时间给每个音节写入 alpha / blur；可叠加基于本 Event 有效 Scale Y / Rotation X 的翻转拉伸。仍保持单 Event，不猜字形宽度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (fxTargetEventIds.size > 1) {
                        Text(
                            "当前将对选中的 ${fxTargetEventIds.size} 条字幕原子应用同一规则；每条字幕分别解析自己的 Style / Event 基础状态。任一条不兼容则整批不写入。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = revealMs,
                            onValueChange = { revealMs = it },
                            label = { Text("显现时长 ms") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = revealBlur,
                            onValueChange = { revealBlur = it },
                            label = { Text("起始 Blur") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = revealAccel,
                            onValueChange = { revealAccel = it },
                            label = { Text("Accel") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(
                            checked = withFlip,
                            onCheckedChange = { withFlip = it },
                        )
                        Text("翻转 / 拉伸显现")
                    }
                    if (withFlip) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = flipStartScale,
                                onValueChange = { flipStartScale = it },
                                label = { Text("起始高度 %") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = flipOvershoot,
                                onValueChange = { flipOvershoot = it },
                                label = { Text("回弹高度 %") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = flipRotationX,
                                onValueChange = { flipRotationX = it },
                                label = { Text("起始 X 旋转 °") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (revealSpec == null) {
                        Text(
                            "参数无效：显现时长需 ≥ 0；Blur 0..20；Accel 为空或 > 0；翻转开启时起始/回弹高度需 > 0，Rotation X 必须是有限数字。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (revealError != null) {
                        Text(
                            revealError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            enabled = revealSpec != null && revealError == null,
                            onClick = {
                                revealSpec?.let { viewModel.previewKaraokeRevealFx(fxTargetEventIds, it) }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("预览")
                        }
                        Button(
                            enabled = revealSpec != null && revealError == null,
                            onClick = {
                                revealSpec?.let { viewModel.applyKaraokeRevealFx(fxTargetEventIds, it) }
                            },
                            modifier = Modifier.weight(2f),
                        ) {
                            val plannedSegments = revealCompatibility?.getOrNull()?.sourceSegmentCount
                            Text(
                                buildString {
                                    if (fxTargetEventIds.size > 1) {
                                        append("对 ").append(fxTargetEventIds.size).append(" 条字幕 · ")
                                    }
                                    append(if (withFlip) "写入翻转显现 FX" else "写入逐音节 FX")
                                    plannedSegments?.let { append(" · ").append(it).append(" 音节") }
                                }
                            )
                        }
                    }
                }
            }

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

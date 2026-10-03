package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssFlipEntranceSpec
import io.github.assworkbench.domain.AssGlowFxSpec
import io.github.assworkbench.domain.AssReflectionFxSpec

/**
 * First composition-level FX authoring slice.
 *
 * This deliberately generates ordinary independent ASS Events. There is no hidden persistent
 * parent/child relation after generation, so raw ASS remains the canonical and portable result.
 */
@Composable
internal fun FxCompositionPane(
    event: AssEvent,
    targetEventIds: Set<Long>,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var offsetY by rememberSaveable(event.id) { mutableStateOf("56") }
    var scaleY by rememberSaveable(event.id) { mutableStateOf("35") }
    var opacity by rememberSaveable(event.id) { mutableStateOf("35") }
    var blur by rememberSaveable(event.id) { mutableStateOf("1.5") }
    var withGlow by rememberSaveable(event.id) { mutableStateOf(true) }
    var glowOpacity by rememberSaveable(event.id) { mutableStateOf("22") }
    var glowBlur by rememberSaveable(event.id) { mutableStateOf("4") }
    var glowBorder by rememberSaveable(event.id) { mutableStateOf("3") }
    var withEntrance by rememberSaveable(event.id) { mutableStateOf(true) }
    var entranceMs by rememberSaveable(event.id) { mutableStateOf("280") }

    val parsedOffset = offsetY.toDoubleOrNull()
    val parsedScale = scaleY.toDoubleOrNull()
    val parsedOpacity = opacity.toDoubleOrNull()
    val parsedBlur = blur.toDoubleOrNull()
    val parsedGlowOpacity = glowOpacity.toDoubleOrNull()
    val parsedGlowBlur = glowBlur.toDoubleOrNull()
    val parsedGlowBorder = glowBorder.toDoubleOrNull()
    val parsedEntranceMs = entranceMs.toLongOrNull()
    val valid =
        parsedOffset?.isFinite() == true &&
            parsedScale?.let { it.isFinite() && it > 0.0 } == true &&
            parsedOpacity?.let { it.isFinite() && it in 0.0..100.0 } == true &&
            parsedBlur?.let { it.isFinite() && it in 0.0..20.0 } == true &&
            (!withGlow || (
                parsedGlowOpacity?.let { it.isFinite() && it in 0.0..100.0 } == true &&
                    parsedGlowBlur?.let { it.isFinite() && it in 0.0..20.0 } == true &&
                    parsedGlowBorder?.let { it.isFinite() && it in 0.0..20.0 } == true
                )) &&
            (!withEntrance || (parsedEntranceMs != null && parsedEntranceMs >= 2L))

    Surface(
        modifier = modifier.fillMaxWidth().testTag("fx-composition-pane"),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("FX Composition · 多层镜像", style = MaterialTheme.typography.titleSmall)
            Text(
                if (targetEventIds.size > 1) {
                    "当前将对选中的 ${targetEventIds.size} 条字幕一次性生成柔光层 + 倒影层，并可给各自主体写入翻转 / 拉伸关键帧。整个批次只产生一次 Undo 事务。"
                } else {
                    "一次提交可生成柔光层 + 倒影层，并可给主体写入翻转 / 拉伸关键帧。生成后都是普通独立 ASS Event，没有隐藏联动。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = offsetY,
                    onValueChange = { offsetY = it },
                    label = { Text("倒影 Y 偏移") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("fx-reflection-offset-y"),
                )
                OutlinedTextField(
                    value = scaleY,
                    onValueChange = { scaleY = it },
                    label = { Text("高度 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("fx-reflection-scale-y"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = opacity,
                    onValueChange = { opacity = it },
                    label = { Text("不透明度 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("fx-reflection-opacity"),
                )
                OutlinedTextField(
                    value = blur,
                    onValueChange = { blur = it },
                    label = { Text("Blur") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("fx-reflection-blur"),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = withGlow,
                    onCheckedChange = { withGlow = it },
                    modifier = Modifier.testTag("fx-mirror-with-glow"),
                )
                Text("生成主体后方柔光层")
            }
            if (withGlow) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = glowOpacity,
                        onValueChange = { glowOpacity = it },
                        label = { Text("柔光不透明度 %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-glow-opacity"),
                    )
                    OutlinedTextField(
                        value = glowBlur,
                        onValueChange = { glowBlur = it },
                        label = { Text("柔光 Blur") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-glow-blur"),
                    )
                }
                OutlinedTextField(
                    value = glowBorder,
                    onValueChange = { glowBorder = it },
                    label = { Text("柔光 Border") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("fx-glow-border"),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = withEntrance,
                    onCheckedChange = { withEntrance = it },
                    modifier = Modifier.testTag("fx-reflection-with-entrance"),
                )
                Text("同时给主体添加翻转 / 拉伸入场")
            }
            if (withEntrance) {
                OutlinedTextField(
                    value = entranceMs,
                    onValueChange = { entranceMs = it },
                    label = { Text("入场时长 ms") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("fx-entrance-duration"),
                )
            }

            if (!valid) {
                Text(
                    "参数无效：高度需 > 0；各不透明度 0..100；Blur/Border 0..20；入场至少 2 ms。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                enabled = valid,
                onClick = {
                    val reflection = AssReflectionFxSpec(
                        offsetY = requireNotNull(parsedOffset),
                        verticalScalePercent = requireNotNull(parsedScale),
                        opacityPercent = requireNotNull(parsedOpacity),
                        blur = requireNotNull(parsedBlur),
                    )
                    val glow = if (withGlow) {
                        AssGlowFxSpec(
                            opacityPercent = requireNotNull(parsedGlowOpacity),
                            blur = requireNotNull(parsedGlowBlur),
                            border = requireNotNull(parsedGlowBorder),
                        )
                    } else null
                    val entrance = if (withEntrance) {
                        AssFlipEntranceSpec(durationMs = requireNotNull(parsedEntranceMs))
                    } else null
                    viewModel.createMirrorFxComposition(targetEventIds, reflection, glow, entrance)
                },
                modifier = Modifier.fillMaxWidth().testTag("fx-compose-reflection"),
            ) {
                Text(
                    (if (targetEventIds.size > 1) "对 ${targetEventIds.size} 条字幕 · " else "") +
                    when {
                        withGlow && withEntrance -> "生成柔光 + 倒影 + 翻转入场"
                        withGlow -> "生成柔光 + 倒影"
                        withEntrance -> "生成倒影 + 翻转入场"
                        else -> "生成倒影 Event"
                    }
                )
            }

            Text(
                "位置继承会被解析成显式 \\pos；已有 \\move 会整体偏移路径。若源 Event 同时含 \\pos 与 \\move，工具会拒绝猜测。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

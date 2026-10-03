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
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var offsetY by rememberSaveable(event.id) { mutableStateOf("56") }
    var scaleY by rememberSaveable(event.id) { mutableStateOf("35") }
    var opacity by rememberSaveable(event.id) { mutableStateOf("35") }
    var blur by rememberSaveable(event.id) { mutableStateOf("1.5") }
    var withEntrance by rememberSaveable(event.id) { mutableStateOf(true) }
    var entranceMs by rememberSaveable(event.id) { mutableStateOf("280") }

    val parsedOffset = offsetY.toDoubleOrNull()
    val parsedScale = scaleY.toDoubleOrNull()
    val parsedOpacity = opacity.toDoubleOrNull()
    val parsedBlur = blur.toDoubleOrNull()
    val parsedEntranceMs = entranceMs.toLongOrNull()
    val valid =
        parsedOffset?.isFinite() == true &&
            parsedScale?.let { it.isFinite() && it > 0.0 } == true &&
            parsedOpacity?.let { it.isFinite() && it in 0.0..100.0 } == true &&
            parsedBlur?.let { it.isFinite() && it in 0.0..20.0 } == true &&
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
            Text("FX Composition · 倒影 / 翻转入场", style = MaterialTheme.typography.titleSmall)
            Text(
                "一次提交生成独立倒影 Event；生成后没有隐藏联动，可分别编辑、移动或删除。主体可同时写入关键帧翻转入场。",
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
                    "参数无效：高度需 > 0；不透明度 0..100；Blur 0..20；入场至少 2 ms。",
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
                    val entrance = if (withEntrance) {
                        AssFlipEntranceSpec(durationMs = requireNotNull(parsedEntranceMs))
                    } else null
                    viewModel.createReflectionFxComposition(event.id, reflection, entrance)
                },
                modifier = Modifier.fillMaxWidth().testTag("fx-compose-reflection"),
            ) {
                Text(if (withEntrance) "生成倒影 + 翻转入场" else "生成倒影 Event")
            }

            Text(
                "位置继承会被解析成显式 \\pos；已有 \\move 会整体偏移路径。若源 Event 同时含 \\pos 与 \\move，工具会拒绝猜测。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

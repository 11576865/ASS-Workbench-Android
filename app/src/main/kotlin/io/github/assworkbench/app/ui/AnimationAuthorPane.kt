package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*

@Composable
internal fun AnimationAuthorPane(event: AssEvent, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    var propertyName by rememberSaveable(event.id) { mutableStateOf(AssTransformVisualProperty.SCALE_X.name) }
    var keyframesText by rememberSaveable(event.id) { mutableStateOf("0=80\n300=110\n900=100") }
    var accelText by rememberSaveable(event.id) { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val property = AssTransformVisualProperty.entries.firstOrNull { it.name == propertyName }
        ?: AssTransformVisualProperty.SCALE_X
    val keyframes = remember(keyframesText) { runCatching { AssAnimationAuthoring.parseKeyframes(keyframesText) } }
    val accel = accelText.takeIf(String::isNotBlank)?.toDoubleOrNull()
    val plan = remember(property, keyframes.getOrNull(), accel) {
        keyframes.getOrNull()?.let { runCatching { AssAnimationAuthoring.planNumericTrack(property, it, accel) }.getOrNull() }
    }

    Surface(modifier.fillMaxWidth(), tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("关键帧动画作者", style = MaterialTheme.typography.titleSmall)
            Text("把多个关键帧编译为连续 \\t 段；现有 Raw ASS 保留，新轨道按 ASS 标签顺序取得该属性的控制。",
                style = MaterialTheme.typography.labelSmall)
            Box {
                OutlinedButton(onClick = { menuOpen = true }) { Text("\\\${property.tag} · \${property.name}") }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    AssTransformVisualProperty.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text("\\\${item.tag} · \${item.name}") },
                            onClick = { propertyName = item.name; menuOpen = false },
                        )
                    }
                }
            }
            OutlinedTextField(
                keyframesText,
                { keyframesText = it },
                label = { Text("关键帧：毫秒=值") },
                minLines = 3,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(accelText, { accelText = it }, label = { Text("每段 Accel（留空=默认）") }, singleLine = true)
            if (keyframes.isFailure) {
                Text(keyframes.exceptionOrNull()?.message ?: "关键帧无效", color = MaterialTheme.colorScheme.error)
            }
            plan?.let {
                Text(it.generatedOverrideBlock, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
            }
            Button(
                onClick = {
                    val frames = keyframes.getOrNull() ?: return@Button
                    viewModel.applyEventNumericAnimation(event.id, property, frames, accel)
                },
                enabled = plan != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("生成连续 \\t 动画") }
        }
    }
}

package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.AssSubtitleSynchronizer

@Composable
internal fun SubtitleSyncPane(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    var anchorsText by rememberSaveable {
        mutableStateOf("# 源时间 = 目标时间\n# 0:00:10.000 = 0:00:11.250")
    }
    var selectedOnly by rememberSaveable { mutableStateOf(false) }
    val parsed = remember(anchorsText) { runCatching { AssSubtitleSynchronizer.parseAnchors(anchorsText) } }
    val anchors = parsed.getOrNull()
    val preview = remember(state.document, state.selectedEventIds, anchors, selectedOnly) {
        anchors?.let {
            runCatching {
                AssSubtitleSynchronizer.preview(
                    state.document,
                    it,
                    state.selectedEventIds.takeIf { selectedOnly && state.selectedEventIds.isNotEmpty() },
                )
            }.getOrNull()
        }
    }

    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("高级字幕同步", style = MaterialTheme.typography.titleMedium)
        Text("1 个锚点 = 整体偏移；2 个以上 = 分段线性漂移校正。区间外沿首/尾斜率连续外推。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = anchorsText,
            onValueChange = { anchorsText = it },
            label = { Text("同步锚点：source = target") },
            supportingText = { Text("支持 ASS 时间码或毫秒；例 0:10:00.000 = 0:10:01.320") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterChip(
            selected = selectedOnly,
            onClick = { selectedOnly = !selectedOnly },
            label = { Text("仅已选 \${state.selectedEventIds.size} 条") },
            enabled = state.selectedEventIds.isNotEmpty(),
        )
        if (parsed.isFailure) {
            Text(parsed.exceptionOrNull()?.message ?: "锚点无效", color = MaterialTheme.colorScheme.error)
        } else {
            Text("锚点 \${anchors?.size ?: 0} 个 · 将修改 \${preview?.changedEventIds?.size ?: 0} 条",
                style = MaterialTheme.typography.labelMedium)
            preview?.document?.events
                ?.zip(state.document.events)
                ?.filter { (after, before) -> after.start != before.start || after.end != before.end }
                ?.take(3)
                ?.forEach { (after, before) ->
                    Text("#\${before.id}: \${before.start.toAss()}–\${before.end.toAss()} → \${after.start.toAss()}–\${after.end.toAss()}",
                        style = MaterialTheme.typography.labelSmall)
                }
        }
        Button(
            onClick = { anchors?.let { viewModel.applySubtitleSynchronization(it, selectedOnly) } },
            enabled = !anchors.isNullOrEmpty() && preview?.changedEventIds?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("应用同步 · 一个 Undo 事务") }
    }
}

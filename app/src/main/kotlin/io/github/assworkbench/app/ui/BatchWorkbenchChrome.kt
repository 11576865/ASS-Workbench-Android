package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.AssBatchScope

@Composable
internal fun BatchScopePanel(scope: AssBatchScope, count: Int, onScope: (AssBatchScope) -> Unit) {
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("字幕范围", style = MaterialTheme.typography.titleSmall)
            AssBatchScope.entries.chunked(2).forEach { choices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { candidate ->
                        val label = when (candidate) {
                            AssBatchScope.SELECTED -> "已选字幕"
                            AssBatchScope.RESULTS -> "筛选结果"
                            AssBatchScope.SELECTED_RESULTS -> "已选∩结果"
                            AssBatchScope.DOCUMENT -> "整个文档"
                        }
                        FilterChip(scope == candidate, { onScope(candidate) }, { Text(label) },
                            modifier = Modifier.weight(1f).testTag("batch-scope-${candidate.name}"))
                    }
                }
            }
            Text("作用范围 $count 条", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("batch-scope-count"))
            Text("筛选结果沿用字幕列表条件；其他条件继续取交集。", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun BatchActionBar(
    count: Int, changed: Int?, error: String?, previewEnabled: Boolean, applyEnabled: Boolean,
    previewTag: String, applyTag: String, onPreview: () -> Unit, onApply: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("$applyTag-bar")) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(when {
                error != null -> "请修正参数：$error"
                changed == null -> "范围 $count 条 · 尚未预检"
                changed == 0 -> "范围 $count 条 · 没有实际变化"
                else -> "范围 $count 条 · 将修改 $changed 条 · 可一次撤销"
            }, style = MaterialTheme.typography.labelSmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onPreview, enabled = previewEnabled,
                    modifier = Modifier.weight(1f).testTag(previewTag)) { Text("预检与预览") }
                Button(onApply, enabled = applyEnabled,
                    modifier = Modifier.weight(1f).testTag(applyTag)) { Text("应用 ${changed ?: 0} 条") }
            }
        }
    }
}

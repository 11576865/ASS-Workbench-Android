package io.github.assworkbench.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * R3 is a presentation of exactly the existing persisted 18 tool keys.
 * Grouping changes neither tool identity nor the editor used on activation.
 */
internal data class R3ToolCatalogGroup(val label: String, val tools: List<WorkbenchTool>)
internal val r3CatalogGroups = listOf(
    R3ToolCatalogGroup("对象与选择", listOf(WorkbenchTool.SUBTITLES, WorkbenchTool.EVENT)),
    R3ToolCatalogGroup("文本与时间", listOf(WorkbenchTool.TEXT, WorkbenchTool.TIMELINE, WorkbenchTool.FRAMES)),
    R3ToolCatalogGroup("排版与空间", listOf(WorkbenchTool.STYLE, WorkbenchTool.POSITION, WorkbenchTool.VECTOR_CLIP)),
    R3ToolCatalogGroup("效果", listOf(WorkbenchTool.EFFECTS, WorkbenchTool.KARAOKE)),
    R3ToolCatalogGroup("字体与资源", listOf(WorkbenchTool.FONTS, WorkbenchTool.FONT_REQUIREMENTS)),
    R3ToolCatalogGroup("检查与诊断", listOf(WorkbenchTool.QC, WorkbenchTool.COMPATIBILITY, WorkbenchTool.DIAGNOSTICS)),
    R3ToolCatalogGroup("工程与批量", listOf(WorkbenchTool.PROJECT, WorkbenchTool.BATCH)),
    R3ToolCatalogGroup("工作区", listOf(WorkbenchTool.CAPABILITIES)),
)

@Composable
internal fun R3ToolCatalogDialog(
    onDismiss: () -> Unit,
    onOpenTool: (WorkbenchTool) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f).fillMaxHeight(0.87f)
                .testTag("r3-tool-catalog"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 16.dp,
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("专业工具", style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f))
                    Text("${WorkbenchTool.entries.size} 项能力",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 12.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("r3-tool-close")) {
                        Icon(Icons.Filled.Close, "关闭目录")
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().testTag("r3-tool-search"),
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("搜索工具名称、能力、标签…") },
                    singleLine = true,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    val visibleGroups = r3CatalogGroups.map { group ->
                        group.copy(tools = group.tools.filter { tool ->
                            query.isBlank() || tool.title.contains(query, ignoreCase = true) ||
                                tool.name.contains(query, ignoreCase = true) ||
                                tool.description.contains(query, ignoreCase = true) ||
                                group.label.contains(query, ignoreCase = true)
                        })
                    }
                    visibleGroups.chunked(4).forEachIndexed { rowIndex, rowGroups ->
                        item(key = "r3-group-row-$rowIndex") {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                rowGroups.forEach { group ->
                                    Column(Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(group.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary)
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        group.tools.forEach { tool ->
                                            val current = tool == WorkbenchTool.CAPABILITIES
                                            Surface(
                                                modifier = Modifier.fillMaxWidth()
                                                    .height(57.dp)
                                                    .testTag("r3-tool-${tool.name}")
                                                    .clickable(enabled = !current) { onOpenTool(tool) },
                                                shape = RoundedCornerShape(5.dp),
                                                color = if (current) MaterialTheme.colorScheme.surfaceContainerHighest
                                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                border = BorderStroke(1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant),
                                            ) {
                                                Row(Modifier.fillMaxSize()
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(tool.title, style = MaterialTheme.typography.labelMedium,
                                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(tool.name, style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1)
                                                    }
                                                    if (!current) Icon(Icons.Filled.NorthEast, null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                                repeat(4 - rowGroups.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp)) {
                    Text("所有工具调用现有专业编辑器；CAPABILITIES 为当前目录，不创建新工具实例。",
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

package io.github.assworkbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*

/** Existing task/tool commands, arranged as the permanent V1.1 left rail. */
@Composable
internal fun FixedV11TaskRail(activeTool: WorkbenchTool, listRequested: Boolean,
    onTool: (WorkbenchTool) -> Unit, onDirectory: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.width(FixedV11LayoutPolicy.railWidthDp.dp)
        .testTag("fixed-v11-task-rail"), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val tools = listOf(WorkbenchTool.SUBTITLES, WorkbenchTool.TEXT, WorkbenchTool.TIMELINE,
                WorkbenchTool.STYLE, WorkbenchTool.POSITION, WorkbenchTool.EFFECTS, WorkbenchTool.FONTS, WorkbenchTool.CAPABILITIES)
            tools.forEach { tool ->
                val label = when(tool) {
                    WorkbenchTool.SUBTITLES -> "字幕"
                    WorkbenchTool.TEXT -> "正文"
                    WorkbenchTool.TIMELINE -> "校时"
                    WorkbenchTool.POSITION -> "位置"
                    WorkbenchTool.STYLE -> "样式"
                    WorkbenchTool.EFFECTS -> "效果"
                    WorkbenchTool.FONTS -> "资源"
                    else -> "工具"
                }
                val icon = when(tool) {
                    WorkbenchTool.SUBTITLES -> Icons.Filled.Subtitles
                    WorkbenchTool.TEXT -> Icons.Filled.Article
                    WorkbenchTool.TIMELINE -> Icons.Filled.AccessTime
                    WorkbenchTool.POSITION -> Icons.Filled.MyLocation
                    WorkbenchTool.STYLE -> Icons.Filled.Palette
                    WorkbenchTool.EFFECTS -> Icons.Filled.AutoFixHigh
                    WorkbenchTool.FONTS -> Icons.Filled.Folder
                    else -> Icons.Filled.Apps
                }
                val selected = if(listRequested) tool == WorkbenchTool.SUBTITLES
                    else if(tool == WorkbenchTool.SUBTITLES) false
                    else if(tool == WorkbenchTool.FONTS) activeTool.group == WorkbenchToolGroup.RESOURCES
                    else if(tool == WorkbenchTool.STYLE) activeTool == WorkbenchTool.STYLE
                    else if(tool == WorkbenchTool.POSITION) activeTool == WorkbenchTool.POSITION || activeTool == WorkbenchTool.VECTOR_CLIP
                    else if(tool == WorkbenchTool.CAPABILITIES) activeTool.task == WorkbenchTask.OTHER &&
                        activeTool.group != WorkbenchToolGroup.RESOURCES
                    else tool.task == activeTool.task
                Surface(color = if(selected) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
                    TextButton(onClick = { if(tool == WorkbenchTool.CAPABILITIES) onDirectory() else onTool(tool) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.width(52.dp).height(64.dp).semantics { this.selected = selected }
                            .testTag("fixed-tool-${tool.name}")) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, label, modifier = Modifier.size(22.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall)
                            if(selected) Box(Modifier.width(24.dp).height(2.dp)
                                .background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FixedV11CommandStrip(activeTool: WorkbenchTool, preview: PreviewDensity,
    onTool: (WorkbenchTool) -> Unit, onPreview: () -> Unit, onSpatial: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("主 UI", modifier = Modifier.testTag("fixed-r3-main-host"),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Box {
                TextButton(onClick = { menu = true }, modifier = Modifier.testTag("fixed-context-tools")) {
                    Text(activeTool.title); Icon(Icons.Filled.ExpandMore, "当前任务工具")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    contextualToolsFor(activeTool).forEach { tool ->
                        DropdownMenuItem(text = { Text(tool.title) }, onClick = { menu = false; onTool(tool) },
                            modifier = Modifier.testTag("fixed-context-${tool.name}"))
                    }
                    if(contextualToolsFor(activeTool).isEmpty()) DropdownMenuItem(
                        text = { Text("正文编辑") }, onClick = { menu = false; onTool(WorkbenchTool.TEXT) })
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onPreview, modifier = Modifier.testTag("fixed-preview-density")) {
                Icon(Icons.Filled.Visibility, null); Text("预览：${preview.label}")
            }
            TextButton(onClick = onSpatial, modifier = Modifier.testTag("fixed-r3-spatial-switch")) {
                Text("∞ 无限画布")
            }
        }
    }
}

/** Actual saved-document QC and one explicit Raw review, never a fake whole-tool commit. */
@Composable
internal fun FixedV11InspectorStatus(event: AssEvent?, state: EditorState, viewModel: EditorViewModel,
    owner: String, rawReviewAvailable: Boolean, issues: List<AssQcIssue>, onOpenQc: () -> Unit,
    modifier: Modifier = Modifier) {
    val store = LocalEventTextDraftStore.current
    val draft = event?.let { store.obtain(owner, it.id, it.text) }
    val draftState = if(event != null && draft != null) draft.snapshot().classify(event.text) else RawEventDraftState.CLEAN
    var review by remember(state.workspaceSessionId, event?.id) { mutableStateOf<R3TextWriteReceipt?>(null) }
    val session = state.workspaceSessionId
    val localIssues = issues.filter { it.eventId == event?.id }
    Surface(modifier.fillMaxWidth().testTag("fixed-v11-inspector-status"),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onOpenQc, modifier = Modifier.fillMaxWidth().testTag("fixed-v11-open-qc")) {
                Text(if(event == null) "质量检查 · 全部 ${issues.size} 项"
                    else "质量检查 · 当前 ${localIssues.size} 项 · 已保存文档",
                    color = if(localIssues.any { it.severity == AssQcSeverity.ERROR })
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(rawReviewAvailable) {
                Text(when(draftState) {
                    RawEventDraftState.CLEAN -> "正文与已保存值一致"
                    RawEventDraftState.EXTERNAL_CONFLICT -> "正文存在并发修改，审核中将阻止覆盖"
                    else -> "正文草稿未应用"
                }, style = MaterialTheme.typography.labelSmall)
                Button(enabled = event != null && draft != null && draftState != RawEventDraftState.CLEAN,
                    onClick = {
                        if(event != null && draft != null) review = R3TextWriteReceipt(event.id, event.style,
                            draft.baseText, event.text, draft.value.text)
                    }, modifier = Modifier.fillMaxWidth().testTag("fixed-v11-review-current")) {
                    Text("审核当前正文修改")
                }
            }
        }
    }
    review?.let { receipt ->
        R3TextWriteReviewDialog(receipt, event?.text, onDismiss = { review = null }, onConfirm = {
            val latest = viewModel.state.value
            if(latest.workspaceSessionId == session && draft?.value?.text == receipt.draftText &&
                receipt.mayCommit(latest.document.events.firstOrNull { it.id == receipt.eventId }?.text)) {
                viewModel.updateEventText(receipt.eventId, receipt.draftText)
                review = null
            }
        }, document = state.document, event = event)
    }
}

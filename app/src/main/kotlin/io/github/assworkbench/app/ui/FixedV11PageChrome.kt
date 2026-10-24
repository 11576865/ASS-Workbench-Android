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

/** Commands share the scene-tab row so tablet chrome does not consume another row. */
@Composable
internal fun FixedV11TabCommands(activeTool: WorkbenchTool, preview: PreviewDensity,
    onTool: (WorkbenchTool) -> Unit, onPreview: () -> Unit, onSpatial: () -> Unit,
    onDrawer: (() -> Unit)? = null, eventCount: Int = 0) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.testTag("fixed-r3-main-host"), verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { menu = true }, modifier = Modifier.testTag("fixed-context-tools")) {
                Text(activeTool.title); Icon(Icons.Filled.ExpandMore, "当前任务工具")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                contextualToolsFor(activeTool).forEach { tool ->
                    DropdownMenuItem(text = { Text(tool.title) }, onClick = { menu = false; onTool(tool) },
                        modifier = Modifier.testTag("fixed-context-${tool.name}"))
                }
                if (contextualToolsFor(activeTool).isEmpty()) DropdownMenuItem(
                    text = { Text("正文编辑") }, onClick = { menu = false; onTool(WorkbenchTool.TEXT) })
            }
        }
        IconButton(onClick = onPreview, modifier = Modifier.testTag("fixed-preview-density")) {
            Icon(Icons.Filled.Visibility, "预览：${preview.label}")
        }
        IconButton(onClick = onSpatial, modifier = Modifier.testTag("fixed-r3-spatial-switch")) {
            Icon(Icons.Filled.OpenInFull, "无限画布")
        }
        if (onDrawer != null) TextButton(onClick = onDrawer,
            modifier = Modifier.testTag("fixed-v11-drawer-open")) { Text("字幕列表 · $eventCount") }
    }
}

/** Shared visual hierarchy; binding text is supplied by the resolved live instance. */
@Composable
internal fun FixedV11InspectorHeading(target: String, binding: String, scope: String?, unresolved: Boolean = false) {
    Surface(Modifier.fillMaxWidth().testTag("r3-inspector-context"),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("检查器", style = MaterialTheme.typography.titleMedium)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                Text("$target · $binding", Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            if (scope != null) Text(scope, style = MaterialTheme.typography.labelSmall,
                color = if (unresolved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Saved-document QC plus one atomic review of Event text/time/metadata/Style/margins. */
@Composable
internal fun FixedV11InspectorStatus(event: AssEvent?, state: EditorState, viewModel: EditorViewModel,
    owner: String, rawReviewAvailable: Boolean, issues: List<AssQcIssue>, onOpenQc: () -> Unit,
    modifier: Modifier = Modifier) {
    val store = LocalEventTextDraftStore.current
    val draft = event?.let { store.obtain(owner, it.id, it.text) }
    val timing = event?.let { LocalEventTimingDraftStore.current.obtain(owner, it) }
    val metadata = event?.let { LocalEventMetadataDraftStore.current.obtain(owner, it) }
    val margins = event?.let { LocalEventMarginDraftStore.current.obtain(owner, it) }
    val style = event?.let { LocalEventStyleDraftStore.current.obtain(owner, it.id, it.style) }
    val pending = if (event != null && draft != null && timing != null && metadata != null)
        EventEditReviewReceipt(event.id, draft.snapshot(), timing.snapshot(), metadata.snapshot(), style?.snapshot(), margins?.snapshot()) else null
    var review by remember(state.workspaceSessionId, event?.id, owner) { mutableStateOf<EventEditReviewReceipt?>(null) }
    val session = state.workspaceSessionId
    val localIssues = issues.filter { it.eventId == event?.id }
    Surface(modifier.fillMaxWidth().testTag("fixed-v11-inspector-status"),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shownIssues = if (event == null) issues else localIssues
            val errors = shownIssues.count { it.severity == AssQcSeverity.ERROR }
            val warnings = shownIssues.count { it.severity == AssQcSeverity.WARNING }
            Surface(onClick = onOpenQc, modifier = Modifier.fillMaxWidth().testTag("fixed-v11-open-qc"),
                shape = MaterialTheme.shapes.small,
                color = if (errors > 0) MaterialTheme.colorScheme.errorContainer
                    else if (warnings > 0) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("质量检查 · ${shownIssues.size} 项 · $errors 错误 / $warnings 警告",
                        style = MaterialTheme.typography.labelMedium)
                    Text("已保存文档 · 按需展开，检查不会自动修复",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            if(rawReviewAvailable) {
                Text(if (pending?.hasChanges == true) "当前字幕有 ${pending.changedGroupCount} 组草稿修改"
                    else "当前字幕草稿与已保存值一致", style = MaterialTheme.typography.labelSmall)
                Button(enabled = pending?.hasChanges == true,
                    onClick = { review = pending },
                    modifier = Modifier.fillMaxWidth().testTag("fixed-v11-review-current")) {
                    Text("审核当前字幕修改")
                }
            }
        }
    }
    review?.let { receipt ->
        val draftsUnchanged = (!receipt.textChanged || draft?.value?.text == receipt.text.text) &&
            (!receipt.timingChanged || timing?.values == receipt.timing.values) &&
            (!receipt.metadataChanged || metadata?.values == receipt.metadata.values) &&
            (!receipt.styleChanged || style?.value?.text == receipt.style?.text) &&
            (!receipt.marginsChanged || margins?.values == receipt.margins?.values)
        EventEditReviewDialog(receipt, event, draftsUnchanged,
            styleNames = state.document.styles.mapTo(hashSetOf()) { it.name },
            onDismiss = { review = null }, onConfirm = {
                val latest = viewModel.state.value
                val stillSameDrafts = (!receipt.textChanged || draft?.value?.text == receipt.text.text) &&
                    (!receipt.timingChanged || timing?.values == receipt.timing.values) &&
                    (!receipt.metadataChanged || metadata?.values == receipt.metadata.values) &&
                    (!receipt.styleChanged || style?.value?.text == receipt.style?.text) &&
            (!receipt.marginsChanged || margins?.values == receipt.margins?.values)
                if (stillSameDrafts && latest.workspaceSessionId == session &&
                    viewModel.applyEventEditReview(receipt, session)) {
                    viewModel.state.value.document.events.firstOrNull { it.id == receipt.eventId }?.let { committed ->
                        style?.reconcile(committed.style)
                        draft?.reconcile(committed.text)
                        timing?.reconcile(EventTimingCanonical.from(committed))
                        metadata?.reconcile(EventMetadataValues.from(committed))
                        margins?.reconcile(EventMarginValues.from(committed))
                    }
                    review = null
                }
            })
    }
}

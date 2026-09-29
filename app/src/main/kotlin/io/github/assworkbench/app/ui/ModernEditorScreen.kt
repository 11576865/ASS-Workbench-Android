package io.github.assworkbench.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*
import io.github.assworkbench.fonts.FontOrigin
import kotlin.math.abs

private enum class WorkbenchTool(val title: String) {
    TIMELINE("时间轴"), STYLE("Style"), POSITION("位置"),
    FONTS("字体"), QC("质量检查"), BATCH("批量"), PROJECT("项目"), DIAGNOSTICS("诊断"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernEditorScreen(
    state: EditorState,
    viewModel: EditorViewModel,
    onOpenVideo: () -> Unit,
    onOpenReferenceVideo: () -> Unit,
    onOpenMkvProject: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onImportFont: () -> Unit,
    onSave: () -> Unit,
    onSaveMkv: () -> Unit,
) {
    var toolName by rememberSaveable { mutableStateOf(WorkbenchTool.TIMELINE.name) }
    var supportingOpen by rememberSaveable { mutableStateOf(false) }
    var expandedEventId by rememberSaveable { mutableStateOf<Long?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var openMenu by remember { mutableStateOf(false) }
    var saveConfirmOpen by remember { mutableStateOf(false) }
    var mkvConfirmOpen by remember { mutableStateOf(false) }
    val tool = WorkbenchTool.entries.firstOrNull { it.name == toolName } ?: WorkbenchTool.TIMELINE
    fun openTool(next: WorkbenchTool) {
        toolName = next.name
        supportingOpen = true
    }
    fun toggleTool(next: WorkbenchTool) {
        if (supportingOpen && tool == next) {
            supportingOpen = false
        } else {
            toolName = next.name
            supportingOpen = true
        }
    }
    val issues = remember(state.document) { AssQualityCheck.inspect(state.document) }
    val issuesByEvent = remember(issues) { issues.groupBy { it.eventId } }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
        ModernAppBar(
            state, viewModel, state.selectedEventIds.isNotEmpty(), searchOpen,
            { searchOpen = !searchOpen }, { openMenu = true }, openMenu, { openMenu = false },
            onOpenReferenceVideo,
            {
                val hasWorkspace = state.subtitleLoaded || state.project.videoUri != null ||
                    state.container.uri != null || state.dirty
                if (hasWorkspace) mkvConfirmOpen = true else onOpenMkvProject()
            },
            onOpenSubtitle, onImportFont,
            {
                if (state.project.subtitleUri == null) onSaveAs()
                else saveConfirmOpen = true
            },
            onSaveMkv,
            { openTool(it) },
        )

        if (saveConfirmOpen) {
            AlertDialog(
                onDismissRequest = { saveConfirmOpen = false },
                title = { Text("覆盖保存当前 ASS？") },
                text = {
                    Text(
                        if (state.dirty) "将把当前修改写回原字幕文件。原文件内容会被替换。"
                        else "当前没有未保存修改；仍可覆盖写回原字幕文件。"
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        saveConfirmOpen = false
                        onSave()
                    }) { Text("覆盖保存") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            saveConfirmOpen = false
                            onSaveAs()
                        }) { Text("另存为") }
                        TextButton(onClick = { saveConfirmOpen = false }) { Text("取消") }
                    }
                },
            )
        }

        if (mkvConfirmOpen) {
            AlertDialog(
                onDismissRequest = { mkvConfirmOpen = false },
                title = { Text("切换到 MKV 工程？") },
                text = {
                    Text(
                        "MKV 是独立工作流。选择新的 MKV 后，当前工作台中的字幕、参考视频、选择状态和项目字体会被清空。未保存修改请先保存。"
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        mkvConfirmOpen = false
                        onOpenMkvProject()
                    }) { Text("选择 MKV") }
                },
                dismissButton = {
                    TextButton(onClick = { mkvConfirmOpen = false }) { Text("取消") }
                },
            )
        }

        VideoPreview(
            videoUri = state.project.videoUri,
            document = state.document,
            seekRequestMs = state.seekRequestMs,
            seekRequestNonce = state.seekRequestNonce,
            onPosition = viewModel::setPlaybackPosition,
            onRendererDiagnostics = viewModel::updateRendererDiagnostics,
            configDir = viewModel.rendererConfigDir(),
            fontsDir = viewModel.rendererFontsDir(),
            fontRevision = state.fontRevision,
            initialPositionMs = state.playbackPositionMs,
            showLayoutGuides = false,
            focusedEventId = if (tool == WorkbenchTool.POSITION) state.focusedEventId else state.focusedEventId,
            onSetEventPosition = viewModel::setFocusedPosition,
            onFocusEvent = { viewModel.focusEvent(it, seek = false) },
            onSetEventTiming = viewModel::setEventTiming,
            onOpenVideo = onOpenVideo,
            onOpenTimeline = { toggleTool(WorkbenchTool.TIMELINE) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (searchOpen) SearchStrip(state.query, viewModel::setQuery) {
            viewModel.setQuery(""); searchOpen = false
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxWidth < WorkbenchDimens.CompactWidth
            if (compact) {
                EventWorkspace(
                    state, viewModel, issuesByEvent, expandedEventId,
                    { expandedEventId = it }, { toggleTool(it) }, Modifier.fillMaxSize()
                )
                if (supportingOpen) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                    ModalBottomSheet(
                        onDismissRequest = { supportingOpen = false },
                        sheetState = sheetState,
                    ) {
                        SupportingWorkbench(
                            state, viewModel, tool, issues,
                            { supportingOpen = false }, onImportFont, onSaveMkv,
                            Modifier.fillMaxWidth().heightIn(min = 224.dp, max = 640.dp),
                        )
                    }
                }
            } else {
                EventWorkspace(
                    state, viewModel, issuesByEvent, expandedEventId,
                    { expandedEventId = it }, { toggleTool(it) }, Modifier.fillMaxSize()
                )
                if (supportingOpen) {
                    val overlayWidth = (maxWidth * 0.48f).coerceIn(320.dp, 480.dp)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(overlayWidth),
                        tonalElevation = 6.dp,
                        shadowElevation = 10.dp,
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        SupportingWorkbench(
                            state,
                            viewModel,
                            tool,
                            issues,
                            { supportingOpen = false },
                            onImportFont,
                            onSaveMkv,
                            Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ModernAppBar(
    state: EditorState,
    viewModel: EditorViewModel,
    selectionMode: Boolean,
    searchOpen: Boolean,
    onSearchToggle: () -> Unit,
    onOpenMenu: () -> Unit,
    openMenu: Boolean,
    onDismissMenu: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenMkvProject: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onImportFont: () -> Unit,
    onSave: () -> Unit,
    onSaveMkv: () -> Unit,
    onTool: (WorkbenchTool) -> Unit,
) {
    var moreMenuOpen by remember { mutableStateOf(false) }
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().height(WorkbenchDimens.AppBarHeight).padding(horizontal = WorkbenchDimens.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (selectionMode) {
                IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Filled.Close, "退出多选") }
                Text("已选 ${state.selectedEventIds.size} 条", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = viewModel::toggleSelectAllVisible) { Text("全选") }
                TextButton(onClick = { onTool(WorkbenchTool.BATCH) }) { Text("批量") }
            } else {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(state.project.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            state.container.uri != null -> "MKV 工程 · ${state.document.events.size} events"
                            state.subtitleLoaded -> "独立 ASS · ${state.document.events.size} events"
                            else -> "未载入字幕"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                TooltipIconButton(
                    label = if (searchOpen) "关闭搜索" else "搜索",
                    onClick = onSearchToggle,
                ) {
                    Icon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, null)
                }
                TooltipIconButton("保存", onSave, enabled = state.subtitleLoaded) { Icon(Icons.Filled.Save, null) }
                TooltipIconButton("撤销", viewModel::undo, enabled = state.canUndo) { Icon(Icons.Filled.Undo, null) }
                TooltipIconButton("重做", viewModel::redo, enabled = state.canRedo) { Icon(Icons.Filled.Redo, null) }
                Box {
                    TooltipIconButton("打开文件 / 工程", onOpenMenu) { Icon(Icons.Filled.FolderOpen, null) }
                    DropdownMenu(expanded = openMenu, onDismissRequest = onDismissMenu) {
                        DropdownMenuItem(text = { Text("打开独立 ASS") }, leadingIcon = { Icon(Icons.Filled.Subtitles, null) }, onClick = { onDismissMenu(); onOpenSubtitle() })
                        DropdownMenuItem(text = { Text("打开 / 更换参考视频") }, leadingIcon = { Icon(Icons.Filled.Movie, null) }, onClick = { onDismissMenu(); onOpenVideo() })
                        DropdownMenuItem(text = { Text("打开 MKV 工程") }, leadingIcon = { Icon(Icons.Filled.VideoFile, null) }, onClick = { onDismissMenu(); onOpenMkvProject() })
                        Divider()
                        DropdownMenuItem(text = { Text("新建空白 ASS") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = { onDismissMenu(); viewModel.newSubtitleProject() })
                        DropdownMenuItem(text = { Text("导入字体") }, leadingIcon = { Icon(Icons.Filled.FontDownload, null) }, onClick = { onDismissMenu(); onImportFont() })
                    }
                }
                Box {
                    TooltipIconButton("工具和更多操作", { moreMenuOpen = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("字体管理") }, leadingIcon = { Icon(Icons.Filled.FontDownload, null) }, onClick = { moreMenuOpen = false; onTool(WorkbenchTool.FONTS) })
                        DropdownMenuItem(text = { Text("质量检查") }, leadingIcon = { Icon(Icons.Filled.ErrorOutline, null) }, onClick = { moreMenuOpen = false; onTool(WorkbenchTool.QC) })
                        DropdownMenuItem(text = { Text("项目") }, leadingIcon = { Icon(Icons.Filled.Info, null) }, onClick = { moreMenuOpen = false; onTool(WorkbenchTool.PROJECT) })
                        DropdownMenuItem(text = { Text("诊断") }, leadingIcon = { Icon(Icons.Filled.Tune, null) }, onClick = { moreMenuOpen = false; onTool(WorkbenchTool.DIAGNOSTICS) })
                        Divider()
                        if (state.container.uri != null) DropdownMenuItem(text = { Text("保存为新 MKV") }, onClick = { moreMenuOpen = false; onSaveMkv() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled) { content() }
    }
}

@Composable
private fun SearchStrip(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    Surface(tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = WorkbenchDimens.Small, vertical = WorkbenchDimens.Micro), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, onQuery, placeholder = { Text("搜索正文 / Actor / Style") }, singleLine = true, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "关闭") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    issuesByEvent: Map<Long, List<AssQcIssue>>,
    expandedEventId: Long?,
    onExpandedChange: (Long?) -> Unit,
    onTool: (WorkbenchTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionMode = state.selectedEventIds.isNotEmpty()
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().height(WorkbenchDimens.PaneHeaderHeight).padding(horizontal = WorkbenchDimens.Small), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selectionMode) "选择模式" else "字幕", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(if (state.query.isBlank()) "${state.document.events.size}" else "${state.filteredEvents.size}/${state.document.events.size}", style = MaterialTheme.typography.labelSmall)
        }
        Divider()
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.filteredEvents) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                offset.y.toInt() in it.offset..(it.offset + it.size)
                            }
                            val eventId = item?.index?.let { index -> state.filteredEvents.getOrNull(index)?.id }
                            if (eventId != null) viewModel.beginRangeSelection(eventId)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val y = change.position.y.toInt()
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                y in it.offset..(it.offset + it.size)
                            }
                            val eventId = item?.index?.let { index -> state.filteredEvents.getOrNull(index)?.id }
                            if (eventId != null) viewModel.previewRangeSelection(eventId)
                        },
                        onDragEnd = viewModel::finishRangeSelection,
                        onDragCancel = viewModel::finishRangeSelection,
                    )
                },
        ) {
            items(state.filteredEvents, key = { it.id }) { event ->
                ModernEventRow(
                    event, event.id == state.focusedEventId, event.id == expandedEventId,
                    event.id in state.selectedEventIds, selectionMode, issuesByEvent[event.id].orEmpty(),
                    state, viewModel,
                    {
                        if (selectionMode) viewModel.toggleSelected(event.id)
                        else {
                            viewModel.focusEvent(event.id, seek = true)
                            onExpandedChange(if (expandedEventId == event.id) null else event.id)
                        }
                    },
                    {
                        if (event.id !in state.selectedEventIds) {
                            viewModel.toggleSelected(event.id)
                        }
                    },
                    { onExpandedChange(null) },
                    onTool,
                )
                Divider()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernEventRow(
    event: AssEvent,
    focused: Boolean,
    expanded: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    issues: List<AssQcIssue>,
    state: EditorState,
    viewModel: EditorViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCollapse: () -> Unit,
    onTool: (WorkbenchTool) -> Unit,
) {
    var qcOpen by remember { mutableStateOf(false) }
    val visible = remember(event.text) { AssInlineSyntax.visibleText(event.text).ifBlank { "（空字幕）" } }
    val style = state.document.styles.firstOrNull { it.name == event.style }

    Column(
        Modifier.fillMaxWidth()
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                    focused -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f)
                    else -> Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .animateContentSize()
            .heightIn(min = WorkbenchDimens.ListRowMinHeight)
            .padding(
                horizontal = WorkbenchDimens.Small,
                vertical = if (expanded) WorkbenchDimens.Small else WorkbenchDimens.Micro,
            ),
        verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onClick() })
                else Text("#${event.id}", style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${event.start.toAss().removePrefix("0:")}–${event.end.toAss().removePrefix("0:")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (event.style != "Default") Text(event.style, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    if (event.layer != 0) Text(" · L${event.layer}", style = MaterialTheme.typography.labelSmall)
                }
                Text(visible, maxLines = if (expanded) 3 else 2, overflow = TextOverflow.Ellipsis, style = if (expanded) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall)
            }
            if (issues.isNotEmpty()) {
                Box {
                    TextButton(onClick = { qcOpen = true }, modifier = Modifier.widthIn(min = 34.dp)) {
                        val severity = issues.maxByOrNull { it.severity.ordinal }?.severity
                        Text(
                            when (severity) {
                                AssQcSeverity.ERROR -> "!${issues.size}"
                                AssQcSeverity.WARNING -> "⚠${issues.size}"
                                else -> "•${issues.size}"
                            },
                            color = when (severity) {
                                AssQcSeverity.ERROR -> MaterialTheme.colorScheme.error
                                AssQcSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    DropdownMenu(expanded = qcOpen, onDismissRequest = { qcOpen = false }) {
                        issues.forEach { issue ->
                            DropdownMenuItem(
                                text = { Column { Text(issue.message); Text(issue.kind.name, style = MaterialTheme.typography.labelSmall) } },
                                onClick = { qcOpen = false },
                            )
                        }
                    }
                }
            }
            if (expanded) IconButton(onClick = onCollapse) { Icon(Icons.Filled.Close, "收起") }
        }

        if (expanded) InlineEventEditor(event, style?.name ?: event.style, state, viewModel, onTool)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InlineEventEditor(
    event: AssEvent,
    styleName: String,
    state: EditorState,
    viewModel: EditorViewModel,
    onTool: (WorkbenchTool) -> Unit,
) {
    var startText by remember(event.id, event.start) { mutableStateOf(event.start.toAss()) }
    var endText by remember(event.id, event.end) { mutableStateOf(event.end.toAss()) }
    var raw by remember(event.id, event.text) { mutableStateOf(event.text) }
    var metadataOpen by remember(event.id) { mutableStateOf(false) }
    var effectsOpen by remember(event.id) { mutableStateOf(false) }
    var layerText by remember(event.id, event.layer) { mutableStateOf(event.layer.toString()) }
    var actorText by remember(event.id, event.name) { mutableStateOf(event.name) }
    var comment by remember(event.id, event.comment) { mutableStateOf(event.comment) }

    val effective = remember(state.document, event) {
        AssEffectiveInspector.inspect(state.document, event).associateBy { it.name }
    }
    val font = effective["Font"]?.effectiveValue ?: "?"
    val size = effective["Size"]?.effectiveValue ?: "?"
    val bold = effective["Bold"]?.effectiveValue?.let { if (it == "true") "1" else if (it == "false") "0" else it } ?: "?"
    val italic = effective["Italic"]?.effectiveValue?.let { if (it == "true") "1" else if (it == "false") "0" else it } ?: "?"
    val primary = effective["Primary"]?.effectiveValue ?: effective["Primary Color"]?.effectiveValue ?: "?"
    val outlineColor = effective["Outline Color"]?.effectiveValue ?: "?"
    val border = effective["Border"]?.effectiveValue ?: "?"
    val alignment = effective["Alignment"]?.effectiveValue ?: "?"
    val marginV = effective["Margin V"]?.effectiveValue ?: "?"
    val pos = effective["Position"]?.effectiveValue ?: "alignment anchor"

    LaunchedEffect(event.text) {
        if (raw != event.text) raw = event.text
    }

    Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
        OutlinedTextField(startText, { startText = it }, label = { Text("Start") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(endText, { endText = it }, label = { Text("End") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(
            onClick = {
                val start = runCatching { SubTime.fromEditable(startText) }.getOrNull()
                val end = runCatching { SubTime.fromEditable(endText) }.getOrNull()
                if (start != null && end != null && end >= start) {
                    viewModel.setEventTiming(event.id, start.millis, end.millis)
                }
            },
            modifier = Modifier.align(Alignment.CenterVertically),
        ) { Text("应用") }
    }

    OutlinedTextField(
        value = raw,
        onValueChange = { raw = it },
        label = { Text("字幕正文 · ASS Event Text") },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        visualTransformation = rememberAssSyntaxTransformation(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp, max = 220.dp),
    )
    if (raw != event.text) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { raw = event.text }) { Text("还原") }
            Button(onClick = { viewModel.updateEventText(event.id, raw) }) { Text("应用正文") }
        }
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
    ) {
        AssistChip(onClick = { onTool(WorkbenchTool.FONTS) }, label = { Text("fn$font") })
        AssistChip(onClick = { onTool(WorkbenchTool.STYLE) }, label = { Text("fs$size") })
        AssistChip(onClick = { onTool(WorkbenchTool.STYLE) }, label = { Text("b$bold · i$italic") })
        AssistChip(onClick = { onTool(WorkbenchTool.STYLE) }, label = { Text("c$primary") })
        AssistChip(onClick = { onTool(WorkbenchTool.STYLE) }, label = { Text("3c$outlineColor · bord$border") })
        AssistChip(
            onClick = { onTool(WorkbenchTool.POSITION) },
            label = { Text("an$alignment · V$marginV" + if (pos != "alignment anchor") " · pos" else "") },
        )
        AssistChip(onClick = { onTool(WorkbenchTool.STYLE) }, label = { Text("Style $styleName") })
        AssistChip(
            onClick = { effectsOpen = !effectsOpen },
            label = { Text(if (effectsOpen) "收起效果" else "效果") },
        )
        AssistChip(
            onClick = { metadataOpen = !metadataOpen },
            label = {
                Text(
                    "Event · L${event.layer}" +
                        (if (event.name.isNotBlank()) " · ${event.name}" else "") +
                        (if (event.comment) " · Comment" else "")
                )
            },
        )
    }

    if (effectsOpen) InlineEffectsEditor(event, viewModel)

    if (metadataOpen) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(layerText, { layerText = it }, label = { Text("Layer") }, singleLine = true, modifier = Modifier.width(88.dp))
                    OutlinedTextField(actorText, { actorText = it }, label = { Text("Actor") }, singleLine = true, modifier = Modifier.weight(1f))
                    FilterChip(selected = comment, onClick = { comment = !comment }, label = { Text(if (comment) "Comment" else "Dialogue") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        layerText = event.layer.toString()
                        actorText = event.name
                        comment = event.comment
                        metadataOpen = false
                    }) { Text("取消") }
                    Button(onClick = {
                        viewModel.updateFocusedMetadata(
                            layer = layerText.toIntOrNull() ?: event.layer,
                            actor = actorText,
                            comment = comment,
                        )
                        metadataOpen = false
                    }) { Text("应用") }
                }
            }
        }
    }
}

@Composable
private fun InlineEffectsEditor(
    event: AssEvent,
    viewModel: EditorViewModel,
) {
    val snapshot = remember(event.id, event.text) { EventOverrideEditor.inspect(event.text) }
    var blur by remember(event.id, event.text) { mutableStateOf(snapshot.blur?.toString().orEmpty()) }
    var fadeIn by remember(event.id, event.text) { mutableStateOf(snapshot.fadeInMs?.toString().orEmpty()) }
    var fadeOut by remember(event.id, event.text) { mutableStateOf(snapshot.fadeOutMs?.toString().orEmpty()) }
    var softEntry by remember(event.id, event.text) { mutableStateOf(snapshot.softEntry) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(WorkbenchDimens.Small),
            verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                OutlinedTextField(blur, { blur = it }, label = { Text("Blur") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(fadeIn, { fadeIn = it }, label = { Text("Fade In ms") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(fadeOut, { fadeOut = it }, label = { Text("Fade Out ms") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = softEntry,
                    onClick = { softEntry = !softEntry },
                    label = { Text("Soft Entry · 160ms") },
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    viewModel.applyEventOverrides(
                        id = event.id,
                        x = snapshot.x,
                        y = snapshot.y,
                        blur = blur.toDoubleOrNull(),
                        fadeInMs = fadeIn.toIntOrNull(),
                        fadeOutMs = fadeOut.toIntOrNull(),
                        softEntry = softEntry,
                    )
                }) { Text("应用") }
            }
        }
    }
}

@Composable
private fun SupportingWorkbench(
    state: EditorState,
    viewModel: EditorViewModel,
    tool: WorkbenchTool,
    issues: List<AssQcIssue>,
    onClose: () -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth()
                .height(WorkbenchDimens.PaneHeaderHeight)
                .padding(horizontal = WorkbenchDimens.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tool.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TooltipIconButton("关闭 " + tool.title, onClose) {
                Icon(Icons.Filled.Close, null)
            }
        }
        Divider()
        AnimatedContent(
            targetState = tool,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "supporting-tool",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { activeTool ->
            when (activeTool) {
                WorkbenchTool.TIMELINE -> ModernTimelinePane(state, viewModel, Modifier.fillMaxSize())
                WorkbenchTool.STYLE -> StylePane(state, viewModel, Modifier.fillMaxSize())
                WorkbenchTool.POSITION -> PositionPane(state, viewModel, Modifier.fillMaxSize())
                WorkbenchTool.FONTS -> FontManagerPane(state, viewModel, onImportFont, Modifier.fillMaxSize())
                WorkbenchTool.QC -> QcPane(state, viewModel, issues, Modifier.fillMaxSize())
                WorkbenchTool.BATCH -> BatchPane(state, viewModel, Modifier.fillMaxSize())
                WorkbenchTool.PROJECT -> ProjectPane(state, viewModel, onSaveMkv, Modifier.fillMaxSize())
                WorkbenchTool.DIAGNOSTICS -> DiagnosticsPane(state, viewModel, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ModernTimelinePane(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    var windowSeconds by rememberSaveable { mutableStateOf(30) }
    var snapEnabled by rememberSaveable { mutableStateOf(true) }
    var snapEvents by rememberSaveable { mutableStateOf(true) }
    var snapPlayhead by rememberSaveable { mutableStateOf(true) }
    var snapGrid by rememberSaveable { mutableStateOf(true) }
    var snapGridMs by rememberSaveable { mutableLongStateOf(10L) }
    var settingsOpen by remember { mutableStateOf(false) }
    val centerMs = state.playbackPositionMs
    val focusedEvent = state.document.events.firstOrNull { it.id == state.focusedEventId }
    val half = windowSeconds * 500L
    val windowStart = (centerMs - half).coerceAtLeast(0L)
    val windowEnd = windowStart + windowSeconds * 1000L
    val visible = state.document.events.filter { it.end.millis >= windowStart && it.start.millis <= windowEnd }.take(50)
    val snapTargets = remember(visible, centerMs, snapEvents, snapPlayhead) {
        buildList {
            if (snapPlayhead) add(centerMs)
            if (snapEvents) visible.forEach { add(it.start.millis); add(it.end.millis) }
        }.distinct()
    }

    Column(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatMs(centerMs), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { settingsOpen = true }) { Text("${windowSeconds}s · " + if (snapEnabled) "Snap ${snapGridMs}ms" else "Snap off") }
            DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                listOf(10, 30, 60).forEach { seconds ->
                    DropdownMenuItem(text = { Text((if (windowSeconds == seconds) "✓ " else "") + "窗口 ${seconds}s") }, onClick = { windowSeconds = seconds; settingsOpen = false })
                }
                DropdownMenuItem(
                    text = { Text((if (snapEnabled) "✓ " else "") + "吸附总开关") },
                    onClick = { snapEnabled = !snapEnabled },
                )
                DropdownMenuItem(
                    text = { Text((if (snapEvents) "✓ " else "") + "字幕边界") },
                    enabled = snapEnabled,
                    onClick = { snapEvents = !snapEvents },
                )
                DropdownMenuItem(
                    text = { Text((if (snapPlayhead) "✓ " else "") + "播放头") },
                    enabled = snapEnabled,
                    onClick = { snapPlayhead = !snapPlayhead },
                )
                DropdownMenuItem(
                    text = { Text((if (snapGrid) "✓ " else "") + "时间网格") },
                    enabled = snapEnabled,
                    onClick = { snapGrid = !snapGrid },
                )
                listOf(10L, 100L).forEach { grid ->
                    DropdownMenuItem(
                        text = { Text((if (snapGridMs == grid) "✓ " else "") + "网格 " + grid + "ms") },
                        enabled = snapEnabled && snapGrid,
                        onClick = { snapGridMs = grid; settingsOpen = false },
                    )
                }
            }
        }
        Text(
            "拖左右边缘调整 Start / End；先点选 Event，再拖主体平移。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (focusedEvent != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
            ) {
                OutlinedButton(
                    onClick = viewModel::setFocusedStartToPlayback,
                    modifier = Modifier.weight(1f),
                ) { Text("Start ← 播放头") }
                OutlinedButton(
                    onClick = viewModel::setFocusedEndToPlayback,
                    modifier = Modifier.weight(1f),
                ) { Text("End ← 播放头") }
            }
        }
        Divider()
        if (visible.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("当前窗口没有字幕事件。") }
        else LazyColumn(Modifier.fillMaxSize()) {
            items(visible, key = { it.id }) { event ->
                ModernTimelineEventRow(
                    event, windowStart, windowEnd, centerMs, event.id == state.focusedEventId,
                    snapTargets, snapEnabled, snapGrid, snapGridMs,
                    { viewModel.focusEvent(event.id, seek = true) },
                    { a, b -> viewModel.setEventTiming(event.id, a, b) },
                )
            }
        }
    }
}

private enum class ModernTimelineDragMode { START, MOVE, END }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernTimelineEventRow(
    event: AssEvent,
    windowStartMs: Long,
    windowEndMs: Long,
    playheadMs: Long,
    focused: Boolean,
    snapTargets: List<Long>,
    snapEnabled: Boolean,
    snapGrid: Boolean,
    snapGridMs: Long,
    onFocus: () -> Unit,
    onCommit: (Long, Long) -> Unit,
) {
    var previewStart by remember(event.id, event.start) { mutableLongStateOf(event.start.millis) }
    var previewEnd by remember(event.id, event.end) { mutableLongStateOf(event.end.millis) }
    var dragMode by remember { mutableStateOf<ModernTimelineDragMode?>(null) }
    var baseStart by remember { mutableLongStateOf(previewStart) }
    var baseEnd by remember { mutableLongStateOf(previewEnd) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    val timelineColors = MaterialTheme.colorScheme

    Row(
        Modifier.fillMaxWidth().height(40.dp).combinedClickable(onClick = onFocus, onLongClick = onFocus),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
    ) {
        Column(Modifier.width(86.dp)) {
            Text("#${event.id}", style = MaterialTheme.typography.labelSmall)
            Text(AssInlineSyntax.visibleText(event.text), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BoxWithConstraints(
            Modifier.weight(1f).height(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .pointerInput(event.id, windowStartMs, windowEndMs, snapTargets, focused, snapGrid, snapGridMs) {
                    val span = (windowEndMs - windowStartMs).coerceAtLeast(1L)
                    fun xFor(ms: Long): Float = ((ms - windowStartMs).toFloat() / span).coerceIn(0f, 1f) * size.width
                    fun snap(candidate: Long): Long {
                        if (!snapEnabled) return candidate
                        val step = snapGridMs.coerceAtLeast(1L)
                        val gridCandidate = if (snapGrid) {
                            ((candidate + step / 2L) / step) * step
                        } else {
                            candidate
                        }
                        val threshold = minOf(120L, maxOf(24L, span / 220L))
                        val nearest = snapTargets
                            .asSequence()
                            .filterNot { it == baseStart || it == baseEnd }
                            .minByOrNull { abs(it - candidate) }
                        return if (nearest != null && abs(nearest - candidate) <= threshold) nearest else gridCandidate
                    }
                    detectDragGestures(
                        onDragStart = { offset ->
                            val sx = xFor(previewStart); val ex = xFor(previewEnd); val hit = 18.dp.toPx()
                            dragMode = if (!focused) {
                                null
                            } else {
                                when {
                                    abs(offset.x - sx) <= hit -> ModernTimelineDragMode.START
                                    abs(offset.x - ex) <= hit -> ModernTimelineDragMode.END
                                    offset.x in sx..ex -> ModernTimelineDragMode.MOVE
                                    else -> null
                                }
                            }
                            baseStart = previewStart; baseEnd = previewEnd; dragPx = 0f
                        },
                        onDrag = { change, amount ->
                            val mode = dragMode ?: return@detectDragGestures
                            change.consume(); dragPx += amount.x
                            val delta = (dragPx / size.width.coerceAtLeast(1) * span).toLong()
                            when (mode) {
                                ModernTimelineDragMode.START -> previewStart = snap(baseStart + delta).coerceIn(0L, (previewEnd - 10L).coerceAtLeast(0L))
                                ModernTimelineDragMode.END -> previewEnd = snap(baseEnd + delta).coerceAtLeast(previewStart + 10L)
                                ModernTimelineDragMode.MOVE -> {
                                    val duration = (baseEnd - baseStart).coerceAtLeast(10L)
                                    val next = snap(baseStart + delta).coerceAtLeast(0L)
                                    previewStart = next; previewEnd = next + duration
                                }
                            }
                        },
                        onDragEnd = { if (dragMode != null) onCommit(previewStart, previewEnd); dragMode = null },
                        onDragCancel = { previewStart = event.start.millis; previewEnd = event.end.millis; dragMode = null },
                    )
                },
        ) {
            val span = (windowEndMs - windowStartMs).coerceAtLeast(1L).toFloat()
            val leftFraction = ((previewStart - windowStartMs) / span).coerceIn(0f, 1f)
            val rightFraction = ((previewEnd - windowStartMs) / span).coerceIn(0f, 1f)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(leftFraction.coerceAtLeast(0.001f)))
                Box(
                    Modifier.weight((rightFraction - leftFraction).coerceAtLeast(0.015f)).fillMaxHeight()
                        .background(if (focused) timelineColors.primary.copy(alpha = 0.72f) else timelineColors.secondary.copy(alpha = 0.44f))
                )
                Spacer(Modifier.weight((1f - rightFraction).coerceAtLeast(0.001f)))
            }
            Canvas(Modifier.fillMaxSize()) {
                val playFraction = ((playheadMs - windowStartMs).toFloat() / (windowEndMs - windowStartMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                val playX = size.width * playFraction
                drawLine(
                    color = timelineColors.onSurface,
                    start = androidx.compose.ui.geometry.Offset(playX, 0f),
                    end = androidx.compose.ui.geometry.Offset(playX, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                if (focused) {
                    val sx = size.width * leftFraction
                    val ex = size.width * rightFraction
                    drawLine(timelineColors.onPrimary, androidx.compose.ui.geometry.Offset(sx, 0f), androidx.compose.ui.geometry.Offset(sx, size.height), 2.dp.toPx())
                    drawLine(timelineColors.onPrimary, androidx.compose.ui.geometry.Offset(ex, 0f), androidx.compose.ui.geometry.Offset(ex, size.height), 2.dp.toPx())
                }
            }
            Text("${formatMs(previewStart)}–${formatMs(previewEnd)}", Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun StylePane(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    val focused = state.document.events.firstOrNull { it.id == state.focusedEventId }
    val style = focused?.let { e -> state.document.styles.firstOrNull { it.name == e.style } } ?: state.document.styles.firstOrNull()
    if (style == null) Box(modifier, contentAlignment = Alignment.Center) { Text("没有 Style") }
    else TypesettingPanel(state, viewModel, style, modifier)
}

@Composable
private fun PositionPane(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    val event = state.document.events.firstOrNull { it.id == state.focusedEventId }
    if (event == null) { Box(modifier, contentAlignment = Alignment.Center) { Text("先选择一条字幕") }; return }
    val override = remember(event.text) { EventOverrideEditor.inspect(event.text) }
    val effective = remember(state.document, event) { AssEffectiveInspector.inspect(state.document, event).associateBy { it.name } }
    var x by remember(event.id, event.text) { mutableStateOf(override.x?.toString().orEmpty()) }
    var y by remember(event.id, event.text) { mutableStateOf(override.y?.toString().orEmpty()) }
    Column(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("九宫格对齐", style = MaterialTheme.typography.titleSmall)
        listOf(listOf(7,8,9), listOf(4,5,6), listOf(1,2,3)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                row.forEach { a -> OutlinedButton({ viewModel.setFocusedAlignment(a) }, Modifier.weight(1f)) { Text(a.toString()) } }
            }
        }
        Text("Effective: an${effective["Alignment"]?.effectiveValue} · V${effective["Margin V"]?.effectiveValue} · ${effective["Position"]?.effectiveValue}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Divider()
        Text("任意位置", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
            OutlinedTextField(x, { x = it }, label = { Text("X") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(y, { y = it }, label = { Text("Y") }, singleLine = true, modifier = Modifier.weight(1f))
            Button({
                viewModel.applyEventOverrides(event.id, x.toDoubleOrNull(), y.toDoubleOrNull(), override.blur, override.fadeInMs, override.fadeOutMs, override.softEntry)
            }, Modifier.align(Alignment.CenterVertically)) { Text("应用") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
            listOf("←" to (-5.0 to 0.0), "→" to (5.0 to 0.0), "↑" to (0.0 to -5.0), "↓" to (0.0 to 5.0)).forEach { (label, delta) ->
                OutlinedButton({ viewModel.nudgeEventPosition(event.id, delta.first, delta.second) }, Modifier.weight(1f)) { Text(label) }
            }
        }
        Text("此工具打开时，可以直接在 16:9 预览上拖动字幕锚点。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FontManagerPane(state: EditorState, viewModel: EditorViewModel, onImportFont: () -> Unit, modifier: Modifier = Modifier) {
    val focused = state.document.events.firstOrNull { it.id == state.focusedEventId }
    val style = focused?.let { e -> state.document.styles.firstOrNull { it.name == e.style } }
    Column(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("字体管理", style = MaterialTheme.typography.titleSmall)
                Text("${state.importedFonts.size} 个可用字体" + if (style != null) " · 当前 ${style.fontName}" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onImportFont, enabled = !state.fontImportBusy) { Text(if (state.fontImportBusy) "导入中…" else "导入字体") }
        }
        Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(state.importedFonts, key = { it.sha256 }) { font ->
                Row(Modifier.fillMaxWidth().padding(vertical = WorkbenchDimens.Micro), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                    Column(Modifier.weight(1f)) {
                        Text(font.metadata.family, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${font.metadata.rendererFamily} · " + when (font.origin) {
                                FontOrigin.MANUAL -> "手动导入"
                                FontOrigin.MKV_ATTACHMENT -> "MKV 附件"
                                FontOrigin.UNKNOWN -> "来源未知"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (style != null) TextButton({ viewModel.setStyleFont(style.name, font.metadata.rendererFamily) }) { Text("用于 ${style.name}") }
                }
                Divider()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QcPane(state: EditorState, viewModel: EditorViewModel, issues: List<AssQcIssue>, modifier: Modifier = Modifier) {
    Column(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
        val errors = issues.count { it.severity == AssQcSeverity.ERROR }
        val warnings = issues.count { it.severity == AssQcSeverity.WARNING }
        Text("质量检查 · ${issues.size}", style = MaterialTheme.typography.titleSmall)
        Text("$errors error · $warnings warning · ${issues.size-errors-warnings} info", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Divider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(issues) { issue ->
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = { viewModel.focusEvent(issue.eventId, true) }, onLongClick = { viewModel.focusEvent(issue.eventId, true) }).padding(vertical = WorkbenchDimens.Small),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        when (issue.severity) { AssQcSeverity.ERROR -> "!"; AssQcSeverity.WARNING -> "⚠"; AssQcSeverity.INFO -> "•" },
                        color = when (issue.severity) { AssQcSeverity.ERROR -> MaterialTheme.colorScheme.error; AssQcSeverity.WARNING -> MaterialTheme.colorScheme.tertiary; AssQcSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant }
                    )
                    Column(Modifier.weight(1f)) {
                        Text("#${issue.eventId} · ${issue.message}")
                        Text(issue.kind.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Divider()
            }
        }
    }
}

@Composable
private fun BatchPane(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    Column(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("已选 ${state.selectedEventIds.size} 条", style = MaterialTheme.typography.titleSmall)
        if (state.selectedEventIds.isEmpty()) { Text("长按字幕进入多选。"); return }
        Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
            OutlinedButton({ viewModel.shiftSelected(-100) }, Modifier.weight(1f)) { Text("−100 ms") }
            OutlinedButton({ viewModel.shiftSelected(100) }, Modifier.weight(1f)) { Text("+100 ms") }
        }
        Button(viewModel::alignSelectedStartToPlayback, Modifier.fillMaxWidth()) { Text("第一条对齐播放头") }
        Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
            OutlinedButton({ viewModel.setSelectedComment(false) }, Modifier.weight(1f)) { Text("Dialogue") }
            OutlinedButton({ viewModel.setSelectedComment(true) }, Modifier.weight(1f)) { Text("Comment") }
        }
        OutlinedButton(viewModel::clearSelectedStyleOverrides, Modifier.fillMaxWidth()) { Text("清除 Style / 位置覆盖") }
        OutlinedButton(viewModel::copyFocusedFormatToClipboard, Modifier.fillMaxWidth(), enabled = state.focusedEventId != null) { Text("复制当前字幕格式") }
        Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
            OutlinedButton({ viewModel.pasteFormatClipboardToSelected(EventFormatPasteMode.STYLE) }, Modifier.weight(1f)) { Text("粘贴 Style") }
            OutlinedButton({ viewModel.pasteFormatClipboardToSelected(EventFormatPasteMode.ALL) }, Modifier.weight(1f)) { Text("粘贴全部格式") }
        }
        Button(viewModel::deleteSelectedOrFocused, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(4.dp)); Text("删除已选字幕") }
    }
}

@Composable
private fun ProjectPane(state: EditorState, viewModel: EditorViewModel, onSaveMkv: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (state.container.uri != null) "MKV 工程" else "独立 ASS 工程", style = MaterialTheme.typography.titleSmall)
        Text(state.project.title)
        Text("PlayRes ${state.document.playResX}×${state.document.playResY} · ${state.document.styles.size} Style · ${state.document.events.size} Event", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.container.uri != null) { Divider(); ContainerBridgePanel(state.container, viewModel, onSaveMkv) }
        else Text(if (state.project.videoUri == null) "未附加参考视频" else "已附加参考视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DiagnosticsPane(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    val event = state.document.events.firstOrNull { it.id == state.focusedEventId }
    val effective = event?.let { AssEffectiveInspector.inspect(state.document, it) }.orEmpty()
    LazyColumn(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
        item {
            Text("渲染几何指纹", style = MaterialTheme.typography.titleSmall)
            Text("PlayRes ${state.document.playResX}×${state.document.playResY}")
            state.document.scriptInfo["LayoutResX"]?.let { Text("LayoutResX $it") }
            state.document.scriptInfo["LayoutResY"]?.let { Text("LayoutResY $it") }
            Text("ScaledBorderAndShadow ${state.document.scriptInfo["ScaledBorderAndShadow"] ?: "—"}")
        }
        if (event != null) {
            item { Divider(); Text("Event #${event.id} · ${event.style}", style = MaterialTheme.typography.titleSmall); Text("Margins ${event.marginL}/${event.marginR}/${event.marginV} · Layer ${event.layer}") }
            items(effective) { value ->
                Text("${value.name}: ${value.effectiveValue}" + (value.overrideValue?.let { " · override $it" } ?: "") + (value.eventValue?.let { " · event $it" } ?: ""), style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            Divider(); Text("Renderer", style = MaterialTheme.typography.titleSmall)
            if (state.rendererDiagnostics.isEmpty()) Text("暂无 renderer 诊断")
            else state.rendererDiagnostics.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = viewModel::rebuildRendererFontCache) { Text("重建字体缓存") }
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val h = total / 3_600_000
    val m = (total % 3_600_000) / 60_000
    val s = (total % 60_000) / 1000
    val cs = (total % 1000) / 10
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
}

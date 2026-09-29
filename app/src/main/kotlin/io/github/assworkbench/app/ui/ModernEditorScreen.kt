package io.github.assworkbench.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onOpenReferenceVideo: () -> Unit,
    onOpenMkvProject: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onImportFont: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
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
    val issues by produceState<List<AssQcIssue>>(initialValue = emptyList(), state.document) {
        value = withContext(Dispatchers.Default) {
            AssQualityCheck.inspect(state.document)
        }
    }
    val issuesByEvent = remember(issues) { issues.groupBy { it.eventId } }

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            ModernAppBar(
                state = state,
                viewModel = viewModel,
                selectionMode = state.selectedEventIds.isNotEmpty(),
                searchOpen = searchOpen,
                onSearchToggle = { searchOpen = !searchOpen },
                onOpenMenu = { openMenu = true },
                openMenu = openMenu,
                onDismissMenu = { openMenu = false },
                onOpenVideo = onOpenReferenceVideo,
                onOpenMkvProject = {
                    val hasWorkspace = state.subtitleLoaded || state.project.videoUri != null ||
                        state.container.uri != null || state.dirty
                    if (hasWorkspace) mkvConfirmOpen = true else onOpenMkvProject()
                },
                onOpenSubtitle = onOpenSubtitle,
                onImportFont = onImportFont,
                onSave = {
                    if (state.project.subtitleUri == null) onSaveAs()
                    else saveConfirmOpen = true
                },
                onSaveMkv = onSaveMkv,
                onTool = ::openTool,
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

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val landscape = maxWidth > maxHeight && maxWidth >= WorkbenchDimens.CompactWidth

                if (landscape) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .weight(0.58f)
                                .fillMaxHeight()
                                .background(Color.Black),
                        ) {
                            WorkbenchPreview(
                                state = state,
                                viewModel = viewModel,
                                positionEditing = supportingOpen && tool == WorkbenchTool.POSITION,
                                onOpenVideo = onOpenReferenceVideo,
                                onOpenTimeline = { toggleTool(WorkbenchTool.TIMELINE) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(Modifier.weight(0.42f).fillMaxHeight()) {
                            if (searchOpen) {
                                SearchStrip(state.query, viewModel::setQuery) {
                                    viewModel.setQuery("")
                                    searchOpen = false
                                }
                            }
                            WorkbenchEventArea(
                                state = state,
                                viewModel = viewModel,
                                issues = issues,
                                issuesByEvent = issuesByEvent,
                                expandedEventId = expandedEventId,
                                onExpandedChange = { expandedEventId = it },
                                onTool = ::toggleTool,
                                supportingOpen = supportingOpen,
                                tool = tool,
                                onCloseSupporting = { supportingOpen = false },
                                onImportFont = onImportFont,
                                onSaveMkv = onSaveMkv,
                                forceOverlay = true,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        WorkbenchPreview(
                            state = state,
                            viewModel = viewModel,
                            positionEditing = supportingOpen && tool == WorkbenchTool.POSITION,
                            onOpenVideo = onOpenReferenceVideo,
                            onOpenTimeline = { toggleTool(WorkbenchTool.TIMELINE) },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (searchOpen) {
                            SearchStrip(state.query, viewModel::setQuery) {
                                viewModel.setQuery("")
                                searchOpen = false
                            }
                        }

                        WorkbenchEventArea(
                            state = state,
                            viewModel = viewModel,
                            issues = issues,
                            issuesByEvent = issuesByEvent,
                            expandedEventId = expandedEventId,
                            onExpandedChange = { expandedEventId = it },
                            onTool = ::toggleTool,
                            supportingOpen = supportingOpen,
                            tool = tool,
                            onCloseSupporting = { supportingOpen = false },
                            onImportFont = onImportFont,
                            onSaveMkv = onSaveMkv,
                            forceOverlay = false,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkbenchPreview(
    state: EditorState,
    viewModel: EditorViewModel,
    positionEditing: Boolean,
    onOpenVideo: () -> Unit,
    onOpenTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VideoPreview(
        videoUri = state.project.videoUri,
        document = state.document,
        renderDocument = state.previewDocument ?: state.document,
        seekRequestMs = state.seekRequestMs,
        seekRequestNonce = state.seekRequestNonce,
        onPosition = viewModel::setPlaybackPosition,
        onRendererDiagnostics = viewModel::updateRendererDiagnostics,
        configDir = viewModel.rendererConfigDir(),
        fontsDir = viewModel.rendererFontsDir(),
        fontRevision = state.fontRevision,
        initialPositionMs = state.playbackPositionMs,
        focusedEventId = state.focusedEventId,
        positionEditEventId = if (positionEditing) state.focusedEventId else null,
        onPreviewEventPosition = viewModel::previewFocusedPosition,
        onSetEventPosition = viewModel::setFocusedPosition,
        onPreviewEventMove = viewModel::previewFocusedMove,
        onSetEventMove = viewModel::setFocusedMove,
        onPreviewEventOrigin = viewModel::previewFocusedOrigin,
        onSetEventOrigin = viewModel::setFocusedOrigin,
        onPreviewEventRotation = viewModel::previewFocusedRotationZ,
        onSetEventRotation = viewModel::setFocusedRotationZ,
        scaleLocked = state.geometryScaleLocked,
        onPreviewEventScale = viewModel::previewFocusedScale,
        onSetEventScale = viewModel::setFocusedScale,
        onPreviewEventShear = viewModel::previewFocusedShear,
        onSetEventShear = viewModel::setFocusedShear,
        onCancelEventPositionPreview = viewModel::clearTransientPreview,
        onFocusEvent = { viewModel.focusEvent(it, seek = false) },
        onSetEventTiming = viewModel::setEventTiming,
        onOpenVideo = onOpenVideo,
        onOpenTimeline = onOpenTimeline,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkbenchEventArea(
    state: EditorState,
    viewModel: EditorViewModel,
    issues: List<AssQcIssue>,
    issuesByEvent: Map<Long, List<AssQcIssue>>,
    expandedEventId: Long?,
    onExpandedChange: (Long?) -> Unit,
    onTool: (WorkbenchTool) -> Unit,
    supportingOpen: Boolean,
    tool: WorkbenchTool,
    onCloseSupporting: () -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    forceOverlay: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val compact = !forceOverlay && maxWidth < WorkbenchDimens.CompactWidth

        EventWorkspace(
            state = state,
            viewModel = viewModel,
            issuesByEvent = issuesByEvent,
            expandedEventId = expandedEventId,
            onExpandedChange = onExpandedChange,
            onTool = onTool,
            modifier = Modifier.fillMaxSize(),
        )

        if (supportingOpen) {
            if (compact) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                ModalBottomSheet(
                    onDismissRequest = onCloseSupporting,
                    sheetState = sheetState,
                ) {
                    SupportingWorkbench(
                        state = state,
                        viewModel = viewModel,
                        tool = tool,
                        issues = issues,
                        onClose = onCloseSupporting,
                        onImportFont = onImportFont,
                        onSaveMkv = onSaveMkv,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 224.dp, max = 640.dp),
                    )
                }
            } else {
                val overlayFraction = if (forceOverlay) 0.82f else 0.48f
                val minOverlay = if (forceOverlay) 280.dp else 320.dp
                val overlayWidth = (maxWidth * overlayFraction).coerceIn(minOverlay, 480.dp)
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = WorkbenchDimens.Micro)
                        .fillMaxHeight()
                        .width(overlayWidth),
                    shape = RoundedCornerShape(
                        topStart = WorkbenchDimens.Large,
                        bottomStart = WorkbenchDimens.Large,
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.72f),
                    ),
                    tonalElevation = 8.dp,
                    shadowElevation = 18.dp,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                        )
                        SupportingWorkbench(
                            state = state,
                            viewModel = viewModel,
                            tool = tool,
                            issues = issues,
                            onClose = onCloseSupporting,
                            onImportFont = onImportFont,
                            onSaveMkv = onSaveMkv,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
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
        val rangeScrollScope = rememberCoroutineScope()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.filteredEvents) {
                    var lastPreviewId: Long? = null
                    var lastAutoScrollAt = 0L
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                offset.y.toInt() in it.offset..(it.offset + it.size)
                            }
                            val eventId = item?.index?.let { index -> state.filteredEvents.getOrNull(index)?.id }
                            if (eventId != null) {
                                lastPreviewId = eventId
                                viewModel.beginRangeSelection(eventId)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val y = change.position.y
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                y.toInt() in it.offset..(it.offset + it.size)
                            }
                            val eventId = item?.index?.let { index -> state.filteredEvents.getOrNull(index)?.id }
                            if (eventId != null && eventId != lastPreviewId) {
                                lastPreviewId = eventId
                                viewModel.previewRangeSelection(eventId)
                            }

                            val edge = 64.dp.toPx()
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastAutoScrollAt >= 60L) {
                                val delta = when {
                                    y < edge -> -28.dp.toPx()
                                    y > size.height - edge -> 28.dp.toPx()
                                    else -> 0f
                                }
                                if (delta != 0f) {
                                    lastAutoScrollAt = now
                                    rangeScrollScope.launch { listState.scrollBy(delta) }
                                }
                            }
                        },
                        onDragEnd = {
                            lastPreviewId = null
                            viewModel.finishRangeSelection()
                        },
                        onDragCancel = {
                            lastPreviewId = null
                            viewModel.finishRangeSelection()
                        },
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
    var inlinePanel by remember(event.id) { mutableStateOf<String?>(null) }
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
        // Stable feature-level entrances. The summaries follow familiar ASS override
        // order without promoting every individual tag into its own button.
        AssistChip(
            onClick = { onTool(WorkbenchTool.FONTS) },
            label = { Text("字体 · $font") },
        )
        AssistChip(
            onClick = { onTool(WorkbenchTool.STYLE) },
            label = { Text("Style · $styleName · fs$size · b$bold/i$italic · bord$border") },
        )
        AssistChip(
            onClick = { onTool(WorkbenchTool.POSITION) },
            label = { Text("位置 · an$alignment · V$marginV" + if (pos != "alignment anchor") " · pos" else "") },
        )
        AssistChip(
            onClick = { inlinePanel = if (inlinePanel == "effects") null else "effects" },
            label = { Text(if (inlinePanel == "effects") "收起效果" else "效果") },
        )
        AssistChip(
            onClick = { inlinePanel = if (inlinePanel == "event") null else "event" },
            label = {
                Text(
                    "Event · L${event.layer}" +
                        (if (event.name.isNotBlank()) " · ${event.name}" else "") +
                        (if (event.comment) " · Comment" else "")
                )
            },
        )
    }

    if (inlinePanel == "effects") InlineEffectsEditor(event, viewModel)

    if (inlinePanel == "event") {
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
                        inlinePanel = null
                    }) { Text("取消") }
                    Button(onClick = {
                        viewModel.updateFocusedMetadata(
                            layer = layerText.toIntOrNull() ?: event.layer,
                            actor = actorText,
                            comment = comment,
                        )
                        inlinePanel = null
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

    fun previewEffects(blurValue: Double? = blur.toDoubleOrNull()) {
        viewModel.previewEventOverrides(
            id = event.id,
            x = snapshot.x,
            y = snapshot.y,
            blur = blurValue,
            fadeInMs = fadeIn.toIntOrNull(),
            fadeOutMs = fadeOut.toIntOrNull(),
            softEntry = softEntry,
        )
    }

    fun commitEffects() {
        viewModel.applyEventOverrides(
            id = event.id,
            x = snapshot.x,
            y = snapshot.y,
            blur = blur.toDoubleOrNull(),
            fadeInMs = fadeIn.toIntOrNull(),
            fadeOutMs = fadeOut.toIntOrNull(),
            softEntry = softEntry,
        )
    }

    DisposableEffect(event.id) {
        onDispose { viewModel.clearTransientPreview() }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(WorkbenchDimens.Small),
            verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
        ) {
            ContinuousParameterControl(
                label = "Blur",
                valueText = blur,
                onValueTextChange = { blur = it },
                range = 0f..20f,
                step = 0.1,
                supportingText = "连续拖动直接送入 libass transient preview。",
                onPreview = { previewEffects(it) },
                onGestureActive = { active ->
                    if (!active) commitEffects()
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
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
                Button(onClick = ::commitEffects) { Text("应用") }
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
    if (event == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("先选择一条字幕") }
        return
    }
    val style = state.document.styles.firstOrNull { it.name == event.style }
    val geometry = remember(event.text) { AssGeometrySemantic.inspect(event.text) }
    val effective = remember(state.document, event) {
        AssEffectiveInspector.inspect(state.document, event).associateBy { it.name }
    }

    var x by remember(event.id, event.text) { mutableStateOf(geometry.position?.x?.toString().orEmpty()) }
    var y by remember(event.id, event.text) { mutableStateOf(geometry.position?.y?.toString().orEmpty()) }
    var moveStartX by remember(event.id, event.text) { mutableStateOf(geometry.move?.start?.x?.toString().orEmpty()) }
    var moveStartY by remember(event.id, event.text) { mutableStateOf(geometry.move?.start?.y?.toString().orEmpty()) }
    var moveEndX by remember(event.id, event.text) { mutableStateOf(geometry.move?.end?.x?.toString().orEmpty()) }
    var moveEndY by remember(event.id, event.text) { mutableStateOf(geometry.move?.end?.y?.toString().orEmpty()) }
    var originX by remember(event.id, event.text) { mutableStateOf(geometry.origin?.x?.toString().orEmpty()) }
    var originY by remember(event.id, event.text) { mutableStateOf(geometry.origin?.y?.toString().orEmpty()) }
    var rotationText by remember(event.id, event.text, style?.angle) {
        mutableStateOf((geometry.rotationZ ?: style?.angle ?: 0.0).toString())
    }
    var rotationGestureActive by remember(event.id) { mutableStateOf(false) }
    var rotationDraftChanged by remember(event.id) { mutableStateOf(false) }
    var rotationPreviewValue by remember(event.id, event.text, style?.angle) {
        mutableStateOf(geometry.rotationZ ?: style?.angle ?: 0.0)
    }
    val effectiveScaleX = geometry.scaleX ?: style?.scaleX ?: 100.0
    val effectiveScaleY = geometry.scaleY ?: style?.scaleY ?: 100.0
    var scaleXText by remember(event.id, event.text, style?.scaleX) { mutableStateOf(effectiveScaleX.toString()) }
    var scaleYText by remember(event.id, event.text, style?.scaleY) { mutableStateOf(effectiveScaleY.toString()) }
    var scaleGestureActive by remember(event.id) { mutableStateOf(false) }
    var scaleDraftChanged by remember(event.id) { mutableStateOf(false) }
    var scalePreviewX by remember(event.id, event.text, style?.scaleX) { mutableStateOf(effectiveScaleX) }
    var scalePreviewY by remember(event.id, event.text, style?.scaleY) { mutableStateOf(effectiveScaleY) }
    var shearXText by remember(event.id, event.text) { mutableStateOf((geometry.shearX ?: 0.0).toString()) }
    var shearYText by remember(event.id, event.text) { mutableStateOf((geometry.shearY ?: 0.0).toString()) }
    var shearGestureActive by remember(event.id) { mutableStateOf(false) }
    var shearDraftChanged by remember(event.id) { mutableStateOf(false) }
    var shearPreviewX by remember(event.id, event.text) { mutableStateOf(geometry.shearX ?: 0.0) }
    var shearPreviewY by remember(event.id, event.text) { mutableStateOf(geometry.shearY ?: 0.0) }
    val scaleRatioYPerX = remember(event.id, event.text, style?.scaleX, style?.scaleY) {
        if (effectiveScaleX != 0.0) effectiveScaleY / effectiveScaleX else 1.0
    }
    fun formatScale(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
    var styleAlignment by remember(style?.name, style?.alignment) { mutableIntStateOf(style?.alignment ?: 2) }
    var styleMarginL by remember(style?.name, style?.marginL) { mutableStateOf((style?.marginL ?: 0).toString()) }
    var styleMarginR by remember(style?.name, style?.marginR) { mutableStateOf((style?.marginR ?: 0).toString()) }
    var styleMarginV by remember(style?.name, style?.marginV) { mutableStateOf((style?.marginV ?: 0).toString()) }

    LaunchedEffect(event.id, rotationText, rotationGestureActive, rotationDraftChanged) {
        if (!rotationDraftChanged || rotationGestureActive) return@LaunchedEffect
        val value = rotationText.toDoubleOrNull() ?: return@LaunchedEffect
        viewModel.previewFocusedRotationZ(value)
        kotlinx.coroutines.delay(320)
        viewModel.setFocusedRotationZ(value)
        rotationDraftChanged = false
    }
    LaunchedEffect(event.id, scaleXText, scaleYText, scaleGestureActive, scaleDraftChanged) {
        if (!scaleDraftChanged || scaleGestureActive) return@LaunchedEffect
        val sx = scaleXText.toDoubleOrNull() ?: return@LaunchedEffect
        val sy = scaleYText.toDoubleOrNull() ?: return@LaunchedEffect
        scalePreviewX = sx
        scalePreviewY = sy
        viewModel.previewFocusedScale(sx, sy)
        kotlinx.coroutines.delay(320)
        viewModel.setFocusedScale(sx, sy)
        scaleDraftChanged = false
    }
    LaunchedEffect(event.id, shearXText, shearYText, shearGestureActive, shearDraftChanged) {
        if (!shearDraftChanged || shearGestureActive) return@LaunchedEffect
        val fx = shearXText.toDoubleOrNull() ?: return@LaunchedEffect
        val fy = shearYText.toDoubleOrNull() ?: return@LaunchedEffect
        shearPreviewX = fx
        shearPreviewY = fy
        viewModel.previewFocusedShear(fx, fy)
        kotlinx.coroutines.delay(320)
        viewModel.setFocusedShear(fx, fy)
        shearDraftChanged = false
    }

    DisposableEffect(event.id) {
        onDispose { viewModel.clearTransientPreview() }
    }

    LazyColumn(
        modifier.padding(WorkbenchDimens.Small),
        verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
    ) {
        item {
            Text("当前 Event", style = MaterialTheme.typography.titleSmall)
            Text(
                "Effective: an${effective["Alignment"]?.effectiveValue} · V${effective["Margin V"]?.effectiveValue} · ${effective["Position"]?.effectiveValue}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text("Event 对齐覆盖")
            Column(verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3)).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                        row.forEach { value ->
                            OutlinedButton(
                                onClick = { viewModel.setFocusedAlignment(value) },
                                modifier = Modifier.weight(1f),
                            ) { Text(value.toString()) }
                        }
                    }
                }
            }
        }
        if (geometry.positionMode == AssPositionMode.MOVE && geometry.move != null) {
            item {
                Text("运动路径 · \\move", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                    OutlinedTextField(moveStartX, { moveStartX = it }, label = { Text("Start X") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(moveStartY, { moveStartY = it }, label = { Text("Start Y") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                    OutlinedTextField(moveEndX, { moveEndX = it }, label = { Text("End X") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(moveEndY, { moveEndY = it }, label = { Text("End Y") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (geometry.move.startMs != null && geometry.move.endMs != null) {
                            "Timing ${geometry.move.startMs.toInt()}–${geometry.move.endMs.toInt()} ms · 编辑端点时原样保留"
                        } else {
                            "Timing：整个 Event 时长 · 4 参数 move"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {
                        val sx = moveStartX.toDoubleOrNull()
                        val sy = moveStartY.toDoubleOrNull()
                        val ex = moveEndX.toDoubleOrNull()
                        val ey = moveEndY.toDoubleOrNull()
                        if (sx != null && sy != null && ex != null && ey != null) {
                            viewModel.setFocusedMove(sx, sy, ex, ey)
                        }
                    }) { Text("应用路径") }
                }
                Text(
                    "预览上的空心圆是 Start，实心圆是 End；拖任一端点都会实时走 libass transient preview。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Divider() }
        item {
            Text("变换原点 · \\org", style = MaterialTheme.typography.titleSmall)
            if (geometry.origin != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                    OutlinedTextField(originX, { originX = it }, label = { Text("Origin X") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(originY, { originY = it }, label = { Text("Origin Y") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = viewModel::clearFocusedOrigin) { Text("移除 \\org") }
                    Button(onClick = {
                        val ox = originX.toDoubleOrNull()
                        val oy = originY.toDoubleOrNull()
                        if (ox != null && oy != null) viewModel.setFocusedOrigin(ox, oy)
                    }) { Text("应用原点") }
                }
                Text(
                    "预览上的圆环叉标记是显式变换原点；可直接拖动。精确值允许超出画布范围。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "当前没有显式 \\org。ASS 将使用默认变换原点；工作台不会伪造一个可拖动标记。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = {
                    viewModel.setFocusedOrigin(
                        state.document.playResX / 2.0,
                        state.document.playResY / 2.0,
                    )
                }) { Text("在画布中心添加 \\org") }
            }
        }
        item { Divider() }
        item {
            Text("旋转 · \\frz", style = MaterialTheme.typography.titleSmall)
            ContinuousParameterControl(
                label = "Rotation Z",
                valueText = rotationText,
                onValueTextChange = {
                    rotationText = it
                    rotationDraftChanged = true
                },
                range = -180f..180f,
                step = 1.0,
                suffix = "°",
                supportingText = if (geometry.rotationZ != null) {
                    "Event override：${geometry.rotationZ}° · 画布旋转手柄和数值控制共享同一语义。"
                } else {
                    "继承 Style：${style?.angle ?: 0.0}° · 首次编辑会创建 \\frz。"
                },
                resetLabel = if (geometry.rotationZ != null) "继承 Style" else null,
                onReset = if (geometry.rotationZ != null) ({
                    rotationDraftChanged = false
                    rotationGestureActive = false
                    rotationText = (style?.angle ?: 0.0).toString()
                    viewModel.clearFocusedRotationZ()
                }) else null,
                onPreview = { value ->
                    rotationPreviewValue = value
                    viewModel.previewFocusedRotationZ(value)
                },
                onGestureActive = { active ->
                    rotationGestureActive = active
                    if (!active) {
                        viewModel.setFocusedRotationZ(rotationPreviewValue)
                        rotationDraftChanged = false
                    }
                },
            )
            Text(
                if (geometry.positionMode == AssPositionMode.MOVE && geometry.origin == null) {
                    "当前是 \\move 且没有显式 \\org：数值/Slider 可用，但画布旋转手柄暂不显示，因为默认旋转中心随运动位置变化。"
                } else {
                    "画布上的旋转手柄围绕显式 \\org；没有 \\org 时，静态字幕围绕当前定位锚点。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Divider() }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("缩放 · \\fscx / \\fscy", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "\\fs 是字号；\\fscx / \\fscy 是排版后的百分比缩放。100% 表示不额外缩放。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = state.geometryScaleLocked,
                    onClick = { viewModel.setGeometryScaleLocked(!state.geometryScaleLocked) },
                    label = { Text(if (state.geometryScaleLocked) "比例锁定" else "独立 X/Y") },
                )
            }
            ContinuousParameterControl(
                label = "Scale X",
                valueText = scaleXText,
                onValueTextChange = { raw ->
                    scaleXText = raw
                    raw.toDoubleOrNull()?.let { sx ->
                        if (state.geometryScaleLocked) scaleYText = formatScale(sx * scaleRatioYPerX)
                    }
                    scaleDraftChanged = true
                },
                range = 10f..400f,
                step = 1.0,
                suffix = "%",
                supportingText = if (geometry.scaleX != null) "Event override：${geometry.scaleX}%" else "继承 Style：${style?.scaleX ?: 100.0}%",
                onPreview = { sx ->
                    val sy = if (state.geometryScaleLocked) sx * scaleRatioYPerX else scaleYText.toDoubleOrNull() ?: effectiveScaleY
                    if (state.geometryScaleLocked) scaleYText = formatScale(sy)
                    scalePreviewX = sx
                    scalePreviewY = sy
                    viewModel.previewFocusedScale(sx, sy)
                },
                onGestureActive = { active ->
                    scaleGestureActive = active
                    if (!active) {
                        viewModel.setFocusedScale(scalePreviewX, scalePreviewY)
                        scaleDraftChanged = false
                    }
                },
            )
            ContinuousParameterControl(
                label = "Scale Y",
                valueText = scaleYText,
                onValueTextChange = { raw ->
                    scaleYText = raw
                    raw.toDoubleOrNull()?.let { sy ->
                        if (state.geometryScaleLocked && scaleRatioYPerX != 0.0) scaleXText = formatScale(sy / scaleRatioYPerX)
                    }
                    scaleDraftChanged = true
                },
                range = 10f..400f,
                step = 1.0,
                suffix = "%",
                supportingText = if (geometry.scaleY != null) "Event override：${geometry.scaleY}%" else "继承 Style：${style?.scaleY ?: 100.0}%",
                onPreview = { sy ->
                    val sx = if (state.geometryScaleLocked && scaleRatioYPerX != 0.0) sy / scaleRatioYPerX else scaleXText.toDoubleOrNull() ?: effectiveScaleX
                    if (state.geometryScaleLocked) scaleXText = formatScale(sx)
                    scalePreviewX = sx
                    scalePreviewY = sy
                    viewModel.previewFocusedScale(sx, sy)
                },
                onGestureActive = { active ->
                    scaleGestureActive = active
                    if (!active) {
                        viewModel.setFocusedScale(scalePreviewX, scalePreviewY)
                        scaleDraftChanged = false
                    }
                },
            )
            if (geometry.scaleX != null || geometry.scaleY != null) {
                TextButton(onClick = {
                    scaleDraftChanged = false
                    scaleGestureActive = false
                    scaleXText = (style?.scaleX ?: 100.0).toString()
                    scaleYText = (style?.scaleY ?: 100.0).toString()
                    scalePreviewX = style?.scaleX ?: 100.0
                    scalePreviewY = style?.scaleY ?: 100.0
                    viewModel.clearFocusedScale()
                }) { Text("继承 Style Scale") }
            }
            Text(
                if (geometry.positionMode == AssPositionMode.MOVE || geometry.positionMode == AssPositionMode.CONFLICT) {
                    "运动/冲突位置下仍可用数值与 Slider；画布 Scale gizmo 暂只用于静态位置，避免伪造随时间移动的锚点。"
                } else {
                    "画布上的 Scale gizmo 是参数控制框，不冒充 libass 的真实文字边界；拖右上角控制点可同时调 X/Y。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Divider() }
        item {
            Text("错切 · \\fax / \\fay", style = MaterialTheme.typography.titleSmall)
            Text(
                "\\fax / \\fay 是几何错切因子，不是斜体。0 表示无错切；X 改变水平倾斜，Y 改变垂直倾斜。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ContinuousParameterControl(
                label = "Shear X · \\fax",
                valueText = shearXText,
                onValueTextChange = {
                    shearXText = it
                    shearDraftChanged = true
                },
                range = -2f..2f,
                step = 0.05,
                supportingText = if (geometry.shearX != null) "Event override：${geometry.shearX}" else "无 override：0",
                onPreview = { fx ->
                    val fy = shearYText.toDoubleOrNull() ?: 0.0
                    shearPreviewX = fx
                    shearPreviewY = fy
                    viewModel.previewFocusedShear(fx, fy)
                },
                onGestureActive = { active ->
                    shearGestureActive = active
                    if (!active) {
                        viewModel.setFocusedShear(shearPreviewX, shearPreviewY)
                        shearDraftChanged = false
                    }
                },
            )
            ContinuousParameterControl(
                label = "Shear Y · \\fay",
                valueText = shearYText,
                onValueTextChange = {
                    shearYText = it
                    shearDraftChanged = true
                },
                range = -2f..2f,
                step = 0.05,
                supportingText = if (geometry.shearY != null) "Event override：${geometry.shearY}" else "无 override：0",
                onPreview = { fy ->
                    val fx = shearXText.toDoubleOrNull() ?: 0.0
                    shearPreviewX = fx
                    shearPreviewY = fy
                    viewModel.previewFocusedShear(fx, fy)
                },
                onGestureActive = { active ->
                    shearGestureActive = active
                    if (!active) {
                        viewModel.setFocusedShear(shearPreviewX, shearPreviewY)
                        shearDraftChanged = false
                    }
                },
            )
            if (geometry.shearX != null || geometry.shearY != null) {
                TextButton(onClick = {
                    shearDraftChanged = false
                    shearGestureActive = false
                    shearXText = "0"
                    shearYText = "0"
                    shearPreviewX = 0.0
                    shearPreviewY = 0.0
                    viewModel.clearFocusedShear()
                }) { Text("清除 Shear override") }
            }
            Text(
                if (geometry.positionMode == AssPositionMode.MOVE || geometry.positionMode == AssPositionMode.CONFLICT) {
                    "运动/冲突位置下保留精确值和 Slider；画布 Shear gizmo 暂只用于静态位置。"
                } else {
                    "静态位置下，参数框的上边控制点编辑 \\fax，右边控制点编辑 \\fay；它仍是参数示意，不是字形真实边界。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text("任意位置")
            Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                OutlinedTextField(x, { x = it }, label = { Text("X") }, singleLine = true, enabled = geometry.positionMode != AssPositionMode.MOVE && geometry.positionMode != AssPositionMode.CONFLICT, modifier = Modifier.weight(1f))
                OutlinedTextField(y, { y = it }, label = { Text("Y") }, singleLine = true, enabled = geometry.positionMode != AssPositionMode.MOVE && geometry.positionMode != AssPositionMode.CONFLICT, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        val px = x.toDoubleOrNull()
                        val py = y.toDoubleOrNull()
                        if (px != null && py != null) viewModel.setFocusedPosition(px, py)
                    },
                    enabled = geometry.positionMode != AssPositionMode.MOVE &&
                        geometry.positionMode != AssPositionMode.CONFLICT,
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) { Text("应用") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                listOf(
                    "←" to (-5.0 to 0.0), "→" to (5.0 to 0.0),
                    "↑" to (0.0 to -5.0), "↓" to (0.0 to 5.0),
                ).forEach { (label, delta) ->
                    OutlinedButton(
                        onClick = { viewModel.nudgeEventPosition(event.id, delta.first, delta.second) },
                        enabled = geometry.positionMode != AssPositionMode.MOVE && geometry.positionMode != AssPositionMode.CONFLICT,
                        modifier = Modifier.weight(1f),
                    ) { Text(label) }
                }
            }
            Text(
                when (geometry.positionMode) {
                    AssPositionMode.MOVE -> "当前 Event 使用 \\move；直接拖动 Start / End 编辑路径，不会隐式转换成 \\pos。"
                    AssPositionMode.CONFLICT -> "当前 Event 同时存在 \\pos 与 \\move；为避免破坏语义，直接位置编辑已暂停。"
                    AssPositionMode.POSITION -> "当前为显式 \\pos；可直接在 16:9 预览上拖动锚点。"
                    AssPositionMode.INHERITED -> "当前位置由 Alignment + Margin 推导；第一次拖动会创建显式 \\pos。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (geometry.positionMode == AssPositionMode.CONFLICT) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (style != null) {
            item { Divider() }
            item {
                Text("Style 基础位置 · ${style.name}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "这里编辑 Style 的 an / Margin；Event override 仍会覆盖这些值。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text("Style 对齐")
                Column(verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                    listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3)).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                            row.forEach { value ->
                                if (styleAlignment == value) {
                                    Button(onClick = { styleAlignment = value }, modifier = Modifier.weight(1f)) { Text(value.toString()) }
                                } else {
                                    OutlinedButton(onClick = { styleAlignment = value }, modifier = Modifier.weight(1f)) { Text(value.toString()) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                    OutlinedTextField(styleMarginL, { styleMarginL = it }, label = { Text("Margin L") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(styleMarginR, { styleMarginR = it }, label = { Text("Margin R") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(styleMarginV, { styleMarginV = it }, label = { Text("Margin V") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = {
                        viewModel.updateStylePosition(
                            styleName = style.name,
                            alignment = styleAlignment,
                            marginL = styleMarginL.toIntOrNull() ?: style.marginL,
                            marginR = styleMarginR.toIntOrNull() ?: style.marginR,
                            marginV = styleMarginV.toIntOrNull() ?: style.marginV,
                        )
                    }) { Text("应用 Style 位置") }
                }
            }
        }
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

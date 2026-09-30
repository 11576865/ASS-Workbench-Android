package io.github.assworkbench.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.github.assworkbench.app.BuildConfig
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.WaveformLiteState
import io.github.assworkbench.app.WaveformLiteStatus
import io.github.assworkbench.domain.*
import io.github.assworkbench.fonts.FontDiagnostics
import io.github.assworkbench.fonts.FontOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class WorkbenchTool(val title: String) {
    INVENTORY("功能总览"),
    TEXT("正文"), TIMELINE("时间轴"), STYLE("样式"), POSITION("位置"),
    EFFECTS("效果"), EVENT("事件"), FONTS("字体"), QC("检查"), BATCH("批量"),
    PROJECT("项目"), DIAGNOSTICS("诊断"),
}

private enum class PreviewWorkspaceMode(val title: String) {
    NORMAL("工作台"),
    FOCUS("预览聚焦"),
    MANIPULATION("操控"),
    FLOATING("浮动视频"),
}

private enum class DestructiveWorkspaceAction { OPEN_ASS, NEW_ASS }

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
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("workbench-ui", 0) }
    var themeMode by rememberSaveable { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
    val darkTheme = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    var toolName by rememberSaveable { mutableStateOf(WorkbenchTool.TEXT.name) }
    var supportingOpen by rememberSaveable { mutableStateOf(false) }
    var expandedEventId by rememberSaveable { mutableStateOf<Long?>(null) }
    val eventEditorStateHolder = rememberSaveableStateHolder()
    var previewVisible by rememberSaveable { mutableStateOf(true) }
    var videoAspectRatio by rememberSaveable(state.project.videoUri) { mutableStateOf(16f / 9f) }
    var landscapePreviewWidthDp by rememberSaveable {
        mutableStateOf(
            if (preferences.contains("landscape-preview-width-dp")) {
                preferences.getFloat("landscape-preview-width-dp", 0f).takeIf { it > 0f }
            } else {
                null
            },
        )
    }
    var previewModeName by rememberSaveable { mutableStateOf(PreviewWorkspaceMode.NORMAL.name) }
    var openSurfaceNames by rememberSaveable { mutableStateOf(WorkbenchTool.INVENTORY.name) }
    var floatingSurfacesHidden by rememberSaveable { mutableStateOf(false) }
    val floatingSurfaceOffsetsDp = remember { mutableStateMapOf<String, Offset>() }
    val floatingSurfaceExpanded = remember { mutableStateMapOf<String, Boolean>() }
    val floatingSurfaceZ = remember { mutableStateMapOf<String, Float>() }
    var nextFloatingSurfaceZ by remember { mutableStateOf(10f) }
    var floatingVideoOffsetDp by remember { mutableStateOf(Offset(36f, 72f)) }
    var floatingVideoExpanded by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var openMenu by remember { mutableStateOf(false) }
    var saveConfirmOpen by remember { mutableStateOf(false) }
    var mkvConfirmOpen by remember { mutableStateOf(false) }
    var destructiveWorkspaceAction by remember { mutableStateOf<DestructiveWorkspaceAction?>(null) }

    val tool = WorkbenchTool.entries.firstOrNull { it.name == toolName } ?: WorkbenchTool.TEXT
    val previewMode = PreviewWorkspaceMode.entries.firstOrNull { it.name == previewModeName }
        ?: PreviewWorkspaceMode.NORMAL
    val openSurfaces = remember(openSurfaceNames) {
        openSurfaceNames.split(';').mapNotNull { name ->
            WorkbenchTool.entries.firstOrNull { it.name == name }
        }.toSet()
    }
    val issues by produceState<List<AssQcIssue>>(initialValue = emptyList(), state.document) {
        value = withContext(Dispatchers.Default) {
            AssQualityCheck.inspect(state.document)
        }
    }
    val issuesByEvent = remember(issues) { issues.groupBy { it.eventId } }

    fun setOpenSurfaces(next: Set<WorkbenchTool>) {
        openSurfaceNames = next.joinToString(";") { it.name }
    }

    fun raiseSurface(next: WorkbenchTool) {
        nextFloatingSurfaceZ += 1f
        floatingSurfaceZ[next.name] = nextFloatingSurfaceZ
    }

    fun openTool(next: WorkbenchTool) {
        toolName = next.name
        supportingOpen = true
        if (next !in openSurfaces) setOpenSurfaces(openSurfaces + next)
        floatingSurfacesHidden = false
        raiseSurface(next)
    }

    fun toggleTool(next: WorkbenchTool) {
        toolName = next.name
        supportingOpen = true
        setOpenSurfaces(if (next in openSurfaces) openSurfaces - next else openSurfaces + next)
        floatingSurfacesHidden = false
        if (next !in openSurfaces) raiseSurface(next)
    }

    MaterialTheme(colorScheme = workbenchColors(darkTheme)) {
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
                onOpenSubtitle = {
                    if (state.dirty) {
                        destructiveWorkspaceAction = DestructiveWorkspaceAction.OPEN_ASS
                    } else {
                        onOpenSubtitle()
                    }
                },
                onNewSubtitle = {
                    if (state.dirty) {
                        destructiveWorkspaceAction = DestructiveWorkspaceAction.NEW_ASS
                    } else {
                        viewModel.newSubtitleProject()
                    }
                },
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
                            if (state.dirty) {
                                "当前字幕有未保存修改。继续选择新的 MKV 会明确放弃这些修改，并清空当前字幕、参考视频、选择状态和项目字体。"
                            } else {
                                "MKV 是独立工作流。选择新的 MKV 后，当前工作台中的字幕、参考视频、选择状态和项目字体会被清空。"
                            }
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            mkvConfirmOpen = false
                            onOpenMkvProject()
                        }) { Text(if (state.dirty) "放弃修改并选择 MKV" else "选择 MKV") }
                    },
                    dismissButton = {
                        TextButton(onClick = { mkvConfirmOpen = false }) { Text("取消") }
                    },
                )
            }

            if (state.recoveryAvailable) {
                AlertDialog(
                    onDismissRequest = { /* Recovery requires an explicit choice. */ },
                    title = { Text("发现未保存编辑") },
                    text = {
                        Text(
                            buildString {
                                append("检测到上次异常退出留下的恢复记录")
                                if (state.recoveryLabel.isNotBlank()) {
                                    append("：").append(state.recoveryLabel)
                                }
                                append("。恢复后可以继续编辑并正常保存；丢弃后该恢复记录会被删除。")
                            }
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = viewModel::restoreRecovery,
                            modifier = Modifier.testTag("recovery-restore"),
                        ) { Text("恢复") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = viewModel::discardRecovery,
                            modifier = Modifier.testTag("recovery-discard"),
                        ) { Text("丢弃") }
                    },
                )
            }

            destructiveWorkspaceAction?.let { action ->
                AlertDialog(
                    onDismissRequest = { destructiveWorkspaceAction = null },
                    title = { Text("放弃未保存修改？") },
                    text = {
                        Text(
                            when (action) {
                                DestructiveWorkspaceAction.OPEN_ASS ->
                                    "当前字幕有未保存修改。继续打开另一份 ASS 会丢弃当前未保存内容与对应恢复日志。"
                                DestructiveWorkspaceAction.NEW_ASS ->
                                    "当前字幕有未保存修改。继续新建空白 ASS 会丢弃当前未保存内容与对应恢复日志。"
                            }
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            destructiveWorkspaceAction = null
                            when (action) {
                                DestructiveWorkspaceAction.OPEN_ASS -> onOpenSubtitle()
                                DestructiveWorkspaceAction.NEW_ASS -> viewModel.newSubtitleProject()
                            }
                        }) { Text("放弃修改并继续") }
                    },
                    dismissButton = {
                        TextButton(onClick = { destructiveWorkspaceAction = null }) { Text("取消") }
                    },
                )
            }

            WorkbenchToolStrip(
                selected = tool,
                openSurfaces = openSurfaces,
                onTool = ::toggleTool,
                previewVisible = previewVisible,
                onPreviewToggle = { previewVisible = !previewVisible },
                previewMode = previewMode,
                onPreviewMode = { mode ->
                    previewModeName = mode.name
                    previewVisible = true
                    if (mode == PreviewWorkspaceMode.MANIPULATION) {
                        openTool(WorkbenchTool.POSITION)
                    }
                },
                floatingSurfacesHidden = floatingSurfacesHidden,
                onToggleAllSurfaces = { floatingSurfacesHidden = !floatingSurfacesHidden },
                themeMode = themeMode,
                onThemeToggle = {
                    themeMode = when (themeMode) { "system" -> "dark"; "dark" -> "light"; else -> "system" }
                    preferences.edit().putString("theme", themeMode).apply()
                },
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
                val density = LocalDensity.current
                val splitHandleWidth = 14.dp
                val paneGap = 4.dp
                val editorMinWidth = minOf(320.dp, maxWidth * 0.46f)
                val maxPreviewWidth = (maxWidth - editorMinWidth - splitHandleWidth - paneGap * 2)
                    .coerceAtLeast(minOf(220.dp, maxWidth))
                val minPreviewWidth = minOf(280.dp, maxPreviewWidth)
                val safeAspectRatio = videoAspectRatio.takeIf { it.isFinite() && it in 0.25f..4.0f } ?: (16f / 9f)
                val aspectIdealWidth = (maxHeight * safeAspectRatio).coerceAtLeast(minPreviewWidth)
                val automaticPreviewWidth = minOf(aspectIdealWidth, maxPreviewWidth).coerceAtLeast(minPreviewWidth)
                val previewWidth = landscapePreviewWidthDp
                    ?.dp
                    ?.coerceIn(minPreviewWidth, maxPreviewWidth)
                    ?: automaticPreviewWidth
                val previewHeight = (maxHeight * 0.28f).coerceAtMost(220.dp)
                val preview: @Composable (Modifier) -> Unit = { paneModifier ->
                    Surface(
                        paneModifier,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        WorkbenchPreview(
                            state,
                            viewModel,
                            previewMode == PreviewWorkspaceMode.MANIPULATION || WorkbenchTool.POSITION in openSurfaces,
                            onOpenReferenceVideo,
                            { openTool(WorkbenchTool.TIMELINE) },
                            rendererEnabled, onEnableRenderer,
                            onVideoAspectRatio = { reported ->
                                if (reported.isFinite() && reported in 0.25f..4.0f) {
                                    videoAspectRatio = reported
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                val editor: @Composable (Modifier) -> Unit = { paneModifier ->
                    Column(paneModifier) {
                        if (searchOpen) SearchStrip(state.query, viewModel::setQuery) {
                            viewModel.setQuery(""); searchOpen = false
                        }
                        WorkbenchEventArea(
                            state, viewModel, issues, issuesByEvent, expandedEventId,
                            { expandedEventId = it }, ::openTool, supportingOpen, tool,
                            { supportingOpen = false; toolName = WorkbenchTool.TEXT.name },
                            onImportFont, onSaveMkv, landscape, eventEditorStateHolder,
                            Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
                AnimatedContent(
                    targetState = previewMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "preview-workspace-mode",
                    modifier = Modifier.fillMaxSize(),
                ) { mode ->
                    when (mode) {
                        PreviewWorkspaceMode.NORMAL -> {
                            if (landscape) {
                                Row(
                                    Modifier.fillMaxSize().padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(paneGap),
                                ) {
                                    if (previewVisible) {
                                        preview(
                                            Modifier
                                                .width(previewWidth)
                                                .fillMaxHeight()
                                                .testTag("preview-workspace"),
                                        )
                                        Box(
                                            Modifier
                                                .width(splitHandleWidth)
                                                .fillMaxHeight()
                                                .testTag("preview-divider")
                                                .pointerInput(minPreviewWidth, maxPreviewWidth) {
                                                    detectHorizontalDragGestures(
                                                        onHorizontalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            val currentWidth = landscapePreviewWidthDp?.dp ?: previewWidth
                                                            val delta = with(density) { dragAmount.toDp() }
                                                            landscapePreviewWidthDp = (currentWidth + delta)
                                                                .coerceIn(minPreviewWidth, maxPreviewWidth)
                                                                .value
                                                        },
                                                        onDragEnd = {
                                                            landscapePreviewWidthDp?.let { value ->
                                                                preferences.edit()
                                                                    .putFloat("landscape-preview-width-dp", value)
                                                                    .apply()
                                                            }
                                                        },
                                                    )
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            VerticalDivider(
                                                Modifier.width(1.dp).fillMaxHeight(0.18f),
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                            )
                                        }
                                    }
                                    editor(Modifier.weight(1f).fillMaxHeight())
                                }
                            } else {
                                Column(
                                    Modifier.fillMaxSize().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (previewVisible) {
                                        preview(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(previewHeight)
                                                .testTag("preview-workspace"),
                                        )
                                    }
                                    editor(Modifier.weight(1f).fillMaxWidth())
                                }
                            }
                        }
                        PreviewWorkspaceMode.FOCUS,
                        PreviewWorkspaceMode.MANIPULATION -> {
                            Box(Modifier.fillMaxSize().padding(8.dp)) {
                                if (previewVisible) {
                                    preview(Modifier.fillMaxSize().testTag("preview-workspace"))
                                }
                            }
                        }
                        PreviewWorkspaceMode.FLOATING -> {
                            editor(Modifier.fillMaxSize().padding(8.dp))
                        }
                    }
                }

                FloatingWorkbenchLayer(
                    state = state,
                    viewModel = viewModel,
                    issues = issues,
                    openTools = openSurfaces,
                    hidden = floatingSurfacesHidden,
                    offsetsDp = floatingSurfaceOffsetsDp,
                    expanded = floatingSurfaceExpanded,
                    zOrder = floatingSurfaceZ,
                    onOffsetChange = { name, value -> floatingSurfaceOffsetsDp[name] = value },
                    onExpandedChange = { name, value -> floatingSurfaceExpanded[name] = value },
                    onRaise = { floatingSurfaceZ[it.name] = ++nextFloatingSurfaceZ },
                    onClose = { setOpenSurfaces(openSurfaces - it) },
                    onOpenTool = ::openTool,
                    onImportFont = onImportFont,
                    onSaveMkv = onSaveMkv,
                    eventEditorStateHolder = eventEditorStateHolder,
                    modifier = Modifier.fillMaxSize().zIndex(50f),
                )

                if (previewMode == PreviewWorkspaceMode.FLOATING && previewVisible) {
                    FloatingPreviewSurface(
                        offsetDp = floatingVideoOffsetDp,
                        expanded = floatingVideoExpanded,
                        onOffsetChange = { floatingVideoOffsetDp = it },
                        onExpandedChange = { floatingVideoExpanded = it },
                        onDock = { previewModeName = PreviewWorkspaceMode.NORMAL.name },
                        modifier = Modifier.fillMaxSize().zIndex(40f),
                    ) { previewModifier ->
                        preview(previewModifier.testTag("preview-workspace"))
                    }
                }
            }
        }
    }
}

}

@Composable
private fun WorkbenchToolStrip(
    selected: WorkbenchTool,
    openSurfaces: Set<WorkbenchTool>,
    onTool: (WorkbenchTool) -> Unit,
    previewVisible: Boolean,
    onPreviewToggle: () -> Unit,
    previewMode: PreviewWorkspaceMode,
    onPreviewMode: (PreviewWorkspaceMode) -> Unit,
    floatingSurfacesHidden: Boolean,
    onToggleAllSurfaces: () -> Unit,
    themeMode: String,
    onThemeToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WorkbenchTool.entries.forEach { entry ->
                        FilterChip(
                            selected = entry in openSurfaces,
                            onClick = { onTool(entry) },
                            modifier = Modifier.testTag("tool-${entry.name}"),
                            label = { Text(entry.title) },
                            leadingIcon = if (entry in openSurfaces) {
                                { Icon(Icons.Filled.Layers, null, Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                TooltipIconButton(
                    if (floatingSurfacesHidden) "呼回所有浮层" else "隐藏所有浮层",
                    onToggleAllSurfaces,
                ) {
                    Icon(if (floatingSurfacesHidden) Icons.Filled.Layers else Icons.Filled.LayersClear, null)
                }
                TooltipIconButton(
                    "主题：" + when (themeMode) { "dark" -> "深色"; "light" -> "浅色"; else -> "跟随系统" },
                    onThemeToggle,
                ) {
                    Icon(
                        when (themeMode) {
                            "dark" -> Icons.Filled.DarkMode
                            "light" -> Icons.Filled.LightMode
                            else -> Icons.Filled.BrightnessAuto
                        },
                        null,
                    )
                }
                TooltipIconButton(if (previewVisible) "隐藏视频" else "呼回视频", onPreviewToggle) {
                    Icon(if (previewVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("视频", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PreviewWorkspaceMode.entries.forEach { mode ->
                    AssistChip(
                        onClick = { onPreviewMode(mode) },
                        label = { Text(mode.title) },
                        leadingIcon = if (previewMode == mode) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }
                        } else null,
                        shape = RoundedCornerShape(10.dp),
                    )
                }
                Text(
                    "· 浮层可并存 / 可拖动 / 再按收回",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
    onVideoAspectRatio: (Float) -> Unit = {},
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
        initialPositionMs = viewModel.playbackPositionMs.value,
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
        onPreviewEventClip = viewModel::previewFocusedRectClip,
        onSetEventClip = viewModel::setFocusedRectClip,
        onCancelEventPositionPreview = viewModel::clearTransientPreview,
        onFocusEvent = { viewModel.focusEvent(it, seek = false) },
        onSetEventTiming = viewModel::setEventTiming,
        onOpenVideo = onOpenVideo,
        onOpenTimeline = onOpenTimeline,
        rendererEnabled = rendererEnabled,
        onEnableRenderer = onEnableRenderer,
        fillViewport = true,
        onVideoAspectRatio = onVideoAspectRatio,
        modifier = modifier,
    )
}

@Composable
private fun WorkbenchEventArea(
    state: EditorState, viewModel: EditorViewModel, issues: List<AssQcIssue>,
    issuesByEvent: Map<Long, List<AssQcIssue>>, expandedEventId: Long?,
    onExpandedChange: (Long?) -> Unit, onTool: (WorkbenchTool) -> Unit,
    supportingOpen: Boolean, tool: WorkbenchTool, onCloseSupporting: () -> Unit,
    onImportFont: () -> Unit, onSaveMkv: () -> Unit, forceOverlay: Boolean,
    editorStateHolder: SaveableStateHolder, modifier: Modifier = Modifier,
) {
    EventWorkspace(state, viewModel, issuesByEvent, expandedEventId, onExpandedChange,
        onTool, editorStateHolder, tool, modifier) {
        SupportingWorkbench(state, viewModel, tool, issues, onCloseSupporting,
            onImportFont, onSaveMkv, Modifier.fillMaxSize())
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
    onNewSubtitle: () -> Unit,
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
                    Text(state.project.title + if (state.dirty) " · 未保存" else "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        DropdownMenuItem(text = { Text("新建空白 ASS") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = { onDismissMenu(); onNewSubtitle() })
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
    eventEditorStateHolder: SaveableStateHolder,
    tool: WorkbenchTool,
    modifier: Modifier = Modifier,
    supportingPane: @Composable () -> Unit,
) {
    val selectionMode = state.selectedEventIds.isNotEmpty()
    val listState = rememberLazyListState()
    // Expanded rows leave composition when they are collapsed or when focus moves.
    // Keep their uncommitted input buffers in a holder owned by the workspace so
    // collapse/switch/scroll does not destroy drafts before "应用正文".
    val rangeScrollScope = rememberCoroutineScope()
    LaunchedEffect(state.focusedEventId, state.filteredEvents) {
        val focusedId = state.focusedEventId
        val index = state.filteredEvents.indexOfFirst { it.id == focusedId }
        if (index >= 0) listState.animateScrollToItem(index)
        if (expandedEventId != null && focusedId != null && expandedEventId != focusedId) {
            onExpandedChange(focusedId)
        }
    }
    val listPane: @Composable (Modifier) -> Unit = { listModifier ->
    Column(listModifier.background(MaterialTheme.colorScheme.surface).testTag("subtitle-navigation")) {
        Row(Modifier.fillMaxWidth().height(WorkbenchDimens.PaneHeaderHeight).padding(horizontal = WorkbenchDimens.Small), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selectionMode) "选择模式" else "字幕", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(if (state.query.isBlank()) "${state.document.events.size}" else "${state.filteredEvents.size}/${state.document.events.size}", style = MaterialTheme.typography.labelSmall)
            if (!selectionMode && state.filteredEvents.isNotEmpty()) {
                TooltipIconButton("上一条筛选结果", viewModel::focusPreviousFilteredEvent) {
                    Icon(Icons.Filled.KeyboardArrowUp, null)
                }
                TooltipIconButton("下一条筛选结果", viewModel::focusNextFilteredEvent) {
                    Icon(Icons.Filled.KeyboardArrowDown, null)
                }
            }
        }
        Divider()
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
                    event, event.id == state.focusedEventId, false,
                    event.id in state.selectedEventIds, selectionMode, issuesByEvent[event.id].orEmpty(),
                    state, viewModel, eventEditorStateHolder,
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
    val inspector: @Composable (Modifier) -> Unit = { inspectorModifier ->
        Surface(inspectorModifier.testTag("event-inspector"), shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            if (tool in listOf(WorkbenchTool.TEXT, WorkbenchTool.EFFECTS, WorkbenchTool.EVENT)) {
                val event = state.document.events.firstOrNull { it.id == expandedEventId }
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (event == null) "事件检查器" else "${tool.title} · #${event.id}",
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (event != null) IconButton(onClick = { onExpandedChange(null) },
                            modifier = Modifier.testTag("event-collapse-${event.id}")) {
                            Icon(Icons.Filled.Close, "收起")
                        }
                    }
                    HorizontalDivider()
                    if (event == null) {
                        Column(Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Subtitles, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("选择一条字幕开始编辑", style = MaterialTheme.typography.titleSmall)
                            Text("正文、样式、位置与效果在上方工具栏", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            eventEditorStateHolder.SaveableStateProvider(event.id) {
                                InlineEventEditor(event, event.style, state, viewModel, onTool, tool)
                            }
                        }
                    }
                }
            } else supportingPane()
        }
    }
    BoxWithConstraints(modifier) {
        val paneWidth = maxWidth
        if (paneWidth >= 680.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listPane(Modifier.width((paneWidth * 0.34f).coerceIn(224.dp, 320.dp)).fillMaxHeight())
                inspector(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listPane(Modifier.weight(if (expandedEventId == null && tool == WorkbenchTool.TEXT) 0.60f else 0.34f).fillMaxWidth())
                inspector(Modifier.weight(if (expandedEventId == null && tool == WorkbenchTool.TEXT) 0.40f else 0.66f).fillMaxWidth())
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
    editorStateHolder: SaveableStateHolder,
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
            .testTag("event-row-${event.id}")
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    focused -> MaterialTheme.colorScheme.secondaryContainer
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
            if (expanded) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("event-collapse-${event.id}"),
                ) { Icon(Icons.Filled.Close, "收起") }
            }
        }

        if (expanded) {
            editorStateHolder.SaveableStateProvider(event.id) {
                InlineEventEditor(event, style?.name ?: event.style, state, viewModel, onTool)
            }
        }
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
    activeSection: WorkbenchTool = WorkbenchTool.TEXT,
) {
    // Inline edit buffers are saveable so a configuration change does not
    // silently discard text that has not yet been committed to the canonical Event.
    var startText by rememberSaveable(event.id, event.start.millis) { mutableStateOf(event.start.toAss()) }
    var endText by rememberSaveable(event.id, event.end.millis) { mutableStateOf(event.end.toAss()) }
    var rawField by rememberSaveable(event.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(event.text, TextRange(event.text.length)))
    }
    var rawBaseText by rememberSaveable(event.id) { mutableStateOf(event.text) }
    var layerText by rememberSaveable(event.id, event.layer) { mutableStateOf(event.layer.toString()) }
    var actorText by rememberSaveable(event.id, event.name) { mutableStateOf(event.name) }
    var comment by rememberSaveable(event.id, event.comment) { mutableStateOf(event.comment) }
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsState()

    LaunchedEffect(event.text) {
        when {
            event.text == rawField.text -> rawBaseText = event.text
            rawField.text == rawBaseText -> {
                val cursor = rawField.selection.start.coerceIn(0, event.text.length)
                rawField = TextFieldValue(event.text, TextRange(cursor))
                rawBaseText = event.text
            }
            else -> Unit // Preserve the unsaved draft; UI below exposes the conflict explicitly.
        }
    }

    if (activeSection == WorkbenchTool.TEXT) {
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
        value = rawField,
        onValueChange = { rawField = it },
        label = { Text("字幕正文 · ASS Event Text") },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        visualTransformation = rememberAssSyntaxTransformation(),
        modifier = Modifier
            .testTag("event-raw-${event.id}")
            .fillMaxWidth()
            .heightIn(min = 104.dp, max = 220.dp),
    )
    val rawDraftState = RawEventDraftPolicy.classify(
        baseText = rawBaseText,
        draftText = rawField.text,
        canonicalText = event.text,
    )
    val rawDirty = rawDraftState != RawEventDraftState.CLEAN
    val rawExternalConflict = rawDraftState == RawEventDraftState.EXTERNAL_CONFLICT
    val splitCursor = rawField.selection.start
    val splitReady = !rawDirty && rawField.selection.collapsed &&
        splitCursor in 1 until event.text.length &&
        playbackPositionMs > event.start.millis && playbackPositionMs < event.end.millis
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (!rawDirty) {
            Text(
                "光标 $splitCursor · 播放头 ${formatMs(playbackPositionMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { viewModel.focusEvent(event.id, seek = false); viewModel.splitFocusedEvent(splitCursor) },
                enabled = splitReady,
            ) { Text("在播放头拆分") }
        } else {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                rawField = TextFieldValue(event.text, TextRange(event.text.length))
                rawBaseText = event.text
            }) { Text(if (rawExternalConflict) "重新载入" else "还原") }
            Button(onClick = { viewModel.updateEventText(event.id, rawField.text) }) {
                Text(if (rawExternalConflict) "以草稿覆盖" else "应用正文")
            }
        }
    }
    if (rawExternalConflict) {
        Text(
            "当前 Event 的正文/override 已在其他面板发生变化；未保存草稿已保留。重新载入会丢弃草稿，“以草稿覆盖”会明确覆盖当前 canonical Event Text。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (!rawDirty && !splitReady) {
        Text(
            "拆分需要：正文光标位于文本中间、没有选区，并且播放头位于当前 Event 的 Start 与 End 之间。override block 内部仍由语义层拒绝拆分。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    }

    if (activeSection == WorkbenchTool.EFFECTS) InlineEffectsEditor(event, playbackPositionMs, viewModel)

    if (activeSection == WorkbenchTool.EVENT) {
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
                    }) { Text("取消") }
                    Button(onClick = {
                        viewModel.focusEvent(event.id, seek = false)
                        viewModel.updateFocusedMetadata(
                            layer = layerText.toIntOrNull() ?: event.layer,
                            actor = actorText,
                            comment = comment,
                        )
                    }) { Text("应用") }
                }
                Divider()
                Text("结构操作", style = MaterialTheme.typography.labelMedium)
                if (state.selectedEventIds.isEmpty()) {
                    fun withCurrentEvent(action: () -> Unit) {
                        viewModel.focusEvent(event.id, seek = false)
                        action()
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
                    ) {
                        OutlinedButton(onClick = { withCurrentEvent(viewModel::insertFocusedBefore) }) { Text("前插") }
                        OutlinedButton(onClick = { withCurrentEvent(viewModel::insertFocusedAfter) }) { Text("后插") }
                        OutlinedButton(onClick = { withCurrentEvent(viewModel::duplicateFocusedEvent) }) { Text("复制") }
                        OutlinedButton(onClick = { withCurrentEvent(viewModel::mergeFocusedWithPrevious) }) { Text("合并上一条") }
                        OutlinedButton(onClick = { withCurrentEvent(viewModel::mergeFocusedWithNext) }) { Text("合并下一条") }
                        OutlinedButton(onClick = { withCurrentEvent(viewModel::deleteSelectedOrFocused) }) {
                            Icon(Icons.Filled.Delete, null)
                            Spacer(Modifier.width(4.dp))
                            Text("删除")
                        }
                    }
                    Text(
                        "前插/后插会继承当前 Event 的 Layer、Style、Actor、Margins、Effect 与 Comment 状态，但正文为空；复制则保留完整正文和时间。所有结构操作都进入 Undo history。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "当前处于多选模式；为避免旧展开面板误删或误合并其他已选字幕，单 Event 结构操作暂时锁定。批量操作请使用“批量”工作台。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineEffectsEditor(event: AssEvent, playbackPositionMs: Long, viewModel: EditorViewModel) {
    val visual = remember(event.id, event.text) { EventOverrideEditor.inspect(event.text) }
    val animation = remember(event.id, event.text) { AssAnimationSemantic.inspect(event.text) }
    var blur by remember(event.id, event.text) { mutableStateOf(visual.blur?.toString().orEmpty()) }
    var softEntry by remember(event.id, event.text) { mutableStateOf(visual.softEntry) }
    var fadeMode by remember(event.id, event.text) {
        mutableStateOf(when { animation.complexFade != null -> "fade"; animation.simpleFade != null -> "fad"; else -> "none" })
    }
    var fadeIn by remember(event.id, event.text) { mutableStateOf((animation.simpleFade?.fadeInMs ?: 150).toString()) }
    var fadeOut by remember(event.id, event.text) { mutableStateOf((animation.simpleFade?.fadeOutMs ?: 150).toString()) }
    val complex = animation.complexFade
    var a1 by remember(event.id, event.text) { mutableStateOf((complex?.alpha1 ?: 255).toString()) }
    var a2 by remember(event.id, event.text) { mutableStateOf((complex?.alpha2 ?: 0).toString()) }
    var a3 by remember(event.id, event.text) { mutableStateOf((complex?.alpha3 ?: 255).toString()) }
    var t1 by remember(event.id, event.text) { mutableStateOf((complex?.time1Ms ?: 0).toString()) }
    var t2 by remember(event.id, event.text) { mutableStateOf((complex?.time2Ms ?: 150).toString()) }
    var t3 by remember(event.id, event.text) { mutableStateOf((complex?.time3Ms ?: 850).toString()) }
    var t4 by remember(event.id, event.text) { mutableStateOf((complex?.time4Ms ?: 1000).toString()) }

    fun previewVisual(value: Double? = blur.toDoubleOrNull()) {
        viewModel.previewEventVisualEffects(event.id, value, softEntry)
    }
    fun commitVisual() { viewModel.applyEventVisualEffects(event.id, blur.toDoubleOrNull(), softEntry) }

    DisposableEffect(event.id) { onDispose { viewModel.clearTransientPreview() } }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
            Text("视觉效果", style = MaterialTheme.typography.titleSmall)
            ContinuousParameterControl(
                label = "Blur",
                valueText = blur,
                onValueTextChange = { blur = it },
                range = 0f..20f,
                step = 0.1,
                supportingText = "只编辑顶层 \\blur；\\t(...) 内部的 Blur 不会被改写。",
                onPreview = { previewVisual(it) },
                onGestureActive = { active -> if (!active) commitVisual() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = softEntry, onClick = { softEntry = !softEntry; previewVisual() }, label = { Text("Soft Entry · 160ms") })
                Spacer(Modifier.weight(1f))
                Button(onClick = ::commitVisual) { Text("应用视觉效果") }
            }

            Divider()
            Text("Fade · \\fad / \\fade", style = MaterialTheme.typography.titleSmall)
            if (animation.fadeConflict) {
                Text("检测到顶层 \\fad 与 \\fade 同时存在；应用任一模式时才会显式消解冲突。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                FilterChip(selected = fadeMode == "none", onClick = { fadeMode = "none" }, label = { Text("无") })
                FilterChip(selected = fadeMode == "fad", onClick = { fadeMode = "fad" }, label = { Text("\\fad") })
                FilterChip(selected = fadeMode == "fade", onClick = { fadeMode = "fade" }, label = { Text("\\fade") })
            }
            if (fadeMode == "fad") {
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                    OutlinedTextField(fadeIn, { fadeIn = it }, label = { Text("Fade In ms") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(fadeOut, { fadeOut = it }, label = { Text("Fade Out ms") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            } else if (fadeMode == "fade") {
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                    OutlinedTextField(a1, { a1 = it }, label = { Text("A1") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(a2, { a2 = it }, label = { Text("A2") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(a3, { a3 = it }, label = { Text("A3") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                    OutlinedTextField(t1, { t1 = it }, label = { Text("T1") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(t2, { t2 = it }, label = { Text("T2") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(t3, { t3 = it }, label = { Text("T3") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(t4, { t4 = it }, label = { Text("T4") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("A1/A2/A3：0 不透明，255 透明；T1–T4：相对 Event 起点的毫秒时间。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = {
                    when (fadeMode) {
                        "none" -> viewModel.clearEventFade(event.id)
                        "fad" -> {
                            val fi = fadeIn.toIntOrNull(); val fo = fadeOut.toIntOrNull()
                            if (fi != null && fo != null) viewModel.setEventSimpleFade(event.id, fi, fo)
                        }
                        "fade" -> {
                            val v = listOf(a1, a2, a3, t1, t2, t3, t4).map { it.toIntOrNull() }
                            if (v.all { it != null }) viewModel.setEventComplexFade(event.id, AssComplexFade(v[0]!!, v[1]!!, v[2]!!, v[3]!!, v[4]!!, v[5]!!, v[6]!!))
                        }
                    }
                }) { Text("应用 Fade") }
            }

            Divider()
            InlineTransformWorkspace(event, animation.transforms, playbackPositionMs, viewModel)
        }
    }
}
@Composable
private fun InlineTransformWorkspace(
    event: AssEvent,
    transforms: List<AssTransform>,
    playbackPositionMs: Long,
    viewModel: EditorViewModel,
) {
    var expanded by rememberSaveable(event.id) { mutableStateOf(false) }
    var addOpen by rememberSaveable(event.id) { mutableStateOf(false) }
    val malformedCount = transforms.count { it.malformed }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Animation · \\t", style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append(transforms.size).append(" transforms")
                    if (malformedCount > 0) append(" · ").append(malformedCount).append(" raw-only")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (malformedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起" else "编辑")
        }
    }

    if (!expanded) {
        Text(
            "Transform 是 Event 内随时间变化的 override。展开后可分别编辑每个 \\t(...)；不会建立新的全局 Animation 工作台。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    AnimationPreviewScrubber(
        event = event,
        playbackPositionMs = playbackPositionMs,
        onSeek = viewModel::seekPreviewTo,
    )

    if (transforms.isEmpty()) {
        Text(
            "当前没有顶层 \\t(...)。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        transforms.forEachIndexed { index, transform ->
            key(event.id, index, transform.rawValue) {
                InlineTransformCard(
                    eventId = event.id,
                    index = index,
                    transform = transform,
                    previewLocalMs = (playbackPositionMs - event.start.millis)
                        .coerceIn(0L, (event.end.millis - event.start.millis).coerceAtLeast(0L)),
                    eventDurationMs = (event.end.millis - event.start.millis).coerceAtLeast(0L),
                    viewModel = viewModel,
                )
            }
        }
    }

    if (addOpen) {
        AddTransformCard(
            event = event,
            onCancel = { addOpen = false },
            onAdd = { transform ->
                viewModel.addEventTransform(event.id, transform)
                addOpen = false
            },
        )
    } else {
        OutlinedButton(
            onClick = { addOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(WorkbenchDimens.Micro))
            Text("添加 Transform")
        }
    }
}

@Composable
private fun AnimationPreviewScrubber(
    event: AssEvent,
    playbackPositionMs: Long,
    onSeek: (Long) -> Unit,
) {
    val durationMs = (event.end.millis - event.start.millis).coerceAtLeast(0L)
    if (durationMs <= 0L) return

    val canonicalLocal = (playbackPositionMs - event.start.millis).coerceIn(0L, durationMs)
    var localMs by remember(event.id) { mutableLongStateOf(canonicalLocal) }
    var dragging by remember(event.id) { mutableStateOf(false) }
    var lastSeekSent by remember(event.id) { mutableLongStateOf(Long.MIN_VALUE) }

    LaunchedEffect(playbackPositionMs, event.id, durationMs, dragging) {
        if (!dragging) {
            localMs = canonicalLocal
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    ) {
        Column(
            Modifier.padding(WorkbenchDimens.Small),
            verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Preview scrub",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    localMs.toString() + " / " + durationMs + " ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = localMs.toFloat(),
                onValueChange = { raw ->
                    dragging = true
                    val next = kotlin.math.round(raw.toDouble()).toLong().coerceIn(0L, durationMs)
                    localMs = next
                    if (lastSeekSent == Long.MIN_VALUE || kotlin.math.abs(next - lastSeekSent) >= 33L) {
                        lastSeekSent = next
                        onSeek(event.start.millis + next)
                    }
                },
                onValueChangeFinished = {
                    dragging = false
                    lastSeekSent = localMs
                    onSeek(event.start.millis + localMs)
                },
                valueRange = 0f..durationMs.toFloat(),
            )
            Text(
                "拖动只改变预览播放头，不改 Event timing，也不产生 Undo 历史；约 33ms 节流避免连续拖动时过量 seek。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InlineTransformCard(
    eventId: Long,
    index: Int,
    transform: AssTransform,
    previewLocalMs: Long,
    eventDurationMs: Long,
    viewModel: EditorViewModel,
) {
    var start by remember(transform.rawValue) { mutableStateOf(transform.startMs?.toString().orEmpty()) }
    var end by remember(transform.rawValue) { mutableStateOf(transform.endMs?.toString().orEmpty()) }
    var accel by remember(transform.rawValue) { mutableStateOf(transform.accel?.toString().orEmpty()) }
    var tags by remember(transform.rawValue) { mutableStateOf(transform.tags) }

    val startValue = start.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val endValue = end.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val accelValue = accel.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val timingPairValid = (start.isBlank() && end.isBlank()) ||
        (startValue != null && endValue != null && startValue <= endValue)
    val accelValid = accel.isBlank() || (accelValue != null && accelValue > 0.0)
    val tagsValid = tags.trimStart().startsWith("\\")
    val canApply = !transform.malformed && timingPairValid && accelValid && tagsValid

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(WorkbenchDimens.Small),
            verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transform #${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.removeEventTransform(eventId, index) }) {
                    Text("删除")
                }
            }
            val effectiveStart = transform.startMs?.toLong()?.coerceAtLeast(0L) ?: 0L
            val effectiveEnd = transform.endMs?.toLong()?.coerceAtMost(eventDurationMs) ?: eventDurationMs
            val playheadInside = !transform.malformed && previewLocalMs in effectiveStart..effectiveEnd
            Text(
                (if (playheadInside) "播放头在此 Transform 范围内" else "播放头在范围外") +
                    " · " + effectiveStart + "–" + effectiveEnd + " ms",
                style = MaterialTheme.typography.labelSmall,
                color = if (playheadInside) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (transform.malformed) {
                Text(
                    "该 \\t(...) 无法安全结构化解析，因此保持 Raw-only；除显式删除外不会自动重写。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "\\t" + transform.rawValue,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Start ms") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("End ms") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(accel, { accel = it }, label = { Text("Accel") }, singleLine = true, modifier = Modifier.weight(1f))
                }

                TransformVisualPropertyEditor(
                    tags = tags,
                    onTagsChange = { nextTags ->
                        tags = nextTags
                        if (timingPairValid && accelValid && nextTags.trimStart().startsWith("\\")) {
                            viewModel.previewEventTransform(
                                eventId,
                                index,
                                AssTransform(
                                    startMs = startValue,
                                    endMs = endValue,
                                    accel = accelValue,
                                    tags = nextTags,
                                ),
                            )
                        }
                    },
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Transform tags") },
                    supportingText = { Text("留空 Start/End = 整个 Event；Accel 留空 = ASS 默认。Tags 必须以 \\ 开始。") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        enabled = canApply,
                        onClick = {
                            viewModel.setEventTransform(
                                eventId,
                                index,
                                AssTransform(
                                    startMs = startValue,
                                    endMs = endValue,
                                    accel = accelValue,
                                    tags = tags,
                                ),
                            )
                        },
                    ) { Text("应用 Transform") }
                }
            }
        }
    }
}

private data class TransformPropertyUiSpec(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val step: Double,
    val suffix: String = "",
)

private fun transformPropertyUiSpec(property: AssTransformVisualProperty): TransformPropertyUiSpec =
    when (property) {
        AssTransformVisualProperty.FONT_SIZE ->
            TransformPropertyUiSpec("Font size · \\fs", 1f..300f, 1.0)
        AssTransformVisualProperty.SPACING ->
            TransformPropertyUiSpec("Spacing · \\fsp", -100f..100f, 0.1)
        AssTransformVisualProperty.SCALE_X ->
            TransformPropertyUiSpec("Scale X · \\fscx", 0f..1000f, 1.0, "%")
        AssTransformVisualProperty.SCALE_Y ->
            TransformPropertyUiSpec("Scale Y · \\fscy", 0f..1000f, 1.0, "%")
        AssTransformVisualProperty.ROTATION_X ->
            TransformPropertyUiSpec("Rotate X · \\frx", -720f..720f, 1.0, "°")
        AssTransformVisualProperty.ROTATION_Y ->
            TransformPropertyUiSpec("Rotate Y · \\fry", -720f..720f, 1.0, "°")
        AssTransformVisualProperty.ROTATION_Z ->
            TransformPropertyUiSpec("Rotate Z · \\frz", -720f..720f, 1.0, "°")
        AssTransformVisualProperty.SHEAR_X ->
            TransformPropertyUiSpec("Shear X · \\fax", -2f..2f, 0.01)
        AssTransformVisualProperty.SHEAR_Y ->
            TransformPropertyUiSpec("Shear Y · \\fay", -2f..2f, 0.01)
        AssTransformVisualProperty.BORDER ->
            TransformPropertyUiSpec("Border · \\bord", 0f..50f, 0.1)
        AssTransformVisualProperty.BORDER_X ->
            TransformPropertyUiSpec("Border X · \\xbord", 0f..50f, 0.1)
        AssTransformVisualProperty.BORDER_Y ->
            TransformPropertyUiSpec("Border Y · \\ybord", 0f..50f, 0.1)
        AssTransformVisualProperty.SHADOW ->
            TransformPropertyUiSpec("Shadow · \\shad", 0f..50f, 0.1)
        AssTransformVisualProperty.SHADOW_X ->
            TransformPropertyUiSpec("Shadow X · \\xshad", -50f..50f, 0.1)
        AssTransformVisualProperty.SHADOW_Y ->
            TransformPropertyUiSpec("Shadow Y · \\yshad", -50f..50f, 0.1)
        AssTransformVisualProperty.EDGE_BLUR ->
            TransformPropertyUiSpec("Edge blur · \\be", 0f..20f, 1.0)
        AssTransformVisualProperty.GAUSSIAN_BLUR ->
            TransformPropertyUiSpec("Gaussian blur · \\blur", 0f..20f, 0.1)
    }

@Composable
private fun TransformVisualPropertyEditor(
    tags: String,
    onTagsChange: (String) -> Unit,
) {
    val snapshot = remember(tags) { AssTransformVisualSemantic.inspect(tags) }
    var selectedName by rememberSaveable {
        mutableStateOf(
            snapshot.values.keys.firstOrNull()?.name
                ?: AssTransformVisualProperty.SCALE_X.name
        )
    }
    var menuOpen by remember { mutableStateOf(false) }
    val selected = AssTransformVisualProperty.entries
        .firstOrNull { it.name == selectedName }
        ?: AssTransformVisualProperty.SCALE_X
    val currentValue = snapshot.values[selected]
    var draft by remember(selected, currentValue) {
        mutableStateOf(currentValue?.toString().orEmpty())
    }
    val spec = transformPropertyUiSpec(selected)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
    ) {
        Column(
            Modifier.padding(WorkbenchDimens.Small),
            verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("常用可动画属性", style = MaterialTheme.typography.labelLarge)
                    Text(
                        snapshot.values.size.toString() + " structured · " +
                            snapshot.warnings.size + " compatibility notes",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (snapshot.warnings.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
                Box {
                    OutlinedButton(onClick = { menuOpen = true }) {
                        Text(spec.label)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        AssTransformVisualProperty.entries.forEach { property ->
                            val propertySpec = transformPropertyUiSpec(property)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (property in snapshot.values) "✓ " else "") +
                                            propertySpec.label
                                    )
                                },
                                onClick = {
                                    selectedName = property.name
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            if (snapshot.values.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
                ) {
                    snapshot.values.keys.forEach { property ->
                        AssistChip(
                            onClick = { selectedName = property.name },
                            label = {
                                Text(
                                    transformPropertyUiSpec(property).label.substringBefore(" · ") +
                                        " " + (snapshot.values[property] ?: "")
                                )
                            },
                        )
                    }
                }
            }

            ContinuousParameterControl(
                label = spec.label,
                valueText = draft,
                onValueTextChange = { draft = it },
                range = spec.range,
                step = spec.step,
                suffix = spec.suffix,
                supportingText = when (selected) {
                    AssTransformVisualProperty.FONT_SIZE ->
                        "\\fs 可动画，但字形 hinting 会让尺寸动画不如 \\fscx/\\fscy 平滑。"
                    AssTransformVisualProperty.SHEAR_X,
                    AssTransformVisualProperty.SHEAR_Y ->
                        "常用范围通常很小；滑杆限制在 ±2，精确值仍可手动输入。"
                    else ->
                        "这里只改当前 Transform 草稿中的一个 tag；其他 tags 保持原顺序和内容。"
                },
                resetLabel = "移除",
                onReset = {
                    draft = ""
                    onTagsChange(
                        AssTransformVisualSemantic.patchNumeric(tags, selected, null)
                    )
                },
                onPreview = { value ->
                    draft = value.toString()
                    onTagsChange(
                        AssTransformVisualSemantic.patchNumeric(tags, selected, value)
                    )
                },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = draft.toDoubleOrNull()?.let { value ->
                        val minimum = selected.minimum
                        value.isFinite() &&
                            (minimum == null || value >= minimum)
                    } == true,
                    onClick = {
                        val value = draft.toDoubleOrNull() ?: return@Button
                        onTagsChange(
                            AssTransformVisualSemantic.patchNumeric(tags, selected, value)
                        )
                    },
                ) {
                    Text("写入 Transform 草稿")
                }
            }

            TransformColorAlphaClipEditor(
                tags = tags,
                snapshot = snapshot,
                onTagsChange = onTagsChange,
            )

            snapshot.warnings.forEach { warning ->
                Text(
                    when (warning.kind) {
                        AssTransformWarningKind.NON_ANIMATABLE_TAG ->
                            "\\" + warning.tag + " 不属于标准 \\t 可动画属性；保留 Raw，不替你猜渲染结果。"
                        AssTransformWarningKind.VECTOR_CLIP ->
                            "检测到 vector \\" + warning.tag + "；标准 \\t 只适合动画矩形 clip。"
                        AssTransformWarningKind.CLIP_ICLIP_MIX ->
                            "同一 Transform 同时含 \\clip 与 \\iclip，存在渲染兼容性风险。"
                        AssTransformWarningKind.NESTED_TRANSFORM ->
                            "检测到嵌套 \\t；保持 Raw-only，不递归结构化。"
                        AssTransformWarningKind.FONT_SIZE_HINTING ->
                            "\\fs 动画会受到字体 hinting 影响；平滑缩放通常优先 \\fscx / \\fscy。"
                        AssTransformWarningKind.DUPLICATE_PROPERTY ->
                            "同一属性重复出现；结构化修改只改最后一个生效值，移除会清除该属性全部重复项。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private fun rgbToHex(rgb: AssRgb?): String =
    rgb?.let { "%02X%02X%02X".format(it.red, it.green, it.blue) }.orEmpty()

private fun parseRgbHex(text: String): AssRgb? {
    val clean = text.trim().removePrefix("#")
    if (clean.length != 6) return null
    val red = clean.substring(0, 2).toIntOrNull(16) ?: return null
    val green = clean.substring(2, 4).toIntOrNull(16) ?: return null
    val blue = clean.substring(4, 6).toIntOrNull(16) ?: return null
    return AssRgb(red, green, blue)
}

@Composable
private fun TransformColorAlphaClipEditor(
    tags: String,
    snapshot: AssTransformVisualSnapshot,
    onTagsChange: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (!open) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("颜色 / Alpha / Rect Clip")
        }
        return
    }

    var colorChannelName by rememberSaveable { mutableStateOf(AssTransformColorChannel.PRIMARY.name) }
    var alphaChannelName by rememberSaveable { mutableStateOf(AssTransformAlphaChannel.ALL.name) }
    val colorChannel = AssTransformColorChannel.entries
        .firstOrNull { it.name == colorChannelName } ?: AssTransformColorChannel.PRIMARY
    val alphaChannel = AssTransformAlphaChannel.entries
        .firstOrNull { it.name == alphaChannelName } ?: AssTransformAlphaChannel.ALL

    val currentColor = snapshot.colors[colorChannel]
    var colorHex by remember(colorChannel, currentColor) {
        mutableStateOf(rgbToHex(currentColor))
    }
    val parsedColor = parseRgbHex(colorHex)

    val currentAlpha = snapshot.alphas[alphaChannel]
    var alphaText by remember(alphaChannel, currentAlpha) {
        mutableStateOf(currentAlpha?.toString().orEmpty())
    }

    val rect = snapshot.rectClip
    var inverse by remember(rect) { mutableStateOf(rect?.inverted ?: false) }
    var left by remember(rect) { mutableStateOf(rect?.left?.toString().orEmpty()) }
    var top by remember(rect) { mutableStateOf(rect?.top?.toString().orEmpty()) }
    var right by remember(rect) { mutableStateOf(rect?.right?.toString().orEmpty()) }
    var bottom by remember(rect) { mutableStateOf(rect?.bottom?.toString().orEmpty()) }

    var colorMenuOpen by remember { mutableStateOf(false) }
    var alphaMenuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
    ) {
        Column(
            Modifier.padding(WorkbenchDimens.Small),
            verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("扩展可动画属性", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { open = false }) { Text("收起") }
            }

            Text("颜色", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
            ) {
                Box {
                    OutlinedButton(onClick = { colorMenuOpen = true }) {
                        Text(
                            when (colorChannel) {
                                AssTransformColorChannel.PRIMARY -> "Primary"
                                AssTransformColorChannel.SECONDARY -> "Secondary"
                                AssTransformColorChannel.OUTLINE -> "Outline"
                                AssTransformColorChannel.SHADOW -> "Shadow"
                            }
                        )
                    }
                    DropdownMenu(expanded = colorMenuOpen, onDismissRequest = { colorMenuOpen = false }) {
                        AssTransformColorChannel.entries.forEach { channel ->
                            DropdownMenuItem(
                                text = { Text(channel.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    colorChannelName = channel.name
                                    colorMenuOpen = false
                                },
                            )
                        }
                    }
                }
                if (parsedColor != null) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(
                                Color(
                                    red = parsedColor.red / 255f,
                                    green = parsedColor.green / 255f,
                                    blue = parsedColor.blue / 255f,
                                ),
                                shape = MaterialTheme.shapes.small,
                            )
                    )
                }
                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { colorHex = it.uppercase().take(7) },
                    label = { Text("#RRGGBB") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        colorHex = ""
                        onTagsChange(
                            AssTransformVisualSemantic.patchColor(tags, colorChannel, null)
                        )
                    },
                ) { Text("移除") }
                Button(
                    enabled = parsedColor != null,
                    onClick = {
                        onTagsChange(
                            AssTransformVisualSemantic.patchColor(tags, colorChannel, parsedColor)
                        )
                    },
                ) { Text("写入") }
            }
            Text(
                "UI 使用常见 #RRGGBB；写入 ASS 时自动转换成 BGR 的 &HBBGGRR&。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Divider()
            Text("Alpha", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
            ) {
                Box {
                    OutlinedButton(onClick = { alphaMenuOpen = true }) {
                        Text(
                            when (alphaChannel) {
                                AssTransformAlphaChannel.ALL -> "All"
                                AssTransformAlphaChannel.PRIMARY -> "Primary"
                                AssTransformAlphaChannel.SECONDARY -> "Secondary"
                                AssTransformAlphaChannel.OUTLINE -> "Outline"
                                AssTransformAlphaChannel.SHADOW -> "Shadow"
                            }
                        )
                    }
                    DropdownMenu(expanded = alphaMenuOpen, onDismissRequest = { alphaMenuOpen = false }) {
                        AssTransformAlphaChannel.entries.forEach { channel ->
                            DropdownMenuItem(
                                text = { Text(channel.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    alphaChannelName = channel.name
                                    alphaMenuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = alphaText,
                    onValueChange = { alphaText = it.filter(Char::isDigit).take(3) },
                    label = { Text("0–255") },
                    supportingText = { Text("0 = 不透明，255 = 全透明") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        alphaText = ""
                        onTagsChange(
                            AssTransformVisualSemantic.patchAlpha(tags, alphaChannel, null)
                        )
                    },
                ) { Text("移除") }
                Button(
                    enabled = alphaText.toIntOrNull() in 0..255,
                    onClick = {
                        val value = alphaText.toIntOrNull() ?: return@Button
                        onTagsChange(
                            AssTransformVisualSemantic.patchAlpha(tags, alphaChannel, value)
                        )
                    },
                ) { Text("写入") }
            }

            Divider()
            Text("Rect Clip", style = MaterialTheme.typography.labelLarge)
            if (snapshot.vectorClipPresent) {
                Text(
                    "检测到 vector clip。为避免覆盖 drawing path，Rect Clip 结构化编辑已锁定；请继续使用 Raw tags。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                    FilterChip(
                        selected = !inverse,
                        onClick = { inverse = false },
                        label = { Text("\\clip") },
                    )
                    FilterChip(
                        selected = inverse,
                        onClick = { inverse = true },
                        label = { Text("\\iclip") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                    OutlinedTextField(left, { left = it }, label = { Text("L") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(top, { top = it }, label = { Text("T") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(right, { right = it }, label = { Text("R") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(bottom, { bottom = it }, label = { Text("B") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                val clipValues = listOf(left, top, right, bottom).map { it.toDoubleOrNull() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            left = ""; top = ""; right = ""; bottom = ""
                            onTagsChange(AssTransformVisualSemantic.patchRectClip(tags, null))
                        },
                    ) { Text("移除 Rect Clip") }
                    Button(
                        enabled = clipValues.all { it?.isFinite() == true },
                        onClick = {
                            onTagsChange(
                                AssTransformVisualSemantic.patchRectClip(
                                    tags,
                                    AssRectTransformClip(
                                        left = clipValues[0]!!,
                                        top = clipValues[1]!!,
                                        right = clipValues[2]!!,
                                        bottom = clipValues[3]!!,
                                        inverted = inverse,
                                    ),
                                )
                            )
                        },
                    ) { Text("写入 Rect Clip") }
                }
            }
        }
    }
}

@Composable
private fun AddTransformCard(
    event: AssEvent,
    onCancel: () -> Unit,
    onAdd: (AssTransform) -> Unit,
) {
    val durationMs = (event.end.millis - event.start.millis).coerceAtLeast(0L)
    var start by remember(event.id) { mutableStateOf("0") }
    var end by remember(event.id, durationMs) { mutableStateOf(durationMs.toString()) }
    var accel by remember(event.id) { mutableStateOf("") }
    var tags by remember(event.id) { mutableStateOf("") }

    val startValue = start.toDoubleOrNull()
    val endValue = end.toDoubleOrNull()
    val accelValue = accel.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val valid = startValue != null && endValue != null && startValue <= endValue &&
        (accel.isBlank() || (accelValue != null && accelValue > 0.0)) &&
        tags.trimStart().startsWith("\\")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
    ) {
        Column(
            Modifier.padding(WorkbenchDimens.Small),
            verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
        ) {
            Text("新 Transform", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                OutlinedTextField(start, { start = it }, label = { Text("Start ms") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(end, { end = it }, label = { Text("End ms") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(accel, { accel = it }, label = { Text("Accel") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags，例如 \\frz30\\blur2") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 1,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Transform timing 相对当前 Event 起点。这里新增的是上下文子界面，不是另一套 Animation 工作台。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("取消") }
                Button(
                    enabled = valid,
                    onClick = {
                        onAdd(
                            AssTransform(
                                startMs = startValue,
                                endMs = endValue,
                                accel = accelValue,
                                tags = tags,
                            ),
                        )
                    },
                ) { Text("添加") }
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
                WorkbenchTool.INVENTORY -> CapabilityInventoryPane(onOpenTool = {}, modifier = Modifier.fillMaxSize())
                WorkbenchTool.TEXT, WorkbenchTool.EFFECTS, WorkbenchTool.EVENT -> Unit
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
private fun ModernTimelinePane(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    val zoomSteps = listOf(5, 10, 30, 60, 120)
    var windowSeconds by rememberSaveable { mutableStateOf(30) }
    var viewportCenterMs by rememberSaveable { mutableLongStateOf(viewModel.playbackPositionMs.value) }
    var followPlayhead by rememberSaveable { mutableStateOf(true) }
    var snapEnabled by rememberSaveable { mutableStateOf(true) }
    var snapEvents by rememberSaveable { mutableStateOf(true) }
    var snapPlayhead by rememberSaveable { mutableStateOf(true) }
    var snapGrid by rememberSaveable { mutableStateOf(true) }
    var snapGridMs by rememberSaveable { mutableLongStateOf(10L) }
    var snapStrength by rememberSaveable { mutableStateOf(TimelineSnapStrength.NORMAL.name) }
    var settingsOpen by remember { mutableStateOf(false) }

    val playheadMs by viewModel.playbackPositionMs.collectAsState()
    val focusedEvent = state.document.events.firstOrNull { it.id == state.focusedEventId }
    val windowDurationMs = windowSeconds * 1000L
    val halfWindowMs = windowDurationMs / 2L

    LaunchedEffect(playheadMs, followPlayhead, halfWindowMs) {
        viewportCenterMs = TimelineViewportPolicy.resolveCenter(
            playheadMs = playheadMs,
            currentCenterMs = viewportCenterMs,
            halfWindowMs = halfWindowMs,
            followPlayhead = followPlayhead,
        )
    }

    val windowStart = (viewportCenterMs - halfWindowMs).coerceAtLeast(0L)
    val windowEnd = windowStart + windowDurationMs
    val timelineRelations = remember(state.document.events) {
        AssTimelineRelations.analyze(state.document.events)
    }
    val relationByEventId = remember(timelineRelations) { timelineRelations.associateBy { it.eventId } }
    val visible = state.document.events.asSequence()
        .filter { it.end.millis >= windowStart && it.start.millis <= windowEnd }
        .sortedWith(compareBy<AssEvent> { it.start.millis }.thenBy { it.end.millis }.thenBy { it.id })
        .take(120)
        .toList()
    val visibleRelationCount = remember(visible, relationByEventId) {
        visible.asSequence().mapNotNull { relationByEventId[it.id] }.groupingBy { it.kind }.eachCount()
    }
    val snapTargets = remember(visible, playheadMs, snapEvents, snapPlayhead) {
        buildList {
            if (snapPlayhead) add(playheadMs)
            if (snapEvents) visible.forEach { add(it.start.millis); add(it.end.millis) }
        }
    }
    val activeSnapStrength = TimelineSnapStrength.entries
        .firstOrNull { it.name == snapStrength } ?: TimelineSnapStrength.NORMAL
    val zoomIndex = zoomSteps.indexOf(windowSeconds).coerceAtLeast(0)

    Column(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
        ) {
            Text(formatMs(playheadMs), style = MaterialTheme.typography.titleSmall)
            FilterChip(
                selected = followPlayhead,
                onClick = {
                    followPlayhead = !followPlayhead
                    if (followPlayhead) {
                        viewportCenterMs = TimelineViewportPolicy.resolveCenter(
                            playheadMs = playheadMs,
                            currentCenterMs = viewportCenterMs,
                            halfWindowMs = halfWindowMs,
                            followPlayhead = true,
                        )
                    }
                },
                label = { Text("跟随播放头") },
            )
            OutlinedButton(
                onClick = { if (zoomIndex > 0) windowSeconds = zoomSteps[zoomIndex - 1] },
                enabled = zoomIndex > 0,
            ) { Text("放大") }
            OutlinedButton(
                onClick = { if (zoomIndex < zoomSteps.lastIndex) windowSeconds = zoomSteps[zoomIndex + 1] },
                enabled = zoomIndex < zoomSteps.lastIndex,
            ) { Text("缩小") }
            Text("${windowSeconds}s", style = MaterialTheme.typography.labelMedium)
            if (!followPlayhead) {
                TextButton(onClick = {
                    viewportCenterMs = TimelineViewportPolicy.resolveCenter(
                        playheadMs = playheadMs,
                        currentCenterMs = viewportCenterMs,
                        halfWindowMs = halfWindowMs,
                        followPlayhead = true,
                    )
                    followPlayhead = true
                }) { Text("回到播放头") }
            }
            TextButton(onClick = { settingsOpen = true }) {
                Text(if (snapEnabled) "Snap ${snapGridMs}ms" else "Snap off")
            }
            DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
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
                Divider()
                TimelineSnapStrength.entries.forEach { strength ->
                    DropdownMenuItem(
                        text = { Text((if (activeSnapStrength == strength) "✓ " else "") + "吸附强度 · " + strength.label) },
                        enabled = snapEnabled,
                        onClick = { snapStrength = strength.name; settingsOpen = false },
                    )
                }
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(if (compact) 24.dp else 34.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                .pointerInput(windowDurationMs, halfWindowMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { followPlayhead = false },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            viewportCenterMs = TimelineViewportPolicy.panCenter(
                                currentCenterMs = viewportCenterMs,
                                dragAmountPx = dragAmount,
                                widthPx = size.width,
                                windowDurationMs = windowDurationMs,
                                halfWindowMs = halfWindowMs,
                            )
                        },
                    )
                },
        ) {
            val viewportColors = MaterialTheme.colorScheme
            Canvas(Modifier.fillMaxSize()) {
                val span = (windowEnd - windowStart).coerceAtLeast(1L).toFloat()
                for (i in 1..3) {
                    val x = size.width * (i / 4f)
                    drawLine(
                        color = viewportColors.outline.copy(alpha = 0.35f),
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                if (playheadMs in windowStart..windowEnd) {
                    val fraction = (playheadMs - windowStart).toFloat() / span
                    val x = size.width * fraction
                    drawLine(
                        color = viewportColors.primary,
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            Text(
                formatMs(windowStart),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatMs(windowEnd),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TimelineWaveformLite(
            waveform = state.waveform,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            playheadMs = playheadMs,
            onSeek = viewModel::seekPreviewTo,
            compact = compact,
        )

        if (!compact) {
        Text(
            "拖上方时间标尺平移视窗；关闭“跟随播放头”后，播放继续也不会把视窗拉回。Event：拖左右边缘调整 Start / End，拖主体整体平移。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val overlapCount = visibleRelationCount[AssTimelineRelationKind.OVERLAP] ?: 0
            val gapCount = visibleRelationCount[AssTimelineRelationKind.GAP] ?: 0
            Text(
                "当前视窗：Overlap $overlapCount · Gap $gapCount",
                style = MaterialTheme.typography.labelSmall,
                color = if (overlapCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        }
        Divider()
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前视窗没有字幕事件。拖动上方时间标尺，或回到播放头。")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { event ->
                    ModernTimelineEventRow(
                        event = event,
                        relation = relationByEventId[event.id],
                        windowStartMs = windowStart,
                        windowEndMs = windowEnd,
                        playheadMs = playheadMs,
                        focused = event.id == state.focusedEventId,
                        snapTargets = snapTargets,
                        snapEnabled = snapEnabled,
                        snapGrid = snapGrid,
                        snapGridMs = snapGridMs,
                        snapStrength = activeSnapStrength,
                        onFocus = { viewModel.focusEvent(event.id, seek = true) },
                        onCommit = { a, b -> viewModel.setEventTiming(event.id, a, b) },
                    )
                }
            }
        }
    }
}
@Composable
private fun TimelineWaveformLite(
    waveform: WaveformLiteState,
    windowStartMs: Long,
    windowEndMs: Long,
    playheadMs: Long,
    onSeek: (Long) -> Unit,
    compact: Boolean = false,
) {
    when (waveform.status) {
        WaveformLiteStatus.IDLE -> Unit
        WaveformLiteStatus.ANALYZING -> {
            Surface(
                modifier = Modifier.fillMaxWidth().height(if (compact) 28.dp else 52.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Waveform Lite · 后台分析音轨…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        WaveformLiteStatus.UNAVAILABLE -> {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 28.dp else 40.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
            ) {
                Text(
                    "Waveform unavailable" + waveform.error?.let { " · " + it }.orEmpty(),
                    modifier = Modifier.padding(WorkbenchDimens.Small),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        WaveformLiteStatus.READY -> {
            val envelope = waveform.envelope ?: return
            val waveformColor = MaterialTheme.colorScheme.onSurfaceVariant
            val playheadColor = MaterialTheme.colorScheme.primary
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(if (compact) 32.dp else 58.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
                    .pointerInput(windowStartMs, windowEndMs) {
                        detectTapGestures { offset ->
                            val width = size.width.coerceAtLeast(1)
                            val fraction = (offset.x / width).coerceIn(0f, 1f)
                            val target = windowStartMs +
                                ((windowEndMs - windowStartMs) * fraction).toLong()
                            onSeek(target.coerceAtLeast(0L))
                        }
                    },
            ) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val columns = with(density) { maxWidth.roundToPx() }.coerceIn(1, 1200)
                val samples = remember(envelope, windowStartMs, windowEndMs, columns) {
                    WaveformViewportSampler.sample(
                        envelope = envelope,
                        startMs = windowStartMs,
                        endMs = windowEndMs,
                        columns = columns,
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    if (samples.isNotEmpty()) {
                        val centerY = size.height / 2f
                        val amplitude = centerY * 0.88f
                        val xStep = size.width / samples.size
                        samples.forEachIndexed { index, bucket ->
                            val x = (index + 0.5f) * xStep
                            val high = bucket.maximum.toFloat() / Short.MAX_VALUE.toFloat()
                            val lowMagnitude = -bucket.minimum.toFloat() / -Short.MIN_VALUE.toFloat()
                            val top = centerY - high * amplitude
                            val bottom = centerY + lowMagnitude * amplitude
                            drawLine(
                                color = waveformColor.copy(alpha = 0.72f),
                                start = androidx.compose.ui.geometry.Offset(x, top),
                                end = androidx.compose.ui.geometry.Offset(x, bottom),
                                strokeWidth = maxOf(1f, xStep.coerceAtMost(2f)),
                            )
                        }
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    if (playheadMs in windowStartMs..windowEndMs) {
                        val span = (windowEndMs - windowStartMs).coerceAtLeast(1L)
                        val fraction = (playheadMs - windowStartMs).toFloat() / span
                        val x = size.width * fraction
                        drawLine(
                            color = playheadColor,
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }
                Text(
                    "Waveform Lite · 点击定位",
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                )
            }
        }
    }
}

private enum class TimelineSnapStrength(val label: String, val factor: Double) {
    LIGHT("轻", 0.5),
    NORMAL("标准", 1.0),
    STRONG("强", 2.0),
}

private enum class ModernTimelineDragMode { START, MOVE, END }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernTimelineEventRow(
    event: AssEvent,
    relation: AssTimelineRelation?,
    windowStartMs: Long,
    windowEndMs: Long,
    playheadMs: Long,
    focused: Boolean,
    snapTargets: List<Long>,
    snapEnabled: Boolean,
    snapGrid: Boolean,
    snapGridMs: Long,
    snapStrength: TimelineSnapStrength,
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
        Modifier.fillMaxWidth().height(52.dp).combinedClickable(onClick = onFocus, onLongClick = onFocus),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
    ) {
        Column(Modifier.width(108.dp)) {
            Text("#${event.id}", style = MaterialTheme.typography.labelSmall)
            Text(AssInlineSyntax.visibleText(event.text), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            relation?.let { timingRelation ->
                when (timingRelation.kind) {
                    AssTimelineRelationKind.OVERLAP -> Text(
                        "Overlap ${timingRelation.durationMs}ms · #${timingRelation.previousEventId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                    )
                    AssTimelineRelationKind.GAP -> Text(
                        "Gap ${timingRelation.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    AssTimelineRelationKind.TOUCH -> Unit
                }
            }
        }
        BoxWithConstraints(
            Modifier.weight(1f).height(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .pointerInput(event.id, windowStartMs, windowEndMs, snapTargets, focused, snapEnabled, snapGrid, snapGridMs, snapStrength) {
                    val span = (windowEndMs - windowStartMs).coerceAtLeast(1L)
                    fun xFor(ms: Long): Float = ((ms - windowStartMs).toFloat() / span).coerceIn(0f, 1f) * size.width
                    fun thresholdMs(): Long {
                        val base = minOf(120L, maxOf(24L, span / 220L))
                        return (base * snapStrength.factor).toLong().coerceIn(12L, 240L)
                    }
                    fun activeTargets(): List<Long> = snapTargets.toMutableList().apply {
                        // Remove only this Event's own edges once; duplicate timestamps from
                        // another Event or the playhead remain valid snap targets.
                        remove(baseStart)
                        remove(baseEnd)
                    }
                    fun snapPoint(candidate: Long): Long {
                        if (!snapEnabled) return candidate
                        return AssTimelineSnap.snapPoint(
                            candidateMs = candidate,
                            targets = activeTargets(),
                            thresholdMs = thresholdMs(),
                            gridMs = snapGridMs.takeIf { snapGrid },
                        )
                    }
                    fun snapMove(start: Long, end: Long): AssTimelineSpan {
                        if (!snapEnabled) return AssTimelineSpan(start, end)
                        return AssTimelineSnap.snapSpan(
                            startMs = start,
                            endMs = end,
                            targets = activeTargets(),
                            thresholdMs = thresholdMs(),
                            gridMs = snapGridMs.takeIf { snapGrid },
                        )
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
                                ModernTimelineDragMode.START -> previewStart = snapPoint(baseStart + delta).coerceIn(0L, (previewEnd - 10L).coerceAtLeast(0L))
                                ModernTimelineDragMode.END -> previewEnd = snapPoint(baseEnd + delta).coerceAtLeast(previewStart + 10L)
                                ModernTimelineDragMode.MOVE -> {
                                    val duration = (baseEnd - baseStart).coerceAtLeast(10L)
                                    val rawStart = (baseStart + delta).coerceAtLeast(0L)
                                    val snapped = snapMove(rawStart, rawStart + duration)
                                    val clampedStart = snapped.startMs.coerceAtLeast(0L)
                                    previewStart = clampedStart
                                    previewEnd = clampedStart + duration
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
                if (playheadMs in windowStartMs..windowEndMs) {
                    val playFraction = (playheadMs - windowStartMs).toFloat() /
                        (windowEndMs - windowStartMs).coerceAtLeast(1L)
                    val playX = size.width * playFraction
                    drawLine(
                        color = timelineColors.onSurface,
                        start = androidx.compose.ui.geometry.Offset(playX, 0f),
                        end = androidx.compose.ui.geometry.Offset(playX, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
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
    var clipLeftText by remember(event.id, event.text) { mutableStateOf(geometry.clipRect?.left?.toString().orEmpty()) }
    var clipTopText by remember(event.id, event.text) { mutableStateOf(geometry.clipRect?.top?.toString().orEmpty()) }
    var clipRightText by remember(event.id, event.text) { mutableStateOf(geometry.clipRect?.right?.toString().orEmpty()) }
    var clipBottomText by remember(event.id, event.text) { mutableStateOf(geometry.clipRect?.bottom?.toString().orEmpty()) }
    var clipDraftChanged by remember(event.id) { mutableStateOf(false) }
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
    LaunchedEffect(event.id, clipLeftText, clipTopText, clipRightText, clipBottomText, clipDraftChanged) {
        val rect = geometry.clipRect ?: return@LaunchedEffect
        if (!clipDraftChanged) return@LaunchedEffect
        val left = clipLeftText.toDoubleOrNull() ?: return@LaunchedEffect
        val top = clipTopText.toDoubleOrNull() ?: return@LaunchedEffect
        val right = clipRightText.toDoubleOrNull() ?: return@LaunchedEffect
        val bottom = clipBottomText.toDoubleOrNull() ?: return@LaunchedEffect
        viewModel.previewFocusedRectClip(left, top, right, bottom, geometry.clipInverted)
        kotlinx.coroutines.delay(320)
        viewModel.setFocusedRectClip(left, top, right, bottom, geometry.clipInverted)
        clipDraftChanged = false
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
        val moveGeometry = geometry.move
        if (geometry.positionMode == AssPositionMode.MOVE && moveGeometry != null) {
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
                    val moveStartMs = moveGeometry.startMs
                    val moveEndMs = moveGeometry.endMs
                    Text(
                        if (moveStartMs != null && moveEndMs != null) {
                            "Timing ${moveStartMs.toInt()}–${moveEndMs.toInt()} ms · 编辑端点时原样保留"
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
        item { Divider() }
        item {
            Text("矩形裁剪 · \\clip / \\iclip", style = MaterialTheme.typography.titleSmall)
            Text(
                "\\clip 只显示矩形内部；\\iclip 相反，会隐藏矩形内部。矩形坐标使用 ASS Script Resolution，不跟随字幕的旋转或移动坐标系。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                geometry.clipNonRectangular -> {
                    Text(
                        "当前 Event 使用非矩形 / vector ${if (geometry.clipInverted) "\\iclip" else "\\clip"}。0.26 的矩形编辑器不会自动重写它。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                        OutlinedButton(onClick = viewModel::clearFocusedClip) { Text("移除现有 clip") }
                        Button(onClick = {
                            viewModel.setFocusedRectClip(
                                state.document.playResX * 0.1,
                                state.document.playResY * 0.1,
                                state.document.playResX * 0.9,
                                state.document.playResY * 0.9,
                                geometry.clipInverted,
                            )
                        }) { Text("明确替换为矩形") }
                    }
                }
                geometry.clipRect != null -> {
                    val clipRect = requireNotNull(geometry.clipRect)
                    Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                        FilterChip(
                            selected = !geometry.clipInverted,
                            onClick = {
                                viewModel.setFocusedRectClip(clipRect.left, clipRect.top, clipRect.right, clipRect.bottom, false)
                            },
                            label = { Text("\\clip · 内部显示") },
                        )
                        FilterChip(
                            selected = geometry.clipInverted,
                            onClick = {
                                viewModel.setFocusedRectClip(clipRect.left, clipRect.top, clipRect.right, clipRect.bottom, true)
                            },
                            label = { Text("\\iclip · 内部隐藏") },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                        OutlinedTextField(clipLeftText, { clipLeftText = it; clipDraftChanged = true }, label = { Text("Left") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(clipTopText, { clipTopText = it; clipDraftChanged = true }, label = { Text("Top") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                        OutlinedTextField(clipRightText, { clipRightText = it; clipDraftChanged = true }, label = { Text("Right") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(clipBottomText, { clipBottomText = it; clipDraftChanged = true }, label = { Text("Bottom") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = viewModel::clearFocusedClip) { Text("移除 clip") }
                        Button(onClick = {
                            val left = clipLeftText.toDoubleOrNull()
                            val top = clipTopText.toDoubleOrNull()
                            val right = clipRightText.toDoubleOrNull()
                            val bottom = clipBottomText.toDoubleOrNull()
                            if (left != null && top != null && right != null && bottom != null) {
                                clipDraftChanged = false
                                viewModel.setFocusedRectClip(left, top, right, bottom, geometry.clipInverted)
                            }
                        }) { Text("应用矩形") }
                    }
                    Text(
                        "预览中四个角均可直接拖动；拖动只改矩形裁剪坐标，不会改字幕本身的位置、旋转或缩放。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        "当前没有裁剪。新增矩形默认使用画布中央 80% 区域，随后可拖四角或输入精确坐标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                        Button(onClick = {
                            viewModel.setFocusedRectClip(
                                state.document.playResX * 0.1,
                                state.document.playResY * 0.1,
                                state.document.playResX * 0.9,
                                state.document.playResY * 0.9,
                                false,
                            )
                        }) { Text("添加 \\clip") }
                        OutlinedButton(onClick = {
                            viewModel.setFocusedRectClip(
                                state.document.playResX * 0.1,
                                state.document.playResY * 0.1,
                                state.document.playResX * 0.9,
                                state.document.playResY * 0.9,
                                true,
                            )
                        }) { Text("添加 \\iclip") }
                    }
                }
            }
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
private fun FontManagerPane(
    state: EditorState,
    viewModel: EditorViewModel,
    onImportFont: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focused = state.document.events.firstOrNull { it.id == state.focusedEventId }
    val style = focused?.let { event ->
        state.document.styles.firstOrNull { it.name == event.style }
    }
    val requestedFamilies = remember(state.document) {
        FontBindingRewriter.requestedFamilies(state.document)
            .sortedBy { it.lowercase() }
    }
    val explicitlyRequestedFontShas = remember(state.importedFonts, requestedFamilies) {
        state.importedFonts.asSequence()
            .filter { font ->
                FontDiagnostics.matchesRequestedFamily(font, requestedFamilies)
            }
            .map { it.sha256 }
            .toSet()
    }
    val embeddedFontShas = remember(state.importedFonts) {
        state.importedFonts.asSequence()
            .filter { it.origin == FontOrigin.MKV_ATTACHMENT }
            .map { it.sha256 }
            .toSet()
    }
    val packageableFontShas = remember(state.importedFonts, embeddedFontShas) {
        state.importedFonts.asSequence()
            .filter { it.origin == FontOrigin.MANUAL && it.sha256 !in embeddedFontShas }
            .map { it.sha256 }
            .toSet()
    }
    val mkvAttachmentCount = state.importedFonts.count { it.origin == FontOrigin.MKV_ATTACHMENT }
    val unreferencedMkvCount = state.importedFonts.count { font ->
        font.origin == FontOrigin.MKV_ATTACHMENT &&
            font.sha256 !in explicitlyRequestedFontShas
    }

    Column(
        modifier.padding(WorkbenchDimens.Small),
        verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("字体管理", style = MaterialTheme.typography.titleSmall)
                Text(
                    state.importedFonts.size.toString() + " 个可用字体 · 文档请求 " +
                        requestedFamilies.size +
                        if (style != null) " · 当前 " + style.fontName else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onImportFont, enabled = !state.fontImportBusy) {
                Text(if (state.fontImportBusy) "导入中…" else "导入字体")
            }
        }

        if (requestedFamilies.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
            ) {
                requestedFamilies.forEach { family ->
                    AssistChip(
                        onClick = {},
                        label = { Text(family) },
                    )
                }
            }
        } else {
            Text(
                "当前 ASS 没有 Event 请求字体；未使用的 Style 定义不会被算作运行时字体需求。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.container.uri != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
            ) {
                Text(
                    "MKV 写回字体 " + state.fontPackagingSelection.size + " / " + packageableFontShas.size,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = viewModel::selectRequestedFontsForPackaging,
                    enabled = packageableFontShas.isNotEmpty(),
                ) { Text("选择 ASS 请求") }
                TextButton(
                    onClick = viewModel::clearFontPackagingSelection,
                    enabled = state.fontPackagingSelection.isNotEmpty(),
                ) { Text("清空") }
            }
            Text(
                "所选手动字体会在“保存为新 MKV”时与 ASS 轨替换一起封入；当前 MKV 已有的同一字体不会重复选择。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (mkvAttachmentCount > 0) {
            Text(
                "MKV 字体附件 " + mkvAttachmentCount +
                    if (unreferencedMkvCount > 0) {
                        " · " + unreferencedMkvCount + " 个无显式 ASS 字体请求"
                    } else {
                        " · 当前均有显式请求匹配"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = if (unreferencedMkvCount > 0) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (unreferencedMkvCount > 0) {
                Text(
                    "“无显式请求”只用于诊断，不自动删除附件：libass fallback 或同族字重仍可能使用这些文件。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(
                state.importedFonts,
                key = { font -> font.origin.name + ":" + font.sha256 + ":" + font.fileName },
            ) { font ->
                val requested = font.sha256 in explicitlyRequestedFontShas
                Row(
                    Modifier.fillMaxWidth().padding(vertical = WorkbenchDimens.Micro),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            font.metadata.family,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val originLabel = when (font.origin) {
                            FontOrigin.MANUAL -> "手动导入"
                            FontOrigin.MKV_ATTACHMENT -> "MKV 附件"
                            FontOrigin.UNKNOWN -> "来源未知"
                        }
                        Text(
                            font.metadata.rendererFamily + " · " + originLabel +
                                if (requested) " · ASS 已请求" else " · 无显式请求",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (
                                font.origin == FontOrigin.MKV_ATTACHMENT && !requested
                            ) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (state.container.uri != null) {
                        if (font.origin == FontOrigin.MKV_ATTACHMENT || font.sha256 in embeddedFontShas) {
                            Text(
                                "容器已有",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (font.sha256 in packageableFontShas) {
                            Checkbox(
                                checked = font.sha256 in state.fontPackagingSelection,
                                onCheckedChange = { viewModel.toggleFontPackaging(font.sha256) },
                            )
                        }
                    }
                    if (style != null) {
                        TextButton(
                            onClick = {
                                viewModel.setStyleFont(
                                    style.name,
                                    font.metadata.rendererFamily,
                                )
                            },
                        ) {
                            Text("用于 " + style.name)
                        }
                    }
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
        val issueEventIds = remember(issues) { issues.map { it.eventId }.distinct() }
        val issueIndex = issueEventIds.indexOf(state.focusedEventId)
        val previousIssueId = if (issueIndex > 0) issueEventIds[issueIndex - 1] else null
        val nextIssueId = when {
            issueEventIds.isEmpty() -> null
            issueIndex < 0 -> issueEventIds.first()
            issueIndex < issueEventIds.lastIndex -> issueEventIds[issueIndex + 1]
            else -> null
        }
        Text("质量检查 · ${issues.size}", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$errors error · $warnings warning · ${issues.size-errors-warnings} info", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { previousIssueId?.let { viewModel.focusEvent(it, true) } }, enabled = previousIssueId != null) { Text("上一问题") }
            Spacer(Modifier.width(WorkbenchDimens.Micro))
            OutlinedButton(onClick = { nextIssueId?.let { viewModel.focusEvent(it, true) } }, enabled = nextIssueId != null) { Text("下一问题") }
        }
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
    var styleMenuOpen by remember { mutableStateOf(false) }
    var pasteMenuOpen by remember { mutableStateOf(false) }
    Column(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("已选 ${state.selectedEventIds.size} 条", style = MaterialTheme.typography.titleSmall)
        if (state.selectedEventIds.isEmpty()) { Text("长按字幕进入多选。"); return }
        Box {
            OutlinedButton(onClick = { styleMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("批量指定 Style")
            }
            DropdownMenu(expanded = styleMenuOpen, onDismissRequest = { styleMenuOpen = false }) {
                state.document.styles.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(style.name) },
                        onClick = {
                            styleMenuOpen = false
                            viewModel.assignSelectedStyle(style.name)
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
            OutlinedButton({ viewModel.shiftSelected(-100) }, Modifier.weight(1f)) { Text("−100 ms") }
            OutlinedButton({ viewModel.shiftSelected(100) }, Modifier.weight(1f)) { Text("+100 ms") }
        }
        Button(viewModel::alignSelectedStartToPlayback, Modifier.fillMaxWidth()) { Text("第一条对齐播放头") }
        Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
            OutlinedButton({ viewModel.setSelectedComment(false) }, Modifier.weight(1f)) { Text("Dialogue") }
            OutlinedButton({ viewModel.setSelectedComment(true) }, Modifier.weight(1f)) { Text("Comment") }
        }
        if (state.selectedEventIds.size >= 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                OutlinedButton({ viewModel.mergeSelected(useLineBreak = true) }, Modifier.weight(1f)) { Text("合并 · \\N") }
                OutlinedButton({ viewModel.mergeSelected(useLineBreak = false) }, Modifier.weight(1f)) { Text("合并 · 空格") }
            }
            Text(
                "合并只接受 Event 列表中连续的选择；非连续选择会保持原样并提示错误。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(viewModel::clearSelectedStyleOverrides, Modifier.fillMaxWidth()) { Text("清除 Style / 位置覆盖") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
            OutlinedButton(
                viewModel::copyFocusedFormatToClipboard,
                Modifier.weight(1f),
                enabled = state.focusedEventId != null,
            ) { Text("复制焦点格式" + (state.focusedEventId?.let { " · #$it" } ?: "")) }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { pasteMenuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("粘贴格式…") }
                DropdownMenu(expanded = pasteMenuOpen, onDismissRequest = { pasteMenuOpen = false }) {
                    listOf(
                        EventFormatPasteMode.STYLE to "Style",
                        EventFormatPasteMode.MARGINS to "Margins",
                        EventFormatPasteMode.POSITION to "Position · pos/move",
                        EventFormatPasteMode.EFFECTS to "Effects · fad/fade/blur/t",
                        EventFormatPasteMode.OVERRIDES to "全部 leading overrides",
                        EventFormatPasteMode.ALL to "全部格式",
                    ).forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                pasteMenuOpen = false
                                viewModel.pasteFormatClipboardToSelected(mode)
                            },
                        )
                    }
                }
            }
        }
        Text(
            "格式剪贴板只复制结构化格式，不复制正文、时间或 {comment}。Position / Effects 只替换顶层对应 tag，不会误改 \\t(...) 内部的嵌套 tag。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(viewModel::deleteSelectedOrFocused, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(4.dp)); Text("删除已选字幕") }
    }
}

@Composable
private fun ProjectPane(state: EditorState, viewModel: EditorViewModel, onSaveMkv: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (state.container.uri != null) "MKV 工程" else "独立 ASS 工程", style = MaterialTheme.typography.titleSmall)
        Text(state.project.title)
        Text("PlayRes ${state.document.playResX}×${state.document.playResY} · ${state.document.styles.size} Style · ${state.document.events.size} Event", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.container.uri != null) {
            Divider()
            ContainerBridgePanel(state.container, viewModel, onSaveMkv, dirty = state.dirty)
        }
        else Text(if (state.project.videoUri == null) "未附加参考视频" else "已附加参考视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DiagnosticsPane(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    val event = state.document.events.firstOrNull { it.id == state.focusedEventId }
    val effective = event?.let { AssEffectiveInspector.inspect(state.document, it) }.orEmpty()
    LazyColumn(modifier.padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
        item {
            Text("构建身份", style = MaterialTheme.typography.titleSmall)
            Text("Version ${BuildConfig.VERSION_NAME} · code ${BuildConfig.VERSION_CODE}")
            Text(
                "Commit ${BuildConfig.ASSWB_BUILD_COMMIT} · CI ${BuildConfig.ASSWB_BUILD_NUMBER}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Divider()
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

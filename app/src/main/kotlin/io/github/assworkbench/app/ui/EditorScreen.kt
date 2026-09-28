package io.github.assworkbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.BuildConfig
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssInlineSyntax
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.fonts.FontMatchStatus
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

private enum class WorkspaceSection(val label: String) {
    SUBTITLES("字幕"),
    STYLE("样式"),
    EFFECTS("效果"),
    REVIEW("校对"),
    PROJECT("项目"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorState,
    viewModel: EditorViewModel,
    onOpenVideo: () -> Unit,
    onOpenMkvProject: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onImportFont: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onSaveMkv: () -> Unit,
) {
    var sectionName by rememberSaveable { mutableStateOf(WorkspaceSection.SUBTITLES.name) }
    val section = WorkspaceSection.entries.firstOrNull { it.name == sectionName } ?: WorkspaceSection.SUBTITLES
    val snackbarHostState = remember { SnackbarHostState() }
    var initialStatusConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.status) {
        if (!initialStatusConsumed) {
            initialStatusConsumed = true
        } else if (state.status.isNotBlank()) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = state.status,
                duration = SnackbarDuration.Short,
            )
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        topBar = {
            CompactEditorToolbar(
                state = state,
                viewModel = viewModel,
                onOpenVideo = onOpenVideo,
                onOpenMkvProject = onOpenMkvProject,
                onOpenSubtitle = onOpenSubtitle,
                onImportFont = onImportFont,
                onSave = onSave,
                onSaveAs = onSaveAs,
            )
        },
    ) { padding ->
        EditorWorkspace(
            state = state,
            viewModel = viewModel,
            section = section,
            onSectionChange = { sectionName = it.name },
            onImportFont = onImportFont,
            onSaveMkv = onSaveMkv,
            onOpenVideo = onOpenVideo,
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
        )
    }
}

@Composable
private fun CompactEditorToolbar(
    state: EditorState,
    viewModel: EditorViewModel,
    onOpenVideo: () -> Unit,
    onOpenMkvProject: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onImportFont: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        Surface(Modifier.statusBarsPadding(), tonalElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    state.project.title + if (state.dirty) " *" else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.document.events.size.toString() + " · " +
                        if (BuildConfig.ASSWB_RENDERER_FONT_PROVIDER.equals("fontconfig", ignoreCase = true)) "FC" else "libass",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                HintIconButton(Icons.Filled.Movie, "打开 / 更换参考视频", onOpenVideo)
                HintIconButton(Icons.Filled.FolderOpen, "打开 / 更换 ASS 字幕", onOpenSubtitle)
                TextButton(onClick = onOpenMkvProject, modifier = Modifier.height(32.dp)) { Text("MKV") }
                HintIconButton(Icons.Filled.Save, "保存字幕", onSave, enabled = state.subtitleLoaded)
                HintIconButton(Icons.AutoMirrored.Filled.Undo, "撤销", viewModel::undo, enabled = state.canUndo)
                HintIconButton(Icons.AutoMirrored.Filled.Redo, "重做", viewModel::redo, enabled = state.canRedo)
                Box {
                    HintIconButton(Icons.Filled.MoreVert, "更多", { overflowOpen = true })
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("新建空白 ASS") },
                            onClick = { overflowOpen = false; viewModel.newSubtitleProject() },
                        )
                        DropdownMenuItem(
                            text = { Text("另存 ASS") },
                            enabled = state.subtitleLoaded,
                            onClick = { overflowOpen = false; onSaveAs() },
                        )
                        DropdownMenuItem(text = { Text("导入字体") }, onClick = { overflowOpen = false; onImportFont() })
                        DropdownMenuItem(
                            text = { Text("重建字体缓存") },
                            onClick = { overflowOpen = false; viewModel.rebuildRendererFontCache() },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HintIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = if (compact) Modifier.width(30.dp).height(30.dp)
                else Modifier.width(34.dp).height(34.dp),
        ) {
            Icon(icon, contentDescription = label, tint = tint)
        }
    }
}

@Composable
private fun EditorWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    section: WorkspaceSection,
    onSectionChange: (WorkspaceSection) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    onOpenVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true).windowSizeClass
    Box(modifier) {
        when {
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) ->
                ExpandedEditorWorkspace(
                    state,
                    viewModel,
                    section,
                    onSectionChange,
                    onImportFont,
                    onSaveMkv,
                    onOpenVideo,
                )
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) ->
                TabletEditorWorkspace(
                    state,
                    viewModel,
                    section,
                    onSectionChange,
                    onImportFont,
                    onSaveMkv,
                    onOpenVideo,
                )
            else -> CompactEditorWorkspace(
                state,
                viewModel,
                section,
                onSectionChange,
                onImportFont,
                onSaveMkv,
                onOpenVideo,
            )
        }
    }
}

@Composable
private fun ExpandedEditorWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    section: WorkspaceSection,
    onSectionChange: (WorkspaceSection) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    var inspectorVisible by rememberSaveable { mutableStateOf(true) }
    val inspectorWidth = when (section) {
        WorkspaceSection.SUBTITLES -> 320.dp
        WorkspaceSection.STYLE -> 500.dp
        WorkspaceSection.EFFECTS -> 400.dp
        WorkspaceSection.REVIEW -> 500.dp
        WorkspaceSection.PROJECT -> 410.dp
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PreviewPane(state, viewModel, onOpenVideo, Modifier.fillMaxWidth())
            Divider()
            SubtitleDock(state, viewModel, Modifier.weight(1f).fillMaxWidth())
        }
        Box(
            Modifier.width(1.dp).fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        if (inspectorVisible) {
            InspectorPane(
                state = state,
                viewModel = viewModel,
                section = section,
                onSectionChange = onSectionChange,
                onImportFont = onImportFont,
                onSaveMkv = onSaveMkv,
                onCollapse = { inspectorVisible = false },
                modifier = Modifier.width(inspectorWidth).fillMaxHeight(),
            )
        } else {
            Surface(
                Modifier.width(30.dp).fillMaxHeight().clickable { inspectorVisible = true },
                tonalElevation = 1.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("‹", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun TabletEditorWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    section: WorkspaceSection,
    onSectionChange: (WorkspaceSection) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    var inspectorVisible by rememberSaveable { mutableStateOf(true) }
    val inspectorWeight = when (section) {
        WorkspaceSection.SUBTITLES -> 0.36f
        WorkspaceSection.STYLE -> 0.50f
        WorkspaceSection.EFFECTS -> 0.44f
        WorkspaceSection.REVIEW -> 0.50f
        WorkspaceSection.PROJECT -> 0.44f
    }
    Column(Modifier.fillMaxSize()) {
        PreviewPane(state, viewModel, onOpenVideo, Modifier.fillMaxWidth())
        Divider()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            SubtitleDock(state, viewModel, Modifier.weight(1f - inspectorWeight).fillMaxHeight())
            Box(
                Modifier.width(1.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            if (inspectorVisible) {
                InspectorPane(
                    state = state,
                    viewModel = viewModel,
                    section = section,
                    onSectionChange = onSectionChange,
                    onImportFont = onImportFont,
                    onSaveMkv = onSaveMkv,
                    onCollapse = { inspectorVisible = false },
                    modifier = Modifier.weight(inspectorWeight).fillMaxHeight(),
                )
            } else {
                Surface(
                    Modifier.width(30.dp).fillMaxHeight().clickable { inspectorVisible = true },
                    tonalElevation = 1.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("‹", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactEditorWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    section: WorkspaceSection,
    onSectionChange: (WorkspaceSection) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PreviewPane(state, viewModel, onOpenVideo, Modifier.fillMaxWidth())
        Divider()
        WorkspaceTabs(section, onSectionChange)
        Divider()
        if (section == WorkspaceSection.SUBTITLES) {
            SubtitleDock(state, viewModel, Modifier.weight(1f).fillMaxWidth())
        } else {
            InspectorBody(
                state = state,
                viewModel = viewModel,
                section = section,
                onImportFont = onImportFont,
                onSaveMkv = onSaveMkv,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PreviewPane(
    state: EditorState,
    viewModel: EditorViewModel,
    onOpenVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        showLayoutGuides = state.showLayoutGuides,
        onOpenVideo = onOpenVideo,
        modifier = modifier,
    )
}

@Composable
private fun WorkspaceTabs(
    section: WorkspaceSection,
    onSectionChange: (WorkspaceSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkspaceSection.entries.forEach { item ->
            val selected = section == item
            Surface(
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.small,
            ) {
                TextButton(
                    onClick = { onSectionChange(item) },
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        item.label,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorPane(
    state: EditorState,
    viewModel: EditorViewModel,
    section: WorkspaceSection,
    onSectionChange: (WorkspaceSection) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    onCollapse: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WorkspaceTabs(section, onSectionChange, Modifier.weight(1f))
                if (onCollapse != null) {
                    TextButton(onClick = onCollapse, modifier = Modifier.width(30.dp).height(30.dp)) {
                        Text("›")
                    }
                }
            }
            Divider()
            InspectorBody(
                state = state,
                viewModel = viewModel,
                section = section,
                onImportFont = onImportFont,
                onSaveMkv = onSaveMkv,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InspectorBody(
    state: EditorState,
    viewModel: EditorViewModel,
    section: WorkspaceSection,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focused = state.document.events.firstOrNull { it.id == state.focusedEventId }
    val focusedStyle = focused?.let { event ->
        state.document.styles.firstOrNull { it.name == event.style }
    }

    Box(modifier.fillMaxSize()) {
        when (section) {
            WorkspaceSection.SUBTITLES -> {
                TimelineInspector(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            WorkspaceSection.STYLE -> {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FontStatusRow(state, viewModel, onImportFont)
                    Divider()
                    if (focusedStyle != null) {
                        TypesettingPanel(
                            state = state,
                            viewModel = viewModel,
                            style = focusedStyle,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    } else {
                        Text(
                            "选择一条字幕后编辑它所使用的 Style。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            WorkspaceSection.EFFECTS -> {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    if (focused != null) {
                        EventOverridePanel(focused, viewModel)
                    } else {
                        Text(
                            "选择一条字幕后编辑位置、淡入淡出与事件级效果。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            WorkspaceSection.REVIEW -> {
                if (state.document.styles.size >= 2) {
                    ReviewWorkspace(state, viewModel, Modifier.fillMaxSize().padding(8.dp))
                } else {
                    Text(
                        "双语/校对工作区需要至少两个 Style。",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            WorkspaceSection.PROJECT -> {
                ProjectInspector(
                    state = state,
                    viewModel = viewModel,
                    onImportFont = onImportFont,
                    onSaveMkv = onSaveMkv,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ProjectInspector(
    state: EditorState,
    viewModel: EditorViewModel,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("项目 / Renderer", style = MaterialTheme.typography.titleSmall)
        Text(
            "renderer=" + BuildConfig.ASSWB_RENDERER_FONT_PROVIDER +
                " · libmpvKt=" + BuildConfig.ASSWB_RENDERER_VERSION,
            style = MaterialTheme.typography.labelSmall,
            color = if (BuildConfig.ASSWB_RENDERER_EXPERIMENTAL)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onImportFont) { Text("导入字体") }
            TextButton(onClick = viewModel::rebuildRendererFontCache) { Text("重建字体缓存") }
        }
        if (state.container.uri != null) {
            ContainerBridgePanel(state.container, viewModel, onSaveMkv)
        } else {
            Text(
                "当前不是 MKV 工程；视频与 ASS 以独立文件工作。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Divider()
        Text("ASS 几何 / 覆盖概况", style = MaterialTheme.typography.labelMedium)
        val overrideEvents = state.document.events.count { event ->
            Regex("""\\(?:fn|fs(?!c)|b-?\d|i-?\d|u-?\d|s-?\d|fsp|bord|shad|an[1-9]|a\d+|pos\(|move\(|r)""", RegexOption.IGNORE_CASE)
                .containsMatchIn(event.text) ||
                event.marginL > 0 || event.marginR > 0 || event.marginV > 0
        }
        Text(
            "PlayRes " + state.document.playResX + "×" + state.document.playResY +
                " · ScaledBorderAndShadow=" +
                (state.document.scriptInfo["ScaledBorderAndShadow"] ?: "未声明"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf("LayoutResX", "LayoutResY", "YCbCr Matrix").mapNotNull { key ->
            state.document.scriptInfo[key]?.let { value -> "$key=$value" }
        }.takeIf { it.isNotEmpty() }?.let { values ->
            Text(
                values.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "含事件级样式/位置覆盖：" + overrideEvents + "/" + state.document.events.size,
            style = MaterialTheme.typography.labelSmall,
            color = if (overrideEvents > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Divider()
        Text("Renderer 诊断", style = MaterialTheme.typography.labelMedium)
        if (state.rendererDiagnostics.isEmpty()) {
            Text(
                "等待 mpv/libass 字体选择与预览字幕来源日志。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.rendererDiagnostics.takeLast(8).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubtitleDock(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val visibleIds = state.filteredEvents.map { it.id }
    val visibleSelected = visibleIds.count { it in state.selectedEventIds }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleSelected == visibleIds.size
    var searchOpen by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }
    var replaceOpen by remember { mutableStateOf(false) }
    var followPlayback by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val activeIds = remember(state.playbackPositionMs, state.document.events) {
        state.document.activeEvents(SubTime(state.playbackPositionMs)).map { it.id }.toSet()
    }
    val primaryActiveId = activeIds.firstOrNull()
    LaunchedEffect(primaryActiveId, followPlayback, state.query) {
        if (!followPlayback || primaryActiveId == null) return@LaunchedEffect
        val index = state.filteredEvents.indexOfFirst { it.id == primaryActiveId }
        if (index >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (!visible) listState.animateScrollToItem(index)
        }
    }

    Surface(modifier, tonalElevation = 1.dp) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 28.dp) {
            Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 3.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box {
                        HintIconButton(
                            icon = Icons.Filled.Search,
                            label = "搜索字幕",
                            onClick = { searchOpen = true },
                            compact = true,
                            tint = if (state.query.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DropdownMenu(expanded = searchOpen, onDismissRequest = { searchOpen = false }) {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = viewModel::setQuery,
                                singleLine = true,
                                placeholder = { Text("正文 / Actor / Style") },
                                modifier = Modifier.width(300.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    HintIconButton(
                        icon = Icons.Filled.SelectAll,
                        label = if (allVisibleSelected) "取消全选" else "全选当前筛选结果",
                        onClick = viewModel::toggleSelectAllVisible,
                        enabled = visibleIds.isNotEmpty(),
                        compact = true,
                        tint = if (visibleSelected > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.selectedEventIds.isNotEmpty()) {
                        Text(
                            "已选 " + state.selectedEventIds.size,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        state.filteredEvents.size.toString() + "/" + state.document.events.size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    if (state.query.isNotBlank()) {
                        TextButton(onClick = { viewModel.setQuery("") }, modifier = Modifier.height(28.dp)) {
                            Text("清筛", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HintIconButton(
                        icon = Icons.Filled.MyLocation,
                        label = if (followPlayback) "关闭字幕列表跟随播放" else "开启字幕列表跟随播放",
                        onClick = { followPlayback = !followPlayback },
                        compact = true,
                        tint = if (followPlayback) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HintIconButton(
                        Icons.Filled.Add,
                        "在当前播放位置添加字幕",
                        viewModel::insertEventAtPlayback,
                        compact = true,
                    )
                    Box {
                        HintIconButton(
                            Icons.Filled.MoreVert,
                            "字幕操作：删除 / 合并 / 复制格式 / 替换",
                            { actionsOpen = true },
                            compact = true,
                        )
                        DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("删除选中 / 当前字幕") },
                                onClick = { actionsOpen = false; viewModel.deleteSelectedOrFocused() },
                            )
                            DropdownMenuItem(
                                text = { Text("合并选中字幕（换行）") },
                                enabled = state.selectedEventIds.size >= 2,
                                onClick = { actionsOpen = false; viewModel.mergeSelected(useLineBreak = true) },
                            )
                            DropdownMenuItem(
                                text = { Text("合并选中字幕（空格）") },
                                enabled = state.selectedEventIds.size >= 2,
                                onClick = { actionsOpen = false; viewModel.mergeSelected(useLineBreak = false) },
                            )
                            DropdownMenuItem(
                                text = { Text("复制当前字幕格式到选中字幕") },
                                enabled = state.focusedEventId != null && state.selectedEventIds.any { it != state.focusedEventId },
                                onClick = { actionsOpen = false; viewModel.copyFocusedFormattingToSelected() },
                            )
                            DropdownMenuItem(
                                text = { Text("批量查找 / 替换…") },
                                onClick = { actionsOpen = false; replaceOpen = true },
                            )
                        }
                    }
                }

                if (replaceOpen) {
                    ReplaceEventsDialog(
                        onDismiss = { replaceOpen = false },
                        onReplace = { find, replacement, actor ->
                            viewModel.replaceAll(find, replacement, actor)
                            replaceOpen = false
                        },
                    )
                }

                if (!state.subtitleLoaded) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("未选择 ASS 字幕", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "可从顶部文件夹图标打开现有 ASS，或在 ⋮ → 新建空白 ASS 后直接开始。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    return@Column
                }

                var dragLastId by remember { mutableStateOf<Long?>(null) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .pointerInput(state.filteredEvents.map { it.id }) {
                            fun eventAt(y: Float): Long? {
                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    y >= info.offset && y < info.offset + info.size
                                } ?: return null
                                return state.filteredEvents.getOrNull(item.index)?.id
                            }
                            detectDragGesturesAfterLongPress(
                                onDragStart = { point ->
                                    eventAt(point.y)?.let { id ->
                                        dragLastId = id
                                        viewModel.beginRangeSelection(id)
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val id = eventAt(change.position.y)
                                    if (id != null && id != dragLastId) {
                                        dragLastId = id
                                        viewModel.previewRangeSelection(id)
                                    }
                                },
                                onDragEnd = {
                                    viewModel.finishRangeSelection()
                                    dragLastId = null
                                },
                                onDragCancel = {
                                    viewModel.finishRangeSelection()
                                    dragLastId = null
                                },
                            )
                        },
                ) {
                    items(state.filteredEvents, key = { it.id }) { event ->
                        SubtitleRow(
                            event = event,
                            checked = event.id in state.selectedEventIds,
                            focused = event.id == state.focusedEventId,
                            active = event.id in activeIds,
                            viewModel = viewModel,
                            onCheck = { viewModel.toggleSelected(event.id) },
                            onFocus = { viewModel.focusEvent(event.id, seek = false) },
                            onJump = { viewModel.focusEvent(event.id, seek = true) },
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineInspector(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var windowSeconds by rememberSaveable { mutableStateOf(30) }
    val focused = state.document.events.firstOrNull { it.id == state.focusedEventId }
    val centerMs = when {
        state.project.videoUri != null -> state.playbackPositionMs
        state.playbackPositionMs > 0L -> state.playbackPositionMs
        else -> focused?.start?.millis ?: 0L
    }
    val halfWindowMs = windowSeconds * 500L
    val windowStart = (centerMs - halfWindowMs).coerceAtLeast(0L)
    val windowEnd = windowStart + windowSeconds * 1000L
    val visible = state.document.events.filter { event ->
        event.end.millis >= windowStart && event.start.millis <= windowEnd
    }.take(28)

    Column(modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("时间轴", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                formatTimelineClock(centerMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(10, 30, 60).forEach { seconds ->
                TextButton(
                    onClick = { windowSeconds = seconds },
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        if (windowSeconds == seconds) "● ${seconds}s" else "${seconds}s",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            Text(
                "当前窗口没有字幕事件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(visible, key = { it.id }) { event ->
                    TimelineEventRow(
                        event = event,
                        windowStartMs = windowStart,
                        windowEndMs = windowEnd,
                        playheadMs = state.playbackPositionMs,
                        focused = event.id == state.focusedEventId,
                        onClick = { viewModel.focusEvent(event.id, seek = true) },
                    )
                }
            }
        }
        if (state.selectedEventIds.isNotEmpty()) {
            Divider()
            BatchSelectionPanel(state, viewModel)
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: AssEvent,
    windowStartMs: Long,
    windowEndMs: Long,
    playheadMs: Long,
    focused: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(28.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "#${event.id}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(42.dp),
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(
            Modifier.weight(1f).height(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        ) {
            val span = (windowEndMs - windowStartMs).coerceAtLeast(1L).toFloat()
            val startFraction = ((event.start.millis - windowStartMs) / span).coerceIn(0f, 1f)
            val endFraction = ((event.end.millis - windowStartMs) / span).coerceIn(0f, 1f)
            val playFraction = ((playheadMs - windowStartMs) / span).coerceIn(0f, 1f)
            val eventWidth = maxWidth * (endFraction - startFraction).coerceAtLeast(0.012f)
            Box(
                Modifier.offset(x = maxWidth * startFraction)
                    .width(eventWidth).height(16.dp)
                    .background(
                        if (focused) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f)
                    )
            )
            Box(
                Modifier.offset(x = maxWidth * playFraction)
                    .width(1.dp).height(16.dp)
                    .background(MaterialTheme.colorScheme.error)
            )
        }
    }
}

private fun formatTimelineClock(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@Composable
private fun FontStatusRow(state: EditorState, viewModel: EditorViewModel, onImportFont: () -> Unit) {
    val exact = state.fontDiagnostics.count { it.status == FontMatchStatus.EXACT_IMPORTED }
    val aliasOnly = state.fontDiagnostics.count { it.status == FontMatchStatus.METADATA_ALIAS }
    val missing = state.fontDiagnostics.count { it.status == FontMatchStatus.MISSING }
    val fallback = state.fontDiagnostics.count { it.status == FontMatchStatus.FALLBACK_ONLY }
    var menuOpen by remember { mutableStateOf(false) }
    val focusedStyleName = state.focusedEventId?.let { id ->
        state.document.events.firstOrNull { it.id == id }?.style
    }
    val focusedStyle = focusedStyleName?.let { name ->
        state.document.styles.firstOrNull { it.name == name }
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val summaryColor = when {
                missing > 0 -> MaterialTheme.colorScheme.error
                aliasOnly > 0 || fallback > 0 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                "字体 ${state.importedFonts.size} · exact $exact · alias $aliasOnly · fallback $fallback · missing $missing",
                style = MaterialTheme.typography.labelSmall,
                color = summaryColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onImportFont) { Text("导入") }
        }

        if (focusedStyle != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    focusedStyle.name + " · " + focusedStyle.fontName,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = { menuOpen = true },
                    enabled = state.importedFonts.isNotEmpty(),
                ) { Text("字体") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    state.importedFonts.forEach { asset ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(asset.metadata.family)
                                    if (!asset.metadata.rendererFamily.equals(asset.metadata.family, ignoreCase = true)) {
                                        Text(
                                            "libass → " + asset.metadata.rendererFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                menuOpen = false
                                if (state.selectedEventIds.isEmpty()) {
                                    viewModel.setStyleFont(focusedStyle.name, asset.metadata.rendererFamily)
                                } else {
                                    viewModel.applyFontToSelectedStyles(asset.metadata.rendererFamily)
                                }
                            },
                        )
                    }
                }
            }

            val diagnostic = state.fontDiagnostics.firstOrNull {
                it.requestedFamily.equals(focusedStyle.fontName, ignoreCase = true)
            }
            val glyph = state.fontGlyphDiagnostics[focusedStyle.name]
            if (diagnostic != null || glyph != null) {
                val rendererMatch = when (diagnostic?.status) {
                    FontMatchStatus.EXACT_IMPORTED -> "renderer exact"
                    FontMatchStatus.METADATA_ALIAS -> "alias → " + diagnostic.matchedFamily
                    FontMatchStatus.FALLBACK_ONLY -> "fallback → " + diagnostic.matchedFamily
                    FontMatchStatus.MISSING -> "missing"
                    null -> null
                }
                val glyphText = when {
                    glyph == null -> null
                    glyph.matchedFamily == null -> "无匹配字体"
                    glyph.checkedCodePoints == 0 -> "无可检查字形"
                    glyph.missingCodePoints.isEmpty() -> "字形 ${glyph.checkedCodePoints}/${glyph.checkedCodePoints}"
                    else -> "缺字 ${glyph.missingCodePoints.size}/${glyph.checkedCodePoints}"
                }
                Text(
                    listOfNotNull(rendererMatch, glyphText).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        diagnostic?.status == FontMatchStatus.MISSING -> MaterialTheme.colorScheme.error
                        glyph != null && glyph.missingCodePoints.isNotEmpty() -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SubtitleRow(
    event: AssEvent,
    checked: Boolean,
    focused: Boolean,
    active: Boolean,
    viewModel: EditorViewModel,
    onCheck: () -> Unit,
    onFocus: () -> Unit,
    onJump: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f)
                    active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.surface
                },
            )
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onFocus).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onCheck() },
                modifier = Modifier.width(26.dp).height(26.dp),
            )
            Column(Modifier.width(132.dp)) {
                Text(
                    "#${event.id}  ${event.start.toAss()}–${event.end.toAss()}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Text(
                    "L${event.layer} · ${event.style}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = AssInlineSyntax.visibleText(event.text).ifBlank { "（空字幕）" },
                    maxLines = if (focused) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (event.name.isNotBlank()) {
                    Text(
                        "Actor · " + event.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HintIconButton(Icons.Filled.PlayArrow, "跳转到这条字幕", onJump, compact = true)
        }
        if (focused) {
            FocusedEventEditor(
                event = event,
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth().padding(start = 30.dp, end = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun FocusedEventEditor(
    event: AssEvent,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var startText by remember(event.id, event.start) { mutableStateOf(event.start.toAss()) }
    var endText by remember(event.id, event.end) { mutableStateOf(event.end.toAss()) }
    var marginL by remember(event.id, event.marginL) { mutableStateOf(event.marginL.toString()) }
    var marginR by remember(event.id, event.marginR) { mutableStateOf(event.marginR.toString()) }
    var marginV by remember(event.id, event.marginV) { mutableStateOf(event.marginV.toString()) }
    var eventText by remember(event.id) { mutableStateOf(TextFieldValue(event.text)) }
    var timingOpen by remember { mutableStateOf(false) }
    val syntax = remember(event.text) { AssInlineSyntax.analyze(event.text) }

    LaunchedEffect(event.text) {
        if (eventText.text != event.text) {
            val cursor = eventText.selection.start.coerceIn(0, event.text.length)
            eventText = TextFieldValue(event.text, selection = androidx.compose.ui.text.TextRange(cursor))
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactEventField("Start", startText, { startText = it }, Modifier.weight(1f))
            CompactEventField("End", endText, { endText = it }, Modifier.weight(1f))
            TextButton(onClick = { viewModel.updateFocusedTimes(startText, endText) }, modifier = Modifier.height(36.dp)) {
                Text("应用", style = MaterialTheme.typography.labelSmall)
            }
            Box {
                TextButton(onClick = { timingOpen = true }, modifier = Modifier.height(36.dp)) {
                    Text("时间", style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = timingOpen, onDismissRequest = { timingOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("当前播放位置 → Start") },
                        onClick = { viewModel.setFocusedStartToPlayback(); timingOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("当前播放位置 → End") },
                        onClick = { viewModel.setFocusedEndToPlayback(); timingOpen = false },
                    )
                    Text(
                        "整体平移",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.padding(horizontal = 6.dp)) {
                        listOf(-500L, -100L, -10L, 10L, 100L, 500L).forEach { delta ->
                            TextButton(onClick = { viewModel.nudgeFocusedTime(delta) }, modifier = Modifier.height(28.dp)) {
                                Text((if (delta > 0) "+" else "") + delta, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(
                        "起点",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.padding(horizontal = 6.dp)) {
                        listOf(-100L, -10L, 10L, 100L).forEach { delta ->
                            TextButton(onClick = { viewModel.nudgeFocusedStart(delta) }, modifier = Modifier.height(28.dp)) {
                                Text((if (delta > 0) "+" else "") + delta, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(
                        "终点",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        listOf(-100L, -10L, 10L, 100L).forEach { delta ->
                            TextButton(onClick = { viewModel.nudgeFocusedEnd(delta) }, modifier = Modifier.height(28.dp)) {
                                Text((if (delta > 0) "+" else "") + delta, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactEventField("L", marginL, { marginL = it }, Modifier.weight(1f))
            CompactEventField("R", marginR, { marginR = it }, Modifier.weight(1f))
            CompactEventField("V", marginV, { marginV = it }, Modifier.weight(1f))
            TextButton(
                onClick = {
                    viewModel.updateFocusedMargins(
                        marginL.toIntOrNull() ?: event.marginL,
                        marginR.toIntOrNull() ?: event.marginR,
                        marginV.toIntOrNull() ?: event.marginV,
                    )
                },
                modifier = Modifier.height(36.dp),
            ) { Text("Margin", style = MaterialTheme.typography.labelSmall) }
            TextButton(
                onClick = {
                    marginL = "0"; marginR = "0"; marginV = "0"
                    viewModel.clearFocusedMargins()
                },
                modifier = Modifier.height(36.dp),
            ) { Text("继承", style = MaterialTheme.typography.labelSmall) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Event Text · " + syntax.tags.size + " tags" +
                    if (syntax.hasErrors) " · " + syntax.issues.size + " issue" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (syntax.hasErrors) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { viewModel.splitFocusedEvent(eventText.selection.start) },
                enabled = eventText.selection.start in 1 until eventText.text.length,
                modifier = Modifier.height(28.dp),
            ) { Text("光标处分割", style = MaterialTheme.typography.labelSmall) }
        }
        OutlinedTextField(
            value = eventText,
            onValueChange = { value ->
                eventText = value
                viewModel.updateFocusedText(value.text)
            },
            visualTransformation = rememberAssSyntaxTransformation(),
            isError = syntax.hasErrors,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp, max = 132.dp),
        )
    }
}

@Composable
private fun ReplaceEventsDialog(
    onDismiss: () -> Unit,
    onReplace: (String, String, Boolean) -> Unit,
) {
    var find by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var actorMode by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量查找 / 替换") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { actorMode = false }) {
                        Text(if (!actorMode) "✓ 正文" else "正文")
                    }
                    TextButton(onClick = { actorMode = true }) {
                        Text(if (actorMode) "✓ Actor / 角色名" else "Actor / 角色名")
                    }
                }
                OutlinedTextField(
                    value = find,
                    onValueChange = { find = it },
                    label = { Text("查找") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("替换为") },
                    singleLine = true,
                )
                Text(
                    if (actorMode) "只修改 ASS Event 的 Name/Actor 字段，不碰正文。"
                    else "只修改可见正文；ASS override block 与标签值保持不变。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onReplace(find, replacement, actorMode) }, enabled = find.isNotEmpty()) {
                Text("全部替换")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CompactEventField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = modifier.height(44.dp),
    )
}


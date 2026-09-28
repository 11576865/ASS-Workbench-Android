package io.github.assworkbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.AppThemeMode
import io.github.assworkbench.app.BuildConfig
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssInlineSyntax
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
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
) {
    var sectionName by rememberSaveable { mutableStateOf(WorkspaceSection.SUBTITLES.name) }
    val section = WorkspaceSection.entries.firstOrNull { it.name == sectionName } ?: WorkspaceSection.SUBTITLES
    var overflowOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.project.title + if (state.dirty) " *" else "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${state.document.events.size} 条字幕 · " +
                                if (BuildConfig.ASSWB_RENDERER_EXPERIMENTAL) "Fontconfig renderer" else "libass preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenVideo) { Icon(Icons.Filled.Movie, "打开/更换视频") }
                    IconButton(onClick = onOpenSubtitle) { Icon(Icons.Filled.FolderOpen, "打开/更换字幕") }
                    TextButton(onClick = onOpenMkvProject) { Text("MKV") }
                    IconButton(onClick = onSave, enabled = state.subtitleLoaded) { Icon(Icons.Filled.Save, "保存") }
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "撤销")
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, "重做")
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, "更多")
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("另存 ASS") },
                                enabled = state.subtitleLoaded,
                                onClick = {
                                    overflowOpen = false
                                    onSaveAs()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("导入字体") },
                                onClick = {
                                    overflowOpen = false
                                    onImportFont()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("重建字体缓存") },
                                onClick = {
                                    overflowOpen = false
                                    viewModel.rebuildRendererFontCache()
                                },
                            )
                            androidx.compose.material3.HorizontalDivider()
                            AppThemeMode.entries.forEach { mode ->
                                val label = when (mode) {
                                    AppThemeMode.SYSTEM -> "跟随系统"
                                    AppThemeMode.LIGHT -> "浅色"
                                    AppThemeMode.DARK -> "深色"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            (if (themeMode == mode) "✓ " else "") + "主题 · " + label
                                        )
                                    },
                                    onClick = {
                                        overflowOpen = false
                                        onThemeModeChange(mode)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Text(
                state.status,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.fillMaxSize().padding(padding),
        )
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
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
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
                )
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) ->
                TabletEditorWorkspace(
                    state,
                    viewModel,
                    section,
                    onSectionChange,
                    onImportFont,
                    onSaveMkv,
                )
            else -> CompactEditorWorkspace(
                state,
                viewModel,
                section,
                onSectionChange,
                onImportFont,
                onSaveMkv,
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
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PreviewPane(state, viewModel, Modifier.weight(0.64f).fillMaxWidth())
            Divider()
            SubtitleDock(state, viewModel, Modifier.weight(0.36f).fillMaxWidth())
        }
        Box(
            Modifier.width(1.dp).fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        InspectorPane(
            state = state,
            viewModel = viewModel,
            section = section,
            onSectionChange = onSectionChange,
            onImportFont = onImportFont,
            onSaveMkv = onSaveMkv,
            modifier = Modifier.width(372.dp).fillMaxHeight(),
        )
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
) {
    Column(Modifier.fillMaxSize()) {
        PreviewPane(state, viewModel, Modifier.weight(0.56f).fillMaxWidth())
        Divider()
        Row(Modifier.weight(0.44f).fillMaxWidth()) {
            SubtitleDock(state, viewModel, Modifier.weight(0.58f).fillMaxHeight())
            Box(
                Modifier.width(1.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            InspectorPane(
                state = state,
                viewModel = viewModel,
                section = section,
                onSectionChange = onSectionChange,
                onImportFont = onImportFont,
                onSaveMkv = onSaveMkv,
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
            )
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
) {
    Column(Modifier.fillMaxSize()) {
        PreviewPane(state, viewModel, Modifier.weight(0.46f).fillMaxWidth())
        Divider()
        WorkspaceTabs(section, onSectionChange)
        Divider()
        if (section == WorkspaceSection.SUBTITLES) {
            SubtitleDock(state, viewModel, Modifier.weight(0.54f).fillMaxWidth())
        } else {
            InspectorBody(
                state = state,
                viewModel = viewModel,
                section = section,
                onImportFont = onImportFont,
                onSaveMkv = onSaveMkv,
                modifier = Modifier.weight(0.54f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PreviewPane(
    state: EditorState,
    viewModel: EditorViewModel,
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
            .padding(horizontal = 6.dp, vertical = 4.dp),
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
                    modifier = Modifier.height(40.dp),
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
    modifier: Modifier = Modifier,
) {
    Surface(modifier, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxSize()) {
            WorkspaceTabs(section, onSectionChange)
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
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("字幕检查器", style = MaterialTheme.typography.titleSmall)
                    if (state.selectedEventIds.isNotEmpty()) {
                        BatchSelectionPanel(state, viewModel)
                        Divider()
                    }
                    if (focused != null) {
                        FocusedEventEditor(focused, viewModel)
                    } else {
                        Text(
                            "从下方字幕列表选择一条字幕后，在这里编辑时间与文本。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
    val selectState = when {
        visibleIds.isEmpty() || visibleSelected == 0 -> ToggleableState.Off
        visibleSelected == visibleIds.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Surface(modifier, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    placeholder = { Text("搜索正文 / Actor / Style") },
                    modifier = Modifier.weight(1f),
                )
                TriStateCheckbox(
                    state = selectState,
                    onClick = viewModel::toggleSelectAllVisible,
                    enabled = visibleIds.isNotEmpty(),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        state.filteredEvents.size.toString() + "/" + state.document.events.size,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (state.selectedEventIds.isNotEmpty()) {
                        Text(
                            "已选 " + state.selectedEventIds.size,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(state.filteredEvents, key = { it.id }) { event ->
                    SubtitleRow(
                        event = event,
                        checked = event.id in state.selectedEventIds,
                        focused = event.id == state.focusedEventId,
                        onCheck = { viewModel.toggleSelected(event.id) },
                        onFocus = {
                            if (state.selectionAnchorId != null) viewModel.selectRangeTo(event.id)
                            else viewModel.focusEvent(event.id, seek = false)
                        },
                        onLongPress = { viewModel.beginRangeSelection(event.id) },
                        onJump = { viewModel.focusEvent(event.id, seek = true) },
                    )
                    Divider()
                }
            }
        }
    }
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SubtitleRow(
    event: AssEvent,
    checked: Boolean,
    focused: Boolean,
    onCheck: () -> Unit,
    onFocus: () -> Unit,
    onLongPress: () -> Unit,
    onJump: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(
                if (focused) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.surface,
            )
            .combinedClickable(onClick = onFocus, onLongClick = onLongPress)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onCheck() })
        Column(Modifier.width(138.dp)) {
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
                text = rememberAssAnnotatedText(event.text),
                maxLines = if (focused) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (focused && (event.marginL > 0 || event.marginR > 0 || event.marginV > 0)) {
                Text(
                    "Event Margin ${event.marginL}/${event.marginR}/${event.marginV}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        IconButton(onClick = onJump) { Icon(Icons.Filled.PlayArrow, "跳转") }
    }
}

@Composable
private fun FocusedEventEditor(event: AssEvent, viewModel: EditorViewModel) {
    var startText by remember(event.id, event.start) { mutableStateOf(event.start.toAss()) }
    var endText by remember(event.id, event.end) { mutableStateOf(event.end.toAss()) }
    var marginL by remember(event.id, event.marginL) { mutableStateOf(event.marginL.toString()) }
    var marginR by remember(event.id, event.marginR) { mutableStateOf(event.marginR.toString()) }
    var marginV by remember(event.id, event.marginV) { mutableStateOf(event.marginV.toString()) }
    val syntax = remember(event.text) { AssInlineSyntax.analyze(event.text) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("当前字幕 · Style ${event.style} · Layer ${event.layer}", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text("开始") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it },
                label = { Text("结束") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        androidx.compose.material3.TextButton(onClick = { viewModel.updateFocusedTimes(startText, endText) }) { Text("应用时间") }
        Text("事件级 Margin（0 = 继承 Style）", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = marginL,
                onValueChange = { marginL = it },
                label = { Text("L") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = marginR,
                onValueChange = { marginR = it },
                label = { Text("R") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = marginV,
                onValueChange = { marginV = it },
                label = { Text("V") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
                onClick = {
                    viewModel.updateFocusedMargins(
                        marginL.toIntOrNull() ?: event.marginL,
                        marginR.toIntOrNull() ?: event.marginR,
                        marginV.toIntOrNull() ?: event.marginV,
                    )
                },
            ) { Text("应用 Margin") }
            TextButton(
                onClick = {
                    marginL = "0"; marginR = "0"; marginV = "0"
                    viewModel.clearFocusedMargins()
                },
            ) { Text("全部继承 Style") }
        }
        Text(
            "ASS Event Text · " + syntax.tags.size + " tags" +
                if (syntax.hasErrors) " · " + syntax.issues.size + " syntax issue(s)" else "",
            style = MaterialTheme.typography.labelMedium,
            color = if (syntax.hasErrors) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = event.text,
            onValueChange = viewModel::updateFocusedText,
            label = { Text("Event Text / Override Tags") },
            visualTransformation = rememberAssSyntaxTransformation(),
            isError = syntax.hasErrors,
            supportingText = if (syntax.hasErrors) {
                { Text(syntax.issues.first().message) }
            } else null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 220.dp),
        )
    }
}



package io.github.assworkbench.app.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.fonts.FontMatchStatus

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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.project.title + if (state.dirty) " *" else "")
                        Text(
                            "ASS · ${state.document.events.size} events · libass preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenVideo) { Icon(Icons.Filled.Movie, "打开/更换视频") }
                    androidx.compose.material3.TextButton(onClick = onOpenMkvProject) { Text("MKV") }
                    IconButton(onClick = onOpenSubtitle) { Icon(Icons.Filled.FolderOpen, "打开/更换字幕") }
                    IconButton(onClick = onImportFont) { Icon(Icons.Filled.FontDownload, "导入 TTF/OTF 字体") }
                    IconButton(onClick = onSave, enabled = state.subtitleLoaded) { Icon(Icons.Filled.Save, "保存") }
                    IconButton(onClick = onSaveAs, enabled = state.subtitleLoaded) { Icon(Icons.Filled.SaveAs, "另存为") }
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "撤销") }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "重做") }
                },
            )
        },
        bottomBar = {
            Text(
                state.status,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val horizontal = maxWidth >= 720.dp
            ResizableSplitPane(
                ratio = state.project.splitRatio,
                horizontal = horizontal,
                onRatioChange = viewModel::setSplitRatio,
                first = { modifier ->
                    VideoPreview(
                        videoUri = state.project.videoUri,
                        document = state.document,
                        seekRequestMs = state.seekRequestMs,
                        seekRequestNonce = state.seekRequestNonce,
                        onPosition = viewModel::setPlaybackPosition,
                        configDir = viewModel.rendererConfigDir(),
                        fontsDir = viewModel.rendererFontsDir(),
                        fontRevision = state.fontRevision,
                        initialPositionMs = state.playbackPositionMs,
                        showLayoutGuides = state.showLayoutGuides,
                        modifier = modifier,
                    )
                },
                second = { modifier -> SubtitleWorkbench(state, viewModel, onImportFont, onSaveMkv, modifier) },
            )
        }
    }
}

@Composable
private fun SubtitleWorkbench(
    state: EditorState,
    viewModel: EditorViewModel,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focused = state.document.events.firstOrNull { it.id == state.focusedEventId }
    val focusedStyle = focused?.let { event -> state.document.styles.firstOrNull { it.name == event.style } }
    var typesettingOpen by remember { mutableStateOf(false) }
    var reviewOpen by remember { mutableStateOf(false) }
    var effectsOpen by remember { mutableStateOf(false) }
    Column(modifier.padding(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            singleLine = true,
            label = { Text("搜索字幕") },
            modifier = Modifier.fillMaxWidth(),
        )
        ContainerBridgePanel(state.container, viewModel, onSaveMkv)
        FontStatusRow(state, viewModel, onImportFont)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("工作区", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(
                onClick = {
                    reviewOpen = false
                    effectsOpen = false
                    typesettingOpen = !typesettingOpen
                },
                enabled = focusedStyle != null,
            ) { Text(if (typesettingOpen) "收起排版" else "排版") }
            androidx.compose.material3.TextButton(
                onClick = {
                    typesettingOpen = false
                    effectsOpen = false
                    reviewOpen = !reviewOpen
                },
                enabled = state.document.styles.size >= 2,
            ) { Text(if (reviewOpen) "收起校对" else "双语/校对") }
            androidx.compose.material3.TextButton(
                onClick = {
                    typesettingOpen = false
                    reviewOpen = false
                    effectsOpen = !effectsOpen
                },
                enabled = focused != null,
            ) { Text(if (effectsOpen) "收起效果" else "效果/位置") }
        }
        if (typesettingOpen && focusedStyle != null) {
            TypesettingPanel(state, viewModel, focusedStyle)
        }
        if (reviewOpen) {
            ReviewWorkspace(state, viewModel, Modifier.weight(1f))
            return@Column
        }
        if (effectsOpen && focused != null) {
            EventOverridePanel(focused, viewModel)
        }
        val visibleIds = state.filteredEvents.map { it.id }
        val visibleSelected = visibleIds.count { it in state.selectedEventIds }
        val selectState = when {
            visibleIds.isEmpty() || visibleSelected == 0 -> ToggleableState.Off
            visibleSelected == visibleIds.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TriStateCheckbox(
                state = selectState,
                onClick = viewModel::toggleSelectAllVisible,
                enabled = visibleIds.isNotEmpty(),
            )
            Text("字幕总览", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (state.selectedEventIds.isNotEmpty()) {
                Text("已选 " + state.selectedEventIds.size, style = MaterialTheme.typography.labelMedium)
            }
            Text(state.filteredEvents.size.toString() + "/" + state.document.events.size, style = MaterialTheme.typography.labelMedium)
        }
        if (state.selectedEventIds.isNotEmpty()) {
            BatchSelectionPanel(state, viewModel)
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
        focused?.let { event ->
            Divider()
            FocusedEventEditor(event, viewModel)
        }
    }
}

@Composable
private fun FontStatusRow(state: EditorState, viewModel: EditorViewModel, onImportFont: () -> Unit) {
    val exact = state.fontDiagnostics.count { it.status == FontMatchStatus.EXACT_IMPORTED }
    val missing = state.fontDiagnostics.count { it.status == FontMatchStatus.MISSING }
    val fallback = state.fontDiagnostics.count { it.status == FontMatchStatus.FALLBACK_ONLY }
    var menuOpen by remember { mutableStateOf(false) }
    val focusedStyleName = state.focusedEventId?.let { id -> state.document.events.firstOrNull { it.id == id }?.style }
    val focusedStyle = focusedStyleName?.let { name -> state.document.styles.firstOrNull { it.name == name } }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "字体：" + state.importedFonts.size + " imported · " + exact + " exact · " + fallback + " fallback · " + missing + " missing",
                style = MaterialTheme.typography.labelSmall,
                color = if (missing > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = onImportFont) { Text("导入字体") }
        }
        if (focusedStyle != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Style " + focusedStyle.name + "：" + focusedStyle.fontName,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(
                    onClick = { menuOpen = true },
                    enabled = state.importedFonts.isNotEmpty(),
                ) { Text("选择字体") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    state.importedFonts.forEach { asset ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(asset.metadata.family)
                                    Text(asset.fileName, style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            onClick = {
                                menuOpen = false
                                if (state.selectedEventIds.isEmpty()) {
                                    viewModel.setStyleFont(focusedStyle.name, asset.metadata.family)
                                } else {
                                    viewModel.applyFontToSelectedStyles(asset.metadata.family)
                                }
                            },
                        )
                    }
                }
            }
            val diagnostic = state.fontDiagnostics.firstOrNull {
                it.requestedFamily.equals(focusedStyle.fontName, ignoreCase = true)
            }
            if (diagnostic != null) {
                val diagnosticText = when (diagnostic.status) {
                    FontMatchStatus.EXACT_IMPORTED -> "字体名称：EXACT → " + diagnostic.matchedFamily
                    FontMatchStatus.FALLBACK_ONLY -> "字体名称：FALLBACK → " + diagnostic.matchedFamily
                    FontMatchStatus.MISSING -> "字体名称：MISSING"
                }
                Text(
                    diagnosticText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (diagnostic.status == FontMatchStatus.EXACT_IMPORTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            state.fontGlyphDiagnostics[focusedStyle.name]?.let { glyph ->
                val glyphText = when {
                    glyph.matchedFamily == null -> "字形覆盖：未找到已导入的同名字体"
                    glyph.checkedCodePoints == 0 -> "字形覆盖：当前 Style 没有可检查字符"
                    glyph.missingCodePoints.isEmpty() -> "字形覆盖：已检查 " + glyph.checkedCodePoints + " 个字符，全部存在"
                    else -> "字形覆盖：缺少 " + glyph.missingCodePoints.size + " 个样例字形 → " + glyph.missingSampleText
                }
                Text(
                    glyphText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (glyph.matchedFamily != null && glyph.missingCodePoints.isEmpty())
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
        Modifier.fillMaxWidth().combinedClickable(onClick = onFocus, onLongClick = onLongPress).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onCheck() })
        Text("#${event.id}", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall)
        Column(Modifier.width(104.dp)) {
            Text(event.start.toAss(), style = MaterialTheme.typography.labelSmall)
            Text(event.end.toAss(), style = MaterialTheme.typography.labelSmall)
            Text("L${event.layer}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            event.text,
            modifier = Modifier.weight(1f),
            maxLines = if (focused) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onJump) { Icon(Icons.Filled.PlayArrow, "跳转") }
    }
}

@Composable
private fun FocusedEventEditor(event: AssEvent, viewModel: EditorViewModel) {
    var startText by remember(event.id, event.start) { mutableStateOf(event.start.toAss()) }
    var endText by remember(event.id, event.end) { mutableStateOf(event.end.toAss()) }

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
        OutlinedTextField(
            value = event.text,
            onValueChange = viewModel::updateFocusedText,
            label = { Text("文本（保留 ASS override tags）") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp, max = 180.dp),
        )
    }
}

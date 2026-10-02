package io.github.assworkbench.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.AssWorkbenchProjectSnapshot
import io.github.assworkbench.app.BuildConfig
import io.github.assworkbench.app.ProjectFileCodec
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.WaveformLiteState
import io.github.assworkbench.app.WaveformLiteStatus
import io.github.assworkbench.app.ui.interaction.InteractionOverlayRegistry
import io.github.assworkbench.app.ui.interaction.PrecisionInteractionOverlay
import io.github.assworkbench.app.ui.interaction.WindowInteractionOverlay
import io.github.assworkbench.app.ui.interaction.rememberInteractionOverlayRegistry
import io.github.assworkbench.app.ui.preview.PreviewTargetCandidate
import io.github.assworkbench.app.ui.preview.PreviewTargetConfidence
import io.github.assworkbench.app.ui.preview.PreviewTargetResolver
import io.github.assworkbench.app.ui.workspace.WorkspaceBinding
import io.github.assworkbench.app.ui.workspace.SurfaceGeometry
import io.github.assworkbench.app.ui.workspace.FloatingWorkbenchSurface
import io.github.assworkbench.app.ui.workspace.WorkbenchSurfaceController
import io.github.assworkbench.app.ui.workspace.WorkspaceToolPresence
import io.github.assworkbench.app.ui.workspace.ToolContentDensity
import io.github.assworkbench.app.ui.workspace.rememberWorkbenchSurfaceController
import io.github.assworkbench.app.ui.workspace.WorkspaceBindingResolution
import io.github.assworkbench.app.ui.workspace.WorkspaceEditScopeResolver
import io.github.assworkbench.app.ui.workspace.WorkspaceEditScopeSummary
import io.github.assworkbench.app.ui.workspace.WorkspaceState
import io.github.assworkbench.app.ui.workspace.WorkspaceToolInstance
import io.github.assworkbench.app.ui.workspace.resolve
import io.github.assworkbench.domain.*
import io.github.assworkbench.fonts.FontDiagnostics
import io.github.assworkbench.fonts.FontOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DestructiveWorkspaceAction { OPEN_ASS, OPEN_PROJECT, NEW_ASS }

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
    appearance: WorkbenchAppearance = WorkbenchAppearance.SYSTEM,
    onAppearanceChange: (WorkbenchAppearance) -> Unit = {},
) {
    var workspaceState by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toSaveableList() },
            restore = { WorkspaceState.fromSaveableList(it) },
        ),
    ) {
        mutableStateOf(
            WorkspaceState(sessionId = state.workspaceSessionId)
                .openPrimary(WorkbenchTool.STYLE.name)
                .withSurfacesHidden(true)
        )
    }
    val surfaceController = rememberWorkbenchSurfaceController()
    val interactionRegistry = rememberInteractionOverlayRegistry()
    var expandedEventId by rememberSaveable { mutableStateOf<Long?>(null) }
    val eventEditorStateHolder = rememberSaveableStateHolder()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var openMenu by remember { mutableStateOf(false) }
    var uiVariantLabOpen by rememberSaveable { mutableStateOf(false) }
    var saveConfirmOpen by remember { mutableStateOf(false) }
    var mkvConfirmOpen by remember { mutableStateOf(false) }
    var destructiveWorkspaceAction by remember { mutableStateOf<DestructiveWorkspaceAction?>(null) }
    val context = LocalContext.current
    var workspaceModeName by rememberSaveable { mutableStateOf(WorkspacePresentationMode.FIXED.name) }
    var fixedToolName by rememberSaveable { mutableStateOf(WorkbenchTool.STYLE.name) }
    var fixedNavigationRevision by rememberSaveable { mutableIntStateOf(0) }
    var fixedListRequested by rememberSaveable { mutableStateOf(false) }
    val workspaceMode = UiVariantRegistry.resolve(workspaceModeName)
    val fixedTool = WorkbenchTool.valueOf(fixedToolName)

    LaunchedEffect(state.workspaceSessionId) {
        if (workspaceState.sessionId != state.workspaceSessionId) {
            workspaceState = WorkspaceState(sessionId = state.workspaceSessionId)
                .openPrimary(WorkbenchTool.STYLE.name)
                .withSurfacesHidden(true)
            surfaceController.restore(emptyList())
            expandedEventId = null
            fixedToolName = WorkbenchTool.STYLE.name
            fixedListRequested = false
            workspaceModeName = WorkspacePresentationMode.FIXED.name
        }
    }

    val openProjectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("无法读取 Workbench Project")
            ProjectFileCodec.decode(text)
        }.onSuccess { snapshot ->
            val sessionId = viewModel.loadProjectSnapshot(snapshot)
            workspaceState = WorkspaceState.fromSaveableList(snapshot.workspaceState).forSession(sessionId)
            surfaceController.restore(snapshot.surfaceState)
            workspaceModeName = UiVariantRegistry.resolve(snapshot.workspaceMode).name
        }.onFailure { viewModel.reportError("Project 打开失败", it) }
    }

    val saveProjectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val snapshot = AssWorkbenchProjectSnapshot(
            title = state.project.title,
            document = state.document,
            videoUri = state.project.videoUri,
            subtitleUri = state.project.subtitleUri,
            containerUri = state.container.uri,
            containerTrackNumber = state.container.selectedTrackNumber,
            textEncoding = state.subtitleTextEncoding,
            sourceFormat = state.sourceFormat,
            focusedEventId = state.focusedEventId,
            selectedEventIds = state.selectedEventIds,
            workspaceMode = workspaceModeName,
            workspaceState = workspaceState.toSaveableList(),
            surfaceState = surfaceController.save(),
        )
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(ProjectFileCodec.encode(snapshot))
            } ?: error("无法写入 Workbench Project")
        }.onFailure { viewModel.reportError("Project 保存失败", it) }
    }

    val exportSrtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        uri?.let(viewModel::saveSrtTo)
    }

    fun projectFileName(): String =
        state.project.title.substringBeforeLast('.').ifBlank { "subtitle-project" } + ProjectFileCodec.EXTENSION

    fun srtFileName(): String =
        state.project.title.substringBeforeLast('.').ifBlank { "subtitle" } + ".srt"

    val existingEventIds = remember(state.document.events) {
        state.document.events.asSequence().map { it.id }.toSet()
    }
    val activePositionInstance = workspaceState.activeForTool(WorkbenchTool.POSITION.name)
    val activePositionResolution = activePositionInstance?.binding?.resolve(
        focusedEventId = state.focusedEventId,
        selectedEventIds = state.selectedEventIds,
        existingEventIds = existingEventIds,
    )
    val positionEditEventId = when {
        activePositionInstance != null ->
            (activePositionResolution as? WorkspaceBindingResolution.Event)?.eventId
        else -> null
    }
    val issues by produceState<List<AssQcIssue>>(initialValue = emptyList(), state.document) {
        value = withContext(Dispatchers.Default) {
            AssQualityCheck.inspect(state.document)
        }
    }

    fun openTool(next: WorkbenchTool) {
        val primaryId = WorkspaceState.primaryInstanceId(next.name)
        if (workspaceMode == WorkspacePresentationMode.TOOL_INSTANCES_EXPERIMENTAL) {
            val existing = workspaceState.primary(next.name)
            workspaceState = if (
                existing != null &&
                workspaceState.activeInstanceId == primaryId &&
                existing.presence == WorkspaceToolPresence.TEMPORARY
            ) {
                workspaceState.updatePresence(primaryId, WorkspaceToolPresence.HIDDEN)
            } else {
                workspaceState
                    .openPrimary(next.name, next.descriptor.defaultBinding)
                    .updatePresence(primaryId, WorkspaceToolPresence.TEMPORARY)
                    .hideOtherTemporary(primaryId)
                    .withSurfacesHidden(false)
            }
            if (workspaceState.primary(next.name)?.presence == WorkspaceToolPresence.TEMPORARY) {
                surfaceController.bringToFront(primaryId)
            }
            return
        }

        workspaceState = workspaceState.openPrimary(next.name, next.descriptor.defaultBinding)
        if (workspaceMode == WorkspacePresentationMode.FIXED ||
            workspaceMode == WorkspacePresentationMode.PAGER_EXPERIMENTAL
        ) {
            fixedListRequested = next == WorkbenchTool.SUBTITLES
            fixedNavigationRevision += 1
            if (!fixedListRequested) fixedToolName = next.name
            return
        }
        workspaceState = workspaceState.withSurfacesHidden(false)
        surfaceController.bringToFront(primaryId)
    }

    fun openPositionTarget(eventId: Long) {
        if (eventId !in existingEventIds) return
        viewModel.focusEvent(eventId, seek = false)
        val toolKey = WorkbenchTool.POSITION.name
        workspaceState = workspaceState.openPinnedEvent(toolKey, eventId)

        fixedListRequested = false
        fixedNavigationRevision += 1
        fixedToolName = toolKey
        if (workspaceMode == WorkspacePresentationMode.CANVAS_EXPERIMENTAL) {
            workspaceState = workspaceState.withSurfacesHidden(false)
            workspaceState.activeInstanceId?.let { id -> surfaceController.bringToFront(id) }
        }
    }

    fun selectWorkspaceMode(next: WorkspacePresentationMode) {
        if (next == workspaceMode) return
        if (workspaceMode == WorkspacePresentationMode.CANVAS_EXPERIMENTAL) {
            workspaceState.activeInstanceId
                ?.let { id -> workspaceState.tools.firstOrNull { it.id == id }?.toolKey }
                ?.let { key -> WorkbenchTool.entries.firstOrNull { it.name == key } }
                ?.takeIf { it != WorkbenchTool.SUBTITLES && it != WorkbenchTool.CAPABILITIES }
                ?.let { fixedToolName = it.name }
        }
        workspaceModeName = next.name
        workspaceState = workspaceState.withSurfacesHidden(false)
    }

    fun toggleAllSurfaces() {
        workspaceState = workspaceState.withSurfacesHidden(!workspaceState.surfacesHidden)
    }

    MaterialTheme(colorScheme = workbenchColors(appearance)) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = workspaceMode == WorkspacePresentationMode.FIXED || !workspaceState.surfacesHidden
            ) {
            ModernAppBar(
                state = state,
                viewModel = viewModel,
                selectionMode = state.selectedEventIds.isNotEmpty(),
                searchOpen = searchOpen,
                onSearchToggle = {
                    searchOpen = !searchOpen
                    if (searchOpen || workspaceMode == WorkspacePresentationMode.CANVAS_EXPERIMENTAL) {
                        openTool(WorkbenchTool.SUBTITLES)
                    }
                },
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
                workspaceMode = workspaceMode,
                onOpenUiVariantLab = { uiVariantLabOpen = true },
                onOpenProject = {
                    if (state.dirty) {
                        destructiveWorkspaceAction = DestructiveWorkspaceAction.OPEN_PROJECT
                    } else {
                        openProjectLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                },
                onSaveProject = { saveProjectLauncher.launch(projectFileName()) },
                onExportSrt = { exportSrtLauncher.launch(srtFileName()) },
                appearance = appearance,
                onAppearanceChange = onAppearanceChange,
            )
            }

            if (uiVariantLabOpen) {
                UiVariantLabDialog(
                    selected = workspaceMode,
                    onSelect = { next ->
                        selectWorkspaceMode(next)
                        uiVariantLabOpen = false
                    },
                    onDismiss = { uiVariantLabOpen = false },
                )
            }

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
                                    "当前字幕有未保存修改。继续打开另一份 ASS / SRT 会丢弃当前未保存内容与对应恢复日志。"
                                DestructiveWorkspaceAction.OPEN_PROJECT ->
                                    "当前字幕有未保存修改。继续打开 Workbench Project 会替换当前文档与工作区，并丢弃当前未保存内容与对应恢复日志。"
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
                                DestructiveWorkspaceAction.OPEN_PROJECT ->
                                    openProjectLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                DestructiveWorkspaceAction.NEW_ASS -> viewModel.newSubtitleProject()
                            }
                        }) { Text("放弃修改并继续") }
                    },
                    dismissButton = {
                        TextButton(onClick = { destructiveWorkspaceAction = null }) { Text("取消") }
                    },
                )
            }

            if (workspaceMode == WorkspacePresentationMode.FIXED) {
                FixedWorkspace(
                    state = state,
                    viewModel = viewModel,
                    issues = issues,
                    activeTool = fixedTool,
                    instance = workspaceState.activeForTool(fixedTool.name)
                        ?: WorkspaceToolInstance(
                            id = WorkspaceState.primaryInstanceId(fixedTool.name),
                            toolKey = fixedTool.name,
                            binding = fixedTool.descriptor.defaultBinding,
                        ),
                    navigationRevision = fixedNavigationRevision,
                    listRequested = fixedListRequested,
                    onActiveTool = { tool ->
                        fixedToolName = tool.name
                        workspaceState = workspaceState
                            .openPrimary(tool.name, tool.descriptor.defaultBinding)
                            .activate(WorkspaceState.primaryInstanceId(tool.name))
                    },
                    expandedEventId = expandedEventId,
                    onExpandedChange = { expandedEventId = it },
                    onImportFont = onImportFont,
                    onSaveMkv = onSaveMkv,
                    eventEditorStateHolder = eventEditorStateHolder,
                    onOpenVideo = onOpenReferenceVideo,
                    rendererEnabled = rendererEnabled,
                    onEnableRenderer = onEnableRenderer,
                    interactionRegistry = interactionRegistry,
                    onEditEventPosition = ::openPositionTarget,
                    searchOpen = searchOpen,
                    onCloseSearch = { searchOpen = false; viewModel.setQuery("") },
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("fixed-workspace"),
                )
            } else if (workspaceMode == WorkspacePresentationMode.PAGER_EXPERIMENTAL) {
                PagerWorkspace(
                    state = state,
                    viewModel = viewModel,
                    issues = issues,
                    activeTool = fixedTool,
                    instance = workspaceState.activeForTool(fixedTool.name)
                        ?: WorkspaceToolInstance(
                            id = WorkspaceState.primaryInstanceId(fixedTool.name),
                            toolKey = fixedTool.name,
                            binding = fixedTool.descriptor.defaultBinding,
                        ),
                    onActiveTool = { tool ->
                        fixedToolName = tool.name
                        workspaceState = workspaceState
                            .openPrimary(tool.name, tool.descriptor.defaultBinding)
                            .activate(WorkspaceState.primaryInstanceId(tool.name))
                    },
                    expandedEventId = expandedEventId,
                    onExpandedChange = { expandedEventId = it },
                    onImportFont = onImportFont,
                    onSaveMkv = onSaveMkv,
                    eventEditorStateHolder = eventEditorStateHolder,
                    onOpenVideo = onOpenReferenceVideo,
                    rendererEnabled = rendererEnabled,
                    onEnableRenderer = onEnableRenderer,
                    onEditEventPosition = ::openPositionTarget,
                    searchOpen = searchOpen,
                    onCloseSearch = { searchOpen = false; viewModel.setQuery("") },
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("pager-workspace"),
                )
            } else if (workspaceMode == WorkspacePresentationMode.SPATIAL_EXPERIMENTAL) {
                SpatialWorkspace(
                    state = state,
                    viewModel = viewModel,
                    issues = issues,
                    activeTool = fixedTool,
                    instance = workspaceState.activeForTool(fixedTool.name)
                        ?: WorkspaceToolInstance(
                            id = WorkspaceState.primaryInstanceId(fixedTool.name),
                            toolKey = fixedTool.name,
                            binding = fixedTool.descriptor.defaultBinding,
                        ),
                    onActiveTool = { tool ->
                        fixedToolName = tool.name
                        workspaceState = workspaceState
                            .openPrimary(tool.name, tool.descriptor.defaultBinding)
                            .activate(WorkspaceState.primaryInstanceId(tool.name))
                    },
                    expandedEventId = expandedEventId,
                    onExpandedChange = { expandedEventId = it },
                    onImportFont = onImportFont,
                    onSaveMkv = onSaveMkv,
                    eventEditorStateHolder = eventEditorStateHolder,
                    onOpenVideo = onOpenReferenceVideo,
                    rendererEnabled = rendererEnabled,
                    onEnableRenderer = onEnableRenderer,
                    onEditEventPosition = ::openPositionTarget,
                    searchOpen = searchOpen,
                    onCloseSearch = { searchOpen = false; viewModel.setQuery("") },
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("spatial-workspace"),
                )
            } else if (workspaceMode == WorkspacePresentationMode.TOOL_INSTANCES_EXPERIMENTAL) {
                ToolInstanceWorkspace(
                    state = state,
                    viewModel = viewModel,
                    issues = issues,
                    workspaceState = workspaceState,
                    onWorkspaceStateChange = { workspaceState = it },
                    surfaceController = surfaceController,
                    expandedEventId = expandedEventId,
                    onExpandedChange = { expandedEventId = it },
                    onImportFont = onImportFont,
                    onSaveMkv = onSaveMkv,
                    eventEditorStateHolder = eventEditorStateHolder,
                    onOpenVideo = onOpenReferenceVideo,
                    rendererEnabled = rendererEnabled,
                    onEnableRenderer = onEnableRenderer,
                    onOpenTool = ::openTool,
                    searchOpen = searchOpen,
                    onCloseSearch = { searchOpen = false; viewModel.setQuery("") },
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("tool-instance-workspace"),
                )
            } else if (workspaceMode == WorkspacePresentationMode.GLASS_LAYERED_EXPERIMENTAL) {
                GlassLayeredWorkspace(
                    state = state,
                    viewModel = viewModel,
                    issues = issues,
                    workspaceState = workspaceState,
                    onWorkspaceStateChange = { workspaceState = it },
                    expandedEventId = expandedEventId,
                    onExpandedChange = { expandedEventId = it },
                    onImportFont = onImportFont,
                    onSaveMkv = onSaveMkv,
                    eventEditorStateHolder = eventEditorStateHolder,
                    onOpenVideo = onOpenReferenceVideo,
                    rendererEnabled = rendererEnabled,
                    onEnableRenderer = onEnableRenderer,
                    onOpenTool = ::openTool,
                    onEditEventPosition = ::openPositionTarget,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("glass-layered-workspace"),
                )
            } else if (workspaceMode == WorkspacePresentationMode.PRECISION_LENS_EXPERIMENTAL) {
                PrecisionLensWorkspace(
                    state = state,
                    viewModel = viewModel,
                    issues = issues,
                    onOpenVideo = onOpenReferenceVideo,
                    rendererEnabled = rendererEnabled,
                    onEnableRenderer = onEnableRenderer,
                    interactionRegistry = interactionRegistry,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("precision-lens-workspace"),
                )
            } else if (workspaceMode == WorkspacePresentationMode.SUBTITLE_OBJECT_EXPERIMENTAL) {
                SubtitleObjectWorkspace(
                    state = state,
                    viewModel = viewModel,
                    issues = issues,
                    workspaceState = workspaceState,
                    onWorkspaceStateChange = { workspaceState = it },
                    expandedEventId = expandedEventId,
                    onExpandedChange = { expandedEventId = it },
                    onImportFont = onImportFont,
                    onSaveMkv = onSaveMkv,
                    eventEditorStateHolder = eventEditorStateHolder,
                    onOpenVideo = onOpenReferenceVideo,
                    rendererEnabled = rendererEnabled,
                    onEnableRenderer = onEnableRenderer,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("subtitle-object-workspace"),
                )
            } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("canvas-workspace")) {
                val viewportWidth = maxWidth.value
                val viewportHeight = maxHeight.value
                WorkbenchPreview(
                    state, viewModel, if (workspaceState.surfacesHidden) null else positionEditEventId,
                    onOpenReferenceVideo, { openTool(WorkbenchTool.TIMELINE) },
                    rendererEnabled, onEnableRenderer,
                    onEditEventPosition = ::openPositionTarget,
                    interactionRegistry = if (workspaceState.surfacesHidden) null else interactionRegistry,
                    viewportGesturesEnabled = positionEditEventId == null || workspaceState.surfacesHidden,
                    modifier = Modifier.fillMaxSize().testTag("preview-workspace"),
                )
                workspaceState.tools.forEachIndexed { index, instance ->
                    val surfaceTool = WorkbenchTool.entries.firstOrNull { it.name == instance.toolKey }
                        ?: return@forEachIndexed
                    val groupIds = surfaceController.tabGroup(instance.id)
                    val activeTabId = surfaceController.activeTab(instance.id)
                    val visible = !workspaceState.surfacesHidden && activeTabId == instance.id
                    val tabTitles = groupIds.mapNotNull { groupId ->
                        workspaceState.tools.firstOrNull { it.id == groupId }?.let { grouped ->
                            WorkbenchTool.entries.firstOrNull { it.name == grouped.toolKey }?.let { groupedTool ->
                                groupId to groupedTool.title
                            }
                        }
                    }
                    val eventBound = surfaceTool.descriptor.eventBindable
                    val bindingResolution = instance.binding.resolve(
                        focusedEventId = state.focusedEventId,
                        selectedEventIds = state.selectedEventIds,
                        existingEventIds = existingEventIds,
                    )
                    val bindingLabel = if (eventBound) {
                        when (val binding = instance.binding) {
                            WorkspaceBinding.FollowFocus ->
                                state.focusedEventId?.let { "跟随 #$it" } ?: "跟随焦点"
                            WorkspaceBinding.FollowSelection -> "跟随选择"
                            is WorkspaceBinding.PinnedEvent -> when (bindingResolution) {
                                is WorkspaceBindingResolution.UnresolvedPinnedEvent ->
                                    "固定 #${binding.eventId} · 已失效"
                                else -> "固定 #${binding.eventId}"
                            }
                        }
                    } else {
                        null
                    }
                    val isPrimary = instance.id == WorkspaceState.primaryInstanceId(surfaceTool.name)
                    val tagId = if (isPrimary) surfaceTool.name else instance.id.replace(':', '-')

                    FloatingWorkbenchSurface(
                        id = instance.id,
                        testTagId = tagId,
                        title = surfaceTool.title,
                        bindingLabel = bindingLabel,
                        bindingPinned = instance.binding is WorkspaceBinding.PinnedEvent,
                        visible = visible,
                        controller = surfaceController,
                        initialOffset = Offset.Zero,
                        initialGeometry = when (surfaceTool) {
                            WorkbenchTool.SUBTITLES -> SurfaceGeometry(
                                x = (viewportWidth - 326f).coerceAtLeast(0f), y = 12f,
                                width = 310f, height = (viewportHeight - 24f).coerceIn(160f, 620f),
                            )
                            WorkbenchTool.CAPABILITIES -> SurfaceGeometry(12f, 12f, 320f,
                                (viewportHeight - 24f).coerceIn(160f, 620f))
                            else -> surfaceTool.initialGeometry(16f + (index % 3) * 24f, 16f + (index % 4) * 20f)
                        },
                        onActivate = {
                            workspaceState = workspaceState.activate(instance.id)
                        },
                        onToggleBinding = if (eventBound) {
                            {
                                val next = when (instance.binding) {
                                    is WorkspaceBinding.PinnedEvent -> WorkspaceBinding.FollowFocus
                                    else -> state.focusedEventId
                                        ?.let { WorkspaceBinding.PinnedEvent(it) }
                                        ?: instance.binding
                                }
                                workspaceState = workspaceState
                                    .updateBinding(instance.id, next)
                                    .activate(instance.id)
                            }
                        } else {
                            null
                        },
                        tabTitles = tabTitles,
                        onSelectTab = { tabId ->
                            surfaceController.activateTab(tabId)
                            workspaceState = workspaceState.activate(tabId)
                        },
                        onDuplicate = if (surfaceTool.descriptor.canDuplicate) {
                            {
                                workspaceState.newSibling(instance.id)?.let { sibling ->
                                    workspaceState = workspaceState
                                        .addInstance(sibling)
                                        .activate(sibling.id)
                                        .withSurfacesHidden(false)
                                    surfaceController.bringToFront(sibling.id)
                                }
                            }
                        } else {
                            null
                        },
                        onClose = {
                            if (surfaceTool == WorkbenchTool.TEXT) expandedEventId = null
                            val survivingTabId = surfaceController.remove(instance.id)
                            workspaceState = workspaceState.closeInstance(instance.id).let { closed ->
                                val activated = survivingTabId
                                    ?.takeIf { survivor -> closed.tools.any { it.id == survivor } }
                                    ?.let(closed::activate)
                                    ?: closed
                                if (activated.tools.isEmpty()) activated.withSurfacesHidden(true) else activated
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        FloatingToolContent(
                            instance = instance,
                            tool = surfaceTool,
                            state = state,
                            viewModel = viewModel,
                            issues = issues,
                            expandedEventId = expandedEventId,
                            onExpandedChange = { expandedEventId = it },
                            onImportFont = onImportFont,
                            onSaveMkv = onSaveMkv,
                        eventEditorStateHolder = eventEditorStateHolder,
                        onOpenTool = { next ->
                            workspaceState = workspaceState.closeInstance(WorkspaceState.primaryInstanceId(WorkbenchTool.CAPABILITIES.name))
                            openTool(next)
                        },
                        onCloseText = {
                            expandedEventId = null
                            workspaceState = workspaceState.closeInstance(WorkspaceState.primaryInstanceId(WorkbenchTool.TEXT.name))
                        },
                        searchOpen = searchOpen,
                        onCloseSearch = { searchOpen = false; viewModel.setQuery("") },
                    )
                    }
                }

                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 44.dp).zIndex(10000f),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { openTool(WorkbenchTool.CAPABILITIES) }, modifier = Modifier.testTag("workspace-tools")) {
                            Icon(Icons.Filled.Apps, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("工具")
                        }
                        if (workspaceState.tools.isNotEmpty()) TooltipIconButton(
                            if (workspaceState.surfacesHidden) "呼回全部浮层" else "隐藏全部浮层", ::toggleAllSurfaces,
                        ) { Icon(if (workspaceState.surfacesHidden) Icons.Filled.Layers else Icons.Filled.LayersClear, null) }
                    }
                }
            }
        }

        WindowInteractionOverlay(
            registry = interactionRegistry,
            visible = when (workspaceMode) {
                WorkspacePresentationMode.FIXED -> fixedTool == WorkbenchTool.POSITION
                WorkspacePresentationMode.CANVAS_EXPERIMENTAL -> !workspaceState.surfacesHidden
                WorkspacePresentationMode.PAGER_EXPERIMENTAL -> false
                WorkspacePresentationMode.SPATIAL_EXPERIMENTAL -> false
                WorkspacePresentationMode.PRECISION_LENS_EXPERIMENTAL -> false
                WorkspacePresentationMode.TOOL_INSTANCES_EXPERIMENTAL -> false
                WorkspacePresentationMode.GLASS_LAYERED_EXPERIMENTAL -> false
                WorkspacePresentationMode.SUBTITLE_OBJECT_EXPERIMENTAL -> false
            },
            modifier = Modifier.fillMaxSize().testTag("interaction-overlay"),
        )
        PrecisionInteractionOverlay(
            registry = interactionRegistry,
            visible = workspaceMode == WorkspacePresentationMode.PRECISION_LENS_EXPERIMENTAL,
            modifier = Modifier.fillMaxSize(),
        )
        }
    }
}
}

@Composable
private fun GlassLayeredWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    issues: List<AssQcIssue>,
    workspaceState: WorkspaceState,
    onWorkspaceStateChange: (WorkspaceState) -> Unit,
    expandedEventId: Long?,
    onExpandedChange: (Long?) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    eventEditorStateHolder: SaveableStateHolder,
    onOpenVideo: () -> Unit,
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
    onOpenTool: (WorkbenchTool) -> Unit,
    onEditEventPosition: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemBlurAvailable = rememberSystemBackdropBlurEnabled()
    var alpha by rememberSaveable { mutableFloatStateOf(0.72f) }
    var blurDp by rememberSaveable { mutableFloatStateOf(24f) }
    var performanceModeName by rememberSaveable { mutableStateOf(GlassPerformanceMode.AUTO.name) }
    val performanceMode = GlassPerformanceMode.valueOf(performanceModeName)
    val materials = remember { mutableStateMapOf<String, GlassMaterial>() }
    val offsets = remember { mutableStateMapOf<String, IntOffset>() }

    val existingEventIds = remember(state.document.events) {
        state.document.events.asSequence().map { it.id }.toSet()
    }

    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        WorkbenchPreview(
            state = state,
            viewModel = viewModel,
            positionEditEventId = null,
            onOpenVideo = onOpenVideo,
            onOpenTimeline = { onOpenTool(WorkbenchTool.TIMELINE) },
            rendererEnabled = rendererEnabled,
            onEnableRenderer = onEnableRenderer,
            onEditEventPosition = onEditEventPosition,
            viewportGesturesEnabled = true,
            modifier = Modifier.fillMaxSize().testTag("glass-preview"),
        )

        workspaceState.tools.forEachIndexed { index, instance ->
            val tool = WorkbenchTool.entries.firstOrNull { it.name == instance.toolKey }
                ?: return@forEachIndexed
            val active = workspaceState.activeInstanceId == instance.id
            val material = materials[instance.id] ?: GlassMaterial.FROSTED
            val plan = resolveGlassRenderPlan(
                material = material,
                requestedAlpha = alpha,
                requestedBlurDp = blurDp,
                performanceMode = performanceMode,
                systemBackdropBlurAvailable = systemBlurAvailable,
                activeLayer = active,
                deemphasized = workspaceState.activeInstanceId != null && !active,
            )
            val bindingResolution = instance.binding.resolve(
                focusedEventId = state.focusedEventId,
                selectedEventIds = state.selectedEventIds,
                existingEventIds = existingEventIds,
            )
            val bindingLabel = when (val binding = instance.binding) {
                WorkspaceBinding.FollowFocus ->
                    state.focusedEventId?.let { "跟随 #$it" } ?: "跟随焦点"
                WorkspaceBinding.FollowSelection ->
                    if (state.selectedEventIds.isEmpty()) "跟随选择" else "选择 ${state.selectedEventIds.size} 条"
                is WorkspaceBinding.PinnedEvent ->
                    if (bindingResolution is WorkspaceBindingResolution.UnresolvedPinnedEvent) {
                        "固定 #${binding.eventId} · 已失效"
                    } else {
                        "固定 #${binding.eventId}"
                    }
            }
            val offset = offsets.getOrPut(instance.id) {
                IntOffset(
                    x = 18 + (index % 3) * 54,
                    y = 96 + (index % 4) * 62,
                )
            }

            GlassToolWindow(
                id = instance.id,
                title = tool.title,
                subtitle = "${material.label} · $bindingLabel" +
                    if (plan.degraded) " · 模糊降级" else "",
                visible = !workspaceState.surfacesHidden &&
                    instance.presence != WorkspaceToolPresence.HIDDEN &&
                    instance.presence != WorkspaceToolPresence.BOOKMARKED,
                active = active,
                width = if (tool == WorkbenchTool.TIMELINE) 420.dp else 360.dp,
                height = if (tool == WorkbenchTool.TIMELINE) 420.dp else 500.dp,
                offset = offset,
                renderPlan = plan,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                onOffsetChange = { offsets[instance.id] = it },
                onActivate = {
                    onWorkspaceStateChange(workspaceState.activate(instance.id))
                },
                onCycleMaterial = {
                    materials[instance.id] = material.next()
                },
                onClose = {
                    materials.remove(instance.id)
                    offsets.remove(instance.id)
                    onWorkspaceStateChange(workspaceState.closeInstance(instance.id))
                },
            ) {
                FloatingToolContent(
                    instance = instance,
                    tool = tool,
                    state = state,
                    viewModel = viewModel,
                    issues = issues,
                    expandedEventId = expandedEventId,
                    onExpandedChange = onExpandedChange,
                    onImportFont = onImportFont,
                    onSaveMkv = onSaveMkv,
                    eventEditorStateHolder = eventEditorStateHolder,
                    onOpenTool = onOpenTool,
                    onCloseText = { onExpandedChange(null) },
                    searchOpen = false,
                    onCloseSearch = {},
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(8.dp)
                .testTag("glass-control-deck"),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 5.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = { onOpenTool(WorkbenchTool.CAPABILITIES) },
                        label = { Text("工具") },
                        leadingIcon = { Icon(Icons.Filled.Apps, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("glass-open-tools"),
                    )
                    listOf(
                        WorkbenchTool.STYLE,
                        WorkbenchTool.POSITION,
                        WorkbenchTool.TIMELINE,
                        WorkbenchTool.SUBTITLES,
                    ).forEach { tool ->
                        AssistChip(
                            onClick = { onOpenTool(tool) },
                            label = { Text(tool.title) },
                        )
                    }
                    AssistChip(
                        onClick = {
                            onWorkspaceStateChange(
                                workspaceState.withSurfacesHidden(!workspaceState.surfacesHidden)
                            )
                        },
                        label = { Text(if (workspaceState.surfacesHidden) "显示各层" else "隐藏各层") },
                        leadingIcon = {
                            Icon(
                                if (workspaceState.surfacesHidden) Icons.Filled.Layers
                                else Icons.Filled.LayersClear,
                                null,
                                Modifier.size(18.dp),
                            )
                        },
                    )
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassPerformanceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = performanceMode == mode,
                            onClick = { performanceModeName = mode.name },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("glass-performance-" + mode.name),
                        )
                    }
                    Text(
                        if (systemBlurAvailable) "系统背景模糊：可用" else "系统背景模糊：不可用，磨砂将降级",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (systemBlurAvailable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("透明度", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(54.dp))
                    Slider(
                        value = alpha,
                        onValueChange = { alpha = it },
                        valueRange = 0.25f..0.95f,
                        modifier = Modifier.weight(1f).testTag("glass-alpha"),
                    )
                    Text("${(alpha * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("模糊", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(54.dp))
                    Slider(
                        value = blurDp,
                        onValueChange = { blurDp = it },
                        valueRange = 0f..42f,
                        enabled = performanceMode != GlassPerformanceMode.LOW_COST,
                        modifier = Modifier.weight(1f).testTag("glass-blur"),
                    )
                    Text("${blurDp.roundToInt()}dp", style = MaterialTheme.typography.labelSmall)
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .testTag("glass-layer-overview"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    workspaceState.tools.forEach { instance ->
                        val tool = WorkbenchTool.entries.firstOrNull { it.name == instance.toolKey }
                            ?: return@forEach
                        FilterChip(
                            selected = workspaceState.activeInstanceId == instance.id,
                            onClick = {
                                onWorkspaceStateChange(workspaceState.activate(instance.id))
                            },
                            label = {
                                Text(
                                    tool.title + " · " +
                                        (materials[instance.id] ?: GlassMaterial.FROSTED).label
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolInstanceWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    issues: List<AssQcIssue>,
    workspaceState: WorkspaceState,
    onWorkspaceStateChange: (WorkspaceState) -> Unit,
    surfaceController: WorkbenchSurfaceController,
    expandedEventId: Long?,
    onExpandedChange: (Long?) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    eventEditorStateHolder: SaveableStateHolder,
    onOpenVideo: () -> Unit,
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
    onOpenTool: (WorkbenchTool) -> Unit,
    searchOpen: Boolean,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val existingEventIds = remember(state.document.events) {
        state.document.events.asSequence().map { it.id }.toSet()
    }
    val temporaryIds = workspaceState.tools
        .filter { it.presence == WorkspaceToolPresence.TEMPORARY }
        .map { it.id }
        .toSet()

    fun hideTemporaryTools() {
        var next = workspaceState
        temporaryIds.forEach { id ->
            next = next.updatePresence(id, WorkspaceToolPresence.HIDDEN)
        }
        onWorkspaceStateChange(next)
    }

    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(temporaryIds) {
                detectTapGestures(onTap = { hideTemporaryTools() })
            }
    ) {
        WorkbenchPreview(
            state = state,
            viewModel = viewModel,
            positionEditEventId = null,
            onOpenVideo = onOpenVideo,
            onOpenTimeline = { onOpenTool(WorkbenchTool.TIMELINE) },
            rendererEnabled = rendererEnabled,
            onEnableRenderer = onEnableRenderer,
            viewportGesturesEnabled = true,
            modifier = Modifier.fillMaxSize().testTag("tool-instance-preview"),
        )

        workspaceState.tools.forEachIndexed { index, instance ->
            val tool = WorkbenchTool.entries.firstOrNull { it.name == instance.toolKey }
                ?: return@forEachIndexed
            val groupIds = surfaceController.tabGroup(instance.id)
            val activeTabId = surfaceController.activeTab(instance.id)
            val visible = instance.presence != WorkspaceToolPresence.HIDDEN &&
                instance.presence != WorkspaceToolPresence.BOOKMARKED &&
                activeTabId == instance.id
            val bindingResolution = instance.binding.resolve(
                focusedEventId = state.focusedEventId,
                selectedEventIds = state.selectedEventIds,
                existingEventIds = existingEventIds,
            )
            val unresolved = bindingResolution is WorkspaceBindingResolution.UnresolvedPinnedEvent
            val bindingLabel = when (val binding = instance.binding) {
                WorkspaceBinding.FollowFocus ->
                    state.focusedEventId?.let { "跟随焦点 · #$it" } ?: "跟随焦点"
                WorkspaceBinding.FollowSelection ->
                    if (state.selectedEventIds.isEmpty()) "跟随选择 · 空"
                    else "跟随选择 · ${state.selectedEventIds.size} 条"
                is WorkspaceBinding.PinnedEvent ->
                    if (unresolved) "固定 #${binding.eventId} · 已失效"
                    else "固定 #${binding.eventId}"
            }
            val tabTitles = groupIds.mapNotNull { id ->
                workspaceState.tools.firstOrNull { it.id == id }?.let { grouped ->
                    WorkbenchTool.entries.firstOrNull { it.name == grouped.toolKey }
                        ?.let { groupedTool -> id to groupedTool.title }
                }
            }
            val tagId = instance.id.replace(':', '-')

            FloatingWorkbenchSurface(
                id = instance.id,
                testTagId = tagId,
                title = tool.title + if (instance.id.endsWith(":primary")) "" else " · " + instance.id.substringAfterLast(':'),
                bindingLabel = bindingLabel,
                bindingPinned = instance.binding is WorkspaceBinding.PinnedEvent,
                visible = visible,
                controller = surfaceController,
                initialOffset = Offset.Zero,
                initialGeometry = tool.initialGeometry(
                    18f + (index % 4) * 34f,
                    24f + (index % 5) * 30f,
                ),
                onActivate = {
                    onWorkspaceStateChange(workspaceState.activate(instance.id))
                },
                onToggleBinding = if (tool.descriptor.eventBindable) {
                    {
                        val nextBinding = when (instance.binding) {
                            is WorkspaceBinding.PinnedEvent -> WorkspaceBinding.FollowFocus
                            else -> state.focusedEventId
                                ?.let(WorkspaceBinding::PinnedEvent)
                                ?: WorkspaceBinding.FollowFocus
                        }
                        onWorkspaceStateChange(
                            workspaceState.updateBinding(instance.id, nextBinding).activate(instance.id)
                        )
                    }
                } else null,
                onDuplicate = if (tool.descriptor.canDuplicate) {
                    {
                        workspaceState.newSibling(instance.id)?.let { sibling ->
                            val copy = sibling.copy(
                                presence = WorkspaceToolPresence.RESIDENT,
                                contentDensity = instance.contentDensity,
                            )
                            onWorkspaceStateChange(
                                workspaceState.addInstance(copy).activate(copy.id)
                            )
                            surfaceController.bringToFront(copy.id, tool.initialGeometry(52f, 58f))
                        }
                    }
                } else null,
                onDuplicateFollowFocus = if (tool.descriptor.canDuplicate) {
                    {
                        workspaceState.newSibling(instance.id)?.let { sibling ->
                            val copy = sibling.copy(
                                binding = WorkspaceBinding.FollowFocus,
                                presence = WorkspaceToolPresence.RESIDENT,
                                contentDensity = instance.contentDensity,
                            )
                            onWorkspaceStateChange(
                                workspaceState.addInstance(copy).activate(copy.id)
                            )
                            surfaceController.bringToFront(copy.id, tool.initialGeometry(72f, 74f))
                        }
                    }
                } else null,
                presenceLabel = instance.presence.label,
                contentDensityLabel = instance.contentDensity.label,
                onToggleResident = {
                    val nextPresence = if (instance.presence == WorkspaceToolPresence.RESIDENT) {
                        WorkspaceToolPresence.TEMPORARY
                    } else {
                        WorkspaceToolPresence.RESIDENT
                    }
                    onWorkspaceStateChange(
                        workspaceState.updatePresence(instance.id, nextPresence)
                    )
                },
                onBookmark = {
                    onWorkspaceStateChange(
                        workspaceState.updatePresence(instance.id, WorkspaceToolPresence.BOOKMARKED)
                    )
                },
                onCycleContentDensity = {
                    onWorkspaceStateChange(workspaceState.cycleContentDensity(instance.id))
                },
                onRelink = if (unresolved && state.focusedEventId != null) {
                    {
                        onWorkspaceStateChange(
                            workspaceState
                                .updateBinding(
                                    instance.id,
                                    WorkspaceBinding.PinnedEvent(state.focusedEventId),
                                )
                                .activate(instance.id)
                        )
                    }
                } else null,
                tabTitles = tabTitles,
                onSelectTab = { tabId ->
                    surfaceController.activateTab(tabId)
                    onWorkspaceStateChange(workspaceState.activate(tabId))
                },
                onClose = {
                    val surviving = surfaceController.remove(instance.id)
                    var next = workspaceState.closeInstance(instance.id)
                    if (surviving != null) next = next.activate(surviving)
                    onWorkspaceStateChange(next)
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                val baseDensity = LocalDensity.current
                val factor = when (instance.contentDensity) {
                    ToolContentDensity.COMPACT -> 0.86f
                    ToolContentDensity.STANDARD -> 1f
                    ToolContentDensity.PRECISION -> 1.12f
                }
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = baseDensity.density * factor,
                        fontScale = baseDensity.fontScale,
                    )
                ) {
                    FloatingToolContent(
                        instance = instance,
                        tool = tool,
                        state = state,
                        viewModel = viewModel,
                        issues = issues,
                        expandedEventId = expandedEventId,
                        onExpandedChange = onExpandedChange,
                        onImportFont = onImportFont,
                        onSaveMkv = onSaveMkv,
                        eventEditorStateHolder = eventEditorStateHolder,
                        onOpenTool = onOpenTool,
                        onCloseText = { onExpandedChange(null) },
                        searchOpen = searchOpen,
                        onCloseSearch = onCloseSearch,
                    )
                }
            }
        }

        val bookmarked = workspaceState.tools.filter {
            it.presence == WorkspaceToolPresence.BOOKMARKED
        }
        if (bookmarked.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .testTag("tool-bookmark-rail"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                bookmarked.forEach { instance ->
                    val tool = WorkbenchTool.entries.firstOrNull { it.name == instance.toolKey }
                        ?: return@forEach
                    AssistChip(
                        onClick = {
                            val next = workspaceState
                                .updatePresence(instance.id, WorkspaceToolPresence.RESIDENT)
                                .activate(instance.id)
                            onWorkspaceStateChange(next)
                            surfaceController.bringToFront(instance.id, tool.initialGeometry(44f, 44f))
                        },
                        label = { Text(tool.title) },
                        leadingIcon = { Icon(Icons.Filled.Bookmark, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tool-bookmark-" + instance.id.replace(':', '-')),
                    )
                }
            }
        }

        val hidden = workspaceState.tools.filter {
            it.presence == WorkspaceToolPresence.HIDDEN
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 5.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { onOpenTool(WorkbenchTool.CAPABILITIES) },
                    label = { Text("工具") },
                    leadingIcon = { Icon(Icons.Filled.Apps, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tool-instance-directory"),
                )
                if (temporaryIds.isNotEmpty()) {
                    AssistChip(
                        onClick = ::hideTemporaryTools,
                        label = { Text("收回临时") },
                        leadingIcon = { Icon(Icons.Filled.KeyboardHide, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tool-hide-temporary"),
                    )
                }
                hidden.forEach { instance ->
                    val tool = WorkbenchTool.entries.firstOrNull { it.name == instance.toolKey }
                        ?: return@forEach
                    AssistChip(
                        onClick = {
                            val next = workspaceState
                                .updatePresence(instance.id, WorkspaceToolPresence.TEMPORARY)
                                .hideOtherTemporary(instance.id)
                                .activate(instance.id)
                            onWorkspaceStateChange(next)
                            surfaceController.bringToFront(instance.id, tool.initialGeometry(34f, 42f))
                        },
                        label = { Text(tool.title) },
                        leadingIcon = { Icon(Icons.Filled.Visibility, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tool-hidden-" + instance.id.replace(':', '-')),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrecisionLensWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    issues: List<AssQcIssue>,
    onOpenVideo: () -> Unit,
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
    interactionRegistry: InteractionOverlayRegistry,
    modifier: Modifier = Modifier,
) {
    val focusedEvent = state.focusedEventId?.let { id ->
        state.document.events.firstOrNull { it.id == id }
    }
    val geometry = focusedEvent?.let { AssGeometrySemantic.inspect(it.text) }
    val unsupportedReason = focusedEvent?.let {
        when {
            geometry?.positionMode == AssPositionMode.CONFLICT ->
                "当前 Event 同时包含 pos 与 move，直接位置操控已受限；可继续使用 org / 旋转，或转到 Raw / 数值编辑。"
            geometry?.positionMode == AssPositionMode.MOVE && geometry.move == null ->
                "move 语义无法可靠解析；保留 Raw 编辑入口。"
            else -> null
        }
    }

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Surface(
            Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("精密几何", style = MaterialTheme.typography.titleSmall)
                state.document.events.take(16).forEach { event ->
                    FilterChip(
                        selected = event.id == state.focusedEventId,
                        onClick = { viewModel.focusEvent(event.id, seek = true) },
                        label = { Text("#${event.id}") },
                    )
                }
                if (state.document.events.size > 16) {
                    Text("+${state.document.events.size - 16}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            WorkbenchPreview(
                state = state,
                viewModel = viewModel,
                positionEditEventId = state.focusedEventId,
                onOpenVideo = onOpenVideo,
                onOpenTimeline = {},
                rendererEnabled = rendererEnabled,
                onEnableRenderer = onEnableRenderer,
                interactionRegistry = interactionRegistry,
                viewportGesturesEnabled = false,
                modifier = Modifier.fillMaxSize().testTag("precision-preview"),
            )

            if (focusedEvent == null) {
                Surface(
                    modifier = Modifier.align(Alignment.Center)
                        .padding(18.dp)
                        .testTag("precision-empty-state"),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("先选择一条字幕", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "选择后直接出现位置、旋转、缩放、倾斜与 org 操纵杆。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            unsupportedReason?.let { message ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(10.dp)
                        .fillMaxWidth(0.92f)
                        .testTag("precision-unsupported-reason"),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Info, null)
                        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        focusedEvent?.let { event ->
            Surface(
                Modifier.fillMaxWidth().testTag("precision-object-strip"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("#${event.id} · ${event.style} · L${event.layer}", style = MaterialTheme.typography.labelLarge)
                    Text(event.start.toAss() + " — " + event.end.toAss(), style = MaterialTheme.typography.labelSmall)
                    AssistChip(
                        onClick = { viewModel.setGeometryScaleLocked(!state.geometryScaleLocked) },
                        label = { Text(if (state.geometryScaleLocked) "XY 缩放锁定" else "XY 独立缩放") },
                    )
                    AssistChip(
                        onClick = { viewModel.clearTransientPreview() },
                        label = { Text("清临时预览") },
                    )
                    AssistChip(
                        onClick = { viewModel.focusEvent(event.id, seek = false) },
                        label = { Text("保持对象焦点") },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleObjectWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    issues: List<AssQcIssue>,
    workspaceState: WorkspaceState,
    onWorkspaceStateChange: (WorkspaceState) -> Unit,
    expandedEventId: Long?,
    onExpandedChange: (Long?) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    eventEditorStateHolder: SaveableStateHolder,
    onOpenVideo: () -> Unit,
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsState()
    var frozenPick by remember { mutableStateOf<PreviewObjectPick?>(null) }
    var selectedObjectId by rememberSaveable { mutableStateOf<Long?>(state.focusedEventId) }
    var referenceObjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var activeToolName by rememberSaveable { mutableStateOf<String?>(null) }
    var relationOpen by rememberSaveable { mutableStateOf(false) }
    var candidatePickerOpen by remember { mutableStateOf(false) }

    val selectedEvent = selectedObjectId?.let { id ->
        state.document.events.firstOrNull { it.id == id }
    }
    val selectedCandidate = frozenPick?.candidates?.firstOrNull { it.eventId == selectedObjectId }

    fun selectObject(id: Long, pick: PreviewObjectPick? = frozenPick) {
        if (state.document.events.none { it.id == id }) return
        selectedObjectId = id
        frozenPick = pick
        candidatePickerOpen = false
        viewModel.focusEvent(id, seek = false)
    }

    fun acceptPick(pick: PreviewObjectPick) {
        frozenPick = pick
        val reliable = pick.candidates.filter { it.confidence != PreviewTargetConfidence.UNRESOLVED }
        if (pick.candidates.size == 1 && reliable.size == 1) {
            selectObject(pick.candidates.single().eventId, pick)
        } else {
            candidatePickerOpen = true
        }
    }

    fun openObjectTool(tool: WorkbenchTool) {
        val id = selectedObjectId ?: return
        viewModel.focusEvent(id, seek = false)
        val next = if (tool.descriptor.supportsPinnedEvent) {
            workspaceState.openPinnedEvent(tool.name, id)
        } else {
            workspaceState.openPrimary(tool.name, tool.descriptor.defaultBinding)
        }
        onWorkspaceStateChange(next)
        activeToolName = tool.name
    }

    val activeTool = activeToolName?.let { key ->
        WorkbenchTool.entries.firstOrNull { it.name == key }
    }
    val activeInstance = activeTool?.let { tool ->
        workspaceState.activeForTool(tool.name)
            ?: WorkspaceToolInstance(
                id = WorkspaceState.primaryInstanceId(tool.name),
                toolKey = tool.name,
                binding = if (tool.descriptor.supportsPinnedEvent && selectedObjectId != null) {
                    WorkspaceBinding.PinnedEvent(selectedObjectId!!)
                } else {
                    tool.descriptor.defaultBinding
                },
            )
    }

    BoxWithConstraints(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        if (state.project.videoUri != null) {
            WorkbenchPreview(
                state = state,
                viewModel = viewModel,
                positionEditEventId = null,
                onOpenVideo = onOpenVideo,
                onOpenTimeline = { openObjectTool(WorkbenchTool.TIMELINE) },
                rendererEnabled = rendererEnabled,
                onEnableRenderer = onEnableRenderer,
                viewportGesturesEnabled = true,
                onObjectLongPress = ::acceptPick,
                modifier = Modifier.fillMaxSize().testTag("object-preview"),
            )
        } else {
            ScriptObjectCanvas(
                state = state,
                positionMs = frozenPick?.frozenPositionMs ?: playbackPositionMs,
                selectedEventId = selectedObjectId,
                onObjectPick = ::acceptPick,
                onOpenVideo = onOpenVideo,
                modifier = Modifier.fillMaxSize().testTag("object-script-canvas"),
            )
        }

        selectedEvent?.let { event ->
            val fractionX = selectedCandidate?.anchor?.let {
                (it.x / state.document.playResX.coerceAtLeast(1)).toFloat()
            } ?: frozenPick?.viewportFractionX ?: 0.5f
            val fractionY = selectedCandidate?.anchor?.let {
                (it.y / state.document.playResY.coerceAtLeast(1)).toFloat()
            } ?: frozenPick?.viewportFractionY ?: 0.72f
            val hudX = (maxWidth * fractionX.coerceIn(0.08f, 0.80f))
            val hudY = (maxHeight * fractionY.coerceIn(0.10f, 0.72f))

            Surface(
                modifier = Modifier
                    .offset(x = hudX, y = hudY)
                    .widthIn(max = 320.dp)
                    .testTag("object-capability-hud"),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                tonalElevation = 5.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            ) {
                Column(
                    Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "#${event.id} · ${event.style} · L${event.layer}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                "${event.start.toAss()} — ${event.end.toAss()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.selectedEventIds.isNotEmpty()) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("${state.selectedEventIds.size} 选中") },
                            )
                        }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ObjectCapabilityButton("文字", Icons.Filled.TextFields) { openObjectTool(WorkbenchTool.TEXT) }
                        ObjectCapabilityButton("字体", Icons.Filled.FontDownload) { openObjectTool(WorkbenchTool.FONTS) }
                        ObjectCapabilityButton("排版", Icons.Filled.FormatColorText) { openObjectTool(WorkbenchTool.STYLE) }
                        ObjectCapabilityButton("位置", Icons.Filled.OpenWith) { openObjectTool(WorkbenchTool.POSITION) }
                        ObjectCapabilityButton("旋转", Icons.Filled.RotateRight) { openObjectTool(WorkbenchTool.POSITION) }
                        ObjectCapabilityButton("时间", Icons.Filled.Timeline) { openObjectTool(WorkbenchTool.TIMELINE) }
                        ObjectCapabilityButton("效果", Icons.Filled.AutoAwesome) { openObjectTool(WorkbenchTool.EFFECTS) }
                        ObjectCapabilityButton("Raw", Icons.Filled.Code) { openObjectTool(WorkbenchTool.TEXT) }
                        ObjectCapabilityButton("检查", Icons.Filled.FactCheck) { openObjectTool(WorkbenchTool.QC) }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = event.id in state.selectedEventIds,
                            onClick = { viewModel.toggleSelected(event.id) },
                            label = { Text("多选") },
                            leadingIcon = { Icon(Icons.Filled.SelectAll, null, Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("object-multiselect"),
                        )
                        FilterChip(
                            selected = referenceObjectId == event.id,
                            onClick = {
                                referenceObjectId = if (referenceObjectId == event.id) null else event.id
                            },
                            label = { Text("固定参考") },
                            leadingIcon = { Icon(Icons.Filled.PushPin, null, Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("object-reference-pin"),
                        )
                        AssistChip(
                            onClick = { relationOpen = !relationOpen },
                            label = { Text("关系") },
                            leadingIcon = { Icon(Icons.Filled.AccountTree, null, Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("object-relations"),
                        )
                    }
                }
            }
        }

        if (state.selectedEventIds.size > 1) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .testTag("object-group-controls"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("${state.selectedEventIds.size} 个对象", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { viewModel.nudgeSelectedObjects(-8.0, 0.0) }) {
                        Icon(Icons.Filled.ArrowBack, "选中对象左移")
                    }
                    IconButton(onClick = { viewModel.nudgeSelectedObjects(8.0, 0.0) }) {
                        Icon(Icons.Filled.ArrowForward, "选中对象右移")
                    }
                    IconButton(onClick = { viewModel.nudgeSelectedObjects(0.0, -8.0) }) {
                        Icon(Icons.Filled.ArrowUpward, "选中对象上移")
                    }
                    IconButton(onClick = { viewModel.nudgeSelectedObjects(0.0, 8.0) }) {
                        Icon(Icons.Filled.ArrowDownward, "选中对象下移")
                    }
                    TextButton(onClick = { openObjectTool(WorkbenchTool.BATCH) }) { Text("统一参数") }
                    TextButton(onClick = viewModel::clearSelection) { Text("清除") }
                }
            }
        }

        referenceObjectId?.let { refId ->
            val ref = state.document.events.firstOrNull { it.id == refId }
            if (ref != null && ref.id != selectedObjectId) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(8.dp)
                        .fillMaxWidth(0.62f)
                        .testTag("object-reference-card"),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PushPin, null)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text("参考对象 #${ref.id} · ${ref.style}", style = MaterialTheme.typography.labelLarge)
                            Text(
                                AssInlineSyntax.visibleText(ref.text).take(56),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                        TextButton(onClick = { selectObject(ref.id) }) { Text("转到") }
                    }
                }
            }
        }

        frozenPick?.let { pick ->
            Surface(
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(8.dp)
                    .testTag("object-frozen-time"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    "命中冻结：${SubTime(pick.frozenPositionMs).toAss()}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (candidatePickerOpen) {
            ObjectCandidatePicker(
                pick = frozenPick,
                document = state.document,
                onSelect = { selectObject(it, frozenPick) },
                onDismiss = { candidatePickerOpen = false },
                modifier = Modifier.align(Alignment.Center).testTag("object-candidate-picker"),
            )
        }

        if (relationOpen && selectedEvent != null) {
            ObjectRelationPanel(
                document = state.document,
                event = selectedEvent,
                onFocusEvent = { id -> selectObject(id) },
                onOpenStyle = { openObjectTool(WorkbenchTool.STYLE) },
                onDismiss = { relationOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight(0.78f)
                    .fillMaxWidth(0.78f)
                    .testTag("object-relation-panel"),
            )
        }

        if (activeTool != null && activeInstance != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.48f)
                    .testTag("object-tool-panel"),
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            activeTool.title + (selectedObjectId?.let { " · #$it" } ?: ""),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { activeToolName = null }) {
                            Icon(Icons.Filled.Close, "关闭对象工具")
                        }
                    }
                    HorizontalDivider()
                    FloatingToolContent(
                        instance = activeInstance,
                        tool = activeTool,
                        state = state,
                        viewModel = viewModel,
                        issues = issues,
                        expandedEventId = expandedEventId,
                        onExpandedChange = onExpandedChange,
                        onImportFont = onImportFont,
                        onSaveMkv = onSaveMkv,
                        eventEditorStateHolder = eventEditorStateHolder,
                        onOpenTool = ::openObjectTool,
                        onCloseText = { activeToolName = null },
                        searchOpen = false,
                        onCloseSearch = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectCapabilityButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
    )
}

@Composable
private fun ScriptObjectCanvas(
    state: EditorState,
    positionMs: Long,
    selectedEventId: Long?,
    onObjectPick: (PreviewObjectPick) -> Unit,
    onOpenVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(state.document, positionMs) {
                detectTapGestures(
                    onLongPress = { offset ->
                        val x = offset.x / size.width.coerceAtLeast(1) * state.document.playResX
                        val y = offset.y / size.height.coerceAtLeast(1) * state.document.playResY
                        val candidates = PreviewTargetResolver.candidates(
                            document = state.document,
                            positionMs = positionMs,
                            x = x.toDouble(),
                            y = y.toDouble(),
                        )
                        onObjectPick(
                            PreviewObjectPick(
                                frozenPositionMs = positionMs,
                                playX = x.toDouble(),
                                playY = y.toDouble(),
                                viewportFractionX = offset.x / size.width.coerceAtLeast(1),
                                viewportFractionY = offset.y / size.height.coerceAtLeast(1),
                                candidates = candidates,
                            )
                        )
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gridColor = Color.Gray.copy(alpha = 0.20f)
            val centerColor = Color.Gray.copy(alpha = 0.46f)
            for (i in 1 until 10) {
                val x = size.width * i / 10f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            for (i in 1 until 10) {
                val y = size.height * i / 10f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            }
            drawLine(centerColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 2f)
            drawLine(centerColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 2f)
        }

        Text(
            "脚本坐标 ${state.document.playResX} × ${state.document.playResY} · 无视频对象模式",
            modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val active = state.document.activeEvents(SubTime(positionMs))
        active.forEach { event ->
            val candidate = PreviewTargetResolver.candidates(
                document = state.document,
                positionMs = positionMs,
                x = state.document.playResX / 2.0,
                y = state.document.playResY / 2.0,
                limit = state.document.events.size.coerceAtLeast(1),
            ).firstOrNull { it.eventId == event.id }
            val anchor = candidate?.anchor ?: return@forEach
            val x = maxWidth * (anchor.x / state.document.playResX.coerceAtLeast(1)).toFloat().coerceIn(0f, 0.92f)
            val y = maxHeight * (anchor.y / state.document.playResY.coerceAtLeast(1)).toFloat().coerceIn(0f, 0.92f)
            Surface(
                modifier = Modifier.offset(x, y)
                    .combinedClickable(
                        onClick = {
                            onObjectPick(
                                PreviewObjectPick(
                                    frozenPositionMs = positionMs,
                                    playX = anchor.x,
                                    playY = anchor.y,
                                    viewportFractionX = (anchor.x / state.document.playResX.coerceAtLeast(1)).toFloat(),
                                    viewportFractionY = (anchor.y / state.document.playResY.coerceAtLeast(1)).toFloat(),
                                    candidates = listOfNotNull(candidate),
                                )
                            )
                        },
                        onLongClick = {
                            val candidates = PreviewTargetResolver.candidates(
                                state.document,
                                positionMs,
                                anchor.x,
                                anchor.y,
                            )
                            onObjectPick(
                                PreviewObjectPick(
                                    positionMs,
                                    anchor.x,
                                    anchor.y,
                                    (anchor.x / state.document.playResX.coerceAtLeast(1)).toFloat(),
                                    (anchor.y / state.document.playResY.coerceAtLeast(1)).toFloat(),
                                    candidates,
                                )
                            )
                        },
                    )
                    .testTag("script-object-${event.id}"),
                shape = RoundedCornerShape(12.dp),
                color = if (event.id == selectedEventId) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
                },
                border = BorderStroke(
                    1.dp,
                    if (event.id == selectedEventId) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    "#${event.id} " + AssInlineSyntax.visibleText(event.text).take(36),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }

        TextButton(
            onClick = onOpenVideo,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        ) {
            Icon(Icons.Filled.Movie, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("加入参考视频")
        }
    }
}

@Composable
private fun ObjectCandidatePicker(
    pick: PreviewObjectPick?,
    document: AssDocument,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val candidates = pick?.candidates.orEmpty()
    Surface(
        modifier = modifier.fillMaxWidth(0.92f),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("选择字幕对象", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (candidates.isEmpty()) {
                            "这个触点没有可靠的字形归属；显示当前时刻候选，不伪装成像素命中。"
                        } else {
                            "候选冻结于 ${pick?.frozenPositionMs?.let { SubTime(it).toAss() } ?: "—"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭对象候选") }
            }
            if (candidates.isEmpty()) {
                Text("当前时刻没有可选择 Dialogue。")
            }
            candidates.forEach { candidate ->
                val event = document.events.firstOrNull { it.id == candidate.eventId }
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onSelect(candidate.eventId) }
                        .testTag("object-candidate-${candidate.eventId}"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        when (candidate.confidence) {
                            PreviewTargetConfidence.EXACT_ANCHOR -> MaterialTheme.colorScheme.primary
                            PreviewTargetConfidence.APPROXIMATE_ANCHOR -> MaterialTheme.colorScheme.tertiary
                            PreviewTargetConfidence.UNRESOLVED -> MaterialTheme.colorScheme.error
                        }.copy(alpha = 0.55f),
                    ),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "#${candidate.eventId} · ${candidate.styleName} · Layer ${candidate.layer}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(candidate.textLabel, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        Text(
                            event?.let { "${it.start.toAss()} — ${it.end.toAss()}" } ?: "时间未知",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            when (candidate.confidence) {
                                PreviewTargetConfidence.EXACT_ANCHOR -> "明确 ASS anchor；仍不是 libass 字形边界命中"
                                PreviewTargetConfidence.APPROXIMATE_ANCHOR -> "按 Style 对齐与 Margin 推导 anchor"
                                PreviewTargetConfidence.UNRESOLVED -> "无法可靠定位，只作为同一时刻候选"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ObjectRelationPanel(
    document: AssDocument,
    event: AssEvent,
    onFocusEvent: (Long) -> Unit,
    onOpenStyle: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val references = remember(document.events, event.style) {
        document.events.filter { it.style == event.style }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 7.dp,
        shadowElevation = 9.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountTree, null)
                Text("对象关系", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭对象关系") }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenStyle),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("Event #${event.id} → Style ${event.style}", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "共享 Style 被 ${references.size} 个 Event 引用；事件级 override 仍属于各自 Event Text。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text("引用同一 Style 的 Event", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(references, key = { it.id }) { ref ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onFocusEvent(ref.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (ref.id == event.id) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("#${ref.id} · L${ref.layer}", style = MaterialTheme.typography.labelLarge)
                            Text(
                                AssInlineSyntax.visibleText(ref.text).take(70),
                                maxLines = 2,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpatialWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    issues: List<AssQcIssue>,
    activeTool: WorkbenchTool,
    instance: WorkspaceToolInstance,
    onActiveTool: (WorkbenchTool) -> Unit,
    expandedEventId: Long?,
    onExpandedChange: (Long?) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    eventEditorStateHolder: SaveableStateHolder,
    onOpenVideo: () -> Unit,
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
    onEditEventPosition: (Long) -> Unit,
    searchOpen: Boolean,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val worldWidth = 1800.dp
    val worldHeight = 1320.dp
    var scale by rememberSaveable { mutableFloatStateOf(0.82f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(-80f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(-60f) }
    var navigationMode by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier.clipToBounds()) {
        val density = LocalDensity.current
        val fitScale = minOf(
            maxWidth.value / worldWidth.value,
            maxHeight.value / worldHeight.value,
        ).coerceIn(0.2f, 1f)

        fun focusWorldPoint(x: Dp, y: Dp, targetScale: Float = 0.9f) {
            scale = targetScale.coerceIn(0.35f, 1.6f)
            with(density) {
                offsetX = -(x.toPx() * scale) + 20.dp.toPx()
                offsetY = -(y.toPx() * scale) + 20.dp.toPx()
            }
        }

        fun transform(pan: Offset, zoom: Float) {
            val nextScale = (scale * zoom).coerceIn(0.25f, 1.8f)
            offsetX += pan.x
            offsetY += pan.y
            scale = nextScale
        }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Box(
                Modifier.fillMaxSize()
                    .testTag("spatial-background")
                    .pointerInput(scale) {
                        detectTransformGestures { _, pan, zoom, _ -> transform(pan, zoom) }
                    }
            )

            Box(
                Modifier.size(worldWidth, worldHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .testTag("spatial-world"),
            ) {
                SpatialNode(
                    title = "预览",
                    subtitle = "实时视频 / ASS",
                    modifier = Modifier.offset(x = 72.dp, y = 72.dp).size(760.dp, 460.dp)
                        .testTag("spatial-node-preview"),
                ) {
                    WorkbenchPreview(
                        state = state,
                        viewModel = viewModel,
                        positionEditEventId = null,
                        onOpenVideo = onOpenVideo,
                        onOpenTimeline = { onActiveTool(WorkbenchTool.TIMELINE) },
                        rendererEnabled = rendererEnabled,
                        onEnableRenderer = onEnableRenderer,
                        onEditEventPosition = { eventId ->
                            onEditEventPosition(eventId)
                            onActiveTool(WorkbenchTool.POSITION)
                        },
                        viewportGesturesEnabled = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                SpatialNode(
                    title = "字幕导航",
                    subtitle = "Event / Search / Selection",
                    modifier = Modifier.offset(x = 900.dp, y = 92.dp).size(520.dp, 620.dp)
                        .testTag("spatial-node-subtitles"),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (searchOpen) SearchStrip(state.query, viewModel::setQuery, onCloseSearch)
                        EventWorkspace(
                            state = state,
                            viewModel = viewModel,
                            issuesByEvent = issues.groupBy { it.eventId },
                            expandedEventId = expandedEventId,
                            onExpandedChange = { next ->
                                onExpandedChange(next)
                                if (next != null) onActiveTool(WorkbenchTool.TEXT)
                            },
                            onTool = onActiveTool,
                            eventEditorStateHolder = eventEditorStateHolder,
                            tool = WorkbenchTool.TEXT,
                            modifier = Modifier.fillMaxSize(),
                        ) { }
                    }
                }

                SpatialNode(
                    title = activeTool.title,
                    subtitle = "当前工具 · " + activeTool.group.title,
                    modifier = Modifier.offset(x = 300.dp, y = 660.dp).size(720.dp, 560.dp)
                        .testTag("spatial-node-tool"),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AssistChip(
                                onClick = { onActiveTool(WorkbenchTool.CAPABILITIES) },
                                label = { Text("全部工具") },
                                leadingIcon = { Icon(Icons.Filled.Apps, null, Modifier.size(18.dp)) },
                            )
                            WorkbenchTool.entries
                                .filter {
                                    it.group == activeTool.group &&
                                        it != WorkbenchTool.SUBTITLES &&
                                        it != WorkbenchTool.CAPABILITIES
                                }
                                .forEach { tool ->
                                    FilterChip(
                                        selected = tool == activeTool,
                                        onClick = { onActiveTool(tool) },
                                        label = { Text(tool.title) },
                                    )
                                }
                        }
                        FloatingToolContent(
                            instance = instance,
                            tool = activeTool,
                            state = state,
                            viewModel = viewModel,
                            issues = issues,
                            expandedEventId = expandedEventId,
                            onExpandedChange = onExpandedChange,
                            onImportFont = onImportFont,
                            onSaveMkv = onSaveMkv,
                            eventEditorStateHolder = eventEditorStateHolder,
                            onOpenTool = onActiveTool,
                            onCloseText = { onExpandedChange(null) },
                            searchOpen = searchOpen,
                            onCloseSearch = onCloseSearch,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.offset(x = 1120.dp, y = 820.dp).size(420.dp, 230.dp)
                        .testTag("spatial-node-map"),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("工作现场索引", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "预览 · 字幕导航 · 当前工具",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "缩放 ${(scale * 100).roundToInt()}% · X ${offsetX.roundToInt()} · Y ${offsetY.roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            if (navigationMode) {
                Box(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.06f))
                        .testTag("spatial-navigation-overlay")
                        .pointerInput(scale) {
                            detectTransformGestures { _, pan, zoom, _ -> transform(pan, zoom) }
                        }
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = navigationMode,
                        onClick = { navigationMode = !navigationMode },
                        label = { Text(if (navigationMode) "导航中" else "导航") },
                        leadingIcon = { Icon(Icons.Filled.OpenWith, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("spatial-navigation-mode"),
                    )
                    AssistChip(
                        onClick = {
                            scale = fitScale
                            offsetX = 0f
                            offsetY = 0f
                        },
                        label = { Text("鸟瞰") },
                        leadingIcon = { Icon(Icons.Filled.GridView, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("spatial-overview"),
                    )
                    AssistChip(
                        onClick = { focusWorldPoint(72.dp, 72.dp, 0.92f) },
                        label = { Text("预览") },
                        leadingIcon = { Icon(Icons.Filled.Movie, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("spatial-focus-preview"),
                    )
                    AssistChip(
                        onClick = { focusWorldPoint(900.dp, 92.dp, 0.9f) },
                        label = { Text("当前字幕") },
                        leadingIcon = { Icon(Icons.Filled.Subtitles, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("spatial-focus-subtitles"),
                    )
                    AssistChip(
                        onClick = { focusWorldPoint(300.dp, 660.dp, 0.9f) },
                        label = { Text("工具") },
                        leadingIcon = { Icon(Icons.Filled.Tune, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("spatial-focus-tool"),
                    )
                    IconButton(
                        onClick = { scale = (scale / 1.15f).coerceAtLeast(0.25f) },
                        modifier = Modifier.testTag("spatial-zoom-out"),
                    ) { Icon(Icons.Filled.Remove, "缩小工作区") }
                    Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                    IconButton(
                        onClick = { scale = (scale * 1.15f).coerceAtMost(1.8f) },
                        modifier = Modifier.testTag("spatial-zoom-in"),
                    ) { Icon(Icons.Filled.Add, "放大工作区") }
                }
            }
        }
    }
}

@Composable
private fun SpatialNode(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        shadowElevation = 5.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Icon(Icons.Filled.DragIndicator, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

private enum class PagerWorkspacePage(val title: String) {
    SUBTITLES("字幕"),
    PREVIEW("预览"),
    TOOL("工具"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagerWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    issues: List<AssQcIssue>,
    activeTool: WorkbenchTool,
    instance: WorkspaceToolInstance,
    onActiveTool: (WorkbenchTool) -> Unit,
    expandedEventId: Long?,
    onExpandedChange: (Long?) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    eventEditorStateHolder: SaveableStateHolder,
    onOpenVideo: () -> Unit,
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
    onEditEventPosition: (Long) -> Unit,
    searchOpen: Boolean,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = PagerWorkspacePage.entries
    val pagerState = rememberPagerState(initialPage = PagerWorkspacePage.PREVIEW.ordinal) {
        pages.size
    }
    val scope = rememberCoroutineScope()

    fun showPage(page: PagerWorkspacePage) {
        scope.launch { pagerState.animateScrollToPage(page.ordinal) }
    }

    fun selectTool(tool: WorkbenchTool) {
        if (tool == WorkbenchTool.SUBTITLES) {
            showPage(PagerWorkspacePage.SUBTITLES)
            return
        }
        onActiveTool(tool)
        showPage(PagerWorkspacePage.TOOL)
    }

    var lastActiveTool by remember { mutableStateOf(activeTool) }
    LaunchedEffect(activeTool) {
        if (activeTool != lastActiveTool) {
            pagerState.animateScrollToPage(PagerWorkspacePage.TOOL.ordinal)
            lastActiveTool = activeTool
        }
    }

    Column(modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
        ) { pageIndex ->
            when (pages[pageIndex]) {
                PagerWorkspacePage.SUBTITLES -> {
                    Column(Modifier.fillMaxSize().testTag("pager-page-subtitles")) {
                        if (searchOpen) {
                            SearchStrip(state.query, viewModel::setQuery, onCloseSearch)
                        }
                        EventWorkspace(
                            state = state,
                            viewModel = viewModel,
                            issuesByEvent = issues.groupBy { it.eventId },
                            expandedEventId = expandedEventId,
                            onExpandedChange = { next ->
                                onExpandedChange(next)
                                if (next != null) {
                                    onActiveTool(WorkbenchTool.TEXT)
                                    showPage(PagerWorkspacePage.TOOL)
                                }
                            },
                            onTool = ::selectTool,
                            eventEditorStateHolder = eventEditorStateHolder,
                            tool = WorkbenchTool.TEXT,
                            modifier = Modifier.fillMaxSize(),
                        ) { }
                    }
                }

                PagerWorkspacePage.PREVIEW -> {
                    WorkbenchPreview(
                        state = state,
                        viewModel = viewModel,
                        positionEditEventId = null,
                        onOpenVideo = onOpenVideo,
                        onOpenTimeline = {
                            onActiveTool(WorkbenchTool.TIMELINE)
                            showPage(PagerWorkspacePage.TOOL)
                        },
                        rendererEnabled = rendererEnabled,
                        onEnableRenderer = onEnableRenderer,
                        onEditEventPosition = { eventId ->
                            onEditEventPosition(eventId)
                            onActiveTool(WorkbenchTool.POSITION)
                            showPage(PagerWorkspacePage.TOOL)
                        },
                        viewportGesturesEnabled = true,
                        modifier = Modifier.fillMaxSize().testTag("pager-page-preview"),
                    )
                }

                PagerWorkspacePage.TOOL -> {
                    Column(Modifier.fillMaxSize().testTag("pager-page-tool")) {
                        Surface(
                            tonalElevation = 2.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AssistChip(
                                    onClick = { selectTool(WorkbenchTool.CAPABILITIES) },
                                    label = { Text("全部工具") },
                                    leadingIcon = { Icon(Icons.Filled.Apps, null, Modifier.size(18.dp)) },
                                    modifier = Modifier.testTag("pager-all-tools"),
                                )
                                WorkbenchTool.entries
                                    .filter {
                                        it.group == activeTool.group &&
                                            it != WorkbenchTool.SUBTITLES &&
                                            it != WorkbenchTool.CAPABILITIES
                                    }
                                    .forEach { tool ->
                                        FilterChip(
                                            selected = tool == activeTool,
                                            onClick = { selectTool(tool) },
                                            label = { Text(tool.title) },
                                            modifier = Modifier.testTag("pager-tool-" + tool.name),
                                        )
                                    }
                            }
                        }
                        FloatingToolContent(
                            instance = instance,
                            tool = activeTool,
                            state = state,
                            viewModel = viewModel,
                            issues = issues,
                            expandedEventId = expandedEventId,
                            onExpandedChange = onExpandedChange,
                            onImportFont = onImportFont,
                            onSaveMkv = onSaveMkv,
                            eventEditorStateHolder = eventEditorStateHolder,
                            onOpenTool = ::selectTool,
                            onCloseText = {
                                onExpandedChange(null)
                                showPage(PagerWorkspacePage.SUBTITLES)
                            },
                            searchOpen = searchOpen,
                            onCloseSearch = onCloseSearch,
                        )
                    }
                }
            }
        }

        NavigationBar(
            modifier = Modifier.fillMaxWidth().testTag("pager-navigation"),
            tonalElevation = 3.dp,
        ) {
            pages.forEach { page ->
                val selected = pagerState.currentPage == page.ordinal
                NavigationBarItem(
                    selected = selected,
                    onClick = { showPage(page) },
                    icon = {
                        Icon(
                            when (page) {
                                PagerWorkspacePage.SUBTITLES -> Icons.Filled.Subtitles
                                PagerWorkspacePage.PREVIEW -> Icons.Filled.Movie
                                PagerWorkspacePage.TOOL -> Icons.Filled.Tune
                            },
                            contentDescription = page.title,
                        )
                    },
                    label = { Text(page.title) },
                    modifier = Modifier.testTag("pager-nav-" + page.name),
                )
            }
        }
    }
}

@Composable
private fun FixedWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    issues: List<AssQcIssue>,
    activeTool: WorkbenchTool,
    instance: WorkspaceToolInstance,
    navigationRevision: Int,
    listRequested: Boolean,
    onActiveTool: (WorkbenchTool) -> Unit,
    expandedEventId: Long?,
    onExpandedChange: (Long?) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    eventEditorStateHolder: SaveableStateHolder,
    onOpenVideo: () -> Unit,
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
    interactionRegistry: InteractionOverlayRegistry,
    onEditEventPosition: (Long) -> Unit,
    searchOpen: Boolean,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var compactPage by rememberSaveable { mutableStateOf("LIST") }
    var lastActiveTool by remember { mutableStateOf(activeTool) }
    LaunchedEffect(activeTool) {
        if (activeTool != lastActiveTool) compactPage = "EDITOR"
        lastActiveTool = activeTool
    }
    var consumedNavigationRevision by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(navigationRevision) {
        if (navigationRevision != consumedNavigationRevision) {
            compactPage = if (listRequested) "LIST" else "EDITOR"
            consumedNavigationRevision = navigationRevision
        }
    }

    var previewDensityName by rememberSaveable { mutableStateOf(PreviewDensity.FULL.name) }
    var navigationGroupRequested by rememberSaveable { mutableStateOf(false) }
    val requestedPreview = runCatching { PreviewDensity.valueOf(previewDensityName) }
        .getOrDefault(PreviewDensity.FULL)
    val fixedTools = WorkbenchTool.entries.filter {
        it.group == activeTool.group && it != WorkbenchTool.SUBTITLES && it != WorkbenchTool.CAPABILITIES
    }

    fun selectTool(tool: WorkbenchTool) {
        navigationGroupRequested = false
        compactPage = "EDITOR"
        onActiveTool(tool)
    }

    val listPane: @Composable (Modifier) -> Unit = { paneModifier ->
        Column(paneModifier) {
            if (searchOpen) SearchStrip(state.query, viewModel::setQuery, onCloseSearch)
            EventWorkspace(
                state = state,
                viewModel = viewModel,
                issuesByEvent = issues.groupBy { it.eventId },
                expandedEventId = expandedEventId,
                onExpandedChange = { next ->
                    val target = next ?: expandedEventId
                    onExpandedChange(target)
                    if (target != null) selectTool(WorkbenchTool.TEXT)
                },
                onTool = ::selectTool,
                eventEditorStateHolder = eventEditorStateHolder,
                tool = WorkbenchTool.TEXT,
                modifier = Modifier.weight(1f),
            ) { }
        }
    }

    val inspector: @Composable (Modifier) -> Unit = { paneModifier ->
        Surface(
            modifier = paneModifier.testTag("fixed-inspector"),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            FloatingToolContent(
                instance = instance,
                tool = activeTool,
                state = state,
                viewModel = viewModel,
                issues = issues,
                expandedEventId = expandedEventId,
                onExpandedChange = onExpandedChange,
                onImportFont = onImportFont,
                onSaveMkv = onSaveMkv,
                eventEditorStateHolder = eventEditorStateHolder,
                onOpenTool = ::selectTool,
                onCloseText = { onExpandedChange(null); compactPage = "LIST" },
                searchOpen = searchOpen,
                onCloseSearch = onCloseSearch,
            )
        }
    }

    Column(modifier) {
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WorkbenchToolGroup.entries.forEach { group ->
                        FilterChip(
                            selected = if (group == WorkbenchToolGroup.NAVIGATION) {
                                navigationGroupRequested
                            } else {
                                !navigationGroupRequested && activeTool.group == group
                            },
                            onClick = {
                                if (group == WorkbenchToolGroup.NAVIGATION) {
                                    navigationGroupRequested = true
                                    compactPage = "LIST"
                                } else {
                                    WorkbenchTool.entries
                                        .firstOrNull { it.group == group && it != WorkbenchTool.SUBTITLES }
                                        ?.let(::selectTool)
                                }
                            },
                            label = { Text(group.title) },
                            modifier = Modifier.testTag("fixed-group-" + group.name),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(activeTool.group.title, style = MaterialTheme.typography.labelMedium)
                    fixedTools.forEach { tool ->
                        FilterChip(
                            selected = tool == activeTool,
                            onClick = { selectTool(tool) },
                            label = { Text(tool.title) },
                            modifier = Modifier.testTag("fixed-tool-" + tool.name),
                        )
                    }
                    FilterChip(
                        selected = requestedPreview != PreviewDensity.HIDDEN,
                        onClick = { previewDensityName = requestedPreview.next().name },
                        label = { Text("预览 · " + requestedPreview.label) },
                        leadingIcon = {
                            Icon(
                                if (requestedPreview == PreviewDensity.HIDDEN) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                null,
                                Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.testTag("fixed-preview-density"),
                    )
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val imeVisible = WindowInsets.ime.getBottom(density) > 0
            val policy = AdaptiveWorkspacePolicy.resolve(
                widthDp = maxWidth.value,
                heightDp = maxHeight.value,
                imeVisible = imeVisible,
                requestedPreview = requestedPreview,
            )
            val briefInspector = activeTool == WorkbenchTool.PROJECT || activeTool == WorkbenchTool.EVENT
            val inspectorFraction = when (policy.profile) {
                WorkbenchLayoutProfile.THREE_PANE -> if (briefInspector) 0.30f else policy.inspectorFraction
                WorkbenchLayoutProfile.DUAL_PANE -> if (briefInspector) 0.38f else policy.inspectorFraction
                WorkbenchLayoutProfile.COMPACT -> 1f
            }
            val previewVisible = policy.effectivePreview != PreviewDensity.HIDDEN

            val preview: @Composable (Modifier) -> Unit = { previewModifier ->
                val positionTargetId = if (activeTool == WorkbenchTool.POSITION) {
                    (instance.binding.resolve(
                        focusedEventId = state.focusedEventId,
                        selectedEventIds = state.selectedEventIds,
                        existingEventIds = state.document.events.mapTo(hashSetOf()) { it.id },
                    ) as? WorkspaceBindingResolution.Event)?.eventId
                } else {
                    null
                }
                WorkbenchPreview(
                    state = state,
                    viewModel = viewModel,
                    positionEditEventId = positionTargetId,
                    onOpenVideo = onOpenVideo,
                    onOpenTimeline = { selectTool(WorkbenchTool.TIMELINE) },
                    rendererEnabled = rendererEnabled,
                    onEnableRenderer = onEnableRenderer,
                    onEditEventPosition = onEditEventPosition,
                    interactionRegistry = if (activeTool == WorkbenchTool.POSITION) interactionRegistry else null,
                    viewportGesturesEnabled = positionTargetId == null,
                    modifier = previewModifier.testTag("preview-workspace"),
                )
            }

            when (policy.profile) {
                WorkbenchLayoutProfile.THREE_PANE -> Row(Modifier.fillMaxSize()) {
                    if (previewVisible) {
                        val previewWeight = if (policy.effectivePreview == PreviewDensity.COMPACT) 0.22f else 0.32f
                        preview(Modifier.weight(previewWeight).fillMaxHeight())
                        VerticalDivider()
                        listPane(
                            Modifier.weight((1f - previewWeight - inspectorFraction).coerceAtLeast(0.20f))
                                .fillMaxHeight()
                        )
                    } else {
                        listPane(Modifier.weight(1f - inspectorFraction).fillMaxHeight())
                    }
                    VerticalDivider()
                    inspector(Modifier.weight(inspectorFraction).fillMaxHeight())
                }

                WorkbenchLayoutProfile.DUAL_PANE -> Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f - inspectorFraction).fillMaxHeight()) {
                        if (previewVisible) {
                            preview(
                                Modifier.fillMaxWidth()
                                    .height(policy.previewHeightDp.dp)
                            )
                            HorizontalDivider()
                        }
                        listPane(Modifier.weight(1f).fillMaxWidth())
                    }
                    VerticalDivider()
                    inspector(Modifier.weight(inspectorFraction).fillMaxHeight())
                }

                WorkbenchLayoutProfile.COMPACT -> Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            compactPage == "LIST",
                            { compactPage = "LIST" },
                            { Text("字幕列表") },
                            modifier = Modifier.testTag("fixed-page-list"),
                        )
                        FilterChip(
                            compactPage == "EDITOR",
                            { compactPage = "EDITOR" },
                            { Text(activeTool.title) },
                            modifier = Modifier.testTag("fixed-page-editor"),
                        )
                    }
                    if (previewVisible) {
                        preview(Modifier.fillMaxWidth().height(policy.previewHeightDp.dp))
                        HorizontalDivider()
                    }
                    if (compactPage == "LIST") {
                        listPane(Modifier.weight(1f).fillMaxWidth())
                    } else {
                        inspector(Modifier.weight(1f).fillMaxWidth())
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
    positionEditEventId: Long?,
    onOpenVideo: () -> Unit,
    onOpenTimeline: () -> Unit,
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
    onEditEventPosition: (Long) -> Unit = { id -> viewModel.focusEvent(id, seek = false) },
    onVideoAspectRatio: (Float) -> Unit = {},
    interactionRegistry: InteractionOverlayRegistry? = null,
    viewportGesturesEnabled: Boolean = false,
    onObjectLongPress: ((PreviewObjectPick) -> Unit)? = null,
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
        selectedAudioOrdinal = state.audioTracks.firstOrNull { it.extractorIndex == state.selectedAudioTrackIndex }?.ordinal,
        positionEditEventId = positionEditEventId,
        onPreviewEventPosition = { x, y ->
            positionEditEventId?.let { viewModel.previewEventPosition(it, x, y) }
        },
        onSetEventPosition = { x, y ->
            positionEditEventId?.let { viewModel.setEventPosition(it, x, y) }
        },
        onPreviewEventMove = { sx, sy, ex, ey ->
            positionEditEventId?.let { viewModel.previewEventMove(it, sx, sy, ex, ey) }
        },
        onSetEventMove = { sx, sy, ex, ey ->
            positionEditEventId?.let { viewModel.setEventMove(it, sx, sy, ex, ey) }
        },
        onPreviewEventOrigin = { x, y ->
            positionEditEventId?.let { viewModel.previewEventOrigin(it, x, y) }
        },
        onSetEventOrigin = { x, y ->
            positionEditEventId?.let { viewModel.setEventOrigin(it, x, y) }
        },
        onPreviewEventRotation = { angle ->
            positionEditEventId?.let { viewModel.previewEventRotationZ(it, angle) }
        },
        onSetEventRotation = { angle ->
            positionEditEventId?.let { viewModel.setEventRotationZ(it, angle) }
        },
        scaleLocked = state.geometryScaleLocked,
        onPreviewEventScale = { sx, sy ->
            positionEditEventId?.let { viewModel.previewEventScale(it, sx, sy) }
        },
        onSetEventScale = { sx, sy ->
            positionEditEventId?.let { viewModel.setEventScale(it, sx, sy) }
        },
        onPreviewEventShear = { fx, fy ->
            positionEditEventId?.let { viewModel.previewEventShear(it, fx, fy) }
        },
        onSetEventShear = { fx, fy ->
            positionEditEventId?.let { viewModel.setEventShear(it, fx, fy) }
        },
        onPreviewEventClip = { left, top, right, bottom, inverted ->
            positionEditEventId?.let {
                viewModel.previewEventRectClip(it, left, top, right, bottom, inverted)
            }
        },
        onSetEventClip = { left, top, right, bottom, inverted ->
            positionEditEventId?.let {
                viewModel.setEventRectClip(it, left, top, right, bottom, inverted)
            }
        },
        onCancelEventPositionPreview = viewModel::clearTransientPreview,
        onFocusEvent = { viewModel.focusEvent(it, seek = false) },
        onEditEventPosition = onEditEventPosition,
        onSetEventTiming = viewModel::setEventTiming,
        onOpenVideo = onOpenVideo,
        onOpenTimeline = onOpenTimeline,
        rendererEnabled = rendererEnabled,
        onEnableRenderer = onEnableRenderer,
        fillViewport = true,
        onVideoAspectRatio = onVideoAspectRatio,
        interactionRegistry = interactionRegistry,
        viewportGesturesEnabled = viewportGesturesEnabled,
        onObjectLongPress = onObjectLongPress,
        modifier = modifier,
    )
}

@Composable
private fun FloatingToolContent(
    instance: WorkspaceToolInstance,
    tool: WorkbenchTool,
    state: EditorState,
    viewModel: EditorViewModel,
    issues: List<AssQcIssue>,
    expandedEventId: Long?,
    onExpandedChange: (Long?) -> Unit,
    onImportFont: () -> Unit,
    onSaveMkv: () -> Unit,
    eventEditorStateHolder: SaveableStateHolder,
    onOpenTool: (WorkbenchTool) -> Unit,
    onCloseText: () -> Unit,
    searchOpen: Boolean,
    onCloseSearch: () -> Unit,
) {
    val resolvedBinding = instance.binding.resolve(
        focusedEventId = state.focusedEventId,
        selectedEventIds = state.selectedEventIds,
        existingEventIds = state.document.events.asSequence().map { it.id }.toSet(),
    )
    val boundEventId = (resolvedBinding as? WorkspaceBindingResolution.Event)?.eventId
    val unresolvedPinnedEventId =
        (resolvedBinding as? WorkspaceBindingResolution.UnresolvedPinnedEvent)?.eventId
    val contextualEventId = if (unresolvedPinnedEventId != null) {
        null
    } else {
        boundEventId ?: expandedEventId ?: state.focusedEventId
    }
    val event = state.document.events.firstOrNull { it.id == contextualEventId }
    val editScope = WorkspaceEditScopeResolver.resolve(
        tool = tool,
        instance = instance,
        document = state.document,
        focusedEventId = state.focusedEventId,
        selectedEventIds = state.selectedEventIds,
    )
    when (tool) {
        WorkbenchTool.SUBTITLES -> Column(Modifier.fillMaxSize()) {
            if (searchOpen) SearchStrip(state.query, viewModel::setQuery, onCloseSearch)
            EventWorkspace(state, viewModel, issues.groupBy { it.eventId }, expandedEventId,
                { next ->
                    onExpandedChange(next)
                    if (next == null) onCloseText() else onOpenTool(WorkbenchTool.TEXT)
                }, onOpenTool, eventEditorStateHolder, WorkbenchTool.TEXT, Modifier.weight(1f)) { }
        }
        WorkbenchTool.TEXT, WorkbenchTool.EFFECTS, WorkbenchTool.EVENT -> {
            if (event == null) {
                Column(Modifier.fillMaxSize()) {
                    editScope?.let { WorkspaceEditScopeBar(it) }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (editScope?.unresolved == true) "绑定目标已失效" else "先选择一条字幕")
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().testTag("event-inspector").verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    editScope?.let { WorkspaceEditScopeBar(it) }
                    Text("#${event.id} · ${tool.title}", style = MaterialTheme.typography.titleSmall)
                    if (tool == WorkbenchTool.TEXT) IconButton(onClick = onCloseText,
                        modifier = Modifier.testTag("event-collapse-${event.id}")) { Icon(Icons.Filled.Close, "收起正文工具") }
                    eventEditorStateHolder.SaveableStateProvider("${instance.id}-${event.id}") {
                        InlineEventEditor(
                            event = event,
                            styleName = event.style,
                            state = state,
                            viewModel = viewModel,
                            onTool = onOpenTool,
                            activeSection = tool,
                        )
                    }
                }
            }
        }
        WorkbenchTool.TIMELINE -> ModernTimelinePane(state, viewModel, Modifier.fillMaxSize())
        WorkbenchTool.FRAMES -> FrameTimingPane(state, viewModel, Modifier.fillMaxSize())
        WorkbenchTool.KARAOKE -> ScopedToolPane(editScope) { paneModifier ->
            KaraokePane(state, viewModel, paneModifier)
        }
        WorkbenchTool.VECTOR_CLIP -> ScopedToolPane(editScope) { paneModifier ->
            VectorClipPane(state, viewModel, paneModifier)
        }
        WorkbenchTool.COMPATIBILITY -> CompatibilityPane(state, viewModel, Modifier.fillMaxSize())
        WorkbenchTool.FONT_REQUIREMENTS -> FontRequirementsPane(state, { onOpenTool(WorkbenchTool.FONTS) }, Modifier.fillMaxSize())
        WorkbenchTool.STYLE -> ScopedToolPane(editScope) { paneModifier ->
            StylePane(
                state = state,
                viewModel = viewModel,
                modifier = paneModifier,
                targetEventId = boundEventId,
                unresolvedPinnedEventId = unresolvedPinnedEventId,
            )
        }
        WorkbenchTool.POSITION -> ScopedToolPane(editScope) { paneModifier ->
            PositionPane(
                state = state,
                viewModel = viewModel,
                modifier = paneModifier,
                targetEventId = boundEventId,
                unresolvedPinnedEventId = unresolvedPinnedEventId,
            )
        }
        WorkbenchTool.FONTS -> FontManagerPane(state, viewModel, onImportFont, Modifier.fillMaxSize())
        WorkbenchTool.QC -> AdvancedQcPane(state, viewModel, Modifier.fillMaxSize())
        WorkbenchTool.BATCH -> ScopedToolPane(editScope) { paneModifier ->
            RuleBatchPane(state, viewModel, paneModifier)
        }
        WorkbenchTool.PROJECT -> ProjectPane(state, viewModel, onSaveMkv, Modifier.fillMaxSize())
        WorkbenchTool.DIAGNOSTICS -> DiagnosticsPane(state, viewModel, Modifier.fillMaxSize())
        WorkbenchTool.CAPABILITIES -> WorkspaceToolDirectory(onOpenTool, Modifier.fillMaxSize())
    }
}

@Composable
private fun ScopedToolPane(
    summary: WorkspaceEditScopeSummary?,
    content: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        summary?.let { WorkspaceEditScopeBar(it) }
        content(Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun WorkspaceEditScopeBar(summary: WorkspaceEditScopeSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("edit-scope-bar"),
        color = if (summary.unresolved) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            1.dp,
            if (summary.unresolved) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(summary.who, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(summary.binding, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "写入：${summary.where} · 影响 ${summary.howMany} 条",
                style = MaterialTheme.typography.labelSmall,
                color = if (summary.unresolved) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (summary.unresolved) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceToolDirectory(onOpenTool: (WorkbenchTool) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("搜索工具") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth().testTag("tool-search"))
        Text("按编辑对象分区；同一工具在固定界面与 Canvas 中共用。", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WorkbenchToolGroup.entries.forEach { group ->
                val entries = WorkbenchTool.entries.filter { it.group == group && it != WorkbenchTool.CAPABILITIES &&
                    (query.isBlank() || it.title.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true) || group.title.contains(query)) }
                if (entries.isNotEmpty()) {
                    item(key = "group-" + group.name) { Text(group.title, style = MaterialTheme.typography.titleSmall) }
                    items(entries, key = { it.name }) { entry ->
                        OutlinedButton(onClick = { onOpenTool(entry) }, modifier = Modifier.fillMaxWidth().testTag("tool-${entry.name}")) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.title)
                                Text(entry.description, style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.Filled.OpenInNew, null, Modifier.size(18.dp))
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
    onNewSubtitle: () -> Unit,
    onImportFont: () -> Unit,
    onSave: () -> Unit,
    onSaveMkv: () -> Unit,
    onTool: (WorkbenchTool) -> Unit,
    workspaceMode: WorkspacePresentationMode,
    onOpenUiVariantLab: () -> Unit,
    onOpenProject: () -> Unit,
    onSaveProject: () -> Unit,
    onExportSrt: () -> Unit,
    appearance: WorkbenchAppearance,
    onAppearanceChange: (WorkbenchAppearance) -> Unit,
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
                        DropdownMenuItem(text = { Text("打开 ASS / SRT") }, leadingIcon = { Icon(Icons.Filled.Subtitles, null) }, onClick = { onDismissMenu(); onOpenSubtitle() })
                        DropdownMenuItem(text = { Text("打开 Workbench Project") }, leadingIcon = { Icon(Icons.Filled.FolderSpecial, null) }, onClick = { onDismissMenu(); onOpenProject() })
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
                        DropdownMenuItem(
                            text = { Text("UI 实验室 · " + workspaceMode.title) },
                            leadingIcon = { Icon(Icons.Filled.ViewCarousel, null) },
                            onClick = { moreMenuOpen = false; onOpenUiVariantLab() },
                            modifier = Modifier.testTag("workspace-mode-toggle"),
                        )
                        DropdownMenuItem(
                            text = { Text("外观：" + appearance.label) },
                            leadingIcon = {
                                Icon(
                                    when (appearance) {
                                        WorkbenchAppearance.SYSTEM -> Icons.Filled.SettingsBrightness
                                        WorkbenchAppearance.LIGHT -> Icons.Filled.LightMode
                                        WorkbenchAppearance.DARK -> Icons.Filled.DarkMode
                                    },
                                    null,
                                )
                            },
                            onClick = {
                                moreMenuOpen = false
                                onAppearanceChange(appearance.next())
                            },
                            modifier = Modifier.testTag("appearance-mode-toggle"),
                        )
                        DropdownMenuItem(text = { Text("保存 Workbench Project") }, leadingIcon = { Icon(Icons.Filled.SaveAs, null) }, onClick = { moreMenuOpen = false; onSaveProject() })
                        DropdownMenuItem(text = { Text("导出 SRT") }, leadingIcon = { Icon(Icons.Filled.Subtitles, null) }, onClick = { moreMenuOpen = false; onExportSrt() })
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
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        ) { content() }
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
        if (index >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(index)
        }
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
    listPane(modifier)
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
                    FilterChip(selected = comment, onClick = { comment = !comment }, label = { Text(if (comment) "Comment" else "Dialogue") })
                }
                OutlinedTextField(actorText, { actorText = it }, label = { Text("Actor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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

    DisposableEffect(event.id) {
        onDispose { viewModel.clearTransientPreview("effects:${event.id}") }
    }

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

    AnimationAuthorPane(event, viewModel)

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
    val speechBoundaries = remember(state.waveform.envelope) {
        state.waveform.envelope?.let { AudioTimingAssist.speechBoundaries(it) }.orEmpty()
    }
    var snapSpeech by rememberSaveable { mutableStateOf(true) }
    var snapScenes by rememberSaveable { mutableStateOf(true) }
    val snapTargets = remember(visible, playheadMs, snapEvents, snapPlayhead, snapSpeech, snapScenes, speechBoundaries, state.sceneCutsMs) {
        buildList {
            if (snapPlayhead) add(playheadMs)
            if (snapEvents) visible.forEach { add(it.start.millis); add(it.end.millis) }
            if (snapSpeech) addAll(speechBoundaries.filter { it in windowStart..windowEnd })
            if (snapScenes) addAll(state.sceneCutsMs.filter { it in windowStart..windowEnd })
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
            if (state.audioTracks.size > 1) {
                state.audioTracks.forEach { track ->
                    FilterChip(
                        selected = track.extractorIndex == state.selectedAudioTrackIndex,
                        onClick = { viewModel.selectAudioTrack(track.extractorIndex) },
                        label = { Text(track.label) },
                    )
                }
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
                DropdownMenuItem(
                    text = { Text((if (snapSpeech) "✓ " else "") + "语音边界") },
                    enabled = snapEnabled && speechBoundaries.isNotEmpty(),
                    onClick = { snapSpeech = !snapSpeech },
                )
                DropdownMenuItem(
                    text = { Text((if (snapScenes) "✓ " else "") + "场景切点") },
                    enabled = snapEnabled && state.sceneCutsMs.isNotEmpty(),
                    onClick = { snapScenes = !snapScenes },
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
private fun StylePane(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    targetEventId: Long? = state.focusedEventId,
    unresolvedPinnedEventId: Long? = null,
) {
    if (unresolvedPinnedEventId != null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("固定目标 #$unresolvedPinnedEventId 已不存在。请解除固定或重新绑定。")
        }
        return
    }
    val contextualEvent = state.document.events.firstOrNull { it.id == targetEventId }
    val style = contextualEvent
        ?.let { event -> state.document.styles.firstOrNull { it.name == event.style } }
        ?: state.document.styles.firstOrNull()
    if (style == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("没有 Style") }
    } else {
        TypesettingPanel(
            state = state,
            viewModel = viewModel,
            style = style,
            modifier = modifier,
            contextEventId = targetEventId,
        )
    }
}

@Composable
private fun PositionPane(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    targetEventId: Long? = state.focusedEventId,
    unresolvedPinnedEventId: Long? = null,
) {
    if (unresolvedPinnedEventId != null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("固定目标 #$unresolvedPinnedEventId 已不存在。请解除固定或重新绑定。")
        }
        return
    }
    val event = state.document.events.firstOrNull { it.id == targetEventId }
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
        viewModel.previewEventRotationZ(event.id, value)
        kotlinx.coroutines.delay(320)
        viewModel.setEventRotationZ(event.id, value)
        rotationDraftChanged = false
    }
    LaunchedEffect(event.id, scaleXText, scaleYText, scaleGestureActive, scaleDraftChanged) {
        if (!scaleDraftChanged || scaleGestureActive) return@LaunchedEffect
        val sx = scaleXText.toDoubleOrNull() ?: return@LaunchedEffect
        val sy = scaleYText.toDoubleOrNull() ?: return@LaunchedEffect
        scalePreviewX = sx
        scalePreviewY = sy
        viewModel.previewEventScale(event.id, sx, sy)
        kotlinx.coroutines.delay(320)
        viewModel.setEventScale(event.id, sx, sy)
        scaleDraftChanged = false
    }
    LaunchedEffect(event.id, shearXText, shearYText, shearGestureActive, shearDraftChanged) {
        if (!shearDraftChanged || shearGestureActive) return@LaunchedEffect
        val fx = shearXText.toDoubleOrNull() ?: return@LaunchedEffect
        val fy = shearYText.toDoubleOrNull() ?: return@LaunchedEffect
        shearPreviewX = fx
        shearPreviewY = fy
        viewModel.previewEventShear(event.id, fx, fy)
        kotlinx.coroutines.delay(320)
        viewModel.setEventShear(event.id, fx, fy)
        shearDraftChanged = false
    }
    LaunchedEffect(event.id, clipLeftText, clipTopText, clipRightText, clipBottomText, clipDraftChanged) {
        val rect = geometry.clipRect ?: return@LaunchedEffect
        if (!clipDraftChanged) return@LaunchedEffect
        val left = clipLeftText.toDoubleOrNull() ?: return@LaunchedEffect
        val top = clipTopText.toDoubleOrNull() ?: return@LaunchedEffect
        val right = clipRightText.toDoubleOrNull() ?: return@LaunchedEffect
        val bottom = clipBottomText.toDoubleOrNull() ?: return@LaunchedEffect
        viewModel.previewEventRectClip(event.id, left, top, right, bottom, geometry.clipInverted)
        kotlinx.coroutines.delay(320)
        viewModel.setEventRectClip(event.id, left, top, right, bottom, geometry.clipInverted)
        clipDraftChanged = false
    }

    DisposableEffect(event.id) {
        onDispose { viewModel.clearTransientPreview("geometry:${event.id}") }
    }

    var sectionName by rememberSaveable { mutableStateOf(PositionSection.PLACEMENT.name) }
    val section = PositionSection.valueOf(sectionName)
    val sectionScroll = rememberLazyListState()
    LaunchedEffect(section) { sectionScroll.scrollToItem(0) }
    Column(modifier) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PositionSection.entries.forEach { entry ->
            FilterChip(section == entry, { sectionName = entry.name }, { Text(entry.title) },
                modifier = Modifier.testTag("position-section-" + entry.name))
        }
    }
    LazyColumn(
        Modifier.weight(1f).padding(WorkbenchDimens.Small),
        state = sectionScroll,
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
        if (section == PositionSection.PLACEMENT) {
        item {
            Text("Event 对齐覆盖")
            Column(verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3)).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro)) {
                        row.forEach { value ->
                            OutlinedButton(
                                onClick = { viewModel.setEventAlignment(event.id, value) },
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
                            viewModel.setEventMove(event.id, sx, sy, ex, ey)
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

        }
        if (section == PositionSection.TRANSFORM) {
        item { Divider() }
        item {
            Text("变换原点 · \\org", style = MaterialTheme.typography.titleSmall)
            if (geometry.origin != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                    OutlinedTextField(originX, { originX = it }, label = { Text("Origin X") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(originY, { originY = it }, label = { Text("Origin Y") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { viewModel.clearEventOrigin(event.id) }) { Text("移除 \\org") }
                    Button(onClick = {
                        val ox = originX.toDoubleOrNull()
                        val oy = originY.toDoubleOrNull()
                        if (ox != null && oy != null) viewModel.setEventOrigin(event.id, ox, oy)
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
                    viewModel.setEventOrigin(event.id,
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
                    viewModel.clearEventRotationZ(event.id)
                }) else null,
                onPreview = { value ->
                    rotationPreviewValue = value
                    viewModel.previewEventRotationZ(event.id, value)
                },
                onGestureActive = { active ->
                    rotationGestureActive = active
                    if (!active) {
                        viewModel.setEventRotationZ(event.id, rotationPreviewValue)
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
                    viewModel.previewEventScale(event.id, sx, sy)
                },
                onGestureActive = { active ->
                    scaleGestureActive = active
                    if (!active) {
                        viewModel.setEventScale(event.id, scalePreviewX, scalePreviewY)
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
                    viewModel.previewEventScale(event.id, sx, sy)
                },
                onGestureActive = { active ->
                    scaleGestureActive = active
                    if (!active) {
                        viewModel.setEventScale(event.id, scalePreviewX, scalePreviewY)
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
                    viewModel.clearEventScale(event.id)
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
                    viewModel.previewEventShear(event.id, fx, fy)
                },
                onGestureActive = { active ->
                    shearGestureActive = active
                    if (!active) {
                        viewModel.setEventShear(event.id, shearPreviewX, shearPreviewY)
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
                    viewModel.previewEventShear(event.id, fx, fy)
                },
                onGestureActive = { active ->
                    shearGestureActive = active
                    if (!active) {
                        viewModel.setEventShear(event.id, shearPreviewX, shearPreviewY)
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
                    viewModel.clearEventShear(event.id)
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
        }
        if (section == PositionSection.CLIP) {
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
                        OutlinedButton(onClick = { viewModel.clearEventClip(event.id) }) { Text("移除现有 clip") }
                        Button(onClick = {
                            viewModel.setEventRectClip(event.id,
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
                                viewModel.setEventRectClip(event.id, clipRect.left, clipRect.top, clipRect.right, clipRect.bottom, false)
                            },
                            label = { Text("\\clip · 内部显示") },
                        )
                        FilterChip(
                            selected = geometry.clipInverted,
                            onClick = {
                                viewModel.setEventRectClip(event.id, clipRect.left, clipRect.top, clipRect.right, clipRect.bottom, true)
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
                        TextButton(onClick = { viewModel.clearEventClip(event.id) }) { Text("移除 clip") }
                        Button(onClick = {
                            val left = clipLeftText.toDoubleOrNull()
                            val top = clipTopText.toDoubleOrNull()
                            val right = clipRightText.toDoubleOrNull()
                            val bottom = clipBottomText.toDoubleOrNull()
                            if (left != null && top != null && right != null && bottom != null) {
                                clipDraftChanged = false
                                viewModel.setEventRectClip(event.id, left, top, right, bottom, geometry.clipInverted)
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
                            viewModel.setEventRectClip(event.id,
                                state.document.playResX * 0.1,
                                state.document.playResY * 0.1,
                                state.document.playResX * 0.9,
                                state.document.playResY * 0.9,
                                false,
                            )
                        }) { Text("添加 \\clip") }
                        OutlinedButton(onClick = {
                            viewModel.setEventRectClip(event.id,
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
        }
        if (section == PositionSection.PLACEMENT) {
        item {
            Text("任意位置")
            Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                OutlinedTextField(x, { x = it }, label = { Text("X") }, singleLine = true, enabled = geometry.positionMode != AssPositionMode.MOVE && geometry.positionMode != AssPositionMode.CONFLICT, modifier = Modifier.weight(1f))
                OutlinedTextField(y, { y = it }, label = { Text("Y") }, singleLine = true, enabled = geometry.positionMode != AssPositionMode.MOVE && geometry.positionMode != AssPositionMode.CONFLICT, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        val px = x.toDoubleOrNull()
                        val py = y.toDoubleOrNull()
                        if (px != null && py != null) viewModel.setEventPosition(event.id, px, py)
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

        }
        if (style != null && section == PositionSection.STYLE_LAYOUT) {
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
                        val collectionLabel = if (font.collectionFaces.isNotEmpty()) {
                            " · Collection " + font.collectionFaces.size + " faces"
                        } else ""
                        Text(
                            font.metadata.rendererFamily + collectionLabel + " · " + originLabel +
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
                        if (font.collectionFaces.isEmpty()) {
                            TextButton(
                                onClick = { viewModel.setStyleFont(style.name, font.metadata.rendererFamily) },
                            ) { Text("用于 " + style.name) }
                        } else {
                            var facesOpen by remember(font.sha256) { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { facesOpen = true }) { Text("选择 Face") }
                                DropdownMenu(facesOpen, { facesOpen = false }) {
                                    font.collectionFaces.forEachIndexed { faceIndex, face ->
                                        DropdownMenuItem(
                                            text = { Text((faceIndex + 1).toString() + " · " + face.rendererFamily) },
                                            onClick = {
                                                viewModel.setStyleFont(style.name, face.rendererFamily)
                                                facesOpen = false
                                            },
                                        )
                                    }
                                }
                            }
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
private fun ProjectPane(state: EditorState, viewModel: EditorViewModel, onSaveMkv: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(WorkbenchDimens.Small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

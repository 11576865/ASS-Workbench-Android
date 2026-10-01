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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.BuildConfig
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.WaveformLiteState
import io.github.assworkbench.app.WaveformLiteStatus
import io.github.assworkbench.app.ui.workspace.WorkspaceBinding
import io.github.assworkbench.app.ui.workspace.FloatingWorkbenchSurface
import io.github.assworkbench.app.ui.workspace.rememberWorkbenchSurfaceController
import io.github.assworkbench.app.ui.workspace.WorkspaceBindingResolution
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

private enum class WorkbenchTool(val title: String) {
    TEXT("正文"), TIMELINE("时间轴"), STYLE("样式"), POSITION("位置"),
    EFFECTS("效果"), EVENT("事件"), FONTS("字体"), QC("检查"), BATCH("批量"),
    PROJECT("项目"), DIAGNOSTICS("诊断"), CAPABILITIES("功能地图"),
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
    var workspaceState by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toSaveableList() },
            restore = { WorkspaceState.fromSaveableList(it) },
        ),
    ) { mutableStateOf(WorkspaceState()) }
    var previewModeName by rememberSaveable { mutableStateOf(PreviewWorkspaceMode.NORMAL.name) }
    val surfaceController = rememberWorkbenchSurfaceController()
    val interactionRegistry = rememberInteractionOverlayRegistry()
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
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var openMenu by remember { mutableStateOf(false) }
    var saveConfirmOpen by remember { mutableStateOf(false) }
    var mkvConfirmOpen by remember { mutableStateOf(false) }
    var destructiveWorkspaceAction by remember { mutableStateOf<DestructiveWorkspaceAction?>(null) }

    val tool = WorkbenchTool.entries.firstOrNull { it.name == toolName } ?: WorkbenchTool.TIMELINE
    val openSurfaces = WorkbenchTool.entries.filter { workspaceState.hasTool(it.name) }.toSet()
    val previewMode = PreviewWorkspaceMode.entries.firstOrNull { it.name == previewModeName }
        ?: PreviewWorkspaceMode.NORMAL
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
        previewMode == PreviewWorkspaceMode.MANIPULATION -> state.focusedEventId
        else -> null
    }
    val issues by produceState<List<AssQcIssue>>(initialValue = emptyList(), state.document) {
        value = withContext(Dispatchers.Default) {
            AssQualityCheck.inspect(state.document)
        }
    }
    val issuesByEvent = remember(issues) { issues.groupBy { it.eventId } }

    fun isInlineOwner(next: WorkbenchTool): Boolean =
        next == WorkbenchTool.TEXT || next == WorkbenchTool.EFFECTS || next == WorkbenchTool.EVENT

    fun openTool(next: WorkbenchTool) {
        if (isInlineOwner(next)) {
            toolName = next.name
            supportingOpen = true
            return
        }
        workspaceState = workspaceState.openPrimary(next.name).withSurfacesHidden(false)
        surfaceController.bringToFront(WorkspaceState.primaryInstanceId(next.name))
    }

    fun toggleTool(next: WorkbenchTool) {
        if (isInlineOwner(next)) {
            toolName = next.name
            supportingOpen = true
            return
        }
        val primary = workspaceState.primary(next.name)
        workspaceState = if (primary != null) {
            workspaceState.closeInstance(primary.id)
        } else {
            workspaceState.openPrimary(next.name)
        }.withSurfacesHidden(false)
        if (primary == null) {
            surfaceController.bringToFront(WorkspaceState.primaryInstanceId(next.name))
        }
    }

    fun toggleAllSurfaces() {
        workspaceState = workspaceState.withSurfacesHidden(!workspaceState.surfacesHidden)
    }

    MaterialTheme(colorScheme = workbenchCol…52923 tokens truncated…, modifier: Modifier = Modifier) {
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

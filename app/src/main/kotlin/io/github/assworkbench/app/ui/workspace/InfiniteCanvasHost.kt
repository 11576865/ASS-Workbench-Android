package io.github.assworkbench.app.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

internal data class InfiniteCanvasEntry(
    val id: String, val title: String, val subtitle: String = "",
    val canClose: Boolean = false,
    val canDuplicate: Boolean = false,
    val canBindEvent: Boolean = false,
    val pinnedEvent: Boolean = false,
    val focusEventId: Long? = null,
    val initiallyHidden: Boolean = false,
    val bookmarked: Boolean = false,
)

/**
 * The board and the editor are different interaction layers:
 * - camera transforms world placement but never scales native control density;
 * - zoomed-out nodes are summaries, close-up nodes can host live tools;
 * - an explicitly focused tool is hosted at native screen density.
 * No pan/zoom or scene operation is routed through ASS document history.
 */
@Composable
internal fun InfiniteCanvasHost(
    sessionId: Long,
    savedScene: List<String>,
    onSaveScene: (List<String>) -> Unit,
    entries: List<InfiniteCanvasEntry>,
    gestureOwned: Boolean,
    onAddTool: () -> Unit,
    onActivate: (String) -> Unit,
    onCloseTool: (String) -> Unit = {},
    onDuplicateTool: (String, Boolean) -> Unit = { _, _ -> },
    onToggleEventBinding: (String) -> Unit = {},
    onToggleBookmark: (String) -> Unit = {},
    renderTimeline: @Composable (Boolean) -> Unit = {},
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    requestedActiveToolId: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable (String, Boolean) -> Unit,
) {
    val saver = listSaver<Pair<InfiniteCanvasCamera, List<InfiniteCanvasNode>>, String>(
        save = { InfiniteCanvasPersistence.encode(it.first, it.second) },
        restore = { InfiniteCanvasPersistence.decode(it) },
    )
    var scene by rememberSaveable(sessionId, stateSaver = saver) {
        mutableStateOf(
            if (savedScene.isEmpty()) InfiniteCanvasCamera(16f, 60f, 0.85f) to emptyList()
            else InfiniteCanvasPersistence.decode(savedScene),
        )
    }
    SideEffect { onSaveScene(InfiniteCanvasPersistence.encode(scene.first, scene.second)) }
    var focusedId by rememberSaveable(sessionId) {
        mutableStateOf<String?>(if (savedScene.isEmpty()) "preview" else null)
    }
    var birdseyeOpen by rememberSaveable(sessionId) { mutableStateOf(false) }
    var toolDrawerOpen by rememberSaveable(sessionId) { mutableStateOf(false) }
    var toolDrawerResident by rememberSaveable(sessionId) { mutableStateOf(false) }
    var recallOpen by remember(sessionId) { mutableStateOf(false) }
    var arrangeOpen by remember(sessionId) { mutableStateOf(false) }
    var switcherOpen by remember(sessionId) { mutableStateOf(false) }
    var timelineDockVisible by rememberSaveable(sessionId) { mutableStateOf(false) }
    var timelineDockExpanded by rememberSaveable(sessionId) { mutableStateOf(false) }
    var referencePreviewVisible by rememberSaveable(sessionId) { mutableStateOf(true) }
    var referencePreviewSize by rememberSaveable(sessionId) { mutableIntStateOf(1) }
    var cardMenuId by remember(sessionId) { mutableStateOf<String?>(null) }
    // A directory selection may asynchronously create a ToolInstance. Carry the
    // invoking set of IDs until the new production tool is actually available.
    var pendingToolSelection by remember(sessionId) { mutableStateOf<Set<String>?>(null) }
    var previouslyPresentEntries by remember(sessionId) { mutableStateOf<Set<String>?>(null) }
    val contentState = rememberSaveableStateHolder()
    val density = LocalDensity.current.density
    val active by rememberUpdatedState(onActivate)

    LaunchedEffect(sessionId, entries.map { it.id to it.initiallyHidden }, requestedActiveToolId) {
        val existing = scene.second.mapTo(mutableSetOf()) { it.id }
        val missing = entries.filterNot { it.id in existing }
        if (missing.isNotEmpty()) {
            val oldMaxZ = scene.second.maxOfOrNull { it.z } ?: 3
            val additions = missing.mapIndexed { i, item ->
                when (item.id) {
                    "preview" -> InfiniteCanvasNode(item.id, 40f, 30f, 760f, 480f, z = 1)
                    "subtitles" -> InfiniteCanvasNode(item.id, 850f, 30f, 420f, 500f, z = 2)
                    "audio" -> InfiniteCanvasNode(item.id, 80f, 570f, 650f, 190f, z = 3, alpha = 0.2f)
                    else -> InfiniteCanvasNode(item.id, 140f + i * 52f, 140f + i * 52f,
                        400f, 430f, z = oldMaxZ + i + 1, hidden = item.initiallyHidden)
                }
            }
            scene = scene.first to (scene.second + additions)
        }
        val beforePicker = pendingToolSelection
        var pickerOpenedId: String? = null
        if (beforePicker != null) {
            // Newly created and already-existing tools must both be reachable
            // from the directory without returning to a scaled overview.
            val selected = requestedActiveToolId?.let { id ->
                entries.firstOrNull { it.id == id && it.id != "CAPABILITIES:primary" }
            }
            val added = entries.lastOrNull {
                it.id !in beforePicker && it.id != "CAPABILITIES:primary"
            }
            (selected ?: added)?.let { opened ->
                // Opening a previously hidden primary is a genuine reveal.
                // Do not leave the domain instance hidden while its editor is
                // apparently active on screen.
                pickerOpenedId = opened.id
                focusedId = opened.id
                pendingToolSelection = null
                if (opened.initiallyHidden) active(opened.id)
            }
        }
        val ids = entries.mapTo(mutableSetOf()) { it.id }
        // Only a previously live identity disappearing is a close. During
        // restore the WorkspaceState may hydrate AFTER the saved canvas scene;
        // never prune those nodes merely because this first frame lacks tools.
        val closedIds = (previouslyPresentEntries ?: emptySet()) - ids
        if (closedIds.isNotEmpty() || entries.any { entry ->
                entry.initiallyHidden && scene.second.any { it.id == entry.id && !it.hidden }
            }) {
            scene = scene.first to scene.second.mapNotNull { node ->
                if (node.id in closedIds) null
                else if (entries.any { it.id == node.id && it.initiallyHidden })
                    node.copy(hidden = true)
                else node
            }
        }
        previouslyPresentEntries = ids
        // Picker navigation wins over the previous HIDDEN presentation flag,
        // even when domain-presence updates asynchronously in the parent.
        pickerOpenedId?.let { id ->
            scene = scene.first to scene.second.map { node ->
                if (node.id == id) node.copy(hidden = false) else node
            }
        }
        if (focusedId != null && entries.none { it.id == focusedId } &&
            focusedId != "CAPABILITIES:primary") focusedId = null
    }

    fun updateNode(node: InfiniteCanvasNode) {
        scene = scene.first to scene.second.map { if (it.id == node.id) node else it }
    }
    fun showToolPicker() {
        if (gestureOwned) return
        pendingToolSelection = entries.mapTo(mutableSetOf()) { it.id }
        scene = scene.first to scene.second.map {
            if (it.id == "CAPABILITIES:primary") it.copy(hidden = false) else it
        }
        // A tool directory is a real native editor, never a 0.85x summary card.
        focusedId = "CAPABILITIES:primary"
        onAddTool()
    }

    BoxWithConstraints(modifier.clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        val dockHeight = if (!timelineDockVisible) 0.dp
            else if (timelineDockExpanded) maxHeight * 0.42f else 142.dp
        val viewportW = maxWidth.value
        val viewportH = (maxHeight - dockHeight).value
        val railWidth = if (viewportW < 480f) 88.dp else 112.dp
        val camera = scene.first
        val nodes = scene.second
        val focused = focusedId?.let { id ->
            entries.firstOrNull { it.id == id }?.let { entry ->
                nodes.firstOrNull { it.id == entry.id }?.let { entry to it }
            }
        }
        val boardMode = focused == null
        BackHandler(enabled = !gestureOwned && focused != null && !birdseyeOpen) {
            focusedId = null
            if (toolDrawerResident) toolDrawerOpen = true
            pendingToolSelection = null
        }
        BackHandler(enabled = !gestureOwned && boardMode && toolDrawerOpen && !birdseyeOpen) {
            toolDrawerOpen = false
            toolDrawerResident = false
        }

        fun focus(id: String) {
            if (gestureOwned) return
            val node = scene.second.firstOrNull { it.id == id } ?: return
            if (entries.none { it.id == id }) return
            // Focus must not silently resize a world node. It only changes
            // camera observation and reveals a hidden surface.
            val nextScene = canvasCameraForNode(node, viewportW, viewportH) to
                scene.second.map { if (it.id == id) it.copy(hidden = false) else it }
            scene = nextScene
            // Publish the newly recalled geometry before activating another tool
            // or dismissing a dialog can recompose the workspace controller.
            onSaveScene(InfiniteCanvasPersistence.encode(nextScene.first, nextScene.second))
            focusedId = id
            // A deliberate recall or birdseye navigation cancels an abandoned
            // directory transaction. Future unrelated ToolInstance changes must
            // never steal navigation focus.
            pendingToolSelection = null
            cardMenuId = null
            toolDrawerOpen = toolDrawerResident
            active(id)
        }
        fun overview() {
            if (gestureOwned) return
            val shown = scene.second.filter { node ->
                !node.hidden && entries.any { it.id == node.id }
            }
            val fitted = fitCanvasCamera(shown, viewportW, viewportH)
            if (fitted != null) {
                scene = fitted to scene.second
            } else if (shown.isNotEmpty()) {
                // A scene spanning extreme coordinates cannot truthfully fit at
                // the camera's minimum zoom; birdseye remains fully reachable.
                birdseyeOpen = true
            }
            focusedId = null
        }
        fun returnToBoard() {
            if (gestureOwned) return
            focusedId = null
            if (toolDrawerResident) toolDrawerOpen = true
            pendingToolSelection = null
        }
        LaunchedEffect(boardMode, toolDrawerResident) {
            if (boardMode && toolDrawerResident) toolDrawerOpen = true
        }
        fun arrange(columns: Int) {
            if (gestureOwned) return
            val changed = arrangeCanvasNodes(scene.second, columns)
            scene = scene.first to changed
            focusedId = null
            val shown = changed.filter { !it.hidden && entries.any { entry -> entry.id == it.id } }
            val fitted = fitCanvasCamera(shown, viewportW, viewportH)
            if (fitted != null) scene = fitted to changed
            else if (shown.isNotEmpty()) birdseyeOpen = true
            arrangeOpen = false
        }

        if (birdseyeOpen) CanvasOverviewDialog(
            entries = entries, nodes = nodes, enabled = !gestureOwned,
            onDismiss = { birdseyeOpen = false },
            onFocus = { id -> focus(id); if (!gestureOwned) birdseyeOpen = false },
        )

        // Spatial summaries are used when zoomed out; close-up tools render at native density.
        if (boardMode) {
            Box(Modifier.fillMaxSize().padding(bottom = dockHeight).testTag("spatial-background")
                .pointerInput(sessionId, gestureOwned) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (!gestureOwned) {
                            val c = scene.first
                            scene = c.zoomAt(centroid.x / density, centroid.y / density,
                                c.scale * zoom).pan(pan.x / density, pan.y / density) to scene.second
                        }
                    }
                }) {
                Canvas(Modifier.fillMaxSize().testTag("spatial-grid")) {
                    val step = 96.dp.toPx() * camera.scale
                    if (step >= 8f) {
                        val startX = ((camera.x * density) % step + step) % step
                        val startY = ((camera.y * density) % step + step) % step
                        val lineColor = Color.Gray.copy(alpha = 0.12f)
                        var x = startX
                        while (x < size.width) {
                            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1f)
                            x += step
                        }
                        var y = startY
                        while (y < size.height) {
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f)
                            y += step
                        }
                    }
                }
            }
            UnboundedBoardLayer(Modifier.fillMaxSize().padding(bottom = dockHeight).testTag("spatial-world")) {
                entries.forEach { entry ->
                    val saved = nodes.firstOrNull { it.id == entry.id } ?: return@forEach
                    if (!saved.hidden && !entry.bookmarked) key(entry.id) {
                        var moving by remember { mutableStateOf<InfiniteCanvasNode?>(null) }
                        val node = moving ?: saved
                        val liveNode by rememberUpdatedState(node)
                        val scale by rememberUpdatedState(camera.scale)
                        val width = (node.width * camera.scale).coerceAtLeast(72f)
                        val height = (node.height * camera.scale).coerceAtLeast(112f)
                        Surface(
                            modifier = Modifier.offset(
                                (camera.x + node.x * camera.scale).dp,
                                (camera.y + node.y * camera.scale).dp,
                            ).requiredSize(width.dp, height.dp)
                                .graphicsLayer(alpha = node.alpha)
                                .zIndex(node.z.toFloat())
                                .testTag("spatial-node-" + entry.id.replace(':', '-')),
                            shape = MaterialTheme.shapes.large,
                            color = if (node.alpha < 1f) Color.Transparent
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 3.dp,
                            shadowElevation = 2.dp,
                        ) {
                            Column {
                                Row(Modifier.fillMaxWidth().height(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Row(Modifier.weight(1f).fillMaxHeight()
                                        .testTag("spatial-drag-" + entry.id.replace(':', '-'))
                                        .pointerInput(entry.id, gestureOwned, node.layoutLocked) {
                                            detectDragGestures(
                                                onDragStart = { if (!gestureOwned && !liveNode.layoutLocked) moving = liveNode },
                                                onDragEnd = {
                                                    if (!gestureOwned) moving?.let(::updateNode)
                                                    moving = null
                                                },
                                                onDragCancel = { moving = null },
                                                onDrag = { change, delta ->
                                                    if (!gestureOwned && !liveNode.layoutLocked) {
                                                        change.consume()
                                                        moving = liveNode.move(
                                                            delta.x / density / scale,
                                                            delta.y / density / scale,
                                                        )
                                                    }
                                                },
                                            )
                                        }.padding(start = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (node.layoutLocked) Icons.Filled.Lock else Icons.Filled.DragIndicator,
                                            if (node.layoutLocked) "已锁定布局" else "拖动工具",
                                            Modifier.size(18.dp),
                                        )
                                        if (width >= 148f) Text(entry.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1)
                                    }
                                    Box {
                                        IconButton(onClick = { cardMenuId = entry.id },
                                            enabled = !gestureOwned,
                                            modifier = Modifier.testTag("spatial-menu-" + entry.id.replace(':', '-'))) {
                                            Icon(Icons.Filled.MoreHoriz, "管理 " + entry.title)
                                        }
                                        DropdownMenu(
                                            expanded = cardMenuId == entry.id,
                                            onDismissRequest = { cardMenuId = null },
                                        ) {
                                            DropdownMenuItem(text = { Text("打开编辑器") },
                                                onClick = { focus(entry.id) })
                                            DropdownMenuItem(text = { Text("提升层级") },
                                                onClick = {
                                                    scene = scene.first to raiseCanvasNode(scene.second, entry.id)
                                                    cardMenuId = null
                                                })
                                            if (entry.canClose) DropdownMenuItem(
                                                text = { Text(if (entry.bookmarked) "取消侧书签" else "放入侧书签") },
                                                modifier = Modifier.testTag("spatial-bookmark-" + entry.id.replace(':', '-')),
                                                onClick = { onToggleBookmark(entry.id); cardMenuId = null },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (node.layoutLocked) "解除布局锁" else "锁定布局") },
                                                onClick = {
                                                    updateNode(node.copy(layoutLocked = !node.layoutLocked))
                                                    cardMenuId = null
                                                },
                                                modifier = Modifier.testTag("spatial-layout-lock-" + entry.id.replace(':', '-')),
                                            )
                                            if (entry.canBindEvent) DropdownMenuItem(
                                                text = { Text(if (entry.pinnedEvent) "解除对象固定（跟随焦点）"
                                                    else "固定读取对象 #" + entry.focusEventId) },
                                                enabled = entry.pinnedEvent || entry.focusEventId != null,
                                                onClick = { onToggleEventBinding(entry.id); cardMenuId = null },
                                            )
                                            if (entry.canDuplicate) {
                                                DropdownMenuItem(text = { Text("复制工具（保留绑定）") }, onClick = {
                                                    pendingToolSelection = entries.mapTo(mutableSetOf()) { it.id }
                                                    onDuplicateTool(entry.id, false)
                                                    cardMenuId = null
                                                })
                                                DropdownMenuItem(text = { Text("复制工具（跟随焦点）") }, onClick = {
                                                    pendingToolSelection = entries.mapTo(mutableSetOf()) { it.id }
                                                    onDuplicateTool(entry.id, true)
                                                    cardMenuId = null
                                                })
                                            }
                                            if (entry.canClose) DropdownMenuItem(text = { Text("关闭工具实例") },
                                                modifier = Modifier.testTag("spatial-close-" + entry.id.replace(':', '-')),
                                                onClick = {
                                                    onCloseTool(entry.id)
                                                    cardMenuId = null
                                                    pendingToolSelection = null
                                                })
                                            DropdownMenuItem(
                                                text = { Text("透明度 " + (node.alpha * 100).toInt() + "% · 调整") },
                                                modifier = Modifier.testTag("spatial-opacity-" + entry.id.replace(':', '-')),
                                                onClick = {
                                                    updateNode(node.copy(alpha = nextCanvasOpacity(node.alpha)))
                                                    cardMenuId = null
                                                },
                                            )
                                            DropdownMenuItem(
                                    text = { Text("透明度 " + (node.alpha * 100).toInt() + "% · 调整") },
                                    modifier = Modifier.testTag("spatial-opacity-" + entry.id.replace(':', '-')),
                                    onClick = {
                                        updateNode(node.copy(alpha = nextCanvasOpacity(node.alpha)))
                                        cardMenuId = null
                                    },
                                )
                                DropdownMenuItem(text = { Text("收回工具") },
                                                onClick = { updateNode(node.copy(hidden = true)); cardMenuId = null })
                                            DropdownMenuItem(
                                                text = { Text(if (node.alpha < 1f) "恢复实底" else "透明叠加") },
                                                onClick = {
                                                    updateNode(node.copy(alpha = if (node.alpha < 1f) 1f else 0.2f))
                                                    cardMenuId = null
                                                })
                                            if (entry.id == "audio") DropdownMenuItem(
                                                text = { Text(if (node.passthrough) "操作波形" else "穿透操作视频") },
                                                onClick = {
                                                    updateNode(node.copy(passthrough = !node.passthrough))
                                                    cardMenuId = null
                                                })
                                        }
                                    }
                                }
                                val compact = camera.scale < 0.95f
                                val mediaNode = entry.id == "preview" || entry.id == "audio"
                                if (!mediaNode) {
                                    // Retain the *same* editor composition while a board node
                                    // changes LOD. A compact card merely masks drawing and input;
                                    // it must not destroy uncommitted non-saveable drafts.
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        Box(Modifier
                                            .then(if (compact) Modifier.requiredSize(
                                                node.width.dp, (node.height - 92f).coerceAtLeast(160f).dp,
                                            ) else Modifier.fillMaxSize())
                                            .alpha(if (compact) 0f else 1f)
                                            .then(if (compact) Modifier.clearAndSetSemantics { } else Modifier)
                                            .testTag("spatial-live-" + entry.id.replace(':', '-'))) {
                                            contentState.SaveableStateProvider(sessionId.toString() + "/" + entry.id) {
                                                content(entry.id, !node.passthrough)
                                            }
                                        }
                                        if (compact) CanvasToolSummary(entry, width, height,
                                            enabled = !gestureOwned, onOpen = { focus(entry.id) })
                                    }
                                } else if (!compact &&
                                    shouldComposeCanvasTool(camera, node, viewportW, viewportH)) {
                                    Box(Modifier.weight(1f).fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = node.alpha))
                                        .testTag("spatial-live-" + entry.id.replace(':', '-'))) {
                                        contentState.SaveableStateProvider(sessionId.toString() + "/" + entry.id) {
                                            content(entry.id, !node.passthrough)
                                        }
                                    }
                                } else {
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        CanvasToolSummary(entry, width, height,
                                            enabled = !gestureOwned, onOpen = { focus(entry.id) })
                                    }
                                }
                                Icon(Icons.Filled.OpenInFull, "调整 " + entry.title + " 大小",
                                    Modifier.align(Alignment.End).size(44.dp)
                                        .testTag("spatial-resize-" + entry.id.replace(':', '-'))
                                        .pointerInput(entry.id, gestureOwned, node.layoutLocked) {
                                            detectDragGestures(
                                                onDragStart = { if (!gestureOwned && !liveNode.layoutLocked) moving = liveNode },
                                                onDragEnd = {
                                                    if (!gestureOwned) moving?.let(::updateNode)
                                                    moving = null
                                                },
                                                onDragCancel = { moving = null },
                                                onDrag = { change, delta ->
                                                    if (!gestureOwned && !liveNode.layoutLocked) {
                                                        change.consume()
                                                        moving = liveNode.resize(
                                                            delta.x / density / scale,
                                                            delta.y / density / scale,
                                                        )
                                                    }
                                                },
                                            )
                                        }.padding(12.dp))
                            }
                        }
                    }
                }
            }
            // Reusable tool access does not require travelling across world space.
            Surface(Modifier.align(Alignment.BottomCenter)
                .padding(bottom = dockHeight + 8.dp, start = 8.dp, end = 8.dp)
                .zIndex(1_000_010f), shape = MaterialTheme.shapes.large, shadowElevation = 3.dp) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // Keep the only creation affordance inside the visible viewport.
                    // Instance tabs may scroll without pushing the directory off-screen.
                    TextButton(onClick = ::showToolPicker, enabled = !gestureOwned,
                        modifier = Modifier.testTag("spatial-add-tool")) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Text("工具")
                    }
                    VerticalDivider(Modifier.height(26.dp).padding(horizontal = 4.dp))
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically) {
                        entries.forEach { entry ->
                            val node = nodes.firstOrNull { it.id == entry.id }
                            TextButton(
                                enabled = node != null && !gestureOwned,
                                onClick = { focus(entry.id) },
                                modifier = Modifier.testTag("spatial-quick-" + entry.id.replace(':', '-')),
                            ) {
                                Icon(if (node?.hidden == true) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Tab, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(entry.title, maxLines = 1)
                            }
                        }
                    }
                }
            }
            // Compact discoverability handle; the drawer is an optional list, not a second editor.
            if (!toolDrawerOpen) FilledTonalIconButton(
                onClick = { toolDrawerOpen = true },
                modifier = Modifier.align(Alignment.CenterStart).zIndex(1_000_012f)
                    .testTag("spatial-edge-handle-left"),
            ) { Icon(Icons.Filled.ChevronRight, "展开工具目录") }

        } else {
            // No camera transform reaches this subtree: editors keep native-size hit areas.
            val (entry, node) = focused!!
            Surface(Modifier.fillMaxSize()
                .padding(bottom = dockHeight)
                .padding(start = if (toolDrawerOpen && toolDrawerResident) railWidth else 0.dp)
                .testTag("spatial-focused-editor"),
                color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = ::returnToBoard, enabled = !gestureOwned,
                            modifier = Modifier.testTag("spatial-return-to-board")) {
                            Icon(Icons.Filled.ArrowBack, null, Modifier.size(18.dp))
                            Text("画布")
                        }
                        Box(Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth()
                                .clickable(enabled = !gestureOwned) { switcherOpen = true }
                                .testTag("spatial-tool-switcher")) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.title, style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1)
                                    Icon(Icons.Filled.ArrowDropDown, "切换工具", Modifier.size(18.dp))
                                }
                                if (entry.subtitle.isNotBlank()) Text(entry.subtitle,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            DropdownMenu(expanded = switcherOpen,
                                onDismissRequest = { switcherOpen = false }) {
                                entries.forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text((if (target.id == entry.id) "✓ " else "") + target.title) },
                                        enabled = !gestureOwned,
                                        modifier = Modifier.testTag(
                                            "spatial-switch-to-" + target.id.replace(':', '-')),
                                        onClick = {
                                            focus(target.id)
                                            switcherOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { timelineDockVisible = !timelineDockVisible },
                            enabled = !gestureOwned, modifier = Modifier.testTag("spatial-timeline-toggle")) {
                            Icon(Icons.Filled.ViewTimeline, if (timelineDockVisible) "收起时间轴" else "展开时间轴")
                        }
                        IconButton(onClick = ::showToolPicker, enabled = !gestureOwned,
                            modifier = Modifier.testTag("spatial-add-tool")) {
                            Icon(Icons.Filled.Add, "添加工具")
                        }
                        IconButton(onClick = { birdseyeOpen = true }, enabled = !gestureOwned,
                            modifier = Modifier.testTag("spatial-birdseye")) {
                            Icon(Icons.Filled.Apps, "切换工具")
                        }
                        Box {
                            IconButton(onClick = { cardMenuId = entry.id }, enabled = !gestureOwned,
                                modifier = Modifier.testTag("spatial-menu-" + entry.id.replace(':', '-'))) {
                                Icon(Icons.Filled.MoreHoriz, "管理 " + entry.title)
                            }
                            DropdownMenu(expanded = cardMenuId == entry.id,
                                onDismissRequest = { cardMenuId = null }) {
                                if (viewportW < 460f) {
                                    DropdownMenuItem(text = { Text("撤销字幕编辑") }, enabled = canUndo,
                                        onClick = { onUndo(); cardMenuId = null })
                                    DropdownMenuItem(text = { Text("重做字幕编辑") }, enabled = canRedo,
                                        onClick = { onRedo(); cardMenuId = null })
                                    HorizontalDivider()
                                }
                                if (entry.canClose) DropdownMenuItem(
                                    text = { Text(if (entry.bookmarked) "取消侧书签" else "放入侧书签") },
                                    modifier = Modifier.testTag("spatial-bookmark-" + entry.id.replace(':', '-')),
                                    onClick = { onToggleBookmark(entry.id); cardMenuId = null },
                                )
                                DropdownMenuItem(text = { Text(if (node.layoutLocked) "解除布局锁" else "锁定布局") },
                                    modifier = Modifier.testTag("spatial-layout-lock-" + entry.id.replace(':', '-')),
                                    onClick = {
                                        updateNode(node.copy(layoutLocked = !node.layoutLocked))
                                        cardMenuId = null
                                    })
                                if (entry.canBindEvent) DropdownMenuItem(
                                    text = { Text(if (entry.pinnedEvent) "解除对象固定（跟随焦点）"
                                        else "固定读取对象 #" + entry.focusEventId) },
                                    enabled = entry.pinnedEvent || entry.focusEventId != null,
                                    onClick = { onToggleEventBinding(entry.id); cardMenuId = null })
                                if (entry.canDuplicate) {
                                    DropdownMenuItem(text = { Text("复制工具（保留绑定）") }, onClick = {
                                        pendingToolSelection = entries.mapTo(mutableSetOf()) { it.id }
                                        onDuplicateTool(entry.id, false)
                                        cardMenuId = null
                                    })
                                    DropdownMenuItem(text = { Text("复制工具（跟随焦点）") }, onClick = {
                                        pendingToolSelection = entries.mapTo(mutableSetOf()) { it.id }
                                        onDuplicateTool(entry.id, true)
                                        cardMenuId = null
                                    })
                                }
                                if (entry.canClose) DropdownMenuItem(text = { Text("关闭工具实例") },
                                    modifier = Modifier.testTag("spatial-close-" + entry.id.replace(':', '-')),
                                    onClick = {
                                        onCloseTool(entry.id)
                                        focusedId = null
                                        cardMenuId = null
                                        pendingToolSelection = null
                                    })
                                DropdownMenuItem(text = { Text("收回工具") }, onClick = {
                                    updateNode(node.copy(hidden = true))
                                    focusedId = null
                                    cardMenuId = null
                                })
                                DropdownMenuItem(text = { Text(if (node.alpha < 1f) "恢复实底" else "透明叠加") },
                                    onClick = {
                                        updateNode(node.copy(alpha = if (node.alpha < 1f) 1f else 0.2f))
                                        cardMenuId = null
                                    })
                                if (entry.id == "audio") DropdownMenuItem(
                                    text = { Text(if (node.passthrough) "操作波形" else "穿透操作视频") },
                                    onClick = {
                                        updateNode(node.copy(passthrough = !node.passthrough))
                                        cardMenuId = null
                                    })
                            }
                        }
                        if (viewportW >= 460f) {
                            IconButton(onClick = onUndo, enabled = canUndo) {
                                Icon(Icons.Filled.Undo, "撤销字幕编辑")
                            }
                            IconButton(onClick = onRedo, enabled = canRedo) {
                                Icon(Icons.Filled.Redo, "重做字幕编辑")
                            }
                        }
                    }
                    HorizontalDivider()
                    val liveNode by rememberUpdatedState(node)
                    val liveNodes by rememberUpdatedState(nodes)
                    val liveContent by rememberUpdatedState(content)
                    // Preserve ordinary remember() drafts while a pane is moved
                    // between split and fullscreen layouts (or resized).
                    val productionEditor = remember(entry.id, sessionId) {
                        movableContentOf<Modifier> { paneModifier ->
                        Box(paneModifier.testTag("spatial-native-content-" + entry.id.replace(':', '-'))) {
                            val preview = liveNodes.firstOrNull { it.id == "preview" && !it.hidden }
                            val audio = liveNodes.firstOrNull { it.id == "audio" && !it.hidden }
                            // Audio/video is a real layered editing surface, not a flattened card.
                            // Keep actual renderer and waveform on their existing clock and callbacks.
                            if (entry.id == "audio" && preview != null) {
                                contentState.SaveableStateProvider(sessionId.toString() + "/preview") {
                                    liveContent("preview", true)
                                }
                                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .height(liveNode.height.coerceIn(160f, 460f).dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = liveNode.alpha))) {
                                    contentState.SaveableStateProvider(sessionId.toString() + "/audio") {
                                        liveContent("audio", !liveNode.passthrough)
                                    }
                                }
                            } else {
                                contentState.SaveableStateProvider(sessionId.toString() + "/" + entry.id) {
                                    liveContent(entry.id, !liveNode.passthrough)
                                }
                                if (entry.id == "preview" && audio != null) {
                                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                        .height(audio.height.coerceIn(160f, 460f).dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = audio.alpha))) {
                                        contentState.SaveableStateProvider(sessionId.toString() + "/audio") {
                                            liveContent("audio", !audio.passthrough)
                                        }
                                    }
                                }
                            }
                        
                        }
                        }
                    }
                    val reference = nodes.firstOrNull { it.id == "preview" && !it.hidden }
                    val showReferenceOption = entry.id != "preview" && entry.id != "audio" && reference != null
                    // Give the text/parameter editor the full available height
                    // while the soft keyboard is visible. The user's split choice
                    // remains intact and automatically returns after IME closes.
                    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    val referenceFraction = when (referencePreviewSize) {
                        0 -> 0.25f
                        2 -> 0.54f
                        else -> 0.40f
                    }
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        if (showReferenceOption) Row(
                            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { referencePreviewVisible = !referencePreviewVisible },
                                enabled = !gestureOwned,
                                modifier = Modifier.testTag("spatial-preview-toggle"),
                            ) {
                                Icon(if (referencePreviewVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (referencePreviewVisible) "隐藏参考画面" else "显示参考画面")
                            }
                            if (referencePreviewVisible) TextButton(
                                onClick = { referencePreviewSize = (referencePreviewSize + 1) % 3 },
                                enabled = !gestureOwned,
                                modifier = Modifier.testTag("spatial-preview-size"),
                            ) { Text(when (referencePreviewSize) {
                                0 -> "预览 25%"
                                2 -> "预览 54%"
                                else -> "预览 40%"
                            }) }
                        }
                        if (showReferenceOption && referencePreviewVisible && !imeVisible) {
                            // The reference is the real video/ASS renderer and shares the
                            // current media clock and document focus with this editor.
                            // SaveableStateProvider keys are unique within the stage.
                            val referencePane: @Composable (Modifier) -> Unit = { paneModifier ->
                                Box(paneModifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .testTag("spatial-reference-preview")) {
                                    contentState.SaveableStateProvider(sessionId.toString() + "/preview") {
                                        content("preview", true)
                                    }
                                }
                            }
                            if (viewportW >= 840f) {
                                Row(Modifier.fillMaxSize()) {
                                    referencePane(Modifier.weight(referenceFraction).fillMaxHeight())
                                    VerticalDivider()
                                    productionEditor(Modifier.weight(1f - referenceFraction).fillMaxHeight())
                                }
                            } else {
                                Column(Modifier.fillMaxSize()) {
                                    referencePane(Modifier.fillMaxWidth().fillMaxHeight(referenceFraction))
                                    HorizontalDivider()
                                    productionEditor(Modifier.weight(1f).fillMaxWidth())
                                }
                            }
                        } else {
                            productionEditor(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }

        // A resident tool directory is a shared overlay, not a board-only
        // surface: pinning it keeps tool switching available while editing.
            if (toolDrawerOpen) Surface(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight()
                    .padding(bottom = dockHeight).width(railWidth)
                    .zIndex(1_000_020f).testTag("spatial-edge-rail-left"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { toolDrawerResident = !toolDrawerResident },
                            modifier = Modifier.size(40.dp).testTag("spatial-edge-pin-left")) {
                            Icon(Icons.Filled.PushPin, if (toolDrawerResident) "取消驻留" else "驻留")
                        }
                        IconButton(onClick = { toolDrawerOpen = false; toolDrawerResident = false },
                            modifier = Modifier.size(40.dp).testTag("spatial-edge-close-left")) {
                            Icon(Icons.Filled.ChevronLeft, "收起目录")
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        entries.forEach { entry ->
                            TextButton(onClick = { focus(entry.id) },
                                enabled = !gestureOwned,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("spatial-edge-entry-" + entry.id.replace(':', '-'))) {
                                Text(entry.title, maxLines = 1)
                            }
                        }
                    }
                    IconButton(onClick = ::showToolPicker,
                        modifier = Modifier.testTag("spatial-edge-add-tool")) {
                        Icon(Icons.Filled.Add, "添加工具")
                    }
                }
            }

        // Side bookmarks are live ToolInstance identities, not cloned editors.
        // The world node remains saved while BOOKMARKED, so recall is reversible.
        val bookmarked = entries.filter { it.bookmarked }
        if (bookmarked.isNotEmpty()) Surface(
            Modifier.align(Alignment.CenterEnd).padding(bottom = dockHeight).width(68.dp)
                .zIndex(1_000_022f).testTag("spatial-bookmark-rail"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                bookmarked.forEach { item ->
                    TextButton(onClick = { focus(item.id) }, enabled = !gestureOwned,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("spatial-bookmark-open-" + item.id.replace(':', '-'))) {
                        Text(item.title.take(4), maxLines = 2)
                    }
                }
            }
        }

        // The timeline is a persistent *real* editor surface owned by this
        // workspace, not a separate presentation mode or a detached media clock.
        if (timelineDockVisible) Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(dockHeight)
                .zIndex(1_000_040f).testTag("spatial-timeline-dock"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 5.dp,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DragHandle, null, Modifier.size(18.dp))
                    Text("时间轴", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { timelineDockExpanded = !timelineDockExpanded },
                        modifier = Modifier.testTag("spatial-timeline-expand")) {
                        Text(if (timelineDockExpanded) "紧凑" else "展开")
                    }
                    IconButton(onClick = { timelineDockVisible = false },
                        modifier = Modifier.testTag("spatial-timeline-close")) {
                        Icon(Icons.Filled.Close, "关闭常驻时间轴")
                    }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth().testTag("spatial-timeline-content")) {
                    renderTimeline(!timelineDockExpanded)
                }
            }
        }

        // Workspace actions remain separate from ASS document Undo/Redo.
        if (boardMode) Surface(Modifier.align(Alignment.TopCenter).padding(8.dp).zIndex(1_000_025f),
            shape = MaterialTheme.shapes.large, shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (boardMode) {
                    Text("空间", Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = ::overview, enabled = !gestureOwned,
                        modifier = Modifier.testTag("spatial-overview")) { Text("总览") }
                    TextButton(onClick = { timelineDockVisible = !timelineDockVisible },
                        enabled = !gestureOwned, modifier = Modifier.testTag("spatial-timeline-toggle")) {
                        Text(if (timelineDockVisible) "收起时间轴" else "时间轴")
                    }
                    Box {
                        TextButton(onClick = { arrangeOpen = true }, enabled = !gestureOwned,
                            modifier = Modifier.testTag("spatial-arrange")) { Text("整理") }
                        DropdownMenu(expanded = arrangeOpen, onDismissRequest = { arrangeOpen = false }) {
                            DropdownMenuItem(text = { Text("纵向排列") },
                                onClick = { arrange(1) }, modifier = Modifier.testTag("spatial-arrange-column"))
                            DropdownMenuItem(text = { Text("两列排列") },
                                onClick = { arrange(2) }, modifier = Modifier.testTag("spatial-arrange-two"))
                        }
                    }
                    TextButton(onClick = { birdseyeOpen = true }, enabled = !gestureOwned,
                        modifier = Modifier.testTag("spatial-birdseye")) { Text("鸟瞰") }
                    Box {
                        TextButton(onClick = { recallOpen = true }, enabled = !gestureOwned) { Text("召回") }
                        DropdownMenu(expanded = recallOpen, onDismissRequest = { recallOpen = false }) {
                            entries.forEach { entry ->
                                DropdownMenuItem(text = { Text(entry.title) },
                                    enabled = !gestureOwned,
                                    modifier = Modifier.testTag("spatial-recall-" + entry.id.replace(':', '-')),
                                    onClick = { focus(entry.id); recallOpen = false })
                            }
                        }
                    }
                    IconButton(enabled = !gestureOwned,
                        onClick = {
                            scene = scene.first.zoomAt(viewportW / 2f, viewportH / 2f,
                                scene.first.scale / 1.2f) to scene.second
                        }, modifier = Modifier.testTag("spatial-zoom-out")) {
                        Icon(Icons.Filled.Remove, "缩小画布")
                    }
                    Text((camera.scale * 100f).toInt().toString() + "%",
                        style = MaterialTheme.typography.labelSmall)
                    IconButton(enabled = !gestureOwned,
                        onClick = {
                            scene = scene.first.zoomAt(viewportW / 2f, viewportH / 2f,
                                scene.first.scale * 1.2f) to scene.second
                        }, modifier = Modifier.testTag("spatial-zoom-in")) {
                        Icon(Icons.Filled.Add, "放大画布")
                    }
                }
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.Filled.Undo, "撤销字幕编辑")
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.Filled.Redo, "重做字幕编辑")
                }
            }
        }
    }
}

/** Compact LOD visual; the native editor remains independent of camera scale. */
@Composable
private fun CanvasToolSummary(
    entry: InfiniteCanvasEntry,
    width: Float,
    height: Float,
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    Box(Modifier.fillMaxSize().clickable(enabled = enabled, onClick = onOpen)
        .testTag("spatial-open-" + entry.id.replace(':', '-'))) {
        Column(Modifier.align(Alignment.Center).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                when (entry.id) {
                    "preview" -> Icons.Filled.Movie
                    "subtitles" -> Icons.Filled.Subtitles
                    "audio" -> Icons.Filled.GraphicEq
                    else -> Icons.Filled.Tune
                },
                contentDescription = "打开 " + entry.title,
                tint = MaterialTheme.colorScheme.primary,
            )
            if (width >= 148f && height >= 140f) {
                Spacer(Modifier.height(6.dp))
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                if (entry.subtitle.isNotBlank()) Text(entry.subtitle,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/** Measure summary cards without constraining their world size to the phone viewport. */
@Composable
private fun UnboundedBoardLayer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val unbounded = Constraints(0, Constraints.Infinity, 0, Constraints.Infinity)
        val children = measurables.map { it.measure(unbounded) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            children.forEach { it.place(0, 0) }
        }
    }
}

package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

internal data class InfiniteCanvasEntry(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val pinLabel: String? = null,
    val onTogglePin: (() -> Unit)? = null,
    val onDuplicate: (() -> Unit)? = null,
    val onDuplicateFollowFocus: (() -> Unit)? = null,
    val onClose: (() -> Unit)? = null,
)

internal data class InfiniteCanvasToolChoice(val key: String, val title: String)


/**
 * Keeps the viewport finite while measuring world-space surfaces without inheriting
 * the viewport's max width/height. The parent still clips drawing to the viewport.
 */
@Composable
private fun UnboundedCanvasLayer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val unbounded = Constraints(
            minWidth = 0,
            maxWidth = Constraints.Infinity,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        )
        val placeables = measurables.map { it.measure(unbounded) }
        val viewportWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val viewportHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        layout(viewportWidth, viewportHeight) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

/** World surfaces are measured in screen dp, so text and hit areas do not shrink with camera zoom. */
@Composable
internal fun InfiniteCanvasHost(
    sessionId: Long,
    savedScene: List<String>,
    onSaveScene: (List<String>) -> Unit,
    entries: List<InfiniteCanvasEntry>,
    gestureOwned: Boolean,
    onAddTool: () -> Unit,
    onActivate: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    toolChoices: List<InfiniteCanvasToolChoice> = emptyList(),
    onOpenTool: (String) -> Unit = {},
    openRequest: InfiniteCanvasOpenRequest? = null,
    retainedEntryIds: Set<String> = entries.map { it.id }.toSet(),
    modifier: Modifier = Modifier,
    content: @Composable (String, Boolean) -> Unit,
) {
    val saver = listSaver<Pair<InfiniteCanvasCamera, List<InfiniteCanvasNode>>, String>(
        save = { InfiniteCanvasPersistence.encode(it.first, it.second) },
        restore = { InfiniteCanvasPersistence.decode(it) },
    )
    var scene by rememberSaveable(sessionId, stateSaver = saver) {
        mutableStateOf(if (savedScene.isEmpty()) InfiniteCanvasCamera(16f, 60f, 0.85f) to emptyList<InfiniteCanvasNode>()
            else InfiniteCanvasPersistence.decode(savedScene))
    }
    SideEffect { onSaveScene(InfiniteCanvasPersistence.encode(scene.first, scene.second)) }
    var detailedId by rememberSaveable(sessionId) { mutableStateOf<String?>(null) }
    var recall by remember(sessionId) { mutableStateOf(false) }
    var addMenu by remember(sessionId) { mutableStateOf(false) }
    var handledOpenRevision by remember(sessionId) { mutableStateOf<Long?>(null) }
    val camera = scene.first
    val nodes = scene.second
    val density = LocalDensity.current.density
    val activeCallback by rememberUpdatedState(onActivate)
    val captured by rememberUpdatedState(gestureOwned)
    LaunchedEffect(entries.map { it.id }, retainedEntryIds) {
        val retained = retainCanvasNodes(scene.second, retainedEntryIds)
        if (retained != scene.second) scene = scene.first to retained
        val missing = entries.filter { e -> scene.second.none { it.id == e.id } }
        if (missing.isNotEmpty()) {
            val added = missing.mapIndexed { i, e ->
                when (e.id) {
                    "preview" -> InfiniteCanvasNode(e.id, 40f, 30f, 760f, 480f, z = 1)
                    "subtitles" -> InfiniteCanvasNode(e.id, 850f, 30f, 420f, 500f, z = 2)
                    "audio" -> InfiniteCanvasNode(e.id, 80f, 380f, 650f, 190f, z = 3, alpha = 0.2f)
                    else -> InfiniteCanvasNode(e.id,
                        (40f - scene.first.x) / scene.first.scale + 100f + i * 28f,
                        (100f - scene.first.y) / scene.first.scale + i * 28f,
                        400f, 430f, z = (scene.second.maxOfOrNull { it.z } ?: 3) + i + 1)
                }
            }
            scene = scene.first to (scene.second + added)
        }
    }
    fun update(node: InfiniteCanvasNode) {
        scene = scene.first to scene.second.map { if (it.id == node.id) node else it }
    }
    BoxWithConstraints(modifier.clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        val viewportW = maxWidth.value
        val viewportH = maxHeight.value
        fun focus(node: InfiniteCanvasNode) {
            if (captured) return
            detailedId = node.id
            val (nextCamera, shown) = approachCanvasNode(node, viewportW, viewportH)
            scene = nextCamera to scene.second.map { if (it.id == node.id) shown else it }
            activeCallback(node.id)
        }
        LaunchedEffect(openRequest, nodes.map { it.id }, gestureOwned) {
            if (openRequest != null && openRequest.revision != handledOpenRevision && !captured) {
                scene.second.firstOrNull { it.id == openRequest.id }?.let {
                    focus(it)
                    handledOpenRevision = openRequest.revision
                }
            }
        }
        fun overview() {
            detailedId = null
            val shown = scene.second.filter { !it.hidden && entries.any { e -> e.id == it.id } }
            if (shown.isEmpty()) return
            val left = shown.minOf { it.x }; val top = shown.minOf { it.y }
            val width = shown.maxOf { it.x + it.width } - left
            val height = shown.maxOf { it.y + it.height } - top
            val scale = minOf(1f, (viewportW - 32f) / width, (viewportH - 96f) / height).coerceIn(0.25f, 2f)
            scene = InfiniteCanvasCamera(16f - left * scale, 64f - top * scale, scale) to scene.second
        }
        Box(Modifier.fillMaxSize().testTag("spatial-background").pointerInput(sessionId) {
            detectTransformGestures { center, pan, zoom, _ ->
                if (!captured) {
                    val c = scene.first
                    scene = c.zoomAt(center.x / density, center.y / density, c.scale * zoom)
                        .pan(pan.x / density, pan.y / density) to scene.second
                }
            }
        })
        UnboundedCanvasLayer(Modifier.fillMaxSize().testTag("spatial-world")) {
            entries.forEach { entry ->
                val savedNode = nodes.firstOrNull { it.id == entry.id } ?: return@forEach
                if (!savedNode.hidden) key(entry.id) {
                    var candidate by remember { mutableStateOf<InfiniteCanvasNode?>(null) }
                    val node = candidate ?: savedNode
                    var menu by remember { mutableStateOf(false) }
                    val latest by rememberUpdatedState(node)
                    val zoom by rememberUpdatedState(scene.first.scale)
                    Column(
                        Modifier.offset((camera.x + node.x * camera.scale).dp, (camera.y + node.y * camera.scale).dp)
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize((node.width * camera.scale).dp, (node.height * camera.scale).dp)
                            .zIndex(node.z.toFloat()).testTag("spatial-node-" + entry.id.replace(':', '-')),
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).fillMaxHeight()
                                    .testTag("spatial-drag-" + entry.id.replace(':', '-'))
                                    .pointerInput(entry.id, node.layoutLocked) {
                                        detectDragGestures(
                                            onDragStart = { if (!captured && !latest.layoutLocked) { candidate = latest; activeCallback(entry.id) } },
                                            onDragEnd = { if (!captured && !latest.layoutLocked) candidate?.let(::update); candidate = null },
                                            onDragCancel = { candidate = null },
                                            onDrag = { change, delta ->
                                                if (!captured && !latest.layoutLocked) {
                                                    change.consume()
                                                    candidate = latest.move(delta.x / density / zoom, delta.y / density / zoom)
                                                }
                                            })
                                    }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.DragIndicator, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column(Modifier.padding(start = 6.dp)) {
                                        Text(entry.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                        if (entry.subtitle.isNotBlank()) Text(entry.subtitle, modifier = Modifier.testTag("spatial-binding-" + entry.id.replace(':', '-')), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }
                                Box {
                                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("spatial-menu-" + entry.id.replace(':', '-'))) {
                                        Icon(Icons.Filled.MoreHoriz, "管理 " + entry.title)
                                    }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        val tagId = entry.id.replace(':', '-')
                                        DropdownMenuItem(
                                            text = { Text(if (node.layoutLocked) "解锁布局" else "锁定布局") },
                                            enabled = !gestureOwned,
                                            modifier = Modifier.testTag("spatial-lock-$tagId"),
                                            onClick = { update(node.copy(layoutLocked = !node.layoutLocked)); menu = false },
                                        )
                                        entry.onTogglePin?.let { action ->
                                            DropdownMenuItem(text = { Text(entry.pinLabel.orEmpty()) }, enabled = !gestureOwned,
                                                modifier = Modifier.testTag("spatial-pin-$tagId"),
                                                onClick = { menu = false; action() })
                                        }
                                        entry.onDuplicate?.let { action ->
                                            DropdownMenuItem(text = { Text("复制当前绑定") }, enabled = !gestureOwned,
                                                modifier = Modifier.testTag("spatial-duplicate-$tagId"),
                                                onClick = { menu = false; action() })
                                        }
                                        entry.onDuplicateFollowFocus?.let { action ->
                                            DropdownMenuItem(text = { Text("复制并跟随焦点") }, enabled = !gestureOwned,
                                                modifier = Modifier.testTag("spatial-duplicate-follow-$tagId"),
                                                onClick = { menu = false; action() })
                                        }
                                        entry.onClose?.let { action ->
                                            DropdownMenuItem(text = { Text("关闭工具") }, enabled = !gestureOwned,
                                                modifier = Modifier.testTag("spatial-close-$tagId"),
                                                onClick = { menu = false; action() })
                                        }
                                        DropdownMenuItem(text = { Text("置顶") }, enabled = !gestureOwned, onClick = {
                                            scene = scene.first to raiseCanvasNode(scene.second, entry.id); menu = false
                                        })
                                        DropdownMenuItem(text = { Text("靠近 / 展开") }, enabled = !gestureOwned, onClick = { focus(node); menu = false })
                                        DropdownMenuItem(text = { Text("收回") }, enabled = !gestureOwned, modifier = Modifier.testTag("spatial-hide-" + entry.id.replace(':', '-')), onClick = { update(node.copy(hidden = true)); menu = false })
                                        DropdownMenuItem(enabled = !gestureOwned, text = { Text(if (node.alpha < 1f) "恢复实底" else "透明叠加") }, onClick = {
                                            update(node.copy(alpha = if (node.alpha < 1f) 1f else 0.2f)); menu = false
                                        })
                                        Text("背景透明度", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                                        Slider(value = node.alpha, enabled = !gestureOwned, onValueChange = { update(latest.copy(alpha = it)) },
                                            modifier = Modifier.width(220.dp).padding(horizontal = 16.dp))
                                        if (entry.id == "audio") DropdownMenuItem(
                                            enabled = !gestureOwned, text = { Text(if (node.passthrough) "操作波形" else "穿透操作视频") },
                                            onClick = { update(node.copy(passthrough = !node.passthrough)); menu = false })
                                    }
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()
                            .then(if (entry.id == "audio") Modifier else Modifier.pointerInput(entry.id) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        if (!captured) activeCallback(entry.id)
                                        do { val event = awaitPointerEvent(PointerEventPass.Initial) }
                                        while (event.changes.any { it.pressed })
                                    }
                                }
                            })
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = node.alpha))) {
                            if (!showCanvasContent(camera.scale, entry.id, detailedId)) {
                                TextButton(onClick = { focus(node) }, modifier = Modifier.align(Alignment.Center)) { Text("靠近 " + entry.title) }
                            } else {
                                content(entry.id, !node.passthrough)
                            }
                            Icon(Icons.Filled.OpenInFull, "调整 " + entry.title + " 大小",
                                Modifier.align(Alignment.BottomEnd).size(44.dp).testTag("spatial-resize-" + entry.id.replace(':', '-'))
                                    .pointerInput(entry.id, node.layoutLocked) {
                                        detectDragGestures(
                                            onDragStart = { if (!captured && !latest.layoutLocked) candidate = latest },
                                            onDragEnd = { if (!captured && !latest.layoutLocked) candidate?.let(::update); candidate = null },
                                            onDragCancel = { candidate = null },
                                            onDrag = { change, delta ->
                                                if (!captured && !latest.layoutLocked) {
                                                    change.consume()
                                                    candidate = latest.resize(delta.x / density / zoom, delta.y / density / zoom)
                                                }
                                            })
                                    }.padding(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Surface(Modifier.align(Alignment.TopCenter).padding(8.dp).zIndex(1_000_010f),
            shape = MaterialTheme.shapes.large, shadowElevation = 2.dp) {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(enabled = !gestureOwned, modifier = Modifier.testTag("spatial-add-tool"), onClick = {
                        if (toolChoices.isEmpty()) onAddTool() else addMenu = true
                    }) { Text("＋ 工具") }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        toolChoices.forEach { choice ->
                            DropdownMenuItem(text = { Text(choice.title) }, enabled = !gestureOwned,
                                modifier = Modifier.testTag("spatial-open-${choice.key}"),
                                onClick = { addMenu = false; onOpenTool(choice.key) })
                        }
                    }
                }
                TextButton(onClick = ::overview, enabled = !gestureOwned, modifier = Modifier.testTag("spatial-overview")) { Text("总览") }
                Box {
                    TextButton(onClick = { recall = true }) { Text("召回") }
                    DropdownMenu(expanded = recall, onDismissRequest = { recall = false }) {
                        entries.forEach { entry ->
                            DropdownMenuItem(text = { Text(entry.title) },
                                modifier = Modifier.testTag("spatial-recall-" + entry.id.replace(':', '-')), enabled = !gestureOwned, onClick = {
                                scene.second.firstOrNull { it.id == entry.id }?.let { focus(it) }; recall = false
                            })
                        }
                    }
                }
                IconButton(onClick = onUndo, enabled = canUndo && !gestureOwned) { Icon(Icons.Filled.Undo, "撤销字幕编辑") }
                IconButton(onClick = onRedo, enabled = canRedo && !gestureOwned) { Icon(Icons.Filled.Redo, "重做字幕编辑") }
                IconButton(enabled = !gestureOwned, onClick = { scene = camera.zoomAt(viewportW / 2, viewportH / 2, camera.scale / 1.2f) to scene.second },
                    modifier = Modifier.testTag("spatial-zoom-out")) { Icon(Icons.Filled.Remove, "缩小工作区") }
                Text("${(camera.scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                IconButton(enabled = !gestureOwned, onClick = { scene = camera.zoomAt(viewportW / 2, viewportH / 2, camera.scale * 1.2f) to scene.second },
                    modifier = Modifier.testTag("spatial-zoom-in")) { Icon(Icons.Filled.Add, "放大工作区") }
            }
        }
    }
}

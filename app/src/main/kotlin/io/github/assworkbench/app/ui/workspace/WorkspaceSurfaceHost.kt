package io.github.assworkbench.app.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
internal fun FloatingWorkbenchSurface(
    id: String,
    title: String,
    visible: Boolean,
    controller: WorkbenchSurfaceController,
    initialOffset: Offset,
    initialGeometry: SurfaceGeometry? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    testTagId: String = id,
    bindingLabel: String? = null,
    bindingPinned: Boolean = false,
    onActivate: () -> Unit = {},
    onToggleBinding: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    tabTitles: List<Pair<String, String>> = emptyList(),
    onSelectTab: (String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().zIndex(controller.z(id))) {
        val density = LocalDensity.current.density
        val viewportWidth = maxWidth.value
        val viewportHeight = maxHeight.value
        val fallback = initialGeometry ?: SurfaceGeometry(initialOffset.x / density, initialOffset.y / density)
        val state = controller.state(id, fallback)
        val baseGeometry = state.geometry.inViewport(viewportWidth, viewportHeight)
        val geometry = when (state.dock) {
            SurfaceDock.FLOATING -> baseGeometry
            SurfaceDock.LEFT -> SurfaceGeometry(0f, 0f, baseGeometry.width.coerceAtMost(viewportWidth * 0.42f), viewportHeight)
            SurfaceDock.RIGHT -> SurfaceGeometry(
                (viewportWidth - baseGeometry.width.coerceAtMost(viewportWidth * 0.42f)).coerceAtLeast(0f),
                0f,
                baseGeometry.width.coerceAtMost(viewportWidth * 0.42f),
                viewportHeight,
            )
            SurfaceDock.BOTTOM -> SurfaceGeometry(
                0f,
                (viewportHeight - baseGeometry.height.coerceAtMost(viewportHeight * 0.45f)).coerceAtLeast(0f),
                viewportWidth,
                baseGeometry.height.coerceAtMost(viewportHeight * 0.45f),
            )
        }.let { if (state.minimized) it.copy(height = 58f, width = it.width.coerceAtMost(340f)) else it }
        val candidate = remember(controller, id) { controller.candidate(id) }
        var resizing by remember { mutableStateOf(false) }
        val activate by rememberUpdatedState(onActivate)
        LaunchedEffect(id, visible) {
            if (visible) controller.ensure(id, fallback)
            else {
                controller.cancel(id)
                resizing = false
            }
        }
        DisposableEffect(controller, id) { onDispose { controller.cancel(id) } }

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.zIndex(controller.z(id))) {
            Surface(
                modifier = Modifier
                    .testTag("surface-$testTagId")
                    .graphicsLayer {
                        val position = if (resizing || state.dock != SurfaceDock.FLOATING) geometry else candidate.value ?: geometry
                        translationX = position.x * density
                        translationY = position.y * density
                    }
                    .pointerInput(id, controller) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                controller.bringToFront(id, fallback)
                                activate()
                                do { val event = awaitPointerEvent(PointerEventPass.Initial) }
                                while (event.changes.any { it.pressed })
                            }
                        }
                    }
                    .size(geometry.width.dp, geometry.height.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().testTag("surface-drag-$testTagId")
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .pointerInput(id, state.layoutLocked, state.dock, state.minimized, geometry, density) {
                                if (!state.layoutLocked && !state.minimized && state.dock == SurfaceDock.FLOATING) {
                                    detectDragGestures(
                                        onDragStart = {
                                            controller.begin(id, fallback, geometry)
                                            controller.bringToFront(id, fallback)
                                            activate()
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            controller.moveBy(id, delta.x / density, delta.y / density, viewportWidth, viewportHeight)
                                        },
                                        onDragEnd = { controller.commit(id, fallback) },
                                        onDragCancel = { controller.cancel(id) },
                                    )
                                }
                            }.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (state.dock == SurfaceDock.FLOATING) Icons.Filled.DragIndicator else Icons.Filled.Dock,
                            contentDescription = null)
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            bindingLabel?.let {
                                Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { controller.toggleMinimized(id, fallback) },
                            modifier = Modifier.testTag("surface-minimize-$testTagId")) {
                            Icon(if (state.minimized) Icons.Filled.ExpandMore else Icons.Filled.Minimize,
                                contentDescription = if (state.minimized) "展开 $title" else "最小化 $title")
                        }
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "收回 $title") }
                    }

                    if (!state.minimized) {
                        if (tabTitles.size > 1) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                tabTitles.forEach { (tabId, tabTitle) ->
                                    FilterChip(
                                        selected = tabId == id,
                                        onClick = { onSelectTab(tabId) },
                                        label = { Text(tabTitle, maxLines = 1) },
                                        modifier = Modifier.padding(horizontal = 2.dp).testTag("surface-tab-" + tabId.replace(':', '-')),
                                    )
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            onToggleBinding?.let {
                                IconButton(onClick = it) {
                                    Icon(if (bindingPinned) Icons.Filled.PushPin else Icons.Filled.Link,
                                        contentDescription = if (bindingPinned) "解除对象固定" else "固定到当前字幕")
                                }
                            }
                            onDuplicate?.let {
                                IconButton(onClick = it) { Icon(Icons.Filled.ContentCopy, contentDescription = "复制工具实例") }
                            }
                            IconButton(onClick = { controller.cycleDock(id, fallback) },
                                modifier = Modifier.testTag("surface-dock-$testTagId")) {
                                Icon(Icons.Filled.ViewSidebar, contentDescription = "切换停靠位置")
                            }
                            if (state.tabGroupId == null) {
                                IconButton(onClick = { controller.stackWithFront(id, fallback) },
                                    modifier = Modifier.testTag("surface-stack-$testTagId")) {
                                    Icon(Icons.Filled.Tab, contentDescription = "与前一个工具叠为标签组")
                                }
                            } else {
                                IconButton(onClick = { controller.unstack(id, fallback) }) {
                                    Icon(Icons.Filled.CallSplit, contentDescription = "移出标签组")
                                }
                            }
                            IconButton(onClick = { controller.toggleLayoutLock(id, fallback) },
                                modifier = Modifier.testTag("surface-lock-$testTagId")) {
                                Icon(if (state.layoutLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                    contentDescription = if (state.layoutLocked) "解除布局锁定" else "锁定窗口布局")
                            }
                            IconButton(
                                onClick = { controller.cycleSize(id, fallback) },
                                enabled = !state.layoutLocked && state.dock == SurfaceDock.FLOATING,
                            ) { Icon(Icons.Filled.OpenInFull, contentDescription = "切换浮层尺寸") }
                            Text(state.dock.label + " · " + if (state.layoutLocked) "布局锁定" else state.sizeClass.label,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            content()
                            if (!state.layoutLocked && state.dock == SurfaceDock.FLOATING) {
                                Icon(Icons.Filled.OpenInFull, contentDescription = "拖动调整窗口大小",
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                        .testTag("surface-resize-$testTagId")
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .size(48.dp)
                                        .pointerInput(id, geometry, density) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    resizing = true
                                                    controller.begin(id, fallback, geometry, resize = true)
                                                },
                                                onDrag = { change, delta ->
                                                    change.consume()
                                                    controller.resizeBy(id, delta.x / density, delta.y / density, viewportWidth, viewportHeight)
                                                },
                                                onDragEnd = {
                                                    controller.commit(id, fallback)
                                                    resizing = false
                                                },
                                                onDragCancel = {
                                                    controller.cancel(id)
                                                    resizing = false
                                                },
                                            )
                                        }.padding(10.dp))
                            }
                        }
                    }
                }
            }
        }

        if (resizing && visible && state.dock == SurfaceDock.FLOATING) {
            val outline = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize().zIndex(controller.z(id) + 0.5f)) {
                candidate.value?.let {
                    drawRect(outline, Offset(it.x * density, it.y * density),
                        Size(it.width * density, it.height * density), style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}

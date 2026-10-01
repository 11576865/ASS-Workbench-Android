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

/** A tool-independent floating presentation. Domain and binding remain with its content owner. */
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
    stackTabs: List<Pair<String, String>> = emptyList(),
    stackCandidates: List<Pair<String, String>> = emptyList(),
    onSelectStackTab: ((String) -> Unit)? = null,
    onStackWith: ((String) -> Unit)? = null,
    onUnstack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().zIndex(controller.z(id))) {
        val density = LocalDensity.current.density
        val viewportWidth = maxWidth.value
        val viewportHeight = maxHeight.value
        val fallback = initialGeometry ?: SurfaceGeometry(initialOffset.x / density, initialOffset.y / density)
        val state = controller.state(id, fallback)
        val floatingGeometry = state.geometry.inViewport(viewportWidth, viewportHeight)
        val dockWidth = floatingGeometry.width.coerceIn(
            280f.coerceAtMost(viewportWidth),
            480f.coerceAtMost(viewportWidth),
        )
        val geometry = when (state.presentation) {
            SurfacePresentation.FLOATING -> floatingGeometry
            SurfacePresentation.DOCK_LEFT -> SurfaceGeometry(
                x = 0f, y = 0f, width = dockWidth, height = viewportHeight
            )
            SurfacePresentation.DOCK_RIGHT -> SurfaceGeometry(
                x = (viewportWidth - dockWidth).coerceAtLeast(0f),
                y = 0f,
                width = dockWidth,
                height = viewportHeight,
            )
            SurfacePresentation.MINIMIZED -> SurfaceGeometry(
                x = floatingGeometry.x,
                y = floatingGeometry.y,
                width = floatingGeometry.width.coerceAtLeast(260f).coerceAtMost(viewportWidth),
                height = 58f.coerceAtMost(viewportHeight),
            ).inViewport(viewportWidth, viewportHeight)
        }
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

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.zIndex(controller.z(id))) {
            Surface(
                modifier = Modifier
                    .testTag("surface-$testTagId")
                    .graphicsLayer {
                        val position = if (resizing) geometry else candidate.value ?: geometry
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
                            .pointerInput(id, state.layoutLocked, state.presentation, geometry, density) {
                                if (!state.layoutLocked && state.presentation == SurfacePresentation.FLOATING) detectDragGestures(
                                    onDragStart = {
                                        controller.begin(id, fallback, geometry)
                                        controller.bringToFront(id, fallback)
                                        activate()
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        controller.moveBy(id, delta.x / density, delta.y / density,
                                            viewportWidth, viewportHeight)
                                    },
                                    onDragEnd = { controller.commit(id, fallback) },
                                    onDragCancel = { controller.cancel(id) },
                                )
                            }.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.DragIndicator, contentDescription = null)
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge)
                            bindingLabel?.let {
                                Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(
                            onClick = { controller.toggleMinimize(id, fallback) },
                            modifier = Modifier.testTag("surface-minimize-$testTagId"),
                        ) {
                            Icon(
                                if (state.presentation == SurfacePresentation.MINIMIZED) Icons.Filled.OpenInFull
                                else Icons.Filled.Minimize,
                                contentDescription = if (state.presentation == SurfacePresentation.MINIMIZED) "恢复 $title" else "最小化 $title",
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "收回 $title")
                        }
                    }
                    if (state.presentation != SurfacePresentation.MINIMIZED && stackTabs.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            stackTabs.forEach { (tabId, tabTitle) ->
                                FilterChip(
                                    selected = tabId == id,
                                    onClick = { onSelectStackTab?.invoke(tabId) },
                                    label = { Text(tabTitle, maxLines = 1) },
                                    modifier = Modifier.testTag("surface-tab-" + tabId.replace(':', '-')),
                                )
                            }
                        }
                    }
                    if (state.presentation != SurfacePresentation.MINIMIZED) {
                    // Actions remain reachable even in the compact presentation.
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically) {
                        onToggleBinding?.let {
                            IconButton(onClick = it) {
                                Icon(if (bindingPinned) Icons.Filled.PushPin else Icons.Filled.Link,
                                    contentDescription = if (bindingPinned) "解除对象固定" else "固定到当前字幕")
                            }
                        }
                        onDuplicate?.let {
                            IconButton(onClick = it) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "复制工具实例")
                            }
                        }
                        IconButton(onClick = { controller.toggleLayoutLock(id, fallback) },
                            modifier = Modifier.testTag("surface-lock-$testTagId")) {
                            Icon(if (state.layoutLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = if (state.layoutLocked) "解除布局锁定" else "锁定窗口布局")
                        }
                        IconButton(onClick = { controller.cycleSize(id, fallback) }, enabled = !state.layoutLocked) {
                            Icon(Icons.Filled.OpenInFull, contentDescription = "切换浮层尺寸")
                        }
                        TextButton(onClick = {
                            when (state.presentation) {
                                SurfacePresentation.DOCK_LEFT, SurfacePresentation.DOCK_RIGHT ->
                                    controller.undock(id, fallback)
                                else -> controller.dock(id, SurfacePresentation.DOCK_LEFT, fallback)
                            }
                        }) { Text(if (state.presentation == SurfacePresentation.DOCK_LEFT) "浮动" else "左停靠") }
                        TextButton(onClick = {
                            when (state.presentation) {
                                SurfacePresentation.DOCK_LEFT, SurfacePresentation.DOCK_RIGHT ->
                                    controller.undock(id, fallback)
                                else -> controller.dock(id, SurfacePresentation.DOCK_RIGHT, fallback)
                            }
                        }) { Text(if (state.presentation == SurfacePresentation.DOCK_RIGHT) "浮动" else "右停靠") }
                        if (state.stackId != null) {
                            TextButton(onClick = { onUnstack?.invoke() }) { Text("拆栈") }
                        } else if (stackCandidates.isNotEmpty()) {
                            var stackMenuOpen by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { stackMenuOpen = true }) { Text("叠放…") }
                                DropdownMenu(expanded = stackMenuOpen, onDismissRequest = { stackMenuOpen = false }) {
                                    stackCandidates.forEach { (candidateId, candidateTitle) ->
                                        DropdownMenuItem(
                                            text = { Text(candidateTitle) },
                                            onClick = {
                                                stackMenuOpen = false
                                                onStackWith?.invoke(candidateId)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Text(if (state.layoutLocked) "布局已锁定" else state.sizeClass.label,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        content()
                        if (!state.layoutLocked && state.presentation == SurfacePresentation.FLOATING) {
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
                                                controller.resizeBy(id, delta.x / density, delta.y / density,
                                                    viewportWidth, viewportHeight)
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
        // Only this outline redraws during resize; the tool remeasures once on release.
        if (resizing && visible) {
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

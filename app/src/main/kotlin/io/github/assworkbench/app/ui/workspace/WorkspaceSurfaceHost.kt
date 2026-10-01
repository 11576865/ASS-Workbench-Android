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
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().zIndex(controller.z(id))) {
        val density = LocalDensity.current.density
        val viewportWidth = maxWidth.value
        val viewportHeight = maxHeight.value
        val fallback = initialGeometry ?: SurfaceGeometry(initialOffset.x / density, initialOffset.y / density)
        val state = controller.state(id, fallback)
        val baseGeometry = state.geometry.inViewport(viewportWidth, viewportHeight)
        val geometry = when (state.placement) {
            SurfacePlacement.FLOATING -> baseGeometry
            SurfacePlacement.DOCK_LEFT -> SurfaceGeometry(
                x = 0f,
                y = 0f,
                width = minOf(360f, viewportWidth),
                height = viewportHeight,
            ).inViewport(viewportWidth, viewportHeight)
            SurfacePlacement.DOCK_RIGHT -> {
                val width = minOf(360f, viewportWidth)
                SurfaceGeometry(
                    x = (viewportWidth - width).coerceAtLeast(0f),
                    y = 0f,
                    width = width,
                    height = viewportHeight,
                ).inViewport(viewportWidth, viewportHeight)
            }
            SurfacePlacement.DOCK_BOTTOM -> {
                val height = minOf(320f, viewportHeight)
                SurfaceGeometry(
                    x = 0f,
                    y = (viewportHeight - height).coerceAtLeast(0f),
                    width = viewportWidth,
                    height = height,
                ).inViewport(viewportWidth, viewportHeight)
            }
            SurfacePlacement.MINIMIZED -> SurfaceGeometry(
                x = baseGeometry.x,
                y = baseGeometry.y,
                width = minOf(320f, viewportWidth),
                height = 58f,
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
                            .pointerInput(id, state.layoutLocked, state.placement, geometry, density) {
                                if (!state.layoutLocked && state.placement == SurfacePlacement.FLOATING) detectDragGestures(
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
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "收回 $title")
                        }
                    }
                    val stackMembers = controller.stackMembers(id)
                    if (stackMembers.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            stackMembers.forEach { memberId ->
                                TextButton(onClick = { controller.bringToFront(memberId) }) {
                                    Text(
                                        if (memberId == id) "● " + memberId.substringBefore(':')
                                        else memberId.substringBefore(':'),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
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
                        TextButton(
                            onClick = { controller.cyclePlacement(id, fallback) },
                            modifier = Modifier.testTag("surface-placement-$testTagId"),
                        ) {
                            Text(
                                when (state.placement) {
                                    SurfacePlacement.FLOATING -> "浮动"
                                    SurfacePlacement.DOCK_LEFT -> "左停靠"
                                    SurfacePlacement.DOCK_RIGHT -> "右停靠"
                                    SurfacePlacement.DOCK_BOTTOM -> "底停靠"
                                    SurfacePlacement.MINIMIZED -> "最小化"
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                if (state.stackId == null) controller.stackWithFrontmost(id, fallback)
                                else controller.unstack(id, fallback)
                            },
                            modifier = Modifier.testTag("surface-stack-$testTagId"),
                        ) {
                            Icon(
                                if (state.stackId == null) Icons.Filled.Layers else Icons.Filled.LayersClear,
                                contentDescription = if (state.stackId == null) "与最近工具组成 Tab Stack" else "移出 Tab Stack",
                            )
                        }
                        IconButton(onClick = { controller.toggleLayoutLock(id, fallback) },
                            modifier = Modifier.testTag("surface-lock-$testTagId")) {
                            Icon(if (state.layoutLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = if (state.layoutLocked) "解除布局锁定" else "锁定窗口布局")
                        }
                        IconButton(onClick = { controller.cycleSize(id, fallback) }, enabled = !state.layoutLocked) {
                            Icon(Icons.Filled.OpenInFull, contentDescription = "切换浮层尺寸")
                        }
                        Text(if (state.layoutLocked) "布局已锁定" else state.sizeClass.label,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (state.placement != SurfacePlacement.MINIMIZED) content()
                        if (!state.layoutLocked && state.placement == SurfacePlacement.FLOATING) {
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

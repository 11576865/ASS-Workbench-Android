package io.github.assworkbench.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

internal enum class PreviewWorkspaceMode(val label: String) {
    NORMAL("普通"),
    FOCUS("聚焦"),
    MANIPULATION("操控"),
    FLOATING("浮窗"),
}

internal enum class FloatingSurfaceSize {
    COMPACT,
    EXPANDED,
    PRECISION,
}

@Stable
internal class WorkbenchSurfaceController {
    private val offsets = mutableStateMapOf<String, Offset>()
    private val sizes = mutableStateMapOf<String, FloatingSurfaceSize>()
    private val zOrders = mutableStateMapOf<String, Int>()
    private var nextZ by mutableIntStateOf(10)

    fun offset(id: String, fallback: Offset): Offset = offsets[id] ?: fallback

    fun moveBy(id: String, delta: Offset, fallback: Offset) {
        offsets[id] = offset(id, fallback) + delta
        bringToFront(id)
    }

    fun resetOffset(id: String) {
        offsets.remove(id)
    }

    fun resetAll() {
        offsets.clear()
        sizes.clear()
        zOrders.clear()
        nextZ = 10
    }

    fun size(id: String): FloatingSurfaceSize = sizes[id] ?: FloatingSurfaceSize.EXPANDED

    fun cycleSize(id: String) {
        sizes[id] = when (size(id)) {
            FloatingSurfaceSize.COMPACT -> FloatingSurfaceSize.EXPANDED
            FloatingSurfaceSize.EXPANDED -> FloatingSurfaceSize.PRECISION
            FloatingSurfaceSize.PRECISION -> FloatingSurfaceSize.COMPACT
        }
        bringToFront(id)
    }

    fun z(id: String): Float = (zOrders[id] ?: 1).toFloat()

    fun bringToFront(id: String) {
        zOrders[id] = ++nextZ
    }
}

@Composable
internal fun rememberWorkbenchSurfaceController(): WorkbenchSurfaceController =
    remember { WorkbenchSurfaceController() }

@Composable
internal fun FloatingWorkbenchSurface(
    id: String,
    title: String,
    visible: Boolean,
    controller: WorkbenchSurfaceController,
    initialOffset: Offset,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxW = maxWidth
        val maxH = maxHeight
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
            modifier = Modifier.zIndex(controller.z(id)),
        ) {
            val surfaceSize = controller.size(id)
            val desiredWidth = when (surfaceSize) {
                FloatingSurfaceSize.COMPACT -> 286.dp
                FloatingSurfaceSize.EXPANDED -> 374.dp
                FloatingSurfaceSize.PRECISION -> 486.dp
            }
            val desiredHeight = when (surfaceSize) {
                FloatingSurfaceSize.COMPACT -> 210.dp
                FloatingSurfaceSize.EXPANDED -> 430.dp
                FloatingSurfaceSize.PRECISION -> 590.dp
            }
            val actualWidth = minOf(desiredWidth, (maxW - 16.dp).coerceAtLeast(220.dp))
            val actualHeight = minOf(desiredHeight, (maxH - 16.dp).coerceAtLeast(160.dp))
            val fallback = initialOffset
            val offset = controller.offset(id, fallback)

            Surface(
                modifier = Modifier
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .widthIn(min = minOf(220.dp, actualWidth), max = actualWidth)
                    .heightIn(min = minOf(160.dp, actualHeight), max = actualHeight)
                    .animateContentSize(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column {
                    Row(
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .pointerInput(id) {
                                detectDragGestures(
                                    onDragStart = { controller.bringToFront(id) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        controller.moveBy(id, dragAmount, fallback)
                                    },
                                )
                            }
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.DragIndicator,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        )
                        IconButton(onClick = { controller.cycleSize(id) }) {
                            Icon(
                                when (surfaceSize) {
                                    FloatingSurfaceSize.COMPACT -> Icons.Filled.UnfoldMore
                                    FloatingSurfaceSize.EXPANDED -> Icons.Filled.OpenInFull
                                    FloatingSurfaceSize.PRECISION -> Icons.Filled.UnfoldLess
                                },
                                contentDescription = "切换浮层尺寸",
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "收回 $title")
                        }
                    }
                    Box(Modifier.weight(1f, fill = true)) {
                        content()
                    }
                }
            }
        }
    }
}

internal data class InteractionProxySpec(
    val id: String,
    val label: String,
    val targetInWindow: Offset,
    val preferredOffsetPx: Offset,
    val onDragDelta: (Offset) -> Unit,
    val onCommit: () -> Unit,
    val onCancel: () -> Unit,
)

@Stable
internal class InteractionOverlayRegistry {
    private val owners = mutableStateMapOf<String, List<InteractionProxySpec>>()
    var activeHandleId by mutableStateOf<String?>(null)
        private set

    val handles: List<InteractionProxySpec>
        get() = owners.values.flatten()

    fun publish(owner: String, handles: List<InteractionProxySpec>) {
        owners[owner] = handles
    }

    fun clear(owner: String) {
        owners.remove(owner)
        if (activeHandleId?.startsWith(owner) == true) activeHandleId = null
    }

    fun setActive(id: String?) {
        activeHandleId = id
    }
}

@Composable
internal fun rememberInteractionOverlayRegistry(): InteractionOverlayRegistry =
    remember { InteractionOverlayRegistry() }

@Composable
internal fun ClearInteractionOwnerOnDispose(
    registry: InteractionOverlayRegistry?,
    owner: String,
) {
    DisposableEffect(registry, owner) {
        onDispose { registry?.clear(owner) }
    }
}

@Composable
internal fun WindowInteractionOverlay(
    registry: InteractionOverlayRegistry,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        var rootOrigin by remember { mutableStateOf(Offset.Zero) }
        val handles = registry.handles
        val activeId = registry.activeHandleId
        val density = androidx.compose.ui.platform.LocalDensity.current
        val proxySize by animateDpAsState(
            targetValue = if (activeId != null) 62.dp else 52.dp,
            label = "proxy-size",
        )

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOrigin = it.positionInWindow() },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                handles.forEach { spec ->
                    val target = spec.targetInWindow - rootOrigin
                    val proxy = target + spec.preferredOffsetPx
                    drawLine(
                        color = Color.White.copy(alpha = if (spec.id == activeId) 0.72f else 0.34f),
                        start = target,
                        end = proxy,
                        strokeWidth = if (spec.id == activeId) 2.5.dp.toPx() else 1.25.dp.toPx(),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.78f),
                        radius = if (spec.id == activeId) 7.dp.toPx() else 4.dp.toPx(),
                        center = target,
                    )
                }
            }

            handles.forEach { spec ->
                val latest by rememberUpdatedState(spec)
                val target = spec.targetInWindow - rootOrigin
                val proxy = target + spec.preferredOffsetPx
                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (proxy.x - with(density) { proxySize.toPx() } / 2f).roundToInt(),
                                (proxy.y - with(density) { proxySize.toPx() } / 2f).roundToInt(),
                            )
                        }
                        .size(proxySize)
                        .zIndex(if (spec.id == activeId) 1000f else 900f)
                        .pointerInput(spec.id) {
                            detectDragGestures(
                                onDragStart = { registry.setActive(spec.id) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    latest.onDragDelta(dragAmount)
                                },
                                onDragEnd = {
                                    latest.onCommit()
                                    registry.setActive(null)
                                },
                                onDragCancel = {
                                    latest.onCancel()
                                    registry.setActive(null)
                                },
                            )
                        },
                    shape = MaterialTheme.shapes.large,
                    color = if (spec.id == activeId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f)
                    },
                    tonalElevation = if (spec.id == activeId) 10.dp else 5.dp,
                    shadowElevation = if (spec.id == activeId) 12.dp else 5.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            latest.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (spec.id == activeId) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }

            if (handles.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                    tonalElevation = 3.dp,
                ) {
                    Text(
                        "触控代理 · ${handles.size} 个控制点",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

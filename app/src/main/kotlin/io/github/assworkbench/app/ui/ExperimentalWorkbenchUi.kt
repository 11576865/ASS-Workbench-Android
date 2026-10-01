package io.github.assworkbench.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
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

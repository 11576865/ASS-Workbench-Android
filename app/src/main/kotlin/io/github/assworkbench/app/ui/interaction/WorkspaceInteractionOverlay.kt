package io.github.assworkbench.app.ui.interaction

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
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

internal enum class RodMode(val label: String, val suffix: String) {
    POSITION("位置", "pos"), SCALE("缩放", "scale"), SHEAR("倾斜", "shear"), ROTATION("旋转", "rotation"),
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
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var modeName by rememberSaveable { mutableStateOf(RodMode.POSITION.name) }
    var orbitOnly by rememberSaveable { mutableStateOf(false) }
    val angles = remember { mutableStateMapOf<String, Float>() }
    if (!visible) return
    val mode = RodMode.valueOf(modeName)
    BoxWithConstraints(modifier.fillMaxSize()) {
        var rootOrigin by remember { mutableStateOf(Offset.Zero) }
        val allHandles = registry.handles
        // Four semantic adapters share one physical rod and one placement.
        val positionHandles = allHandles.filter { it.id.startsWith("position-") }
        val handles = allHandles.filter {
            !it.id.startsWith("position-") || it.id.endsWith("-${mode.suffix}") || it.id.endsWith("-org")
        }
        val activeId = registry.activeHandleId
        val density = androidx.compose.ui.platform.LocalDensity.current
        val radius = with(density) { 112.dp.toPx() }
        val handleSize = 52.dp
        val halfSize = with(density) { handleSize.toPx() / 2f }
        fun rodKey(spec: InteractionProxySpec): String = if (spec.id.startsWith("position-") && !spec.id.endsWith("-org"))
            spec.id.substringBeforeLast('-') else spec.id
        fun angle(spec: InteractionProxySpec): Float {
            angles[rodKey(spec)]?.let { return it }
            val target = spec.targetInWindow - rootOrigin
            // Start in a reachable quadrant, including subtitles at the right edge.
            val ax = if (target.x + radius + halfSize > with(density) { maxWidth.toPx() }) -1f else 1f
            val ay = if (target.y - radius - halfSize < 0f) 1f else -1f
            return kotlin.math.atan2(ay, ax)
        }
        fun end(spec: InteractionProxySpec): Offset = spec.targetInWindow - rootOrigin +
            Offset(kotlin.math.cos(angle(spec)), kotlin.math.sin(angle(spec))) * radius
        Box(Modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.positionInWindow() }) {
            Canvas(Modifier.fillMaxSize()) {
                handles.forEach { spec ->
                    val target = spec.targetInWindow - rootOrigin
                    drawLine(Color.White.copy(alpha = if (spec.id == activeId) 0.8f else 0.42f),
                        target, end(spec), strokeWidth = 2.dp.toPx())
                    drawCircle(Color.White, 4.dp.toPx(), target)
                }
            }
            handles.forEach { spec ->
                key(spec.id) {
                    val latest by rememberUpdatedState(spec)
                    val latestAngle by rememberUpdatedState(angle(spec))
                    val latestRoot by rememberUpdatedState(rootOrigin)
                    val proxy = end(spec)
                    DisposableEffect(spec.id) {
                        onDispose {
                            if (registry.activeHandleId == spec.id) {
                                latest.onCancel()
                                registry.setActive(null)
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.offset {
                            IntOffset((proxy.x - halfSize).roundToInt(), (proxy.y - halfSize).roundToInt())
                        }.size(handleSize).testTag("rod-handle-${spec.id}")
                            .zIndex(if (spec.id == activeId) 1000f else 900f)
                            .pointerInput(spec.id, mode, orbitOnly, radius) {
                                var anchor = Offset.Zero
                                var finger = Offset.Zero
                                var previousAngle = 0f
                                var changed = false
                                detectDragGestures(
                                    onDragStart = {
                                        registry.setActive(spec.id)
                                        anchor = latest.targetInWindow - latestRoot
                                        previousAngle = latestAngle
                                        finger = anchor + Offset(kotlin.math.cos(previousAngle), kotlin.math.sin(previousAngle)) * radius
                                        changed = false
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        finger += amount
                                        val step = FixedRod.advance(anchor.x, anchor.y, finger.x, finger.y, radius, previousAngle)
                                        val angular = FixedRod.angularDelta(previousAngle, step.angle)
                                        angles[rodKey(latest)] = step.angle
                                        if (!orbitOnly) {
                                            val isPosition = !spec.id.startsWith("position-") || spec.id.endsWith("-pos") || spec.id.endsWith("-org")
                                            val delta = when {
                                                isPosition -> Offset(step.dx, step.dy)
                                                mode == RodMode.SCALE -> Offset(step.radial, -step.radial)
                                                mode == RodMode.ROTATION -> Offset(FixedRod.assRotationDegrees(previousAngle, step.angle) / 0.35f, 0f)
                                                else -> Offset(step.radial, angular * radius)
                                            }
                                            if (delta.getDistance() > 0.001f) {
                                                latest.onDragDelta(delta)
                                                changed = true
                                            }
                                            if (isPosition) anchor += delta
                                        }
                                        // Project finger onto the rod after the radial edit; length never drifts.
                                        finger = anchor + Offset(kotlin.math.cos(step.angle), kotlin.math.sin(step.angle)) * radius
                                        previousAngle = step.angle
                                    },
                                    onDragEnd = {
                                        if (changed) latest.onCommit()
                                        registry.setActive(null)
                                    },
                                    onDragCancel = {
                                        if (changed) latest.onCancel()
                                        registry.setActive(null)
                                    },
                                )
                            },
                        shape = MaterialTheme.shapes.large,
                        color = if (spec.id == activeId) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                        shadowElevation = 6.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (orbitOnly) "转杆" else spec.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (positionHandles.isNotEmpty()) {
                Surface(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).zIndex(1100f),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RodMode.entries.forEach { entry ->
                            FilterChip(selected = mode == entry, onClick = { modeName = entry.name; orbitOnly = false },
                                label = { Text(entry.label) }, modifier = Modifier.testTag("rod-mode-${entry.name}"))
                        }
                        FilterChip(selected = orbitOnly, onClick = { orbitOnly = !orbitOnly },
                            label = { Text("仅转杆") }, modifier = Modifier.testTag("rod-orbit-only"))
                        TextButton(onClick = {
                            handles.forEach { angles[rodKey(it)] = angle(it) + kotlin.math.PI.toFloat() / 4f }
                        }, modifier = Modifier.testTag("rod-reposition")) { Text("转杆45°") }
                    }
                }
            }
        }
    }
}

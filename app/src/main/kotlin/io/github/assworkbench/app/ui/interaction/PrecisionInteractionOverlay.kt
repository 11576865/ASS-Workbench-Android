package io.github.assworkbench.app.ui.interaction

import android.os.Build
import android.widget.Magnifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class PrecisionGain(val label: String, val factor: Float) {
    COARSE("粗调", 1f),
    FINE("细调", 0.18f),
}

internal enum class PrecisionLensPresentation(val label: String) {
    LOCAL_FOCUS("局部聚焦"),
    FLOATING_LENS("独立放大窗"),
}

internal data class PrecisionSnapResult(
    val delta: Offset,
    val verticalGuidePx: Float? = null,
    val horizontalGuidePx: Float? = null,
    val label: String? = null,
)

internal object PrecisionInteractionMath {
    fun applyGain(delta: Offset, gain: PrecisionGain): Offset = delta * gain.factor

    fun snap(
        target: Offset,
        delta: Offset,
        width: Float,
        height: Float,
        thresholdPx: Float,
    ): PrecisionSnapResult {
        if (width <= 0f || height <= 0f) return PrecisionSnapResult(delta)
        val proposed = target + delta
        val verticals = listOf(
            "左安全线" to width * 0.10f,
            "水平中心" to width * 0.50f,
            "右安全线" to width * 0.90f,
        )
        val horizontals = listOf(
            "上安全线" to height * 0.10f,
            "垂直中心" to height * 0.50f,
            "下安全线" to height * 0.90f,
        )
        val v = verticals.minByOrNull { abs(proposed.x - it.second) }
            ?.takeIf { abs(proposed.x - it.second) <= thresholdPx }
        val h = horizontals.minByOrNull { abs(proposed.y - it.second) }
            ?.takeIf { abs(proposed.y - it.second) <= thresholdPx }
        val adjusted = Offset(
            x = v?.second?.minus(target.x) ?: delta.x,
            y = h?.second?.minus(target.y) ?: delta.y,
        )
        return PrecisionSnapResult(
            delta = adjusted,
            verticalGuidePx = v?.second,
            horizontalGuidePx = h?.second,
            label = listOfNotNull(v?.first, h?.first).joinToString(" · ").ifBlank { null },
        )
    }
}

@Composable
internal fun PrecisionInteractionOverlay(
    registry: InteractionOverlayRegistry,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var modeName by rememberSaveable { mutableStateOf(RodMode.POSITION.name) }
    var gainName by rememberSaveable { mutableStateOf(PrecisionGain.COARSE.name) }
    var lensName by rememberSaveable { mutableStateOf(PrecisionLensPresentation.FLOATING_LENS.name) }
    var snappingEnabled by rememberSaveable { mutableStateOf(true) }
    var snapBypass by remember { mutableStateOf(false) }
    var activeDelta by remember { mutableStateOf(Offset.Zero) }
    var snapPreview by remember { mutableStateOf<PrecisionSnapResult?>(null) }
    var lensSlot by remember { mutableStateOf<Alignment>(Alignment.TopEnd) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    val mode = RodMode.valueOf(modeName)
    val gain = PrecisionGain.valueOf(gainName)
    val lens = PrecisionLensPresentation.valueOf(lensName)
    val gainUpdated by rememberUpdatedState(gain)
    val snappingUpdated by rememberUpdatedState(snappingEnabled)
    val bypassUpdated by rememberUpdatedState(snapBypass)
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val density = LocalDensity.current
    val angles = remember { mutableStateMapOf<String, Float>() }

    val magnifier = remember(view, lens, density.density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val w = with(density) {
                    if (lens == PrecisionLensPresentation.FLOATING_LENS) 190.dp.roundToPx()
                    else 250.dp.roundToPx()
                }
                val h = with(density) {
                    if (lens == PrecisionLensPresentation.FLOATING_LENS) 132.dp.roundToPx()
                    else 188.dp.roundToPx()
                }
                Magnifier.Builder(view)
                    .setSize(w, h)
                    .setInitialZoom(if (lens == PrecisionLensPresentation.FLOATING_LENS) 2.45f else 1.85f)
                    .build()
            }.getOrNull()
        } else {
            null
        }
    }

    DisposableEffect(magnifier) {
        onDispose { runCatching { magnifier?.dismiss() } }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInWindow() }
            .testTag("precision-interaction-overlay")
    ) {
        val allHandles = registry.handles
        val positionHandles = allHandles.filter { it.id.startsWith("position-") }
        val handles = allHandles.filter {
            !it.id.startsWith("position-") ||
                it.id.endsWith("-${mode.suffix}") ||
                it.id.endsWith("-org")
        }
        val activeId = registry.activeHandleId
        val rootWidthPx = with(density) { maxWidth.toPx() }
        val rootHeightPx = with(density) { maxHeight.toPx() }
        val radius = with(density) { 116.dp.toPx() }
        val handleSize = 54.dp
        val halfHandlePx = with(density) { handleSize.toPx() / 2f }
        val snapThresholdPx = with(density) { 14.dp.toPx() }

        fun rodKey(spec: InteractionProxySpec): String =
            if (spec.id.startsWith("position-") && !spec.id.endsWith("-org")) {
                spec.id.substringBeforeLast('-')
            } else {
                spec.id
            }

        fun rodAngle(spec: InteractionProxySpec): Float {
            angles[rodKey(spec)]?.let { return it }
            val target = spec.targetInWindow - rootOrigin
            val ax = if (target.x + radius + halfHandlePx > rootWidthPx) -1f else 1f
            val ay = if (target.y - radius - halfHandlePx < 0f) 1f else -1f
            return kotlin.math.atan2(ay, ax)
        }

        fun rodEnd(spec: InteractionProxySpec): Offset =
            spec.targetInWindow - rootOrigin +
                Offset(cos(rodAngle(spec)), sin(rodAngle(spec))) * radius

        val activeSpec = handles.firstOrNull { it.id == activeId }
            ?: allHandles.firstOrNull { it.id == activeId }

        SideEffect {
            if (activeSpec == null) {
                runCatching { magnifier?.dismiss() }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && magnifier != null) {
                val viewLocation = IntArray(2)
                view.getLocationInWindow(viewLocation)
                val sourceInView = activeSpec.targetInWindow -
                    Offset(viewLocation[0].toFloat(), viewLocation[1].toFloat())
                val margin = with(density) { 18.dp.toPx() }
                val lensCenterWindow = when (lens) {
                    PrecisionLensPresentation.FLOATING_LENS -> when (lensSlot) {
                        Alignment.TopStart -> rootOrigin + Offset(rootWidthPx * 0.25f, rootHeightPx * 0.20f)
                        Alignment.TopEnd -> rootOrigin + Offset(rootWidthPx * 0.75f, rootHeightPx * 0.20f)
                        Alignment.BottomStart -> rootOrigin + Offset(rootWidthPx * 0.25f, rootHeightPx * 0.78f)
                        else -> rootOrigin + Offset(rootWidthPx * 0.75f, rootHeightPx * 0.78f)
                    }
                    PrecisionLensPresentation.LOCAL_FOCUS -> {
                        val source = activeSpec.targetInWindow
                        val y = if (source.y - rootOrigin.y < rootHeightPx * 0.48f) {
                            (source.y + with(density) { 138.dp.toPx() })
                                .coerceAtMost(rootOrigin.y + rootHeightPx - margin)
                        } else {
                            (source.y - with(density) { 138.dp.toPx() })
                                .coerceAtLeast(rootOrigin.y + margin)
                        }
                        Offset(source.x.coerceIn(rootOrigin.x + margin, rootOrigin.x + rootWidthPx - margin), y)
                    }
                }
                val lensCenterInView = lensCenterWindow -
                    Offset(viewLocation[0].toFloat(), viewLocation[1].toFloat())
                runCatching {
                    magnifier.show(
                        sourceInView.x,
                        sourceInView.y,
                        lensCenterInView.x,
                        lensCenterInView.y,
                    )
                }
            }
        }

        Canvas(Modifier.fillMaxSize().zIndex(800f)) {
            handles.forEach { spec ->
                val target = spec.targetInWindow - rootOrigin
                val end = rodEnd(spec)
                drawLine(
                    color = Color.White.copy(alpha = if (spec.id == activeId) 0.90f else 0.42f),
                    start = target,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                )
                drawCircle(
                    color = if (spec.id.endsWith("-org")) Color.Cyan else Color.White,
                    radius = if (spec.id.endsWith("-org")) 6.dp.toPx() else 4.dp.toPx(),
                    center = target,
                )
            }
            snapPreview?.verticalGuidePx?.let { x ->
                drawLine(
                    color = Color.Cyan.copy(alpha = 0.88f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            snapPreview?.horizontalGuidePx?.let { y ->
                drawLine(
                    color = Color.Cyan.copy(alpha = 0.88f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            activeSpec?.let { spec ->
                val target = spec.targetInWindow - rootOrigin
                drawCircle(
                    color = Color.White.copy(alpha = 0.36f),
                    radius = 48.dp.toPx(),
                    center = target,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
                drawLine(
                    Color.White.copy(alpha = 0.32f),
                    Offset(target.x - 64.dp.toPx(), target.y),
                    Offset(target.x + 64.dp.toPx(), target.y),
                    1.dp.toPx(),
                )
                drawLine(
                    Color.White.copy(alpha = 0.32f),
                    Offset(target.x, target.y - 64.dp.toPx()),
                    Offset(target.x, target.y + 64.dp.toPx()),
                    1.dp.toPx(),
                )
            }
        }

        handles.forEach { spec ->
            key(spec.id) {
                val latest by rememberUpdatedState(spec)
                val latestRoot by rememberUpdatedState(rootOrigin)
                val latestAngle by rememberUpdatedState(rodAngle(spec))
                val proxy = rodEnd(spec)

                DisposableEffect(spec.id) {
                    onDispose {
                        if (registry.activeHandleId == spec.id) {
                            latest.onCancel()
                            registry.setActive(null)
                            activeDelta = Offset.Zero
                            snapPreview = null
                            runCatching { magnifier?.dismiss() }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (proxy.x - halfHandlePx).roundToInt(),
                                (proxy.y - halfHandlePx).roundToInt(),
                            )
                        }
                        .size(handleSize)
                        .testTag("precision-rod-${spec.id}")
                        .zIndex(if (spec.id == activeId) 1200f else 1000f)
                        .pointerInput(spec.id, mode, radius) {
                            var anchor = Offset.Zero
                            var finger = Offset.Zero
                            var previousAngle = 0f
                            var changed = false
                            var lastSnapLabel: String? = null

                            detectDragGestures(
                                onDragStart = {
                                    registry.setActive(spec.id)
                                    anchor = latest.targetInWindow - latestRoot
                                    previousAngle = latestAngle
                                    finger = anchor + Offset(cos(previousAngle), sin(previousAngle)) * radius
                                    activeDelta = Offset.Zero
                                    snapPreview = null
                                    lastSnapLabel = null
                                    val target = latest.targetInWindow - latestRoot
                                    lensSlot = when {
                                        target.x < rootWidthPx / 2f && target.y < rootHeightPx / 2f -> Alignment.BottomEnd
                                        target.x >= rootWidthPx / 2f && target.y < rootHeightPx / 2f -> Alignment.BottomStart
                                        target.x < rootWidthPx / 2f -> Alignment.TopEnd
                                        else -> Alignment.TopStart
                                    }
                                    changed = false
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    finger += amount
                                    val step = FixedRod.advance(
                                        anchor.x,
                                        anchor.y,
                                        finger.x,
                                        finger.y,
                                        radius,
                                        previousAngle,
                                    )
                                    val angular = FixedRod.angularDelta(previousAngle, step.angle)
                                    angles[rodKey(latest)] = step.angle
                                    val isPositionLike =
                                        !spec.id.startsWith("position-") ||
                                            spec.id.endsWith("-pos") ||
                                            spec.id.endsWith("-org") ||
                                            spec.id.endsWith("-start") ||
                                            spec.id.endsWith("-end")

                                    var delta = when {
                                        isPositionLike -> Offset(step.dx, step.dy)
                                        mode == RodMode.SCALE -> Offset(step.radial, -step.radial)
                                        mode == RodMode.ROTATION ->
                                            Offset(FixedRod.assRotationDegrees(previousAngle, step.angle) / 0.35f, 0f)
                                        else -> Offset(step.radial, angular * radius)
                                    }
                                    delta = PrecisionInteractionMath.applyGain(delta, gainUpdated)

                                    if (
                                        isPositionLike &&
                                        snappingUpdated &&
                                        !bypassUpdated
                                    ) {
                                        val result = PrecisionInteractionMath.snap(
                                            target = latest.targetInWindow - latestRoot,
                                            delta = delta,
                                            width = rootWidthPx,
                                            height = rootHeightPx,
                                            thresholdPx = snapThresholdPx,
                                        )
                                        delta = result.delta
                                        snapPreview = result.takeIf { it.label != null }
                                        if (result.label != null && result.label != lastSnapLabel) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            lastSnapLabel = result.label
                                        }
                                    } else {
                                        snapPreview = null
                                        lastSnapLabel = null
                                    }

                                    if (delta.getDistance() > 0.001f) {
                                        latest.onDragDelta(delta)
                                        activeDelta += delta
                                        changed = true
                                        if (isPositionLike) anchor += delta
                                    }
                                    finger = anchor + Offset(cos(step.angle), sin(step.angle)) * radius
                                    previousAngle = step.angle
                                },
                                onDragEnd = {
                                    if (changed) latest.onCommit()
                                    registry.setActive(null)
                                    activeDelta = Offset.Zero
                                    snapPreview = null
                                    runCatching { magnifier?.dismiss() }
                                },
                                onDragCancel = {
                                    if (changed) latest.onCancel()
                                    registry.setActive(null)
                                    activeDelta = Offset.Zero
                                    snapPreview = null
                                    runCatching { magnifier?.dismiss() }
                                },
                            )
                        },
                    shape = MaterialTheme.shapes.large,
                    color = if (spec.id == activeId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.97f)
                    },
                    shadowElevation = 7.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(spec.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .zIndex(1400f)
                .testTag("precision-controls"),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
            shadowElevation = 5.dp,
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (positionHandles.isNotEmpty()) {
                    RodMode.entries.forEach { entry ->
                        FilterChip(
                            selected = mode == entry,
                            onClick = { modeName = entry.name },
                            label = { Text(entry.label) },
                            modifier = Modifier.testTag("precision-mode-${entry.name}"),
                        )
                    }
                }
                PrecisionGain.entries.forEach { entry ->
                    FilterChip(
                        selected = gain == entry,
                        onClick = { gainName = entry.name },
                        label = { Text(entry.label) },
                        modifier = Modifier.testTag("precision-gain-${entry.name}"),
                    )
                }
                PrecisionLensPresentation.entries.forEach { entry ->
                    FilterChip(
                        selected = lens == entry,
                        onClick = { lensName = entry.name },
                        label = { Text(entry.label) },
                        modifier = Modifier.testTag("precision-lens-${entry.name}"),
                    )
                }
                FilterChip(
                    selected = snappingEnabled,
                    onClick = { snappingEnabled = !snappingEnabled },
                    label = { Text("吸附") },
                    modifier = Modifier.testTag("precision-snap-toggle"),
                )
                Surface(
                    modifier = Modifier
                        .testTag("precision-snap-bypass")
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    snapBypass = true
                                    tryAwaitRelease()
                                    snapBypass = false
                                }
                            )
                        },
                    shape = MaterialTheme.shapes.large,
                    color = if (snapBypass) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ) {
                    Text(
                        if (snapBypass) "吸附已临时解除" else "按住解除吸附",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        if (activeSpec != null) {
            val deltaText = if (activeSpec.id.endsWith("-rotation")) {
                "Δθ %.1f°".format(activeDelta.x * 0.35f)
            } else {
                "Δx %.1f · Δy %.1f px".format(activeDelta.x, activeDelta.y)
            }
            Surface(
                modifier = Modifier
                    .align(
                        when (lensSlot) {
                            Alignment.TopStart, Alignment.TopEnd -> Alignment.BottomCenter
                            else -> Alignment.TopCenter
                        }
                    )
                    .padding(vertical = 66.dp)
                    .zIndex(1450f)
                    .testTag("precision-live-readout"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.90f),
            ) {
                Text(
                    "${activeSpec.label} · ${gain.label} · $deltaText" +
                        (snapPreview?.label?.let { " · 吸附：$it" } ?: ""),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

internal data class WorkspaceXYPoint(
    val x: Double,
    val y: Double,
)

internal fun workspaceXYFromPad(
    position: Offset,
    width: Float,
    height: Float,
    maxX: Double,
    maxY: Double,
): WorkspaceXYPoint {
    require(width > 0f && height > 0f) { "XY pad dimensions must be positive." }
    require(maxX.isFinite() && maxX > 0.0 && maxY.isFinite() && maxY > 0.0) {
        "XY pad coordinate bounds must be positive finite values."
    }
    return WorkspaceXYPoint(
        x = (position.x / width).toDouble().coerceIn(0.0, 1.0) * maxX,
        y = (position.y / height).toDouble().coerceIn(0.0, 1.0) * maxY,
    )
}

@Composable
internal fun WorkspacePositionXYPadControl(
    x: Double,
    y: Double,
    maxX: Double,
    maxY: Double,
    gestureKey: String,
    enabled: Boolean,
    onPreview: (Double, Double) -> Boolean,
    onCommit: (Double, Double) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestX by rememberUpdatedState(x)
    val latestY by rememberUpdatedState(y)
    val latestCommit by rememberUpdatedState(onCommit)
    val foreground = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val background = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio((maxX / maxY).toFloat().coerceIn(1.0f, 2.4f))
            .semantics {
                contentDescription = "位置 X/Y 二维控制板"
                stateDescription = "X $x，Y $y"
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction("向右 1") {
                            latestCommit((latestX + 1.0).coerceAtMost(maxX), latestY)
                            true
                        },
                        CustomAccessibilityAction("向左 1") {
                            latestCommit((latestX - 1.0).coerceAtLeast(0.0), latestY)
                            true
                        },
                        CustomAccessibilityAction("向下 1") {
                            latestCommit(latestX, (latestY + 1.0).coerceAtMost(maxY))
                            true
                        },
                        CustomAccessibilityAction("向上 1") {
                            latestCommit(latestX, (latestY - 1.0).coerceAtLeast(0.0))
                            true
                        },
                    )
                }
            }
            .pointerInput(gestureKey, enabled, maxX, maxY) {
                if (!enabled) return@pointerInput
                val previewForGesture = onPreview
                val commitForGesture = onCommit
                val cancelForGesture = onCancel
                var current: WorkspaceXYPoint? = null
                var changed = false

                fun cancel() {
                    if (changed) cancelForGesture()
                    current = null
                    changed = false
                }

                try {
                    detectDragGestures(
                        onDragStart = { position ->
                            val next = workspaceXYFromPad(
                                position,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                maxX,
                                maxY,
                            )
                            if (previewForGesture(next.x, next.y)) {
                                current = next
                                changed = true
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val next = workspaceXYFromPad(
                                change.position,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                maxX,
                                maxY,
                            )
                            if (previewForGesture(next.x, next.y)) {
                                current = next
                                changed = true
                            } else {
                                cancel()
                            }
                        },
                        onDragEnd = {
                            if (changed) current?.let { latestCommit(it.x, it.y) }
                            current = null
                            changed = false
                        },
                        onDragCancel = ::cancel,
                    )
                } finally {
                    cancel()
                }
            },
    ) {
        drawRect(background)
        drawRect(
            color = outline,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
        val px = (x / maxX).coerceIn(0.0, 1.0).toFloat() * size.width
        val py = (y / maxY).coerceIn(0.0, 1.0).toFloat() * size.height
        drawLine(outline, Offset(px, 0f), Offset(px, size.height), 1.dp.toPx())
        drawLine(outline, Offset(0f, py), Offset(size.width, py), 1.dp.toPx())
        drawCircle(foreground, radius = 7.dp.toPx(), center = Offset(px, py))
    }
}

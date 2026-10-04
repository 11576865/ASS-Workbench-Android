package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
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
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun WorkspaceAngleDialControl(
    angle: Double,
    gestureKey: String,
    enabled: Boolean,
    onPreview: (Double) -> Boolean,
    onCommit: (Double) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestAngle by rememberUpdatedState(angle)
    val latestCommit by rememberUpdatedState(onCommit)
    val foreground = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.outline
    Canvas(
        modifier.size(160.dp)
            .semantics {
                contentDescription = "Z 旋转转盘；逆时针增加角度"
                stateDescription = "$angle 度"
                if (enabled) customActions = listOf(
                    CustomAccessibilityAction("增加 1 度") { latestCommit(latestAngle + 1.0); true },
                    CustomAccessibilityAction("减少 1 度") { latestCommit(latestAngle - 1.0); true },
                )
            }
            .pointerInput(gestureKey, enabled) {
                if (!enabled) return@pointerInput
                // Keep callbacks bound to the gesture's original session and Event.
                val previewForGesture = onPreview
                val commitForGesture = onCommit
                val cancelForGesture = onCancel
                var drag: WorkspaceAngleDialDrag? = null
                var changed = false
                fun cancel() {
                    if (changed) cancelForGesture()
                    drag = null
                    changed = false
                }
                try {
                    detectDragGestures(
                        onDragStart = { position ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val offset = position - center
                            drag = WorkspaceAngleDialDrag.begin(
                                latestAngle, offset.x.toDouble(), offset.y.toDouble(),
                                12.dp.toPx().toDouble(),
                            )
                            changed = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val current = drag
                            if (current != null) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val offset = change.position - center
                                val next = current.move(offset.x.toDouble(), offset.y.toDouble())
                                if (next == null) cancel() else {
                                    if (next.value != current.value) {
                                        if (previewForGesture(next.value)) {
                                            drag = next
                                            changed = true
                                        } else cancel()
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (changed) drag?.let { commitForGesture(it.value) }
                            drag = null
                            changed = false
                        },
                        onDragCancel = ::cancel,
                    )
                } finally {
                    cancel()
                }
            },
    ) {
        val radius = size.minDimension * 0.4f
        drawCircle(ring, radius, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        val radians = Math.toRadians(angle % 360.0)
        val tip = center + Offset(cos(radians).toFloat() * radius, -sin(radians).toFloat() * radius)
        drawLine(foreground, center, tip, 3.dp.toPx())
        drawCircle(foreground, 6.dp.toPx(), tip)
        drawCircle(ring, 3.dp.toPx(), center)
    }
}

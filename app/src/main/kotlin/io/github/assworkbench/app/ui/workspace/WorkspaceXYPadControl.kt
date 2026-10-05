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
import io.github.assworkbench.domain.AssPoint

@Composable
internal fun WorkspaceXYPadControl(
    point: AssPoint, scriptWidth: Double, scriptHeight: Double, gestureKey: String,
    enabled: Boolean, onPreview: (AssPoint) -> Boolean, onCommit: (AssPoint) -> Unit,
    onCancel: () -> Unit, modifier: Modifier = Modifier,
    semanticPoint: AssPoint = point, semanticLabel: String = "字幕位置二维板",
    accessibilityStep: Double = 1.0,
) {
    val latestPoint by rememberUpdatedState(point)
    val latestCommit by rememberUpdatedState(onCommit)
    val line = MaterialTheme.colorScheme.outline
    val cursor = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().aspectRatio((scriptWidth / scriptHeight).toFloat().coerceIn(0.25f, 4f))
        .semantics {
            contentDescription = "$semanticLabel；向右增加 X，向下增加 Y"
            stateDescription = "X ${semanticPoint.x}, Y ${semanticPoint.y}"
            if (enabled) customActions = listOf(
                CustomAccessibilityAction("向左 ${accessibilityStep} 单位") { latestCommit(latestPoint.copy(x = (latestPoint.x - accessibilityStep).coerceIn(0.0, scriptWidth))); true },
                CustomAccessibilityAction("向右 ${accessibilityStep} 单位") { latestCommit(latestPoint.copy(x = (latestPoint.x + accessibilityStep).coerceIn(0.0, scriptWidth))); true },
                CustomAccessibilityAction("向上 ${accessibilityStep} 单位") { latestCommit(latestPoint.copy(y = (latestPoint.y - accessibilityStep).coerceIn(0.0, scriptHeight))); true },
                CustomAccessibilityAction("向下 ${accessibilityStep} 单位") { latestCommit(latestPoint.copy(y = (latestPoint.y + accessibilityStep).coerceIn(0.0, scriptHeight))); true },
            )
        }
        .pointerInput(gestureKey, enabled, scriptWidth, scriptHeight) {
            if (!enabled) return@pointerInput
            val previewForGesture = onPreview
            val commitForGesture = onCommit
            val cancelForGesture = onCancel
            var drag: WorkspaceXYPadDrag? = null
            var pending: AssPoint? = null
            var origin = point
            fun cancel() {
                if (pending != null) cancelForGesture()
                drag = null
                pending = null
            }
            try {
                detectDragGestures(
                    onDragStart = { offset ->
                        origin = latestPoint
                        drag = WorkspaceXYPadDrag.begin(origin.x, origin.y,
                            offset.x.toDouble(), offset.y.toDouble(), size.width.toDouble(),
                            size.height.toDouble(), scriptWidth, scriptHeight)
                        pending = null
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        drag?.move(change.position.x.toDouble(), change.position.y.toDouble())?.let { next ->
                            if (next != (pending ?: origin)) {
                                if (previewForGesture(next)) pending = next else cancel()
                            }
                        }
                    },
                    onDragEnd = {
                        pending?.let { next ->
                            if (next == origin) cancelForGesture() else commitForGesture(next)
                        }
                        drag = null
                        pending = null
                    },
                    onDragCancel = ::cancel,
                )
            } finally { cancel() }
        }) {
        drawRect(line, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
        drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        val center = Offset((point.x / scriptWidth * size.width).toFloat(), (point.y / scriptHeight * size.height).toFloat())
        drawCircle(cursor, 6.dp.toPx(), center)
    }
}

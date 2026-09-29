package io.github.assworkbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun ResizableSplitPane(
    ratio: Float,
    horizontal: Boolean,
    onRatioChange: (Float) -> Unit,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val dividerTouch = 24.dp
        val dividerLine = 1.dp
        val latestRatio by rememberUpdatedState(ratio)
        if (horizontal) {
            val totalPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            Row(Modifier.fillMaxSize()) {
                first(Modifier.weight(ratio).fillMaxHeight())
                Box(
                    Modifier.width(dividerTouch).fillMaxHeight()
                        .pointerInput(totalPx) {
                            var workingRatio = latestRatio
                            detectDragGestures(
                                onDragStart = { workingRatio = latestRatio },
                                onDrag = { change, drag ->
                                    change.consume()
                                    workingRatio = (workingRatio + drag.x / totalPx).coerceIn(0.28f, 0.78f)
                                    onRatioChange(workingRatio)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.width(dividerLine).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                second(Modifier.weight(1f - ratio).fillMaxHeight())
            }
        } else {
            val totalPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            Column(Modifier.fillMaxSize()) {
                first(Modifier.weight(ratio).fillMaxWidth())
                Box(
                    Modifier.height(dividerTouch).fillMaxWidth()
                        .pointerInput(totalPx) {
                            var workingRatio = latestRatio
                            detectDragGestures(
                                onDragStart = { workingRatio = latestRatio },
                                onDrag = { change, drag ->
                                    change.consume()
                                    workingRatio = (workingRatio + drag.y / totalPx).coerceIn(0.28f, 0.78f)
                                    onRatioChange(workingRatio)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.height(dividerLine).fillMaxWidth()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                second(Modifier.weight(1f - ratio).fillMaxWidth())
            }
        }
    }
}

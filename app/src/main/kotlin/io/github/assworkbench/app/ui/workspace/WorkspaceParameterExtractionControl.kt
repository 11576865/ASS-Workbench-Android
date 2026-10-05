package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** A real long-press drag with ordinary click fallback and target/session cancellation. */
@Composable
internal fun WorkspaceParameterExtractionControl(
    label: String, gestureKey: String, enabled: Boolean = true,
    onExtract: () -> Unit, modifier: Modifier = Modifier,
) {
    var dragging by remember(gestureKey) { mutableStateOf(false) }
    val latestExtract by rememberUpdatedState(onExtract)
    val latestKey by rememberUpdatedState(gestureKey)
    val latestEnabled by rememberUpdatedState(enabled)
    TextButton(
        enabled = enabled, onClick = { latestExtract() },
        modifier = modifier.pointerInput(gestureKey, enabled) {
            if (!enabled) return@pointerInput
            // Keep target identity fixed while reading the latest workspace
            // mutation callback, so previous extraction/layout changes survive.
            val originKey = gestureKey
            try {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true },
                    onDrag = { change, _ -> change.consume() },
                    onDragEnd = {
                        dragging = false
                        if (latestKey == originKey && latestEnabled) latestExtract()
                    },
                    onDragCancel = { dragging = false },
                )
            } finally { dragging = false }
        },
    ) {
        Icon(if (dragging) Icons.Filled.DragHandle else Icons.Filled.OpenInNew, null)
        Spacer(Modifier.width(6.dp))
        Text(if (dragging) "松手放到工作区" else "长按拖出$label")
    }
}

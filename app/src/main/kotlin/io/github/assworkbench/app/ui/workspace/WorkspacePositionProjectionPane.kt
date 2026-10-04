package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.ui.preview.GeometryParameterDisplay
import io.github.assworkbench.app.ui.preview.PreviewTargetResolver
import io.github.assworkbench.domain.AssPoint

@Composable
internal fun WorkspacePositionProjectionPane(
    projection: WorkspaceParameterProjection, state: EditorState, viewModel: EditorViewModel,
    eventId: Long, onRemove: () -> Unit,
    onPresentationChange: (WorkspaceParameterPresentation) -> Unit, modifier: Modifier = Modifier,
) {
    val sessionId = state.workspaceSessionId
    val ownerId = "geometry:$eventId:${projection.id}"
    val suffix = projection.id.replace(':', '-')
    fun cancelOwned() {
        if (viewModel.state.value.workspaceSessionId == sessionId) viewModel.clearTransientPreview(ownerId)
    }
    DisposableEffect(sessionId, ownerId, projection.presentation) { onDispose { cancelOwned() } }
    val event = state.document.events.first { it.id == eventId }
    val point = PreviewTargetResolver.staticAnchor(state.document, event)
    if (point == null || state.document.playResX <= 0 || state.document.playResY <= 0) {
        Column(modifier.padding(12.dp)) {
            Text("位置 X/Y · Event #$eventId")
            Text("无法安全解析静态位置。运动、冲突或正文内位置标签请先在原位置工具中处理。")
            Button(onClick = { cancelOwned(); onRemove() }) { Text("移除参数控件") }
        }
        return
    }
    val displayEvent = GeometryParameterDisplay.event(state.document, state.previewDocument, state.previewOwnerId, eventId) ?: event
    val displayPoint = PreviewTargetResolver.staticAnchor(state.document, displayEvent) ?: point
    val width = state.document.playResX.toDouble()
    val height = state.document.playResY.toDouble()
    val enabled = state.previewOwnerId == null || state.previewOwnerId == ownerId
    fun mayWrite(): Boolean {
        val current = viewModel.state.value
        val target = projection.binding.resolve(current.focusedEventId, current.selectedEventIds,
            current.document.events.mapTo(hashSetOf()) { it.id }) as? WorkspaceBindingResolution.Event
        val currentEvent = current.document.events.firstOrNull { it.id == eventId }
        return current.workspaceSessionId == sessionId && target?.eventId == eventId &&
            currentEvent != null && PreviewTargetResolver.staticAnchor(current.document, currentEvent) != null &&
            (current.previewOwnerId == null || current.previewOwnerId == ownerId)
    }
    fun preview(next: AssPoint): Boolean {
        if (!mayWrite()) return false
        viewModel.previewEventPosition(eventId, next.x, next.y, ownerId = ownerId)
        return true
    }
    fun commit(next: AssPoint) {
        if (mayWrite()) viewModel.setEventPosition(eventId, next.x, next.y)
    }
    var xText by remember(sessionId, projection.id, eventId, event.text) { mutableStateOf(point.x.toString()) }
    var yText by remember(sessionId, projection.id, eventId, event.text) { mutableStateOf(point.y.toString()) }
    fun draft(): AssPoint? {
        val x = xText.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..width } ?: return null
        val y = yText.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..height } ?: return null
        return AssPoint(x, y)
    }
    fun previewDraft() { draft()?.let { preview(it) } ?: cancelOwned() }
    Column(modifier.padding(12.dp).testTag("parameter-projection-$suffix"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("位置 X/Y · Event #$eventId")
        Text(if (projection.binding is WorkspaceBinding.PinnedEvent) "固定 Event #$eventId" else "跟随当前焦点")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(WorkspaceParameterPresentation.XY_PAD to "二维板",
                WorkspaceParameterPresentation.NUMBER_PAIR to "数值").forEach { (presentation, label) ->
                FilterChip(selected = projection.presentation == presentation,
                    onClick = { cancelOwned(); onPresentationChange(presentation) }, label = { Text(label) })
            }
        }
        Text("X ${displayPoint.x} · Y ${displayPoint.y}")
        when (projection.presentation) {
            WorkspaceParameterPresentation.XY_PAD -> {
                WorkspaceXYPadControl(displayPoint, width, height, "$sessionId:$ownerId", enabled,
                    ::preview, ::commit, ::cancelOwned, Modifier.testTag("parameter-projection-xy-$suffix"))
                Text("拖动调整位置，松手应用。X 向右、Y 向下；范围 0–${width} / 0–${height}。")
            }
            WorkspaceParameterPresentation.NUMBER_PAIR -> {
                OutlinedTextField(if (enabled) xText else displayPoint.x.toString(),
                    { xText = it; previewDraft() }, label = { Text("X") }, singleLine = true,
                    readOnly = !enabled, modifier = Modifier.fillMaxWidth().testTag("parameter-projection-x-$suffix"))
                OutlinedTextField(if (enabled) yText else displayPoint.y.toString(),
                    { yText = it; previewDraft() }, label = { Text("Y") }, singleLine = true,
                    readOnly = !enabled, modifier = Modifier.fillMaxWidth().testTag("parameter-projection-y-$suffix"))
                Button(onClick = { draft()?.let(::commit) }, enabled = enabled && draft() != null,
                    modifier = Modifier.testTag("parameter-projection-apply-$suffix")) { Text("应用") }
                Button(onClick = { cancelOwned(); xText = point.x.toString(); yText = point.y.toString() },
                    modifier = Modifier.testTag("parameter-projection-cancel-$suffix")) { Text("取消") }
            }
            else -> Text("位置参数的此显示方式尚未接通。")
        }
        Button(onClick = { cancelOwned(); onRemove() },
            modifier = Modifier.testTag("parameter-projection-remove-$suffix")) { Text("移除参数控件") }
    }
}

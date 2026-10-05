package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.ui.preview.GeometryParameterDisplay
import io.github.assworkbench.domain.AssGeometrySemantic
import io.github.assworkbench.domain.AssPoint
import kotlinx.coroutines.flow.collect

@Composable
internal fun WorkspaceTransformProjectionPane(
    projection: WorkspaceParameterProjection, state: EditorState, viewModel: EditorViewModel,
    eventId: Long, onRemove: () -> Unit,
    onPresentationChange: (WorkspaceParameterPresentation) -> Unit, modifier: Modifier = Modifier,
) {
    val scale = projection.descriptorKey == WorkspaceParameterCatalog.scaleXY.key
    val range = if (scale) WorkspaceTransformRange.scale else WorkspaceTransformRange.shear
    val sessionId = state.workspaceSessionId
    val ownerId = "geometry:$eventId:${projection.id}"
    val suffix = projection.id.replace(':', '-')
    val event = state.document.events.first { it.id == eventId }
    val style = state.document.styles.firstOrNull { it.name == event.style }
    fun value(text: String): AssPoint {
        val geometry = AssGeometrySemantic.inspect(text)
        return if (scale) AssPoint(geometry.scaleX ?: style?.scaleX ?: 100.0,
            geometry.scaleY ?: style?.scaleY ?: 100.0)
        else AssPoint(geometry.shearX ?: 0.0, geometry.shearY ?: 0.0)
    }
    val committed = value(event.text)
    val displayEvent = GeometryParameterDisplay.event(state.document, state.previewDocument, state.previewOwnerId, eventId) ?: event
    val displayed = value(displayEvent.text)
    val enabled = state.previewOwnerId == null || state.previewOwnerId == ownerId
    fun cancelOwned() {
        if (viewModel.state.value.workspaceSessionId == sessionId) viewModel.clearTransientPreview(ownerId)
    }
    DisposableEffect(sessionId, ownerId, projection.presentation) { onDispose { cancelOwned() } }
    fun mayWrite(): Boolean {
        val current = viewModel.state.value
        val target = projection.binding.resolve(current.focusedEventId, current.selectedEventIds,
            current.document.events.mapTo(hashSetOf()) { it.id }) as? WorkspaceBindingResolution.Event
        return current.workspaceSessionId == sessionId && target?.eventId == eventId &&
            (current.previewOwnerId == null || current.previewOwnerId == ownerId)
    }
    fun preview(next: AssPoint): Boolean {
        if (!mayWrite()) return false
        if (scale) viewModel.previewEventScale(eventId, next.x, next.y, ownerId)
        else viewModel.previewEventShear(eventId, next.x, next.y, ownerId)
        return true
    }
    fun commit(next: AssPoint) {
        if (!mayWrite()) return
        if (scale) viewModel.setEventScale(eventId, next.x, next.y)
        else viewModel.setEventShear(eventId, next.x, next.y)
    }
    var xText by remember(sessionId, projection.id, eventId, event.text, style) { mutableStateOf(committed.x.toString()) }
    var yText by remember(sessionId, projection.id, eventId, event.text, style) { mutableStateOf(committed.y.toString()) }
    var sliderDraft by remember(sessionId, projection.id, eventId, event.text, style, projection.presentation) { mutableStateOf<AssPoint?>(null) }
    // A takeover invalidates the old gesture even if the foreign preview later ends.
    LaunchedEffect(enabled) { if (!enabled) sliderDraft = null }
    fun previewDraft() { range.parse(xText, yText)?.let(::preview) ?: cancelOwned() }
    Column(modifier.padding(12.dp).testTag("parameter-projection-$suffix"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${if (scale) "缩放 X/Y · %" else "错切 X/Y"} · Event #$eventId")
        Text(if (projection.binding is WorkspaceBinding.PinnedEvent) "固定 Event #$eventId" else "跟随当前焦点")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(WorkspaceParameterPresentation.NUMBER_PAIR to "数值",
                WorkspaceParameterPresentation.SLIDER_PAIR to "滑块",
                WorkspaceParameterPresentation.XY_PAD to "二维板").forEach { (presentation, label) ->
                FilterChip(selected = projection.presentation == presentation,
                    onClick = { cancelOwned(); sliderDraft = null; onPresentationChange(presentation) }, label = { Text(label) })
            }
        }
        Text("X ${displayed.x} · Y ${displayed.y}")
        when (projection.presentation) {
            WorkspaceParameterPresentation.NUMBER_PAIR -> {
                OutlinedTextField(if (enabled) xText else displayed.x.toString(),
                    { xText = it; previewDraft() }, label = { Text("X") }, singleLine = true,
                    readOnly = !enabled, modifier = Modifier.fillMaxWidth().testTag("parameter-projection-x-$suffix"))
                OutlinedTextField(if (enabled) yText else displayed.y.toString(),
                    { yText = it; previewDraft() }, label = { Text("Y") }, singleLine = true,
                    readOnly = !enabled, modifier = Modifier.fillMaxWidth().testTag("parameter-projection-y-$suffix"))
                Button(onClick = { range.parse(xText, yText)?.let(::commit) }, enabled = enabled && range.parse(xText, yText) != null,
                    modifier = Modifier.testTag("parameter-projection-apply-$suffix")) { Text("应用") }
            }
            WorkspaceParameterPresentation.SLIDER_PAIR -> {
                val point = sliderDraft ?: displayed
                listOf(true, false).forEach { xAxis ->
                    Text(if (xAxis) "X" else "Y")
                    // Dispose pointer nodes when the target/session changes, not just draft state.
                    key(sessionId, ownerId, projection.presentation) {
                        val interactions = remember { MutableInteractionSource() }
                        LaunchedEffect(interactions) {
                            interactions.interactions.collect { interaction ->
                                if (interaction is DragInteraction.Cancel) {
                                    sliderDraft = null
                                    cancelOwned()
                                }
                            }
                        }
                        Slider(value = (if (xAxis) point.x else point.y).coerceIn(range.min, range.max).toFloat(),
                            onValueChange = { v ->
                                val base = sliderDraft ?: displayed
                                val next = if (xAxis) AssPoint(v.toDouble(), base.y) else AssPoint(base.x, v.toDouble())
                                if (preview(next)) sliderDraft = next else sliderDraft = null
                            },
                            onValueChangeFinished = { sliderDraft?.let(::commit); sliderDraft = null },
                            interactionSource = interactions,
                            enabled = enabled, valueRange = range.min.toFloat()..range.max.toFloat(),
                            modifier = Modifier.fillMaxWidth().testTag("parameter-projection-slider-${if (xAxis) "x" else "y"}-$suffix"))
                    }
                }
            }
            WorkspaceParameterPresentation.XY_PAD -> WorkspaceXYPadControl(
                range.toPad(displayed), range.span, range.span, "$sessionId:$ownerId", enabled,
                { preview(range.fromPad(it)) }, { commit(range.fromPad(it)) }, ::cancelOwned,
                Modifier.testTag("parameter-projection-xy-$suffix"), semanticPoint = displayed,
                semanticLabel = if (scale) "缩放二维板" else "错切二维板",
                accessibilityStep = if (scale) 1.0 else 0.05)
            else -> Text("此显示方式不适用于成对参数。")
        }
        Text("X/Y 独立调整；范围 ${range.min}–${range.max}。二维板向右增加 X，向下增加 Y，松手应用。")
        Button(onClick = { cancelOwned(); sliderDraft = null; xText = committed.x.toString(); yText = committed.y.toString() },
            modifier = Modifier.testTag("parameter-projection-cancel-$suffix")) { Text("取消") }
        Button(onClick = { cancelOwned(); onRemove() },
            modifier = Modifier.testTag("parameter-projection-remove-$suffix")) { Text("移除参数控件") }
    }
}

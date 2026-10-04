package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.ui.preview.GeometryParameterDisplay
import io.github.assworkbench.domain.AssGeometrySemantic

@Composable
internal fun WorkspaceParameterProjectionPane(
    projection: WorkspaceParameterProjection,
    state: EditorState,
    viewModel: EditorViewModel,
    onRemove: () -> Unit,
    onPresentationChange: (WorkspaceParameterPresentation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val descriptor = WorkspaceParameterCatalog.find(projection.descriptorKey)
    if (descriptor == null) {
        Column(modifier.padding(12.dp)) {
            Text("参数描述已失效：${projection.descriptorKey}")
            Button(onClick = onRemove) { Text("移除") }
        }
        return
    }

    val existingIds = state.document.events.mapTo(hashSetOf()) { it.id }
    val resolution = projection.binding.resolve(
        focusedEventId = state.focusedEventId,
        selectedEventIds = state.selectedEventIds,
        existingEventIds = existingIds,
    )
    val eventId = (resolution as? WorkspaceBindingResolution.Event)?.eventId
    if (eventId == null) {
        Column(
            modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(descriptor.title)
            Text(
                when (resolution) {
                    is WorkspaceBindingResolution.UnresolvedPinnedEvent ->
                        "固定 Event #${resolution.eventId} 已不存在；不回退到当前焦点。"
                    else -> "当前没有可解析的 Event 目标。"
                }
            )
            Button(onClick = onRemove) { Text("移除参数控件") }
        }
        return
    }

    if (projection.descriptorKey == WorkspaceParameterCatalog.positionXY.key) {
        WorkspacePositionProjectionPane(projection, state, viewModel, eventId,
            onRemove, onPresentationChange, modifier)
        return
    }

    if (projection.descriptorKey != WorkspaceParameterCatalog.rotationZ.key) {
        Column(modifier.padding(12.dp)) {
            Text(descriptor.title)
            Text("这个参数已有持久化投影，但首个 live renderer 目前只接通 Rotation Z。")
            Button(onClick = onRemove) { Text("移除参数控件") }
        }
        return
    }

    val committedEvent = state.document.events.first { it.id == eventId }
    val displayEvent = GeometryParameterDisplay.event(
        state.document,
        state.previewDocument,
        state.previewOwnerId,
        eventId,
    ) ?: committedEvent
    val style = state.document.styles.firstOrNull { it.name == committedEvent.style }
    val angle = remember(displayEvent.text, style?.angle) {
        AssGeometrySemantic.inspect(displayEvent.text).rotationZ ?: style?.angle ?: 0.0
    }
    var sliderValue by remember(projection.id, eventId) { mutableFloatStateOf(angle.toFloat()) }
    var numberText by remember(projection.id, eventId) { mutableStateOf(angle.toString()) }
    var sliderActive by remember(projection.id, eventId) { mutableStateOf(false) }
    val ownerId = "geometry:$eventId:${projection.id}"

    LaunchedEffect(angle, projection.presentation, sliderActive) {
        if (!sliderActive) {
            sliderValue = angle.toFloat()
            numberText = angle.toString()
        }
    }
    DisposableEffect(ownerId) {
        onDispose { viewModel.clearTransientPreview(ownerId) }
    }

    Column(
        modifier.padding(12.dp).testTag("parameter-projection-${projection.id.replace(':', '-')}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${descriptor.title} · Event #$eventId")
        Text(
            when (projection.binding) {
                WorkspaceBinding.FollowFocus -> "跟随当前焦点"
                WorkspaceBinding.FollowSelection -> "选择集绑定不受支持"
                is WorkspaceBinding.PinnedEvent -> "固定 Event #${projection.binding.eventId}"
            }
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                WorkspaceParameterPresentation.SLIDER to "Slider",
                WorkspaceParameterPresentation.NUMBER to "数值",
                WorkspaceParameterPresentation.ANGLE_DIAL to "转盘",
            ).forEach { (presentation, label) ->
                FilterChip(
                    selected = projection.presentation == presentation,
                    onClick = { onPresentationChange(presentation) },
                    label = { Text(label) },
                )
            }
        }
        Text("${angle}°")

        when (projection.presentation) {
            WorkspaceParameterPresentation.ANGLE_DIAL -> {
                val enabled = state.previewOwnerId == null || state.previewOwnerId == ownerId
                val sessionId = state.workspaceSessionId
                fun mayWrite(): Boolean {
                    val current = viewModel.state.value
                    val target = projection.binding.resolve(
                        current.focusedEventId, current.selectedEventIds,
                        current.document.events.mapTo(hashSetOf()) { it.id },
                    ) as? WorkspaceBindingResolution.Event
                    return current.workspaceSessionId == sessionId && target?.eventId == eventId &&
                        (current.previewOwnerId == null || current.previewOwnerId == ownerId)
                }
                WorkspaceAngleDialControl(
                    angle = angle,
                    gestureKey = "$sessionId:$ownerId",
                    enabled = enabled,
                    onPreview = { value ->
                        if (mayWrite()) {
                            viewModel.previewEventRotationZ(eventId, value, ownerId = ownerId)
                            true
                        } else false
                    },
                    onCommit = { value ->
                        if (mayWrite()) viewModel.setEventRotationZ(eventId, value)
                    },
                    onCancel = {
                        if (viewModel.state.value.workspaceSessionId == sessionId) {
                            viewModel.clearTransientPreview(ownerId)
                        }
                    },
                    modifier = Modifier.testTag("parameter-projection-dial-${projection.id.replace(':', '-')}"),
                )
                Text("拖动转盘调整角度，松手应用；逆时针增加。")
            }

            WorkspaceParameterPresentation.SLIDER -> {
                Slider(
                    value = sliderValue.coerceIn(-180f, 180f),
                    onValueChange = { value ->
                        sliderActive = true
                        sliderValue = value
                        viewModel.previewEventRotationZ(
                            eventId,
                            value.toDouble(),
                            ownerId = ownerId,
                        )
                    },
                    onValueChangeFinished = {
                        if (sliderActive) {
                            viewModel.setEventRotationZ(eventId, sliderValue.toDouble())
                            sliderActive = false
                        }
                    },
                    valueRange = -180f..180f,
                    modifier = Modifier.fillMaxWidth()
                        .testTag("parameter-projection-slider-${projection.id.replace(':', '-')}"),
                )
            }

            WorkspaceParameterPresentation.NUMBER -> {
                OutlinedTextField(
                    value = numberText,
                    onValueChange = { raw ->
                        numberText = raw
                        raw.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { value ->
                            viewModel.previewEventRotationZ(eventId, value, ownerId = ownerId)
                        }
                    },
                    label = { Text("角度") },
                    suffix = { Text("°") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .testTag("parameter-projection-number-${projection.id.replace(':', '-')}"),
                )
                Button(
                    onClick = {
                        numberText.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { value ->
                            viewModel.setEventRotationZ(eventId, value)
                        }
                    },
                    enabled = numberText.toDoubleOrNull()?.isFinite() == true,
                    modifier = Modifier.testTag(
                        "parameter-projection-apply-${projection.id.replace(':', '-')}"
                    ),
                ) {
                    Text("应用")
                }
            }

            else -> {
                Text("此显示方式已在 descriptor 中声明，但 live renderer 尚未接通。")
            }
        }

        Button(
            onClick = {
                viewModel.clearTransientPreview(ownerId)
                onRemove()
            },
            modifier = Modifier.testTag("parameter-projection-remove-${projection.id.replace(':', '-')}"),
        ) {
            Text("移除参数控件")
        }
    }
}

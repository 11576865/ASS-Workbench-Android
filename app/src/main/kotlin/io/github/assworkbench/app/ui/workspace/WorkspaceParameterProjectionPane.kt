package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.ui.preview.GeometryParameterDisplay
import io.github.assworkbench.domain.AssGeometrySemantic
import kotlinx.coroutines.flow.collect

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

    // Canvas nodes can be shorter than the parameter form; preserve access to
    // Apply/Cancel/Remove without requiring users to resize the world surface.
    val contentModifier = modifier.verticalScroll(rememberScrollState())

    if (projection.descriptorKey == WorkspaceParameterCatalog.positionXY.key) {
        WorkspacePositionProjectionPane(projection, state, viewModel, eventId,
            onRemove, onPresentationChange, contentModifier)
        return
    }

    if (projection.descriptorKey == WorkspaceParameterCatalog.scaleXY.key ||
        projection.descriptorKey == WorkspaceParameterCatalog.shearXY.key) {
        WorkspaceTransformProjectionPane(projection, state, viewModel, eventId,
            onRemove, onPresentationChange, contentModifier)
        return
    }

    if (projection.descriptorKey != WorkspaceParameterCatalog.rotationZ.key) {
        Column(modifier.padding(12.dp)) {
            Text(descriptor.title)
            Text("这个参数已有持久化投影，但当前 live router 只接通 Rotation Z。")
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
    val sessionId = state.workspaceSessionId
    val ownerId = "geometry:$eventId:${projection.id}"
    val guard = WorkspaceProjectionGuard(sessionId, eventId, ownerId)
    val enabled = state.previewOwnerId == null || state.previewOwnerId == ownerId
    val committedAngle = AssGeometrySemantic.inspect(committedEvent.text).rotationZ ?: style?.angle ?: 0.0
    var sliderValue by remember(sessionId, projection.id, eventId, projection.presentation) { mutableFloatStateOf(angle.toFloat()) }
    var numberText by remember(sessionId, projection.id, eventId, committedEvent.text, style?.angle, projection.presentation) {
        mutableStateOf(committedAngle.toString())
    }
    var gestureActive by remember(sessionId, projection.id, eventId, projection.presentation) { mutableStateOf(false) }
    var revision by remember(sessionId, projection.id, eventId) { mutableLongStateOf(0L) }

    var sliderGeneration by remember(sessionId, ownerId, projection.presentation) { mutableLongStateOf(0L) }
    var ownedPreviewRevision by remember(sessionId, projection.id, eventId, projection.presentation) { mutableStateOf<Long?>(null) }
    fun dispatch(phase: WorkspaceParameterIntentPhase, value: Double? = null, requireOwnedPreview: Boolean = false): Boolean {
        val current = viewModel.state.value
        if (phase == WorkspaceParameterIntentPhase.CANCEL) {
            if (!guard.mayClear(current.workspaceSessionId)) return false
        } else {
            val target = projection.binding.resolve(current.focusedEventId, current.selectedEventIds,
                current.document.events.mapTo(hashSetOf()) { it.id }) as? WorkspaceBindingResolution.Event
            if (!guard.mayWrite(current.workspaceSessionId, target?.eventId, current.previewOwnerId)) return false
            if (requireOwnedPreview && !guard.mayFinish(current.workspaceSessionId, target?.eventId,
                    current.previewOwnerId, current.geometryPreviewRevision, ownedPreviewRevision)) return false
        }
        revision += 1L
        WorkspaceParameterIntentRouter.dispatch(
            intent = WorkspaceParameterIntent(projection.address, phase,
                if (phase == WorkspaceParameterIntentPhase.CANCEL) emptyList() else listOf(requireNotNull(value)), revision),
            resolvedEventId = eventId, viewModel = viewModel,
        )
        if (phase == WorkspaceParameterIntentPhase.PREVIEW) ownedPreviewRevision = viewModel.state.value.geometryPreviewRevision
        else ownedPreviewRevision = null
        return true
    }

    LaunchedEffect(angle, gestureActive, enabled) {
        if (!enabled) gestureActive = false
        if (!gestureActive) sliderValue = angle.toFloat()
    }
    DisposableEffect(sessionId, ownerId, projection.presentation) {
        onDispose { dispatch(WorkspaceParameterIntentPhase.CANCEL) }
    }

    Column(
        contentModifier.padding(12.dp).testTag("parameter-projection-${projection.id.replace(':', '-')}"),
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
                    onClick = {
                        dispatch(WorkspaceParameterIntentPhase.CANCEL)
                        onPresentationChange(presentation)
                    },
                    label = { Text(label) },
                )
            }
        }
        Text("${angle}°")

        when (projection.presentation) {
            WorkspaceParameterPresentation.ANGLE_DIAL -> {
                WorkspaceAngleDialControl(
                    angle = angle,
                    gestureKey = "$sessionId:$ownerId",
                    enabled = enabled,
                    onPreview = { value ->
                        val accepted = dispatch(WorkspaceParameterIntentPhase.PREVIEW, value, requireOwnedPreview = gestureActive)
                        gestureActive = accepted
                        accepted
                    },
                    onCommit = { value ->
                        dispatch(WorkspaceParameterIntentPhase.COMMIT, value, requireOwnedPreview = gestureActive)
                        gestureActive = false
                    },
                    onCancel = {
                        if (viewModel.state.value.workspaceSessionId == sessionId) {
                            dispatch(WorkspaceParameterIntentPhase.CANCEL)
                        }
                        gestureActive = false
                    },
                    modifier = Modifier.testTag("parameter-projection-dial-${projection.id.replace(':', '-')}"),
                )
                Text("拖动转盘调整角度，松手应用；逆时针增加。")
            }

            WorkspaceParameterPresentation.SLIDER -> {
                key(sessionId, ownerId, projection.presentation, sliderGeneration) {
                    var invalidated by remember { mutableStateOf(false) }
                    val interactions = remember { MutableInteractionSource() }
                    LaunchedEffect(interactions) {
                        interactions.interactions.collect { interaction ->
                            if (interaction is DragInteraction.Cancel) {
                                gestureActive = false
                                dispatch(WorkspaceParameterIntentPhase.CANCEL)
                            }
                        }
                    }
                    Slider(
                        value = sliderValue.coerceIn(-180f, 180f), enabled = enabled,
                        interactionSource = interactions,
                        onValueChange = { value ->
                            if (invalidated) return@Slider
                            if (dispatch(WorkspaceParameterIntentPhase.PREVIEW, value.toDouble(), requireOwnedPreview = gestureActive)) {
                                gestureActive = true
                                sliderValue = value
                            } else {
                                invalidated = true
                                gestureActive = false
                                sliderGeneration += 1L
                            }
                        },
                        onValueChangeFinished = {
                            if (!invalidated && gestureActive) dispatch(WorkspaceParameterIntentPhase.COMMIT, sliderValue.toDouble(), requireOwnedPreview = true)
                            gestureActive = false
                        },
                        valueRange = -180f..180f,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("parameter-projection-slider-${projection.id.replace(':', '-')}"),
                    )
                }
            }

            WorkspaceParameterPresentation.NUMBER -> {
                OutlinedTextField(
                    value = if (enabled) numberText else angle.toString(),
                    readOnly = !enabled,
                    onValueChange = { raw ->
                        numberText = raw
                        raw.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { value ->
                            dispatch(WorkspaceParameterIntentPhase.PREVIEW, value)
                        } ?: dispatch(WorkspaceParameterIntentPhase.CANCEL)
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
                            dispatch(WorkspaceParameterIntentPhase.COMMIT, value)
                        }
                    },
                    enabled = enabled && numberText.toDoubleOrNull()?.isFinite() == true,
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

        Button(onClick = {
            dispatch(WorkspaceParameterIntentPhase.CANCEL)
            gestureActive = false
            numberText = committedAngle.toString()
            sliderValue = committedAngle.toFloat()
        }, modifier = Modifier.testTag("parameter-projection-cancel-${projection.id.replace(':', '-')}")) { Text("取消") }

        Button(
            onClick = {
                dispatch(WorkspaceParameterIntentPhase.CANCEL)
                onRemove()
            },
            modifier = Modifier.testTag("parameter-projection-remove-${projection.id.replace(':', '-')}"),
        ) {
            Text("移除参数控件")
        }
    }
}

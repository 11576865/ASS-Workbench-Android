package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.ui.preview.GeometryParameterDisplay
import io.github.assworkbench.domain.AssGeometrySemantic
import kotlin.math.cos
import kotlin.math.sin

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
    var sliderValue by remember(projection.id, eventId) { mutableFloatStateOf(angle.toFloat()) }
    var numberText by remember(projection.id, eventId) { mutableStateOf(angle.toString()) }
    var gestureActive by remember(projection.id, eventId) { mutableStateOf(false) }
    var revision by remember(projection.id, eventId) { mutableLongStateOf(0L) }

    fun dispatch(
        phase: WorkspaceParameterIntentPhase,
        value: Double? = null,
    ) {
        revision += 1L
        WorkspaceParameterIntentRouter.dispatch(
            intent = WorkspaceParameterIntent(
                address = projection.address,
                phase = phase,
                values = if (phase == WorkspaceParameterIntentPhase.CANCEL) {
                    emptyList()
                } else {
                    listOf(requireNotNull(value))
                },
                revision = revision,
            ),
            resolvedEventId = eventId,
            viewModel = viewModel,
        )
    }

    LaunchedEffect(angle, projection.presentation, gestureActive) {
        if (!gestureActive) {
            sliderValue = angle.toFloat()
            numberText = angle.toString()
        }
    }
    DisposableEffect(projection.id, eventId) {
        onDispose {
            WorkspaceParameterIntentRouter.dispatch(
                intent = WorkspaceParameterIntent(
                    address = projection.address,
                    phase = WorkspaceParameterIntentPhase.CANCEL,
                    revision = revision + 1L,
                ),
                resolvedEventId = eventId,
                viewModel = viewModel,
            )
        }
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
                WorkspaceParameterPresentation.ANGLE_DIAL to "角度盘",
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
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("${formatAngle(angle)}°") },
            )
        }

        when (projection.presentation) {
            WorkspaceParameterPresentation.SLIDER -> {
                Slider(
                    value = sliderValue.coerceIn(-180f, 180f),
                    onValueChange = { value ->
                        gestureActive = true
                        sliderValue = value
                        dispatch(WorkspaceParameterIntentPhase.PREVIEW, value.toDouble())
                    },
                    onValueChangeFinished = {
                        if (gestureActive) {
                            dispatch(WorkspaceParameterIntentPhase.COMMIT, sliderValue.toDouble())
                            gestureActive = false
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
                            dispatch(WorkspaceParameterIntentPhase.PREVIEW, value)
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
                            dispatch(WorkspaceParameterIntentPhase.COMMIT, value)
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

            WorkspaceParameterPresentation.ANGLE_DIAL -> {
                RotationAngleDial(
                    angle = angle.toFloat(),
                    onPreview = { value ->
                        gestureActive = true
                        sliderValue = value
                        dispatch(WorkspaceParameterIntentPhase.PREVIEW, value.toDouble())
                    },
                    onCommit = { value ->
                        dispatch(WorkspaceParameterIntentPhase.COMMIT, value.toDouble())
                        gestureActive = false
                    },
                    modifier = Modifier.testTag(
                        "parameter-projection-dial-${projection.id.replace(':', '-')}"
                    ),
                )
            }

            else -> {
                Text("此显示方式已在 descriptor 中声明，但 live renderer 尚未接通。")
            }
        }

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

@Composable
private fun RotationAngleDial(
    angle: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val active = MaterialTheme.colorScheme.primary
    var draft by remember { mutableFloatStateOf(angle) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(angle, dragging) {
        if (!dragging) draft = angle
    }

    Canvas(
        modifier = modifier
            .size(168.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        dragging = true
                        draft = rotationDialAngle(position, size.width.toFloat(), size.height.toFloat())
                        onPreview(draft)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        draft = rotationDialAngle(
                            change.position,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                        onPreview(draft)
                    },
                    onDragEnd = {
                        if (dragging) onCommit(draft)
                        dragging = false
                    },
                    onDragCancel = {
                        dragging = false
                    },
                )
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.38f
        val radians = Math.toRadians(draft.toDouble())
        val hand = Offset(
            x = center.x + sin(radians).toFloat() * radius,
            y = center.y - cos(radians).toFloat() * radius,
        )
        drawCircle(
            color = outline,
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
        )
        drawLine(
            color = active,
            start = center,
            end = hand,
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = active, radius = 7.dp.toPx(), center = hand)
        drawCircle(color = active, radius = 5.dp.toPx(), center = center)
    }
}

internal fun rotationDialAngle(
    position: Offset,
    width: Float,
    height: Float,
): Float {
    require(width > 0f && height > 0f) { "Dial dimensions must be positive." }
    val dx = position.x - width / 2f
    val dy = position.y - height / 2f
    return Math.toDegrees(kotlin.math.atan2(dx.toDouble(), -dy.toDouble())).toFloat()
}

private fun formatAngle(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else "%.1f".format(value)

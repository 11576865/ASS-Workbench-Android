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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.ui.preview.GeometryParameterDisplay
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssGeometrySemantic
import io.github.assworkbench.domain.AssPoint
import io.github.assworkbench.domain.AssPositionMode
import io.github.assworkbench.domain.AssStyle
import io.github.assworkbench.domain.AssTopLevelOverrideSyntax

@Composable
internal fun WorkspacePositionXYProjectionPane(
    projection: WorkspaceParameterProjection,
    state: EditorState,
    viewModel: EditorViewModel,
    eventId: Long,
    onRemove: () -> Unit,
    onPresentationChange: (WorkspaceParameterPresentation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val committedEvent = state.document.events.firstOrNull { it.id == eventId }
    if (committedEvent == null) {
        Column(modifier.padding(12.dp)) {
            Text("目标 Event #$eventId 已不存在。")
            Button(onClick = onRemove) { Text("移除参数控件") }
        }
        return
    }

    val displayEvent = GeometryParameterDisplay.event(
        state.document,
        state.previewDocument,
        state.previewOwnerId,
        eventId,
    ) ?: committedEvent
    val displayGeometry = remember(displayEvent.text) { AssGeometrySemantic.inspect(displayEvent.text) }
    val committedGeometry = remember(committedEvent.text) { AssGeometrySemantic.inspect(committedEvent.text) }
    val position = remember(state.document, displayEvent) {
        when (displayGeometry.positionMode) {
            AssPositionMode.POSITION -> displayGeometry.position
            AssPositionMode.INHERITED -> workspaceInheritedAnchor(
                state.document,
                displayEvent,
                state.document.styles.firstOrNull { it.name == displayEvent.style },
            )
            AssPositionMode.MOVE,
            AssPositionMode.CONFLICT -> null
        }
    }

    if (
        committedGeometry.positionMode == AssPositionMode.MOVE ||
        committedGeometry.positionMode == AssPositionMode.CONFLICT ||
        position == null
    ) {
        Column(
            modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("位置 X/Y · Event #$eventId")
            Text(
                when (committedGeometry.positionMode) {
                    AssPositionMode.MOVE -> "当前 Event 使用 \\move；Position XY 投影不会隐式改写运动路径。"
                    AssPositionMode.CONFLICT -> "当前 Event 同时存在 \\pos 与 \\move；位置投影按 fail-closed 处理。"
                    else -> "当前 Event 的位置锚点无法可靠解析。"
                }
            )
            Button(onClick = onRemove) { Text("移除参数控件") }
        }
        return
    }

    val ownerId = "geometry:$eventId:${projection.id}"
    val sessionId = state.workspaceSessionId
    var revision by remember(projection.id, eventId) { mutableLongStateOf(0L) }
    var xText by remember(projection.id, eventId) { mutableStateOf(position.x.toString()) }
    var yText by remember(projection.id, eventId) { mutableStateOf(position.y.toString()) }
    var gestureActive by remember(projection.id, eventId) { mutableStateOf(false) }

    fun mayWrite(): Boolean {
        val current = viewModel.state.value
        val target = projection.binding.resolve(
            current.focusedEventId,
            current.selectedEventIds,
            current.document.events.mapTo(hashSetOf()) { it.id },
        ) as? WorkspaceBindingResolution.Event
        return current.workspaceSessionId == sessionId &&
            target?.eventId == eventId &&
            (current.previewOwnerId == null || current.previewOwnerId == ownerId)
    }

    fun dispatch(
        phase: WorkspaceParameterIntentPhase,
        x: Double? = null,
        y: Double? = null,
    ): Boolean {
        val current = viewModel.state.value
        if (current.workspaceSessionId != sessionId) return false
        if (phase != WorkspaceParameterIntentPhase.CANCEL && !mayWrite()) return false
        revision += 1L
        WorkspaceParameterIntentRouter.dispatch(
            intent = WorkspaceParameterIntent(
                address = projection.address,
                phase = phase,
                values = if (phase == WorkspaceParameterIntentPhase.CANCEL) {
                    emptyList()
                } else {
                    listOf(requireNotNull(x), requireNotNull(y))
                },
                revision = revision,
            ),
            resolvedEventId = eventId,
            viewModel = viewModel,
        )
        return true
    }

    LaunchedEffect(position.x, position.y, projection.presentation, gestureActive) {
        if (!gestureActive) {
            xText = position.x.toString()
            yText = position.y.toString()
        }
    }
    DisposableEffect(ownerId, sessionId) {
        onDispose { dispatch(WorkspaceParameterIntentPhase.CANCEL) }
    }

    Column(
        modifier.padding(12.dp)
            .testTag("parameter-projection-${projection.id.replace(':', '-')}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("位置 X/Y · Event #$eventId")
        Text("X ${formatWorkspaceCoordinate(position.x)} · Y ${formatWorkspaceCoordinate(position.y)}")

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                WorkspaceParameterPresentation.NUMBER_PAIR to "数值",
                WorkspaceParameterPresentation.XY_PAD to "二维板",
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

        when (projection.presentation) {
            WorkspaceParameterPresentation.NUMBER_PAIR -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = xText,
                        onValueChange = { raw ->
                            xText = raw
                            val px = raw.toDoubleOrNull()
                            val py = yText.toDoubleOrNull()
                            if (px?.isFinite() == true && py?.isFinite() == true) {
                                dispatch(WorkspaceParameterIntentPhase.PREVIEW, px, py)
                            }
                        },
                        label = { Text("X") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                            .testTag("parameter-position-x-${projection.id.replace(':', '-')}"),
                    )
                    OutlinedTextField(
                        value = yText,
                        onValueChange = { raw ->
                            yText = raw
                            val px = xText.toDoubleOrNull()
                            val py = raw.toDoubleOrNull()
                            if (px?.isFinite() == true && py?.isFinite() == true) {
                                dispatch(WorkspaceParameterIntentPhase.PREVIEW, px, py)
                            }
                        },
                        label = { Text("Y") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                            .testTag("parameter-position-y-${projection.id.replace(':', '-')}"),
                    )
                }
                val px = xText.toDoubleOrNull()
                val py = yText.toDoubleOrNull()
                Button(
                    onClick = {
                        if (px?.isFinite() == true && py?.isFinite() == true) {
                            dispatch(WorkspaceParameterIntentPhase.COMMIT, px, py)
                        }
                    },
                    enabled = px?.isFinite() == true && py?.isFinite() == true,
                    modifier = Modifier.testTag(
                        "parameter-position-apply-${projection.id.replace(':', '-')}"
                    ),
                ) {
                    Text("应用位置")
                }
            }

            WorkspaceParameterPresentation.XY_PAD -> {
                WorkspacePositionXYPadControl(
                    x = position.x,
                    y = position.y,
                    maxX = state.document.playResX.toDouble(),
                    maxY = state.document.playResY.toDouble(),
                    gestureKey = "$sessionId:$ownerId",
                    enabled = state.previewOwnerId == null || state.previewOwnerId == ownerId,
                    onPreview = { x, y ->
                        gestureActive = true
                        dispatch(WorkspaceParameterIntentPhase.PREVIEW, x, y)
                    },
                    onCommit = { x, y ->
                        dispatch(WorkspaceParameterIntentPhase.COMMIT, x, y)
                        gestureActive = false
                    },
                    onCancel = {
                        dispatch(WorkspaceParameterIntentPhase.CANCEL)
                        gestureActive = false
                    },
                    modifier = Modifier.testTag(
                        "parameter-position-pad-${projection.id.replace(':', '-')}"
                    ),
                )
                Text("二维板使用 ASS Script Resolution；左上为 0,0，右下为 PlayResX,PlayResY。")
            }

            else -> Text("此显示方式不属于 Position XY descriptor。")
        }

        Button(
            onClick = {
                dispatch(WorkspaceParameterIntentPhase.CANCEL)
                onRemove()
            },
            modifier = Modifier.testTag(
                "parameter-position-remove-${projection.id.replace(':', '-')}"
            ),
        ) {
            Text("移除参数控件")
        }
    }
}

internal fun workspaceInheritedAnchor(
    document: AssDocument,
    event: AssEvent,
    style: AssStyle?,
): AssPoint? {
    val resolvedStyle = style ?: return null
    val tags = AssTopLevelOverrideSyntax.tags(event.text)
    val leadingEnd = AssTopLevelOverrideSyntax.leadingPrefixLength(event.text)
    if (tags.any { tag ->
            tag.start >= leadingEnd &&
                (
                    tag.name.equals("an", ignoreCase = true) ||
                        tag.name.equals("pos", ignoreCase = true) ||
                        tag.name.equals("move", ignoreCase = true)
                    )
        }) return null

    val alignment = tags
        .filter { it.name.equals("an", ignoreCase = true) }
        .lastOrNull()
        ?.value
        ?.toIntOrNull()
        ?: resolvedStyle.alignment
    if (alignment !in 1..9) return null
    if (event.marginL < 0 || event.marginR < 0 || event.marginV < 0) return null
    if (resolvedStyle.marginL < 0 || resolvedStyle.marginR < 0 || resolvedStyle.marginV < 0) return null

    val marginL = if (event.marginL > 0) event.marginL.toDouble() else resolvedStyle.marginL.toDouble()
    val marginR = if (event.marginR > 0) event.marginR.toDouble() else resolvedStyle.marginR.toDouble()
    val marginV = if (event.marginV > 0) event.marginV.toDouble() else resolvedStyle.marginV.toDouble()
    val x = when (alignment) {
        1, 4, 7 -> marginL
        2, 5, 8 -> document.playResX / 2.0
        else -> document.playResX - marginR
    }
    val y = when (alignment) {
        7, 8, 9 -> marginV
        4, 5, 6 -> document.playResY / 2.0
        else -> document.playResY - marginV
    }
    return AssPoint(x, y).takeIf { it.x.isFinite() && it.y.isFinite() }
}

private fun formatWorkspaceCoordinate(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

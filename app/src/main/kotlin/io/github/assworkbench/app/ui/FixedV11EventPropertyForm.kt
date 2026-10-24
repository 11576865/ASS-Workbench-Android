package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.*

/** Figma 85:337. Presentation of the existing session-owned drafts, not a new editor owner. */
@Composable
internal fun FixedV11EventPropertyForm(
    event: AssEvent,
    document: AssDocument,
    timing: EventTimingDraftEntry,
    metadata: EventMetadataDraftEntry,
    onApplyTiming: (SubTime, SubTime) -> Unit,
    onApplyMetadata: (Int, String, Boolean) -> Unit,
    styleDraft: EventTextDraftEntry? = null,
) {
    val times = timing.values
    val values = metadata.values
    val validation = remember(times) { EventTimingInputValidation.parse(times.start, times.end) }
    val timingState = timing.snapshot().classify(EventTimingCanonical.from(event))
    val metadataState = metadata.snapshot().classify(EventMetadataValues.from(event))
    val timingConflict = timingState == RawEventDraftState.EXTERNAL_CONFLICT
    val metadataConflict = metadataState == RawEventDraftState.EXTERNAL_CONFLICT
    val layer = values.layer.toIntOrNull()
    val validLayer = layer != null && layer in -999..999
    Column(Modifier.fillMaxWidth().testTag("fixed-v11-event-properties"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FixedV11PropertyField("Start", times.start, { timing.values = timing.values.copy(start = it) },
            "event-start-${event.id}", errorText = if (validation.startInvalid) "输入 ASS 时间或非负毫秒数" else null)
        FixedV11PropertyField("End", times.end, { timing.values = timing.values.copy(end = it) },
            "event-end-${event.id}", errorText = when {
                validation.endInvalid -> "输入 ASS 时间或非负毫秒数"
                validation.reversed -> "End 不能早于 Start"
                else -> null
            })
        if (timingConflict) Text("时间已在其他面板修改；草稿已保留。覆盖时间将替换当前 Start/End。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("event-timing-conflict-${event.id}"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (timingState != RawEventDraftState.CLEAN) TextButton(
                onClick = { timing.reset(EventTimingCanonical.from(event)) },
                modifier = Modifier.testTag("event-reset-time-${event.id}")) {
                Text(if (timingConflict) "重新载入时间" else "还原时间")
            }
            TextButton(enabled = validation.canApply, onClick = {
                val start = validation.start
                val end = validation.end
                if (validation.canApply && start != null && end != null) onApplyTiming(start, end)
            }, modifier = Modifier.testTag("event-apply-time-${event.id}")) {
                Text(if (timingConflict) "覆盖时间" else "应用时间")
            }
        }
        HorizontalDivider()
        FixedV11PropertyField("Layer", values.layer, { metadata.values = metadata.values.copy(layer = it) },
            "event-layer-${event.id}", errorText = if (validLayer) null else "输入 -999～999 范围内的整数")
        // The Style NAME is an Event field; it is not a Style-inherited value.
        // Show the actual reference and never silently create/fallback a Style.
        val proposedStyle = styleDraft?.value?.text ?: event.style
        if (styleDraft == null) FixedV11PropertyField("Style", event.style, {}, "event-style-${event.id}",
            readOnly = true, source = "Event · Style 引用")
        else {
            var styleMenu by remember(event.id) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Style", Modifier.width(72.dp).padding(top = 12.dp), style = MaterialTheme.typography.labelMedium)
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { styleMenu = true }, modifier = Modifier.fillMaxWidth()
                        .testTag("event-style-${event.id}")) { Text(proposedStyle) }
                    DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                        document.styles.forEach { candidate ->
                            DropdownMenuItem(text = { Text(candidate.name) },
                                modifier = Modifier.testTag("event-style-option-${event.id}-${candidate.name}"),
                                onClick = {
                                    styleDraft.value = androidx.compose.ui.text.input.TextFieldValue(candidate.name)
                                    styleMenu = false
                                })
                        }
                        if (document.styles.isEmpty()) DropdownMenuItem(text = { Text("工程中没有 Style") },
                            enabled = false, onClick = {})
                    }
                }
            }
            Text("Event · Style 引用；不会清除 Raw 内联覆盖", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (styleDraft.snapshot().classify(event.style) == RawEventDraftState.EXTERNAL_CONFLICT)
                Text("Style 引用存在并发修改；草稿保留，审核将阻止覆盖。",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("event-style-conflict-${event.id}"))
            if (proposedStyle != event.style) {
                Text("已保存：${event.style} · 新引用待统一审核", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = {
                    styleDraft.baseText = event.style
                    styleDraft.value = androidx.compose.ui.text.input.TextFieldValue(event.style)
                }, modifier = Modifier.testTag("event-reset-style-${event.id}")) { Text("还原 Style 引用") }
            }
        }
        Text(if (document.styles.any { it.name == proposedStyle }) "引用的 Style 已找到" else "引用的 Style 未找到",
            style = MaterialTheme.typography.labelSmall,
            color = if (document.styles.any { it.name == proposedStyle }) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().testTag("fixed-v11-style-source-${event.id}"))
        FixedV11PropertyField("Actor", values.actor, { metadata.values = metadata.values.copy(actor = it) },
            "event-actor-${event.id}")
        FilterChip(selected = values.comment,
            onClick = { metadata.values = metadata.values.copy(comment = !metadata.values.comment) },
            label = { Text(if (values.comment) "Comment" else "Dialogue") },
            modifier = Modifier.testTag("event-comment-${event.id}"))
        if (metadataConflict) Text("元数据已在其他面板修改；草稿已保留。以草稿覆盖将替换当前 Layer、Actor 和 Comment。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("event-metadata-conflict-${event.id}"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { metadata.reset(EventMetadataValues.from(event)) },
                modifier = Modifier.testTag("event-reset-metadata-${event.id}")) {
                Text(if (metadataConflict) "重新载入" else "还原元数据")
            }
            TextButton(enabled = validLayer, onClick = {
                if (validLayer && layer != null) onApplyMetadata(layer, values.actor, values.comment)
            }, modifier = Modifier.testTag("event-apply-metadata-${event.id}")) {
                Text(if (metadataConflict) "以草稿覆盖" else "应用元数据")
            }
        }
        HorizontalDivider()
        val color = remember(document, event) {
            runCatching { AssEffectiveInspector.inspect(document, event).firstOrNull { it.name == "Primary" }?.let(::r3ValueEvidence) }.getOrNull()
        }
        Text("正文主色 · 已保存 Raw", style = MaterialTheme.typography.titleSmall)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(color?.effectiveValue ?: "颜色来源未解析", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("fixed-v11-primary-value-${event.id}"))
                Text(color?.let { it.source + if (it.variableAcrossSpanOrTime) " · 首段/时变" else "" } ?: "请使用 Raw ASS 或 QC 核查",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text("已保存 Raw 不包含当前草稿", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Label, editable surface and per-field provenance share the design's two-column grid. */
@Composable
private fun FixedV11PropertyField(label: String, value: String, onChange: (String) -> Unit,
    tag: String, errorText: String? = null, readOnly: Boolean = false, source: String = "Event 字段") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(72.dp).padding(top = 10.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicTextField(value = value, onValueChange = onChange, singleLine = true, readOnly = readOnly,
                textStyle = MaterialTheme.typography.bodyMedium.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp).testTag(tag)
                    .semantics { contentDescription = label; if (errorText != null) error(errorText) },
                decorationBox = { field ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart) { field() }
                    }
                })
            if (errorText != null) Text(errorText, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
            Surface(Modifier.align(Alignment.End), color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small) {
                Text(source, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

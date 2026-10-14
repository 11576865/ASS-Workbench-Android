package io.github.assworkbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEffectiveInspector
import io.github.assworkbench.domain.AssEffectiveValue
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.app.ui.workspace.WorkspaceBinding

/**
 * Read provenance belongs to the effective value, NOT to the write destination.
 * This panel never opens an editor or changes a ToolInstance binding.
 */
/**
 * Provenance can describe a pinned Event, but the shared TEXT tool follows Focus.
 * Never offer an edit shortcut that could silently retarget to another Event.
 */
internal fun r3RawShortcutAllowed(
    binding: WorkspaceBinding,
    displayedEventId: Long?,
    focusedEventId: Long?,
): Boolean = binding == WorkspaceBinding.FollowFocus &&
    displayedEventId != null && displayedEventId == focusedEventId

internal data class R3ValueEvidence(
    val label: String,
    val effectiveValue: String,
    val source: String,
    val variableAcrossSpanOrTime: Boolean,
)

internal fun r3ValueEvidence(value: AssEffectiveValue): R3ValueEvidence {
    val source = when {
        value.eventValue != null -> "Event 字段"
        value.overrideValue != null -> "Event override"
        value.name == "Position" -> "对齐锚点（派生）"
        value.effectiveStyle != null -> "Style · ${value.effectiveStyle}"
        else -> "默认值 / 未明"
    }
    val title = when (value.name) {
        "Position" -> "位置"
        "Alignment" -> "对齐"
        "Size" -> "字号"
        "Font" -> "字体"
        "Primary" -> "主色"
        "Border" -> "描边"
        "Shadow" -> "阴影"
        else -> value.name
    }
    return R3ValueEvidence(
        label = title, effectiveValue = value.effectiveValue,
        source = source, variableAcrossSpanOrTime = value.spanDependent,
    )
}

@Composable
internal fun R3InspectorProvenance(
    document: AssDocument,
    event: AssEvent,
    modifier: Modifier = Modifier,
    onOpenRaw: (() -> Unit)? = null,
) {
    val result = remember(document, event) {
        runCatching {
            val map = AssEffectiveInspector.inspect(document, event).associateBy { it.name }
            listOf("Position", "Alignment", "Size", "Font", "Primary", "Border", "Shadow")
                .mapNotNull { map[it]?.let(::r3ValueEvidence) }
        }
    }
    var expanded by rememberSaveable(event.id) { mutableStateOf(false) }
    Surface(modifier = modifier.testTag("r3-parameter-provenance"),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("有效值 / 读取来源", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f))
                if (result.isSuccess) {
                    TextButton(onClick = { expanded = !expanded },
                        modifier = Modifier.testTag("r3-origin-expand")) {
                        Text(if (expanded) "收起" else "更多来源")
                    }
                }
            }
            result.onSuccess { entries ->
                Column(Modifier.fillMaxWidth().heightIn(max = if (expanded) 240.dp else 112.dp)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    (if (expanded) entries else entries.take(3)).forEach { info ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(info.label, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(46.dp))
                            Text(info.effectiveValue, style = MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text(
                                info.source + if (info.variableAcrossSpanOrTime) " · 首段/时变" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (info.variableAcrossSpanOrTime)
                                    MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp),
                            )
                        }
                    }
                }
                Text("只读来源；写入范围以实际工具为准",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Raw ASS · 当前 Event", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f))
                    if (onOpenRaw != null) {
                        TextButton(
                            onClick = onOpenRaw,
                            modifier = Modifier.testTag("r3-open-raw-editor"),
                        ) { Text("编辑 ↗") }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().testTag("r3-raw-event-value"),
                ) {
                    SelectionContainer {
                        Text(
                            event.text.ifEmpty { "（空字幕）" },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }.onFailure {
                Text("来源未解析：请使用 Raw ASS 或 QC 核查",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

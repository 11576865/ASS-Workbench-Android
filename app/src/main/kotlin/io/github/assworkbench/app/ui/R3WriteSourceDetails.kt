package io.github.assworkbench.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEffectiveInspector
import io.github.assworkbench.domain.AssEffectiveValue
import io.github.assworkbench.domain.AssEvent

/**
 * R3's companion read-only source pane uses actual ASS effective values.
 * Its information is not a write target and does not approve an unsafe draft.
 */
internal data class R3WriteSourceSummary(
    val property: String,
    val originalValue: String,
    val draftValue: String,
    val styleValue: String,
    val originLabel: String,
    val styleLabel: String,
    val changedEffectiveProperties: Int,
    val spanOrTimeDependent: Boolean,
)

internal fun r3WriteSourceSummary(
    document: AssDocument,
    event: AssEvent,
    receipt: R3TextWriteReceipt,
): R3WriteSourceSummary? {
    if (event.id != receipt.eventId || event.text != receipt.canonicalText ||
        event.style != receipt.eventStyle) return null
    return runCatching {
        val original = AssEffectiveInspector.inspect(document, event).associateBy { it.name }
        val after = AssEffectiveInspector.inspect(document, event.copy(text = receipt.draftText))
            .associateBy { it.name }
        val changed = original.keys.filter { name ->
            original[name]?.effectiveValue != after[name]?.effectiveValue ||
                original[name]?.spanDependent != after[name]?.spanDependent
        }
        val selected = (listOf(
            "Size", "Font", "Position", "Alignment", "Primary", "Outline Color",
            "Border", "Shadow"
        ).firstOrNull { it in changed } ?: "Size")
        val current = original[selected] ?: return@runCatching null
        val candidate = after[selected] ?: return@runCatching null
        val label = when (selected) {
            "Size" -> "字号"
            "Font" -> "字体"
            "Position" -> "位置"
            "Alignment" -> "对齐"
            "Primary" -> "主色"
            "Outline Color" -> "描边色"
            "Border" -> "描边"
            "Shadow" -> "阴影"
            else -> selected
        }
        val origin = when {
            current.eventValue != null -> "Event #${event.id} · 独立字段"
            current.overrideValue != null -> "Event #${event.id} · 局部 override"
            current.effectiveStyle != null -> "Style · ${current.effectiveStyle}"
            else -> "对齐锚点 / 派生值"
        }
        R3WriteSourceSummary(
            property = label,
            originalValue = current.effectiveValue,
            draftValue = candidate.effectiveValue,
            styleValue = current.styleValue,
            originLabel = origin,
            styleLabel = current.effectiveStyle ?: event.style,
            changedEffectiveProperties = changed.size,
            spanOrTimeDependent = current.spanDependent || candidate.spanDependent,
        )
    }.getOrNull()
}

@Composable
internal fun R3WriteSourceDetails(
    receipt: R3TextWriteReceipt,
    summary: R3WriteSourceSummary?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    Surface(
        modifier = modifier.testTag("r3-write-source-details"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                (summary?.property ?: "ASS") + " · 来源详情",
                style = MaterialTheme.typography.headlineSmall,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (summary != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("当前有效值", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(summary.originalValue,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.testTag("r3-source-original"))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("正在编辑的草稿", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(summary.draftValue,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.testTag("r3-source-draft"))
                        }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("读取链", style = MaterialTheme.typography.titleSmall)
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(summary.originLabel,
                                style = MaterialTheme.typography.bodyMedium)
                            Text("当前读取来源，不决定写入目的地",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Style: ${summary.styleLabel} · ${summary.property} ${summary.styleValue}",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("共享基线：本次 Event Raw 修改不会改写共享 Style",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text("写入目标（不会因查看来源而变化）",
                        style = MaterialTheme.typography.titleSmall)
                    Text("Event #${receipt.eventId} · 局部 Text · 影响 1 条",
                        color = MaterialTheme.colorScheme.primary)
                    if (summary.changedEffectiveProperties > 1) {
                        Text("注意：草稿涉及 ${summary.changedEffectiveProperties} 项有效值变化；此处仅展示首个重点参数。",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (summary.spanOrTimeDependent) {
                        Text("包含分段或时间相关值；显示的只是首段状态，不能视为全程常量。",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("复杂 ASS 的 \\r、\\t、分段标签应按范围与时间刻分析，不能据此伪造唯一静态来源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("有效值来源暂时无法可靠解析；Raw ASS 原文仍由左侧写入核对显示。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("不猜测 Style/override 来源，也不改变原有草稿、写入目标和冲突保护。",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()
                .testTag("r3-source-back")) {
                Text("返回编辑器")
            }
        }
    }
}

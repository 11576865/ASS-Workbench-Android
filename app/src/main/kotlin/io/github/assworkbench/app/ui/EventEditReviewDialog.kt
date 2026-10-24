package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.*

@Composable
internal fun EventEditReviewDialog(receipt: EventEditReviewReceipt, current: AssEvent?,
    draftsUnchanged: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit, styleNames: Set<String> = emptySet()) {
    val problems = receipt.validationErrors(current, styleNames) +
        if (draftsUnchanged) emptyList() else listOf("审核打开后草稿已改变，请返回后重新审核")
    val next = receipt.applyTo(current, styleNames)
    AlertDialog(onDismissRequest = onDismiss,
        modifier = Modifier.testTag("r3-write-review"),
        title = { Text("审核当前字幕 · #${receipt.eventId}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${receipt.changedGroupCount} 组修改 · 1 条字幕 · 一次应用 / 一次撤销",
                    style = MaterialTheme.typography.labelMedium)
                if (receipt.textChanged) {
                    Text("正文 · Raw ASS", style = MaterialTheme.typography.titleSmall)
                    ReviewValue("修改前", receipt.text.baseText, "r3-write-before")
                    ReviewValue("修改后", receipt.text.text, "r3-write-after")
                }
                if (receipt.timingChanged) {
                    Text("时间", style = MaterialTheme.typography.titleSmall)
                    ReviewValue("修改前", "${SubTime(receipt.timing.baseline.startMillis).toAss()} → ${SubTime(receipt.timing.baseline.endMillis).toAss()}", "event-review-time-before")
                    ReviewValue("修改后", "${receipt.timing.values.start} → ${receipt.timing.values.end}", "event-review-time-after")
                }
                if (receipt.metadataChanged) {
                    fun describe(v: EventMetadataValues) = "Layer ${v.layer} · Actor ${v.actor.ifEmpty { "（空）" }} · ${if (v.comment) "Comment" else "Dialogue"}"
                    Text("元数据", style = MaterialTheme.typography.titleSmall)
                    ReviewValue("修改前", describe(receipt.metadata.baseline), "event-review-meta-before")
                    ReviewValue("修改后", describe(receipt.metadata.values), "event-review-meta-after")
                }
                if (receipt.styleChanged) {
                    Text("Style 引用", style = MaterialTheme.typography.titleSmall)
                    ReviewValue("修改前", receipt.style?.baseText.orEmpty(), "event-review-style-before")
                    ReviewValue("修改后", receipt.style?.text.orEmpty(), "event-review-style-after")
                }
                Text("Style 定义、其他字幕和其他专业工具草稿不在本次应用范围。",
                    style = MaterialTheme.typography.labelSmall)
                problems.forEach { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = problems.isEmpty() && receipt.hasChanges && next != null && next != current,
                modifier = Modifier.testTag("r3-write-confirm")) { Text("应用审核修改") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("返回编辑") } })
}

@Composable
private fun ReviewValue(label: String, value: String, tag: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
            SelectionContainer {
                Text(value.ifEmpty { "（空）" }, Modifier.fillMaxWidth().padding(8.dp).testTag(tag),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

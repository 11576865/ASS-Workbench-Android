package io.github.assworkbench.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.assworkbench.domain.RawEventDraftPolicy
import io.github.assworkbench.domain.RawEventDraftState

/**
 * One Event-local, Raw Text write receipt. This is a read-only projection of
 * the existing draft and canonical text. It never chooses a write target from
 * provenance, and it is NOT a new domain writer or global Save operation.
 */
internal data class R3TextWriteReceipt(
    val eventId: Long,
    val eventStyle: String,
    val baseText: String,
    val canonicalText: String,
    val draftText: String,
) {
    val state: RawEventDraftState
        get() = RawEventDraftPolicy.classify(baseText, draftText, canonicalText)

    /** Re-check at click time; another editor can change this Event while open. */
    fun mayCommit(currentEventText: String?): Boolean =
        currentEventText != null &&
            state == RawEventDraftState.DRAFT_ONLY &&
            currentEventText == canonicalText &&
            canonicalText == baseText &&
            draftText != canonicalText
}

/**
 * R3 write-confirmation screen: exact WHO / WHERE / HOW MANY, current Raw
 * and candidate Raw, style unaffected, and fail-closed external revision.
 */
@Composable
internal fun R3TextWriteReviewDialog(
    receipt: R3TextWriteReceipt,
    currentEventText: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val canCommit = receipt.mayCommit(currentEventText)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f).widthIn(max = 610.dp)
                .testTag("r3-write-review"),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 18.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("应用更改 · 写入核对",
                    style = MaterialTheme.typography.headlineSmall)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("本次命令：更新 Event Raw ASS Text",
                    style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth()) {
                    Text("写入对象", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(122.dp))
                    Text("Event #${receipt.eventId} · 仅当前字幕",
                        color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("影响范围", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(122.dp))
                    Text("1 条 · 不修改其他 Event")
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("共享样式", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(122.dp))
                    Text("${receipt.eventStyle} · 保持不变")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Raw ASS 更改预览",
                    style = MaterialTheme.typography.titleMedium)
                Column(Modifier.fillMaxWidth().heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Column(Modifier.fillMaxWidth().padding(9.dp)) {
                            Text("− 当前 Event", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall)
                            Text(receipt.canonicalText,
                                modifier = Modifier.testTag("r3-write-before"),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(9.dp)) {
                            Text("+ 待提交草稿", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall)
                            Text(receipt.draftText,
                                modifier = Modifier.testTag("r3-write-after"),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!canCommit) {
                    Text(
                        when {
                            currentEventText == null ->
                                "目标 Event 已不存在；已停止写入，草稿保持不变。"
                            receipt.state == RawEventDraftState.EXTERNAL_CONFLICT ||
                                currentEventText != receipt.canonicalText ->
                                "Event 原文已被其他编辑器修改；停止提交，返回编辑器重新核对。"
                            else -> "没有可提交的文本修改。"
                        },
                        modifier = Modifier.testTag("r3-write-conflict"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("提交前再次核对 Event 当前版本；只写入这一条字幕。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f).testTag("r3-write-cancel"),
                        onClick = onDismiss,
                    ) { Text("返回编辑器") }
                    Button(
                        modifier = Modifier.weight(1f).testTag("r3-write-confirm"),
                        enabled = canCommit,
                        onClick = onConfirm,
                    ) { Text("确认应用") }
                }
            }
        }
    }
}

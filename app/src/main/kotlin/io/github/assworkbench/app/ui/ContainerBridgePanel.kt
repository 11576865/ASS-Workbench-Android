package io.github.assworkbench.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.ContainerBridgeState
import io.github.assworkbench.app.ContainerCompatibilityStatus
import io.github.assworkbench.app.ContainerEditPlanUi
import io.github.assworkbench.app.ContainerInventoryEvidence
import io.github.assworkbench.app.ContainerResourceChange
import io.github.assworkbench.app.ContainerResourceKind
import io.github.assworkbench.app.ContainerResourceUi
import io.github.assworkbench.app.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerBridgePanel(
    state: ContainerBridgeState,
    editPlan: ContainerEditPlanUi,
    viewModel: EditorViewModel,
    onSaveMkv: () -> Unit,
    dirty: Boolean,
    modifier: Modifier = Modifier,
) {
    if (state.uri == null) return
    var pendingTrackNumber by remember { mutableStateOf<Long?>(null) }
    var replacementTarget by remember { mutableStateOf<ContainerResourceUi?>(null) }
    var metadataTarget by remember { mutableStateOf<ContainerResourceUi?>(null) }
    var metadataName by remember { mutableStateOf("") }
    var metadataDescription by remember { mutableStateOf("") }
    var extractTarget by remember { mutableStateOf<ContainerResourceUi?>(null) }
    val context = LocalContext.current
    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.addContainerAttachments(uris)
    }
    val replacementPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = replacementTarget
        replacementTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val attachmentTarget = target.attachmentTarget ?: return@rememberLauncherForActivityResult
        viewModel.planExistingAttachmentReplacement(
            target = attachmentTarget,
            originalName = target.title,
            uri = uri,
        )
    }
    val extractPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = extractTarget
        extractTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val attachmentTarget = target.attachmentTarget ?: return@rememberLauncherForActivityResult
        viewModel.extractContainerAttachment(
            target = attachmentTarget,
            name = target.title,
            outputUri = uri,
        )
    }

    val actualCount = state.resources.count { it.change != ContainerResourceChange.REMOVED }
    val added = state.resources.count { it.change == ContainerResourceChange.ADDED }
    val removed = state.resources.count { it.change == ContainerResourceChange.REMOVED }
    val modified = state.resources.count { it.change == ContainerResourceChange.MODIFIED }
    val unresolved = state.resources.count { it.change == ContainerResourceChange.UNRESOLVED }

    Column(
        modifier.fillMaxWidth().padding(vertical = WorkbenchDimens.Micro),
        verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
        ) {
            Column(Modifier.weight(1f)) {
                Text(state.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append("$actualCount 项 · ")
                        append(when (state.inventoryEvidence) {
                        ContainerInventoryEvidence.BASELINE -> "首次检测"
                        ContainerInventoryEvidence.CURRENT_SOURCE -> "重新检测"
                            ContainerInventoryEvidence.VERIFIED_OUTPUT -> "已验证输出"
                        })
                        if (state.pendingAttachments.isNotEmpty()) {
                            append(" · 待添加 ").append(state.pendingAttachments.size)
                        }
                        if (state.pendingAttachmentRemovals.isNotEmpty()) {
                            append(" · 待删除 ").append(state.pendingAttachmentRemovals.size)
                        }
                        if (state.pendingAttachmentReplacements.isNotEmpty()) {
                            append(" · 待替换 ").append(state.pendingAttachmentReplacements.size)
                        }
                        if (state.pendingAttachmentMetadataEdits.isNotEmpty()) {
                            append(" · 待改信息 ").append(state.pendingAttachmentMetadataEdits.size)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            ContainerIconButton(
                label = "重新检测容器内容",
                enabled = !state.loading && !state.writeBackBusy,
                onClick = viewModel::rescanContainer,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "重新检测容器内容")
            }
            ContainerIconButton(
                label = "添加附件",
                enabled = !state.loading && !state.writeBackBusy,
                onClick = { attachmentPicker.launch(arrayOf("*/*")) },
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = "添加附件")
            }
            ContainerIconButton(
                label = "保存为新 MKV；验证通过后写入，源文件不原地修改",
                enabled = editPlan.executable &&
                    !state.loading &&
                    !state.writeBackBusy,
                onClick = onSaveMkv,
            ) {
                Icon(Icons.Filled.Save, contentDescription = "保存为新 MKV")
            }
        }

        if (added + removed + modified + unresolved > 0) {
            Text(
                buildString {
                    if (added > 0) append("+$added ")
                    if (removed > 0) append("−$removed ")
                    if (modified > 0) append("~$modified ")
                    if (unresolved > 0) append("?$unresolved")
                }.trim(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("container-diff-summary"),
            )
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (!state.loading && state.resources.isEmpty() && state.error == null) {
            Text(
                "未检测到可列出的容器内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.resources.forEachIndexed { index, resource ->
            if (index > 0) HorizontalDivider()
            val attachmentTarget = resource.attachmentTarget
            val pendingRemoval = attachmentTarget?.let { target ->
                state.pendingAttachmentRemovals.any { it.target == target }
            } == true
            val pendingReplacement = attachmentTarget?.let { target ->
                state.pendingAttachmentReplacements.firstOrNull { it.target == target }
            }
            val pendingMetadata = attachmentTarget?.let { target ->
                state.pendingAttachmentMetadataEdits.firstOrNull { it.target == target }
            }
            ContainerResourceRow(
                resource = resource,
                selected = resource.editableAss && resource.trackNumber == state.selectedTrackNumber,
                enabled = resource.editableAss &&
                    resource.change != ContainerResourceChange.REMOVED &&
                    !state.writeBackBusy,
                attachmentActionsEnabled = attachmentTarget != null &&
                    resource.change != ContainerResourceChange.REMOVED &&
                    !state.writeBackBusy &&
                    !state.attachmentExtractBusy &&
                    state.inventoryEvidence != ContainerInventoryEvidence.VERIFIED_OUTPUT,
                pendingRemoval = pendingRemoval,
                pendingReplacementName = pendingReplacement?.name,
                pendingMetadataName = pendingMetadata?.name,
                onClick = {
                    val trackNumber = resource.trackNumber ?: return@ContainerResourceRow
                    if (trackNumber == state.selectedTrackNumber) return@ContainerResourceRow
                    if (dirty) {
                        pendingTrackNumber = trackNumber
                    } else {
                        viewModel.selectContainerTrack(trackNumber)
                    }
                },
                onRemoveAttachment = attachmentTarget?.let { target ->
                    {
                        viewModel.planExistingAttachmentRemoval(
                            target = target,
                            name = resource.title,
                        )
                    }
                },
                onReplaceAttachment = attachmentTarget?.let {
                    {
                        replacementTarget = resource
                        replacementPicker.launch(arrayOf("*/*"))
                    }
                },
                onEditAttachmentMetadata = attachmentTarget?.let {
                    {
                        metadataTarget = resource
                        metadataName = pendingMetadata?.name ?: resource.title
                        metadataDescription = pendingMetadata?.description ?: resource.attachmentDescription
                    }
                },
                onExtractAttachment = attachmentTarget?.let {
                    {
                        extractTarget = resource
                        extractPicker.launch(resource.title)
                    }
                },
                onCancelAttachmentEdit = attachmentTarget?.let { target ->
                    {
                        viewModel.cancelExistingAttachmentRemoval(target)
                        viewModel.cancelExistingAttachmentReplacement(target)
                        viewModel.cancelExistingAttachmentMetadata(target)
                    }
                },
            )
        }

        if (state.pendingAttachments.isNotEmpty()) {
            HorizontalDivider()
            Text(
                "待写入附件 · ${state.pendingAttachments.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            state.pendingAttachments.forEach { attachment ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(attachment.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            buildString {
                                append(attachment.mimeType)
                                attachment.sizeBytes?.let { append(" · ").append(formatPendingBytes(it)) }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        enabled = !state.writeBackBusy,
                        onClick = { viewModel.removeContainerAttachment(attachment.uri) },
                    ) {
                        Icon(Icons.Filled.RemoveCircle, contentDescription = "移除待写入附件")
                    }
                }
            }
        }

        HorizontalDivider()
        ContainerPreflightSummary(
            plan = editPlan,
            modifier = Modifier.testTag("container-preflight-summary"),
        )

        if (state.skippedAttachmentCount > 0) {
            Text(
                "${state.skippedAttachmentCount} 个附件未完整载入（大小限制或结构异常）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!state.writeBackAvailable) {
            Text(
                "当前 ABI 暂无 MKV 写回工具",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        pendingTrackNumber?.let { trackNumber ->
            val target = state.tracks.firstOrNull { it.number == trackNumber }
            AlertDialog(
                onDismissRequest = { pendingTrackNumber = null },
                title = { Text("放弃当前轨的未保存修改？") },
                text = {
                    Text(
                        "切换到 " + (target?.name ?: "Track #$trackNumber") +
                            " 会丢弃当前 ASS 轨尚未保存的修改与对应恢复日志。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingTrackNumber = null
                        viewModel.selectContainerTrack(trackNumber, discardUnsaved = true)
                    }) { Text("放弃修改并切换") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingTrackNumber = null }) { Text("取消") }
                },
            )
        }

        metadataTarget?.let { resource ->
            val target = resource.attachmentTarget
            AlertDialog(
                onDismissRequest = { metadataTarget = null },
                title = { Text("附件信息") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = metadataName,
                            onValueChange = { metadataName = it },
                            label = { Text("文件名") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = metadataDescription,
                            onValueChange = { metadataDescription = it },
                            label = { Text("描述") },
                            minLines = 2,
                        )
                        Text(
                            "修改只进入写入计划；源 MKV 不会原地改变。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = target != null && metadataName.isNotBlank(),
                        onClick = {
                            val resolved = target ?: return@TextButton
                            viewModel.planExistingAttachmentMetadata(
                                target = resolved,
                                originalName = resource.title,
                                name = metadataName,
                                description = metadataDescription,
                            )
                            metadataTarget = null
                        },
                    ) { Text("加入计划") }
                },
                dismissButton = {
                    TextButton(onClick = { metadataTarget = null }) { Text("取消") }
                },
            )
        }
    }
}


@Composable
internal fun ContainerPreflightSummary(
    plan: ContainerEditPlanUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "写入计划",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (plan.executable) "可执行 · ${plan.mutations.size} 项" else "未就绪 · ${plan.mutations.size} 项",
                style = MaterialTheme.typography.labelSmall,
                color = if (plan.executable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (plan.mutations.isEmpty()) {
            Text(
                "当前没有待写入修改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            plan.mutations.forEach { mutation ->
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(mutation.title, style = MaterialTheme.typography.bodySmall)
                    Text(
                        mutation.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text("兼容性 / 能力预检", style = MaterialTheme.typography.labelLarge)
        plan.checks.forEach { check ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    when (check.status) {
                        ContainerCompatibilityStatus.SUPPORTED -> "支持"
                        ContainerCompatibilityStatus.WARNING -> "警告"
                        ContainerCompatibilityStatus.UNSUPPORTED -> "阻塞"
                        ContainerCompatibilityStatus.UNKNOWN -> "未知"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (check.status) {
                        ContainerCompatibilityStatus.SUPPORTED -> MaterialTheme.colorScheme.primary
                        ContainerCompatibilityStatus.WARNING -> MaterialTheme.colorScheme.tertiary
                        ContainerCompatibilityStatus.UNSUPPORTED -> MaterialTheme.colorScheme.error
                        ContainerCompatibilityStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 2.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(check.title, style = MaterialTheme.typography.bodySmall)
                    Text(
                        check.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContainerResourceRow(
    resource: ContainerResourceUi,
    selected: Boolean,
    enabled: Boolean,
    attachmentActionsEnabled: Boolean,
    pendingRemoval: Boolean,
    pendingReplacementName: String?,
    pendingMetadataName: String?,
    onClick: () -> Unit,
    onRemoveAttachment: (() -> Unit)?,
    onReplaceAttachment: (() -> Unit)?,
    onEditAttachmentMetadata: (() -> Unit)?,
    onExtractAttachment: (() -> Unit)?,
    onCancelAttachmentEdit: (() -> Unit)?,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        color = background,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("container-resource-" + resource.rowKey)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = when (resource.kind) {
                    ContainerResourceKind.VIDEO -> Icons.Filled.Movie
                    ContainerResourceKind.AUDIO -> Icons.Filled.Audiotrack
                    ContainerResourceKind.SUBTITLE -> Icons.Filled.Subtitles
                    ContainerResourceKind.FONT -> Icons.Filled.FontDownload
                    ContainerResourceKind.ATTACHMENT -> Icons.Filled.AttachFile
                    ContainerResourceKind.CHAPTERS -> Icons.Filled.List
                    ContainerResourceKind.OTHER -> Icons.Filled.HelpOutline
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(resource.title, style = MaterialTheme.typography.bodyMedium)
                if (resource.detail.isNotBlank()) {
                    Text(
                        resource.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "当前编辑轨道",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            when {
                pendingRemoval -> {
                    Text("待删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { onCancelAttachmentEdit?.invoke() }) { Text("取消") }
                }
                pendingReplacementName != null -> {
                    Text(
                        "→ $pendingReplacementName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { onCancelAttachmentEdit?.invoke() }) { Text("取消") }
                }
                pendingMetadataName != null -> {
                    Text(
                        "信息 → $pendingMetadataName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { onCancelAttachmentEdit?.invoke() }) { Text("取消") }
                }
                attachmentActionsEnabled -> {
                    TextButton(
                        onClick = { onExtractAttachment?.invoke() },
                        enabled = onExtractAttachment != null,
                    ) { Text("提取") }
                    TextButton(
                        onClick = { onEditAttachmentMetadata?.invoke() },
                        enabled = onEditAttachmentMetadata != null,
                    ) { Text("信息") }
                    IconButton(
                        onClick = { onReplaceAttachment?.invoke() },
                        enabled = onReplaceAttachment != null,
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "替换附件")
                    }
                    IconButton(
                        onClick = { onRemoveAttachment?.invoke() },
                        enabled = onRemoveAttachment != null,
                    ) {
                        Icon(Icons.Filled.RemoveCircle, contentDescription = "删除附件")
                    }
                }
            }
            ResourceChangeMark(resource.change)
        }
    }
}

@Composable
private fun ResourceChangeMark(change: ContainerResourceChange) {
    val pair = when (change) {
        ContainerResourceChange.UNCHANGED -> null
        ContainerResourceChange.ADDED -> Icons.Filled.AddCircle to "新加入"
        ContainerResourceChange.REMOVED -> Icons.Filled.RemoveCircle to "已删除"
        ContainerResourceChange.MODIFIED -> Icons.Filled.Edit to "已修改"
        ContainerResourceChange.UNRESOLVED -> Icons.Filled.HelpOutline to "身份不确定"
    } ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            pair.first,
            contentDescription = pair.second,
            modifier = Modifier.size(16.dp),
            tint = if (change == ContainerResourceChange.REMOVED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Text(
            pair.second,
            style = MaterialTheme.typography.labelSmall,
            color = if (change == ContainerResourceChange.REMOVED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContainerIconButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, content = content)
    }
}


private fun formatPendingBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes.toDouble() / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes.toDouble() / 1024.0)
    else -> "$bytes B"
}

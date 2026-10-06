package io.github.assworkbench.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import io.github.assworkbench.app.ContainerTrackImportCandidateUi
import io.github.assworkbench.app.ContainerTrackImportSourceKind
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.MediaImportDisposition
import io.github.assworkbench.app.MediaImportTrackKind

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
    var trackMetadataTarget by remember { mutableStateOf<ContainerResourceUi?>(null) }
    var trackMetadataName by remember { mutableStateOf("") }
    var trackMetadataLanguage by remember { mutableStateOf("") }
    var trackMetadataLanguageBcp47 by remember { mutableStateOf("") }
    var trackMetadataDefault by remember { mutableStateOf(false) }
    var trackMetadataForced by remember { mutableStateOf(false) }
    var trackMetadataHearingImpaired by remember { mutableStateOf(false) }
    var trackMetadataVisualImpaired by remember { mutableStateOf(false) }
    var trackMetadataTextDescriptions by remember { mutableStateOf(false) }
    var trackMetadataOriginal by remember { mutableStateOf(false) }
    var trackMetadataCommentary by remember { mutableStateOf(false) }
    var trackImportCandidate by remember { mutableStateOf<ContainerTrackImportCandidateUi?>(null) }
    var trackImportName by remember { mutableStateOf("") }
    var trackImportLanguage by remember { mutableStateOf("") }
    var trackImportLanguageBcp47 by remember { mutableStateOf("") }
    var trackImportDefault by remember { mutableStateOf(false) }
    var trackImportForced by remember { mutableStateOf(false) }
    var trackImportHearingImpaired by remember { mutableStateOf(false) }
    var trackImportVisualImpaired by remember { mutableStateOf(false) }
    var trackImportTextDescriptions by remember { mutableStateOf(false) }
    var trackImportOriginal by remember { mutableStateOf(false) }
    var trackImportCommentary by remember { mutableStateOf(false) }
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
    val trackImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModel.scanContainerTrackImportSource(uri)
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
                            append(" · 待改附件信息 ").append(state.pendingAttachmentMetadataEdits.size)
                        }
                        if (state.pendingTrackRemovals.isNotEmpty()) {
                            append(" · 待删轨 ").append(state.pendingTrackRemovals.size)
                        }
                        if (state.pendingTrackMetadataEdits.isNotEmpty()) {
                            append(" · 待改轨道信息 ").append(state.pendingTrackMetadataEdits.size)
                        }
                        if (state.pendingTrackImports.isNotEmpty()) {
                            append(" · 待导入轨道 ").append(state.pendingTrackImports.size)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.loading || state.attachmentExtractBusy || state.trackImportBusy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            ContainerIconButton(
                label = "重新检测容器内容",
                enabled = !state.loading &&
                    !state.writeBackBusy &&
                    !state.attachmentExtractBusy &&
                    !state.trackImportBusy,
                onClick = viewModel::rescanContainer,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "重新检测容器内容")
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
        ) {
            TextButton(
                modifier = Modifier.testTag("container-add-track"),
                enabled = !state.loading &&
                    !state.writeBackBusy &&
                    !state.attachmentExtractBusy &&
                    !state.trackImportBusy &&
                    state.inventoryEvidence != ContainerInventoryEvidence.VERIFIED_OUTPUT,
                onClick = {
                    trackImportPicker.launch(
                        arrayOf(
                            "video/*",
                            "audio/*",
                            "video/x-matroska",
                            "video/webm",
                            "audio/x-matroska",
                            "application/x-matroska",
                            "application/x-ass",
                            "application/x-ssa",
                            "text/x-ass",
                            "text/x-ssa",
                            "application/x-subrip",
                            "text/srt",
                            "text/plain",
                            "application/octet-stream",
                        )
                    )
                },
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null)
                Text("添加轨道")
            }
            TextButton(
                modifier = Modifier.testTag("container-add-attachment"),
                enabled = !state.loading &&
                    !state.writeBackBusy &&
                    !state.attachmentExtractBusy &&
                    !state.trackImportBusy &&
                    state.inventoryEvidence != ContainerInventoryEvidence.VERIFIED_OUTPUT,
                onClick = { attachmentPicker.launch(arrayOf("*/*")) },
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = null)
                Text("添加附件")
            }
            TextButton(
                modifier = Modifier.testTag("container-save-mkv"),
                enabled = editPlan.executable &&
                    !state.loading &&
                    !state.writeBackBusy &&
                    !state.attachmentExtractBusy &&
                    !state.trackImportBusy,
                onClick = onSaveMkv,
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Text("保存新 MKV")
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

        val resourceGroups = listOf(
            "轨道" to state.resources.filter {
                it.kind == ContainerResourceKind.VIDEO ||
                    it.kind == ContainerResourceKind.AUDIO ||
                    it.kind == ContainerResourceKind.SUBTITLE
            },
            "附件" to state.resources.filter {
                it.kind == ContainerResourceKind.FONT ||
                    it.kind == ContainerResourceKind.ATTACHMENT
            },
            "容器信息" to state.resources.filter {
                it.kind != ContainerResourceKind.VIDEO &&
                    it.kind != ContainerResourceKind.AUDIO &&
                    it.kind != ContainerResourceKind.SUBTITLE &&
                    it.kind != ContainerResourceKind.FONT &&
                    it.kind != ContainerResourceKind.ATTACHMENT
            },
        ).filter { (_, resources) -> resources.isNotEmpty() }

        resourceGroups.forEachIndexed { groupIndex, (label, resources) ->
            if (groupIndex > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    resources.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            resources.forEachIndexed { index, resource ->
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
                val trackTarget = resource.trackTarget
                val pendingTrackRemoval = trackTarget?.let { target ->
                    state.pendingTrackRemovals.any { it.target == target }
                } == true
                val pendingTrackMetadata = trackTarget?.let { target ->
                    state.pendingTrackMetadataEdits.firstOrNull { it.target == target }
                }
                ContainerResourceRow(
                    resource = resource,
                    selected = resource.editableAss && resource.trackNumber == state.selectedTrackNumber,
                    enabled = resource.editableAss &&
                        resource.change != ContainerResourceChange.REMOVED &&
                        !pendingTrackRemoval &&
                        !state.writeBackBusy &&
                        state.inventoryEvidence != ContainerInventoryEvidence.VERIFIED_OUTPUT,
                    attachmentActionsEnabled = attachmentTarget != null &&
                        resource.change != ContainerResourceChange.REMOVED &&
                        !state.writeBackBusy &&
                        !state.attachmentExtractBusy &&
                        state.inventoryEvidence != ContainerInventoryEvidence.VERIFIED_OUTPUT,
                    trackActionsEnabled = trackTarget != null &&
                        resource.change != ContainerResourceChange.REMOVED &&
                        !state.writeBackBusy &&
                        !state.attachmentExtractBusy &&
                        state.inventoryEvidence != ContainerInventoryEvidence.VERIFIED_OUTPUT,
                    pendingRemoval = pendingRemoval,
                    pendingReplacementName = pendingReplacement?.name,
                    pendingMetadataName = pendingMetadata?.name,
                    pendingTrackRemoval = pendingTrackRemoval,
                    pendingTrackMetadataName = pendingTrackMetadata?.name,
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
                    onRemoveTrack = resource.trackNumber?.let { number ->
                        trackTarget?.let { target ->
                            {
                                viewModel.planExistingTrackRemoval(
                                    target = target,
                                    number = number,
                                    name = resource.title,
                                )
                            }
                        }
                    },
                    onEditTrackMetadata = trackTarget?.let {
                        {
                            trackMetadataTarget = resource
                            trackMetadataName = pendingTrackMetadata?.name ?: resource.trackName
                            trackMetadataLanguage = pendingTrackMetadata?.language ?: resource.trackLanguage
                            trackMetadataLanguageBcp47 = pendingTrackMetadata?.languageBcp47 ?: resource.trackLanguageBcp47
                            trackMetadataDefault = pendingTrackMetadata?.isDefault ?: resource.trackIsDefault
                            trackMetadataForced = pendingTrackMetadata?.isForced ?: resource.trackIsForced
                            trackMetadataHearingImpaired =
                                pendingTrackMetadata?.hearingImpaired ?: resource.trackHearingImpaired
                            trackMetadataVisualImpaired =
                                pendingTrackMetadata?.visualImpaired ?: resource.trackVisualImpaired
                            trackMetadataTextDescriptions =
                                pendingTrackMetadata?.textDescriptions ?: resource.trackTextDescriptions
                            trackMetadataOriginal = pendingTrackMetadata?.original ?: resource.trackOriginal
                            trackMetadataCommentary = pendingTrackMetadata?.commentary ?: resource.trackCommentary
                        }
                    },
                    onCancelTrackEdit = trackTarget?.let { target ->
                        {
                            viewModel.cancelExistingTrackRemoval(target)
                            viewModel.cancelExistingTrackMetadata(target)
                        }
                    },
                )
            }
        }

        state.mediaImportAssessment?.let { assessment ->
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "媒体导入兼容性检测 · ${assessment.sourceName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "兼容性结果属于只读证据；只有 execution implemented 的 adapter 会另外出现在可导入候选中。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    enabled = !state.trackImportBusy && !state.writeBackBusy,
                    onClick = viewModel::dismissContainerTrackImportCandidates,
                ) { Text("关闭") }
            }
            assessment.tracks.forEach { result ->
                val track = result.descriptor
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = when (track.kind) {
                            MediaImportTrackKind.VIDEO -> Icons.Filled.Movie
                            MediaImportTrackKind.AUDIO -> Icons.Filled.Audiotrack
                            MediaImportTrackKind.SUBTITLE -> Icons.Filled.Subtitles
                            MediaImportTrackKind.OTHER -> Icons.Filled.HelpOutline
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Extractor Track #${track.extractorIndex} · ${track.mime}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            buildString {
                                append(
                                    when (result.disposition) {
                                        MediaImportDisposition.STREAM_COPY_COMPATIBLE -> "Stream-copy compatible"
                                        MediaImportDisposition.TRANSCODE_REQUIRED -> "需要显式 Transcode"
                                        MediaImportDisposition.UNSUPPORTED -> "当前导入域不支持"
                                        MediaImportDisposition.UNKNOWN -> "兼容性未决"
                                    }
                                )
                                result.matroskaCodecId?.let { append(" · ").append(it) }
                                track.language?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
                                if (track.kind == MediaImportTrackKind.VIDEO &&
                                    track.width != null && track.height != null
                                ) {
                                    append(" · ").append(track.width).append("×").append(track.height)
                                }
                                if (track.kind == MediaImportTrackKind.AUDIO) {
                                    track.channelCount?.let { append(" · ").append(it).append("ch") }
                                    track.sampleRate?.let { append(" · ").append(it).append("Hz") }
                                }
                                if (track.codecPrivateKeys.isNotEmpty()) {
                                    append(" · codec config ").append(track.codecPrivateKeys.joinToString())
                                }
                                track.decoderAvailable?.let {
                                    append(if (it) " · decoder available" else " · decoder unavailable")
                                }
                                append(if (result.executionImplemented) " · execution implemented" else " · execution not wired")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            result.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (state.pendingTrackImports.isNotEmpty()) {
            HorizontalDivider()
            Text(
                "待导入轨道 · ${state.pendingTrackImports.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            state.pendingTrackImports.forEach { track ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("container-pending-track-import-${track.sourceTrackNumber}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = when (track.kind) {
                            ContainerResourceKind.VIDEO -> Icons.Filled.Movie
                            ContainerResourceKind.AUDIO -> Icons.Filled.Audiotrack
                            ContainerResourceKind.SUBTITLE -> Icons.Filled.Subtitles
                            else -> Icons.Filled.HelpOutline
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.name.ifBlank { track.codecId.ifBlank { "Track #${track.sourceTrackNumber}" } },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            buildString {
                                append(track.sourceName)
                                when (track.sourceKind) {
                                    ContainerTrackImportSourceKind.MATROSKA_TRACK ->
                                        append(" · source Track #").append(track.sourceTrackNumber)
                                    ContainerTrackImportSourceKind.STANDALONE_ASS ->
                                        append(" · standalone ASS → normalized ASS")
                                    ContainerTrackImportSourceKind.STANDALONE_SRT ->
                                        append(" · standalone SRT → normalized ASS")
                                    ContainerTrackImportSourceKind.NORMALIZED_MEDIA_PACKETS -> {
                                        append(" · packet stream-copy")
                                        track.sourceExtractorIndex?.let {
                                            append(" · extractor Track #").append(it)
                                        }
                                        track.packetCount?.let {
                                            append(" · ").append(it).append(" packets")
                                        }
                                        track.sampleRate?.let {
                                            append(" · ").append(it).append("Hz")
                                        }
                                        track.channelCount?.let {
                                            append(" · ").append(it).append("ch")
                                        }
                                    }
                                }
                                append(" · ").append(track.codecId)
                                if (track.languageBcp47.isNotBlank()) append(" · ").append(track.languageBcp47)
                                else if (track.language.isNotBlank()) append(" · ").append(track.language)
                                if (track.isDefault) append(" · Default")
                                if (track.isForced) append(" · Forced")
                                if (track.hearingImpaired) append(" · Hearing impaired")
                                if (track.visualImpaired) append(" · Visual impaired")
                                if (track.textDescriptions) append(" · Text descriptions")
                                if (track.original) append(" · Original")
                                if (track.commentary) append(" · Commentary")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        enabled = !state.writeBackBusy,
                        onClick = {
                            viewModel.cancelContainerTrackImport(
                                sourceKind = track.sourceKind,
                                sourceUri = track.sourceUri,
                                sourceTrackNumber = track.sourceTrackNumber,
                                sourceExtractorIndex = track.sourceExtractorIndex,
                            )
                        },
                    ) { Text("取消") }
                }
            }
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

        if (state.trackImportCandidates.isNotEmpty() && trackImportCandidate == null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissContainerTrackImportCandidates,
                title = { Text("选择要导入的轨道") },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            state.trackImportSourceName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.trackImportCandidates.forEach { candidate ->
                            val alreadyPlanned = state.pendingTrackImports.any {
                                it.sourceKind == candidate.sourceKind &&
                                    it.sourceUri == candidate.sourceUri &&
                                    it.sourceTrackNumber == candidate.sourceTrackNumber &&
                                    it.sourceExtractorIndex == candidate.sourceExtractorIndex
                            }
                            Surface(
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = when (candidate.kind) {
                                            ContainerResourceKind.VIDEO -> Icons.Filled.Movie
                                            ContainerResourceKind.AUDIO -> Icons.Filled.Audiotrack
                                            ContainerResourceKind.SUBTITLE -> Icons.Filled.Subtitles
                                            else -> Icons.Filled.HelpOutline
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            candidate.name.ifBlank {
                                                candidate.codecId.ifBlank {
                                                    when (candidate.sourceKind) {
                                                        ContainerTrackImportSourceKind.MATROSKA_TRACK ->
                                                            "Track #${candidate.sourceTrackNumber}"
                                                        ContainerTrackImportSourceKind.STANDALONE_ASS ->
                                                            "Standalone ASS"
                                                        ContainerTrackImportSourceKind.STANDALONE_SRT ->
                                                            "Standalone SRT"
                                                        ContainerTrackImportSourceKind.NORMALIZED_MEDIA_PACKETS ->
                                                            "Media Track #${candidate.sourceExtractorIndex}"
                                                    }
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            buildString {
                                                when (candidate.sourceKind) {
                                                    ContainerTrackImportSourceKind.MATROSKA_TRACK ->
                                                        append("source Track #").append(candidate.sourceTrackNumber)
                                                    ContainerTrackImportSourceKind.STANDALONE_ASS ->
                                                        append("standalone ASS → normalized ASS")
                                                    ContainerTrackImportSourceKind.STANDALONE_SRT ->
                                                        append("standalone SRT → normalized ASS")
                                                    ContainerTrackImportSourceKind.NORMALIZED_MEDIA_PACKETS -> {
                                                        append("MediaExtractor stream-copy")
                                                        candidate.sourceExtractorIndex?.let {
                                                            append(" · extractor Track #").append(it)
                                                        }
                                                        candidate.packetCount?.let {
                                                            append(" · ").append(it).append(" packets")
                                                        }
                                                        candidate.sampleRate?.let {
                                                            append(" · ").append(it).append("Hz")
                                                        }
                                                        candidate.channelCount?.let {
                                                            append(" · ").append(it).append("ch")
                                                        }
                                                    }
                                                }
                                                if (candidate.codecId.isNotBlank()) append(" · ").append(candidate.codecId)
                                                if (candidate.languageBcp47.isNotBlank()) append(" · ").append(candidate.languageBcp47)
                                                else if (candidate.language.isNotBlank()) append(" · ").append(candidate.language)
                                                if (candidate.isDefault) append(" · Default")
                                                if (candidate.isForced) append(" · Forced")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(
                                        enabled = !alreadyPlanned,
                                        onClick = {
                                            trackImportCandidate = candidate
                                            trackImportName = candidate.name
                                            trackImportLanguage = candidate.language
                                            trackImportLanguageBcp47 = candidate.languageBcp47
                                            // Default is destination policy rather than intrinsic
                                            // source semantics, so it starts disabled. Semantic
                                            // dispositions remain inherited and can be changed.
                                            trackImportDefault = false
                                            trackImportForced = candidate.isForced
                                            trackImportHearingImpaired = candidate.hearingImpaired
                                            trackImportVisualImpaired = candidate.visualImpaired
                                            trackImportTextDescriptions = candidate.textDescriptions
                                            trackImportOriginal = candidate.original
                                            trackImportCommentary = candidate.commentary
                                        },
                                    ) {
                                        Text(if (alreadyPlanned) "已加入" else "设置")
                                    }
                                }
                            }
                        }
                        Text(
                            "这里只选择来源 Track；不会把来源 MKV 的其他轨道或附件一起复制。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissContainerTrackImportCandidates) {
                        Text("完成")
                    }
                },
            )
        }

        trackImportCandidate?.let { candidate ->
            AlertDialog(
                onDismissRequest = { trackImportCandidate = null },
                title = {
                    Text(
                        when (candidate.sourceKind) {
                            ContainerTrackImportSourceKind.MATROSKA_TRACK ->
                                "导入 Track #${candidate.sourceTrackNumber}"
                            ContainerTrackImportSourceKind.STANDALONE_ASS ->
                                "导入独立 ASS"
                            ContainerTrackImportSourceKind.STANDALONE_SRT ->
                                "导入独立 SRT"
                            ContainerTrackImportSourceKind.NORMALIZED_MEDIA_PACKETS ->
                                "导入媒体 Track #${candidate.sourceExtractorIndex}"
                        }
                    )
                },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            buildString {
                                append(candidate.sourceName)
                                append(" · ").append(candidate.kind.name.lowercase())
                                if (candidate.codecId.isNotBlank()) append(" · ").append(candidate.codecId)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = trackImportName,
                            onValueChange = { trackImportName = it },
                            label = { Text("目标轨道名称") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = trackImportLanguage,
                            onValueChange = { trackImportLanguage = it },
                            label = { Text("Legacy Language（ISO 639-2）") },
                            supportingText = { Text("3 字母代码，例如 jpn / eng / und；可留空") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = trackImportLanguageBcp47,
                            onValueChange = { trackImportLanguageBcp47 = it },
                            label = { Text("Language IETF（BCP 47）") },
                            supportingText = { Text("例如 ja-JP / en / zh-Hant；可留空") },
                            singleLine = true,
                        )
                        TrackDispositionCheckboxes(
                            isDefault = trackImportDefault,
                            onDefaultChange = { trackImportDefault = it },
                            isForced = trackImportForced,
                            onForcedChange = { trackImportForced = it },
                            hearingImpaired = trackImportHearingImpaired,
                            onHearingImpairedChange = { trackImportHearingImpaired = it },
                            visualImpaired = trackImportVisualImpaired,
                            onVisualImpairedChange = { trackImportVisualImpaired = it },
                            textDescriptions = trackImportTextDescriptions,
                            onTextDescriptionsChange = { trackImportTextDescriptions = it },
                            original = trackImportOriginal,
                            onOriginalChange = { trackImportOriginal = it },
                            commentary = trackImportCommentary,
                            onCommentaryChange = { trackImportCommentary = it },
                        )
                        Text(
                            "保存时分配新的 TrackNumber / TrackUID。Default 作为目标容器策略默认关闭；BCP 47 与无障碍/语义 disposition 保留 current-main 规则。Matroska 来源附件不会自动复制；ASS / SRT 与 packet 来源按各自 normalization evidence 重新验证。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (
                            candidate.kind == ContainerResourceKind.SUBTITLE &&
                            candidate.sourceAttachmentCount > 0
                        ) {
                            Text(
                                "该字幕来源包含 ${candidate.sourceAttachmentCount} 个附件；若它依赖字体或其他资源，请另外加入附件计划。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.planContainerTrackImport(
                                candidate = candidate,
                                name = trackImportName,
                                language = trackImportLanguage,
                                languageBcp47 = trackImportLanguageBcp47,
                                isDefault = trackImportDefault,
                                isForced = trackImportForced,
                                hearingImpaired = trackImportHearingImpaired,
                                visualImpaired = trackImportVisualImpaired,
                                textDescriptions = trackImportTextDescriptions,
                                original = trackImportOriginal,
                                commentary = trackImportCommentary,
                            )
                            trackImportCandidate = null
                        },
                    ) { Text("加入计划") }
                },
                dismissButton = {
                    TextButton(onClick = { trackImportCandidate = null }) { Text("取消") }
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

        trackMetadataTarget?.let { resource ->
            val target = resource.trackTarget
            AlertDialog(
                onDismissRequest = { trackMetadataTarget = null },
                title = { Text("Track #${resource.trackNumber} 信息") },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = trackMetadataName,
                            onValueChange = { trackMetadataName = it },
                            label = { Text("轨道名称") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = trackMetadataLanguage,
                            onValueChange = { trackMetadataLanguage = it },
                            label = { Text("Legacy Language（ISO 639-2）") },
                            supportingText = { Text("3 字母代码，例如 jpn / eng / und；可留空") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = trackMetadataLanguageBcp47,
                            onValueChange = { trackMetadataLanguageBcp47 = it },
                            label = { Text("Language IETF（BCP 47）") },
                            supportingText = { Text("例如 ja-JP / en / zh-Hant；可留空") },
                            singleLine = true,
                        )
                        TrackDispositionCheckboxes(
                            isDefault = trackMetadataDefault,
                            onDefaultChange = { trackMetadataDefault = it },
                            isForced = trackMetadataForced,
                            onForcedChange = { trackMetadataForced = it },
                            hearingImpaired = trackMetadataHearingImpaired,
                            onHearingImpairedChange = { trackMetadataHearingImpaired = it },
                            visualImpaired = trackMetadataVisualImpaired,
                            onVisualImpairedChange = { trackMetadataVisualImpaired = it },
                            textDescriptions = trackMetadataTextDescriptions,
                            onTextDescriptionsChange = { trackMetadataTextDescriptions = it },
                            original = trackMetadataOriginal,
                            onOriginalChange = { trackMetadataOriginal = it },
                            commentary = trackMetadataCommentary,
                            onCommentaryChange = { trackMetadataCommentary = it },
                        )
                        Text(
                            "这里只修改 TrackEntry 元数据；TrackNumber、TrackUID、codec 与 payload 保持。Legacy Language 与 BCP 47 分开保存。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = target != null,
                        onClick = {
                            val resolved = target ?: return@TextButton
                            val number = resource.trackNumber ?: return@TextButton
                            viewModel.planExistingTrackMetadata(
                                target = resolved,
                                number = number,
                                originalName = resource.trackName,
                                name = trackMetadataName,
                                language = trackMetadataLanguage,
                                languageBcp47 = trackMetadataLanguageBcp47,
                                isDefault = trackMetadataDefault,
                                isForced = trackMetadataForced,
                                hearingImpaired = trackMetadataHearingImpaired,
                                visualImpaired = trackMetadataVisualImpaired,
                                textDescriptions = trackMetadataTextDescriptions,
                                original = trackMetadataOriginal,
                                commentary = trackMetadataCommentary,
                            )
                            trackMetadataTarget = null
                        },
                    ) { Text("加入计划") }
                },
                dismissButton = {
                    TextButton(onClick = { trackMetadataTarget = null }) { Text("取消") }
                },
            )
        }
    }
}


@Composable
private fun TrackDispositionCheckboxes(
    isDefault: Boolean,
    onDefaultChange: (Boolean) -> Unit,
    isForced: Boolean,
    onForcedChange: (Boolean) -> Unit,
    hearingImpaired: Boolean,
    onHearingImpairedChange: (Boolean) -> Unit,
    visualImpaired: Boolean,
    onVisualImpairedChange: (Boolean) -> Unit,
    textDescriptions: Boolean,
    onTextDescriptionsChange: (Boolean) -> Unit,
    original: Boolean,
    onOriginalChange: (Boolean) -> Unit,
    commentary: Boolean,
    onCommentaryChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TrackDispositionRow("Default", isDefault, onDefaultChange, "Forced", isForced, onForcedChange)
        TrackDispositionRow(
            "Hearing impaired",
            hearingImpaired,
            onHearingImpairedChange,
            "Visual impaired",
            visualImpaired,
            onVisualImpairedChange,
        )
        TrackDispositionRow(
            "Text descriptions",
            textDescriptions,
            onTextDescriptionsChange,
            "Original",
            original,
            onOriginalChange,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Checkbox(checked = commentary, onCheckedChange = onCommentaryChange)
            Text("Commentary")
        }
    }
}

@Composable
private fun TrackDispositionRow(
    firstLabel: String,
    firstValue: Boolean,
    onFirstChange: (Boolean) -> Unit,
    secondLabel: String,
    secondValue: Boolean,
    onSecondChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Checkbox(checked = firstValue, onCheckedChange = onFirstChange)
        Text(firstLabel, modifier = Modifier.weight(1f))
        Checkbox(checked = secondValue, onCheckedChange = onSecondChange)
        Text(secondLabel, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun ContainerPreflightSummary(
    plan: ContainerEditPlanUi,
    modifier: Modifier = Modifier,
) {
    var showAllChecks by remember { mutableStateOf(false) }
    val notableChecks = plan.checks.filter {
        it.status != ContainerCompatibilityStatus.SUPPORTED
    }
    val visibleChecks = if (showAllChecks) plan.checks else notableChecks

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

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "兼容性 / 能力预检",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (plan.checks.size > notableChecks.size) {
                TextButton(onClick = { showAllChecks = !showAllChecks }) {
                    Text(if (showAllChecks) "收起已通过项" else "全部 ${plan.checks.size} 项")
                }
            }
        }

        if (!showAllChecks && notableChecks.isEmpty()) {
            Text(
                "全部预检通过",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        visibleChecks.forEach { check ->
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
    trackActionsEnabled: Boolean,
    pendingRemoval: Boolean,
    pendingReplacementName: String?,
    pendingMetadataName: String?,
    pendingTrackRemoval: Boolean,
    pendingTrackMetadataName: String?,
    onClick: () -> Unit,
    onRemoveAttachment: (() -> Unit)?,
    onReplaceAttachment: (() -> Unit)?,
    onEditAttachmentMetadata: (() -> Unit)?,
    onExtractAttachment: (() -> Unit)?,
    onCancelAttachmentEdit: (() -> Unit)?,
    onRemoveTrack: (() -> Unit)?,
    onEditTrackMetadata: (() -> Unit)?,
    onCancelTrackEdit: (() -> Unit)?,
) {
    var actionsExpanded by remember { mutableStateOf(false) }
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
                pendingTrackRemoval -> {
                    Text("待删轨", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { onCancelTrackEdit?.invoke() }) { Text("取消") }
                }
                pendingTrackMetadataName != null -> {
                    Text(
                        "轨道信息 → " + pendingTrackMetadataName.ifBlank { "未命名" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { onCancelTrackEdit?.invoke() }) { Text("取消") }
                }
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
                trackActionsEnabled || attachmentActionsEnabled -> {
                    IconButton(
                        onClick = { actionsExpanded = true },
                        modifier = Modifier.testTag("container-resource-actions-" + resource.rowKey),
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "资源操作")
                    }
                    DropdownMenu(
                        expanded = actionsExpanded,
                        onDismissRequest = { actionsExpanded = false },
                    ) {
                        if (trackActionsEnabled) {
                            DropdownMenuItem(
                                text = { Text("轨道信息") },
                                onClick = {
                                    actionsExpanded = false
                                    onEditTrackMetadata?.invoke()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除轨道") },
                                onClick = {
                                    actionsExpanded = false
                                    onRemoveTrack?.invoke()
                                },
                            )
                        }
                        if (attachmentActionsEnabled) {
                            DropdownMenuItem(
                                text = { Text("提取附件") },
                                onClick = {
                                    actionsExpanded = false
                                    onExtractAttachment?.invoke()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("附件信息") },
                                onClick = {
                                    actionsExpanded = false
                                    onEditAttachmentMetadata?.invoke()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("替换附件") },
                                onClick = {
                                    actionsExpanded = false
                                    onReplaceAttachment?.invoke()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除附件") },
                                onClick = {
                                    actionsExpanded = false
                                    onRemoveAttachment?.invoke()
                                },
                            )
                        }
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

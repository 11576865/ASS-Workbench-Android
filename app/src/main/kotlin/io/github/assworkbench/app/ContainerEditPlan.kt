package io.github.assworkbench.app

import io.github.assworkbench.fonts.FontOrigin

enum class ContainerMutationKind {
    REPLACE_ASS_TRACK,
    ADD_ATTACHMENT,
    REMOVE_ATTACHMENT,
    REPLACE_ATTACHMENT,
    EDIT_ATTACHMENT_METADATA,
    REMOVE_TRACK,
    EDIT_TRACK_METADATA,
    ADD_TRACK,
}

enum class ContainerMutationSource {
    ASS_DOCUMENT,
    FONT_PACKAGE,
    GENERIC_ATTACHMENT,
    EXISTING_ATTACHMENT,
    EXISTING_TRACK,
    EXTERNAL_TRACK,
}

data class ContainerMutationUi(
    val id: String,
    val kind: ContainerMutationKind,
    val source: ContainerMutationSource,
    val title: String,
    val detail: String,
)

enum class ContainerCompatibilityDimension {
    SOURCE_INVENTORY,
    CONTAINER_STRUCTURE,
    LOCAL_WRITER,
    OUTPUT_VERIFICATION,
    DOWNSTREAM,
}

enum class ContainerCompatibilityStatus {
    SUPPORTED,
    WARNING,
    UNSUPPORTED,
    UNKNOWN,
}

data class ContainerCompatibilityCheckUi(
    val dimension: ContainerCompatibilityDimension,
    val status: ContainerCompatibilityStatus,
    val title: String,
    val detail: String,
)

data class ContainerEditPlanUi(
    val mutations: List<ContainerMutationUi>,
    val checks: List<ContainerCompatibilityCheckUi>,
) {
    val blockingChecks: List<ContainerCompatibilityCheckUi>
        get() = checks.filter { it.status == ContainerCompatibilityStatus.UNSUPPORTED }

    val executable: Boolean
        get() = mutations.isNotEmpty() && blockingChecks.isEmpty()
}

internal fun buildContainerEditPlan(state: EditorState): ContainerEditPlanUi {
    val mutations = mutableListOf<ContainerMutationUi>()

    val trackNumber = state.container.selectedTrackNumber
    if (trackNumber != null && state.subtitleLoaded && state.dirty) {
        mutations += ContainerMutationUi(
            id = "replace-ass:$trackNumber",
            kind = ContainerMutationKind.REPLACE_ASS_TRACK,
            source = ContainerMutationSource.ASS_DOCUMENT,
            title = "写回 ASS Track #$trackNumber",
            detail = "同槽位替换字幕 payload；保留轨道身份、顺序与元数据",
        )
    }

    val embeddedShas = state.importedFonts.asSequence()
        .filter { it.origin == FontOrigin.MKV_ATTACHMENT }
        .map { it.sha256 }
        .toSet()
    state.importedFonts.asSequence()
        .filter {
            it.origin == FontOrigin.MANUAL &&
                it.sha256 in state.fontPackagingSelection &&
                it.sha256 !in embeddedShas
        }
        .distinctBy { it.sha256 }
        .forEach { asset ->
            mutations += ContainerMutationUi(
                id = "font:${asset.sha256}",
                kind = ContainerMutationKind.ADD_ATTACHMENT,
                source = ContainerMutationSource.FONT_PACKAGE,
                title = "封入字体 ${asset.fileName}",
                detail = "作为 Matroska Attachment 写入",
            )
        }

    state.container.pendingAttachments.forEach { attachment ->
        mutations += ContainerMutationUi(
            id = "attachment:${attachment.uri}",
            kind = ContainerMutationKind.ADD_ATTACHMENT,
            source = ContainerMutationSource.GENERIC_ATTACHMENT,
            title = "添加附件 ${attachment.name}",
            detail = buildString {
                append(attachment.mimeType.ifBlank { "application/octet-stream" })
                attachment.sizeBytes?.let { append(" · ").append(formatContainerBytes(it)) }
            },
        )
    }

    state.container.pendingAttachmentRemovals.forEach { removal ->
        mutations += ContainerMutationUi(
            id = "remove-attachment:${removal.target}",
            kind = ContainerMutationKind.REMOVE_ATTACHMENT,
            source = ContainerMutationSource.EXISTING_ATTACHMENT,
            title = "删除附件 ${removal.name}",
            detail = "目标 ${removal.target}；写回后必须在输出 Inventory 中确认消失",
        )
    }

    state.container.pendingAttachmentReplacements.forEach { replacement ->
        mutations += ContainerMutationUi(
            id = "replace-attachment:${replacement.target}",
            kind = ContainerMutationKind.REPLACE_ATTACHMENT,
            source = ContainerMutationSource.EXISTING_ATTACHMENT,
            title = "替换附件 ${replacement.originalName} → ${replacement.name}",
            detail = buildString {
                append(replacement.mimeType.ifBlank { "application/octet-stream" })
                replacement.sizeBytes?.let { append(" · ").append(formatContainerBytes(it)) }
                append(" · 保留原 Attachment UID/目标身份")
            },
        )
    }

    state.container.pendingAttachmentMetadataEdits.forEach { metadata ->
        mutations += ContainerMutationUi(
            id = "edit-attachment-meta:${metadata.target}",
            kind = ContainerMutationKind.EDIT_ATTACHMENT_METADATA,
            source = ContainerMutationSource.EXISTING_ATTACHMENT,
            title = "修改附件信息 ${metadata.originalName} → ${metadata.name}",
            detail = buildString {
                append("保留 payload 与 Attachment UID/目标身份")
                if (metadata.description.isNotBlank()) {
                    append(" · ").append(metadata.description)
                }
            },
        )
    }

    state.container.pendingTrackRemovals.forEach { removal ->
        mutations += ContainerMutationUi(
            id = "remove-track:${removal.target}",
            kind = ContainerMutationKind.REMOVE_TRACK,
            source = ContainerMutationSource.EXISTING_TRACK,
            title = "删除轨道 ${removal.name}",
            detail = "Track #${removal.number} · 保持其他轨道的 TrackNumber / TrackUID 与顺序",
        )
    }

    state.container.pendingTrackMetadataEdits.forEach { metadata ->
        mutations += ContainerMutationUi(
            id = "edit-track-meta:${metadata.target}",
            kind = ContainerMutationKind.EDIT_TRACK_METADATA,
            source = ContainerMutationSource.EXISTING_TRACK,
            title = "修改轨道信息 Track #${metadata.number}",
            detail = buildString {
                append(metadata.name.ifBlank { "未命名" })
                append(" · ").append(metadata.language.ifBlank { "语言未声明" })
                if (metadata.isDefault) append(" · Default")
                if (metadata.isForced) append(" · Forced")
                append(" · 保留 TrackNumber / TrackUID / codec / payload")
            },
        )
    }

    state.container.pendingTrackAdditions.forEach { addition ->
        mutations += ContainerMutationUi(
            id = "add-track:${addition.sourceUri}:${addition.sourceTrackNumber}",
            kind = ContainerMutationKind.ADD_TRACK,
            source = ContainerMutationSource.EXTERNAL_TRACK,
            title = "添加轨道 " + addition.name.ifBlank {
                "${addition.codecId} · Track #${addition.sourceTrackNumber}"
            },
            detail = buildString {
                append(addition.sourceName)
                append(" · source Track #").append(addition.sourceTrackNumber)
                append(" · ").append(addition.codecId)
                if (addition.language.isNotBlank()) append(" · ").append(addition.language)
                if (addition.isDefault) append(" · Default")
                if (addition.isForced) append(" · Forced")
                append(" · 输出分配新的 TrackNumber / TrackUID")
            },
        )
    }

    val checks = mutableListOf<ContainerCompatibilityCheckUi>()

    checks += if (state.container.skippedAttachmentCount > 0) {
        ContainerCompatibilityCheckUi(
            dimension = ContainerCompatibilityDimension.SOURCE_INVENTORY,
            status = ContainerCompatibilityStatus.WARNING,
            title = "源容器检测存在不完整附件",
            detail = "${state.container.skippedAttachmentCount} 个附件 payload 未完整载入；写回后仍会重新扫描输出，但源证据不是完全可观测。",
        )
    } else {
        ContainerCompatibilityCheckUi(
            dimension = ContainerCompatibilityDimension.SOURCE_INVENTORY,
            status = ContainerCompatibilityStatus.SUPPORTED,
            title = "源容器 Inventory 已建立",
            detail = when (state.container.inventoryEvidence) {
                ContainerInventoryEvidence.BASELINE -> "当前依据：首次检测"
                ContainerInventoryEvidence.CURRENT_SOURCE -> "当前依据：重新检测"
                ContainerInventoryEvidence.VERIFIED_OUTPUT -> "当前依据：上次已验证输出"
            },
        )
    }

    val removalTargets = state.container.pendingAttachmentRemovals.mapTo(hashSetOf()) { it.target }
    val replacementTargets = state.container.pendingAttachmentReplacements.mapTo(hashSetOf()) { it.target }
    val metadataTargets = state.container.pendingAttachmentMetadataEdits.mapTo(hashSetOf()) { it.target }
    val conflictingAttachmentTargets =
        removalTargets.intersect(replacementTargets) +
            removalTargets.intersect(metadataTargets) +
            replacementTargets.intersect(metadataTargets)
    val currentAttachmentTargets = state.container.resources.mapNotNullTo(hashSetOf()) { it.attachmentTarget }
    val missingAttachmentTargets =
        (removalTargets + replacementTargets + metadataTargets).filterNot { it in currentAttachmentTargets }
    val trackRemovalTargets = state.container.pendingTrackRemovals.mapTo(hashSetOf()) { it.target }
    val trackMetadataTargets = state.container.pendingTrackMetadataEdits.mapTo(hashSetOf()) { it.target }
    val conflictingTrackTargets = trackRemovalTargets.intersect(trackMetadataTargets)
    val currentTrackTargets = state.container.resources.mapNotNullTo(hashSetOf()) { it.trackTarget }
    val missingTrackTargets =
        (trackRemovalTargets + trackMetadataTargets).filterNot { it in currentTrackTargets }
    val trackAdditionKeys = state.container.pendingTrackAdditions.map {
        it.sourceUri + "\u0000" + it.sourceTrackNumber
    }
    val duplicateTrackAdditions = trackAdditionKeys.groupingBy { it }.eachCount()
        .filterValues { it > 1 }
        .keys
    val remainingTrackCount =
        currentTrackTargets.size - trackRemovalTargets.size + state.container.pendingTrackAdditions.size
    val selectedTrackTarget = state.container.resources.firstOrNull {
        it.trackNumber == state.container.selectedTrackNumber
    }?.trackTarget
    val removesDirtySelectedAss =
        state.dirty && selectedTrackTarget != null && selectedTrackTarget in trackRemovalTargets

    checks += ContainerCompatibilityCheckUi(
        dimension = ContainerCompatibilityDimension.CONTAINER_STRUCTURE,
        status = when {
            conflictingAttachmentTargets.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            missingAttachmentTargets.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            conflictingTrackTargets.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            missingTrackTargets.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            duplicateTrackAdditions.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            trackRemovalTargets.isNotEmpty() && remainingTrackCount <= 0 -> ContainerCompatibilityStatus.UNSUPPORTED
            removesDirtySelectedAss -> ContainerCompatibilityStatus.UNSUPPORTED
            mutations.isEmpty() -> ContainerCompatibilityStatus.WARNING
            else -> ContainerCompatibilityStatus.SUPPORTED
        },
        title = "Matroska 结构",
        detail = when {
            conflictingAttachmentTargets.isNotEmpty() ->
                "同一附件不能在一次计划中安排互斥的删除 / 替换 / 元数据修改：" + conflictingAttachmentTargets.joinToString()
            missingAttachmentTargets.isNotEmpty() ->
                "附件目标已不在当前检测 Inventory 中：" + missingAttachmentTargets.joinToString()
            conflictingTrackTargets.isNotEmpty() ->
                "同一轨道不能在一次计划中同时删除和修改元数据：" + conflictingTrackTargets.joinToString()
            missingTrackTargets.isNotEmpty() ->
                "轨道目标已不在当前检测 Inventory 中：" + missingTrackTargets.joinToString()
            duplicateTrackAdditions.isNotEmpty() ->
                "同一外部源轨不能在一次计划中重复添加：" + duplicateTrackAdditions.joinToString()
            trackRemovalTargets.isNotEmpty() && remainingTrackCount <= 0 ->
                "不能删除容器中的全部轨道。"
            removesDirtySelectedAss ->
                "当前正在编辑且未保存的 ASS 轨被计划删除；请先保存、放弃修改或取消删除。"
            mutations.isEmpty() -> "尚无待执行的容器修改。"
            else ->
                "当前计划中的 ASS 同槽位替换、Track 添加 / 删除 / 元数据修改与 Attachment 修改，均映射到已实现的 Matroska 写入路径。"
        },
    )

    checks += ContainerCompatibilityCheckUi(
        dimension = ContainerCompatibilityDimension.LOCAL_WRITER,
        status = if (state.container.writeBackAvailable) {
            ContainerCompatibilityStatus.SUPPORTED
        } else {
            ContainerCompatibilityStatus.UNSUPPORTED
        },
        title = "本机写入能力",
        detail = if (state.container.writeBackAvailable) {
            "当前 ABI 可调用 mkvgo bridge。"
        } else {
            "当前 ABI 没有可执行的 MKV 写回工具。"
        },
    )

    checks += ContainerCompatibilityCheckUi(
        dimension = ContainerCompatibilityDimension.OUTPUT_VERIFICATION,
        status = ContainerCompatibilityStatus.SUPPORTED,
        title = "输出验证",
        detail = "写回后重新扫描实际 MKV，并验证幸存轨身份、追加轨的新 TrackNumber / TrackUID、codec / 顺序、章节、未改附件，以及计划中的 Track 与 Attachment 修改。",
    )

    val hasGenericAttachment = mutations.any {
        it.source == ContainerMutationSource.GENERIC_ATTACHMENT ||
            it.source == ContainerMutationSource.EXISTING_ATTACHMENT
    }
    val hasTrackMutation = mutations.any {
        it.source == ContainerMutationSource.EXISTING_TRACK ||
            it.source == ContainerMutationSource.EXTERNAL_TRACK
    }
    val hasImportedSubtitleTrack = state.container.pendingTrackAdditions.any {
        it.kind == ContainerResourceKind.SUBTITLE
    }
    val hasFontAttachment = mutations.any { it.source == ContainerMutationSource.FONT_PACKAGE }
    val hasAss = mutations.any { it.kind == ContainerMutationKind.REPLACE_ASS_TRACK }

    checks += when {
        hasTrackMutation -> ContainerCompatibilityCheckUi(
            dimension = ContainerCompatibilityDimension.DOWNSTREAM,
            status = ContainerCompatibilityStatus.WARNING,
            title = "播放器轨道选择行为需验证",
            detail = buildString {
                append("Track 添加 / 删除、语言 / Default / Forced 等元数据会影响播放器的自动选轨；容器写入可验证，但不同播放器的选择策略不是 Matroska 结构保证。")
                if (hasImportedSubtitleTrack) {
                    append(" 外部字幕轨只导入所选 Track；源 MKV 的字体/其他 Attachment 不会自动随轨导入。")
                }
            },
        )
        hasGenericAttachment -> ContainerCompatibilityCheckUi(
            dimension = ContainerCompatibilityDimension.DOWNSTREAM,
            status = ContainerCompatibilityStatus.UNKNOWN,
            title = "下游播放器行为未知",
            detail = "文件可作为 Matroska Attachment 保存并验证存在；播放器是否展示或消费该附件取决于具体实现。",
        )
        hasAss || hasFontAttachment -> ContainerCompatibilityCheckUi(
            dimension = ContainerCompatibilityDimension.DOWNSTREAM,
            status = ContainerCompatibilityStatus.WARNING,
            title = "下游渲染兼容性需另行判断",
            detail = "ASS 与字体可写入容器，但实际字幕支持、字体选择与渲染结果仍由目标播放器 / renderer 决定。",
        )
        else -> ContainerCompatibilityCheckUi(
            dimension = ContainerCompatibilityDimension.DOWNSTREAM,
            status = ContainerCompatibilityStatus.UNKNOWN,
            title = "暂无下游兼容性结论",
            detail = "尚无待执行修改，无法形成针对目标环境的兼容性判断。",
        )
    }

    return ContainerEditPlanUi(
        mutations = mutations,
        checks = checks,
    )
}

private fun formatContainerBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes.toDouble() / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes.toDouble() / 1024.0)
    else -> "$bytes B"
}

package io.github.assworkbench.app

import io.github.assworkbench.fonts.FontOrigin

enum class ContainerMutationKind {
    REPLACE_ASS_TRACK,
    ADD_ATTACHMENT,
    REMOVE_ATTACHMENT,
    REPLACE_ATTACHMENT,
    EDIT_ATTACHMENT_METADATA,
}

enum class ContainerMutationSource {
    ASS_DOCUMENT,
    FONT_PACKAGE,
    GENERIC_ATTACHMENT,
    EXISTING_ATTACHMENT,
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

    checks += ContainerCompatibilityCheckUi(
        dimension = ContainerCompatibilityDimension.CONTAINER_STRUCTURE,
        status = when {
            conflictingAttachmentTargets.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            missingAttachmentTargets.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            mutations.isEmpty() -> ContainerCompatibilityStatus.WARNING
            else -> ContainerCompatibilityStatus.SUPPORTED
        },
        title = "Matroska 结构",
        detail = when {
            conflictingAttachmentTargets.isNotEmpty() ->
                "同一附件不能在一次计划中安排互斥的删除 / 替换 / 元数据修改：" + conflictingAttachmentTargets.joinToString()
            missingAttachmentTargets.isNotEmpty() ->
                "附件目标已不在当前检测 Inventory 中：" + missingAttachmentTargets.joinToString()
            mutations.isEmpty() -> "尚无待执行的容器修改。"
            else ->
                "当前计划中的 ASS 同槽位替换与 Attachment 添加 / 删除 / 替换 / 元数据修改，均映射到已实现的 Matroska 写入路径。"
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
        detail = "写回后重新扫描实际 MKV，并验证轨道、章节、未改附件，以及计划添加 / 删除 / 替换 / 元数据修改的实际结果。",
    )

    val hasGenericAttachment = mutations.any {
        it.source == ContainerMutationSource.GENERIC_ATTACHMENT ||
            it.source == ContainerMutationSource.EXISTING_ATTACHMENT
    }
    val hasFontAttachment = mutations.any { it.source == ContainerMutationSource.FONT_PACKAGE }
    val hasAss = mutations.any { it.kind == ContainerMutationKind.REPLACE_ASS_TRACK }

    checks += when {
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

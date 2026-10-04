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
                if (metadata.language.isNotBlank()) append(" · legacy ").append(metadata.language)
                if (metadata.languageBcp47.isNotBlank()) append(" · BCP 47 ").append(metadata.languageBcp47)
                if (metadata.language.isBlank() && metadata.languageBcp47.isBlank()) append(" · 语言未声明")
                if (metadata.isDefault) append(" · Default")
                if (metadata.isForced) append(" · Forced")
                if (metadata.hearingImpaired) append(" · Hearing impaired")
                if (metadata.visualImpaired) append(" · Visual impaired")
                if (metadata.textDescriptions) append(" · Text descriptions")
                if (metadata.original) append(" · Original")
                if (metadata.commentary) append(" · Commentary")
                append(" · 保留 TrackNumber / TrackUID / codec / payload")
            },
        )
    }

    state.container.pendingTrackImports.forEach { import ->
        mutations += ContainerMutationUi(
            id = "add-track:${import.sourceUri}#${import.sourceTrackNumber}",
            kind = ContainerMutationKind.ADD_TRACK,
            source = ContainerMutationSource.EXTERNAL_TRACK,
            title = "导入 ${import.kind.name.lowercase()} 轨道",
            detail = buildString {
                append(import.sourceName)
                append(" · Track #").append(import.sourceTrackNumber)
                append(" · ").append(import.codecId)
                if (import.name.isNotBlank()) append(" · ").append(import.name)
                if (import.language.isNotBlank()) append(" · legacy ").append(import.language)
                if (import.languageBcp47.isNotBlank()) append(" · BCP 47 ").append(import.languageBcp47)
                if (import.isDefault) append(" · Default")
                if (import.isForced) append(" · Forced")
                if (import.hearingImpaired) append(" · Hearing impaired")
                if (import.visualImpaired) append(" · Visual impaired")
                if (import.textDescriptions) append(" · Text descriptions")
                if (import.original) append(" · Original")
                if (import.commentary) append(" · Commentary")
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
    val remainingTrackCount = currentTrackTargets.size - trackRemovalTargets.size
    val selectedTrackTarget = state.container.resources.firstOrNull {
        it.trackNumber == state.container.selectedTrackNumber
    }?.trackTarget
    val removesDirtySelectedAss =
        state.dirty && selectedTrackTarget != null && selectedTrackTarget in trackRemovalTargets
    val importKeys = state.container.pendingTrackImports.map {
        it.sourceUri to it.sourceTrackNumber
    }
    val duplicateTrackImports = importKeys.size != importKeys.distinct().size
    val invalidTrackLegacyLanguage =
        state.container.pendingTrackMetadataEdits.any {
            it.language.isNotEmpty() && !it.language.matches(Regex("[a-z]{3}"))
        } ||
            state.container.pendingTrackImports.any {
                it.language.isNotEmpty() && !it.language.matches(Regex("[a-z]{3}"))
            }
    val bcp47Pattern = Regex("[A-Za-z0-9]{1,8}(?:-[A-Za-z0-9]{1,8})*")
    val invalidTrackBcp47 =
        state.container.pendingTrackMetadataEdits.any {
            it.languageBcp47.isNotEmpty() && !it.languageBcp47.matches(bcp47Pattern)
        } ||
            state.container.pendingTrackImports.any {
                it.languageBcp47.isNotEmpty() && !it.languageBcp47.matches(bcp47Pattern)
            }

    checks += ContainerCompatibilityCheckUi(
        dimension = ContainerCompatibilityDimension.CONTAINER_STRUCTURE,
        status = when {
            conflictingAttachmentTargets.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            missingAttachmentTargets.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            conflictingTrackTargets.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            missingTrackTargets.isNotEmpty() -> ContainerCompatibilityStatus.UNSUPPORTED
            trackRemovalTargets.isNotEmpty() && remainingTrackCount <= 0 -> ContainerCompatibilityStatus.UNSUPPORTED
            removesDirtySelectedAss -> ContainerCompatibilityStatus.UNSUPPORTED
            duplicateTrackImports -> ContainerCompatibilityStatus.UNSUPPORTED
            invalidTrackLegacyLanguage -> ContainerCompatibilityStatus.UNSUPPORTED
            invalidTrackBcp47 -> ContainerCompatibilityStatus.UNSUPPORTED
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
            trackRemovalTargets.isNotEmpty() && remainingTrackCount <= 0 ->
                "不能删除容器中的全部轨道。"
            removesDirtySelectedAss ->
                "当前正在编辑且未保存的 ASS 轨被计划删除；请先保存、放弃修改或取消删除。"
            duplicateTrackImports ->
                "同一个外部来源 Track 不能在一次计划中重复导入。"
            invalidTrackLegacyLanguage ->
                "Track 的 legacy Language 必须为空或 3 字母 ISO 639-2 代码。"
            invalidTrackBcp47 ->
                "Track 的 Language IETF / BCP 47 必须是由 1–8 位字母数字 subtags 组成的连字符标签。"
            mutations.isEmpty() -> "尚无待执行的容器修改。"
            else ->
                "当前计划中的 ASS、Track 添加 / 删除 / 元数据修改与 Attachment 修改均有明确写入路径；Track 添加在结构层追加新 TrackNumber / TrackUID。" 
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
        detail = "写回后重新扫描实际 MKV，并验证幸存轨道身份/顺序、导入轨道的新 TrackNumber / TrackUID / codec / metadata、章节、未改附件及其他计划修改。",
    )

    val hasGenericAttachment = mutations.any {
        it.source == ContainerMutationSource.GENERIC_ATTACHMENT ||
            it.source == ContainerMutationSource.EXISTING_ATTACHMENT
    }
    val hasTrackMutation = mutations.any {
        it.source == ContainerMutationSource.EXISTING_TRACK ||
            it.source == ContainerMutationSource.EXTERNAL_TRACK
    }
    val importsSubtitleWithSourceAttachments = state.container.pendingTrackImports.any {
        it.kind == ContainerResourceKind.SUBTITLE && it.sourceAttachmentCount > 0
    }
    val hasFontAttachment = mutations.any { it.source == ContainerMutationSource.FONT_PACKAGE }
    val hasAss = mutations.any { it.kind == ContainerMutationKind.REPLACE_ASS_TRACK }

    if (importsSubtitleWithSourceAttachments) {
        checks += ContainerCompatibilityCheckUi(
            dimension = ContainerCompatibilityDimension.SOURCE_INVENTORY,
            status = ContainerCompatibilityStatus.WARNING,
            title = "导入字幕轨不自动复制来源附件",
            detail = "所选字幕来源包含 Attachment；本次只导入所选 Track。若字幕依赖来源字体，请另外把需要的字体加入附件计划。",
        )
    }

    checks += when {
        hasTrackMutation -> ContainerCompatibilityCheckUi(
            dimension = ContainerCompatibilityDimension.DOWNSTREAM,
            status = ContainerCompatibilityStatus.WARNING,
            title = "播放器轨道选择行为需验证",
            detail = "Track 添加 / 删除、legacy Language、BCP 47、Default / Forced 与无障碍/语义 disposition 可能影响播放器选轨、标签和呈现；容器结构可验证，但播放器策略不是 Matroska 结构保证。",
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

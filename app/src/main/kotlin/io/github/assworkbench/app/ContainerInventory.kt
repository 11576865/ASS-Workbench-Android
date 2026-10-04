package io.github.assworkbench.app

import io.github.assworkbench.container.MatroskaScanResult
import io.github.assworkbench.container.MatroskaTrackKind

private data class InventoryItem(
    val rowKey: String,
    val kind: ContainerResourceKind,
    val matchKey: String,
    val strongIdentity: Boolean,
    val fingerprint: String,
    val ui: ContainerResourceUi,
)

internal fun baselineContainerResources(scan: MatroskaScanResult): List<ContainerResourceUi> =
    scan.inventoryItems().map { it.ui }

internal fun diffContainerResources(
    baseline: MatroskaScanResult,
    current: MatroskaScanResult,
): List<ContainerResourceUi> {
    val before = baseline.inventoryItems()
    val after = current.inventoryItems()
    val beforeByKey = before.groupBy { it.matchKey }
    val afterByKey = after.groupBy { it.matchKey }
    val consumed = mutableSetOf<String>()
    val result = mutableListOf<ContainerResourceUi>()

    after.forEach { item ->
        val candidates = beforeByKey[item.matchKey].orEmpty().filterNot { it.rowKey in consumed }
        val uniqueWeakMatch = !item.strongIdentity &&
            candidates.size == 1 &&
            afterByKey[item.matchKey].orEmpty().size == 1
        val matched = when {
            item.strongIdentity && candidates.size == 1 -> candidates.single()
            uniqueWeakMatch -> candidates.single()
            else -> null
        }

        if (matched != null) {
            consumed += matched.rowKey
            result += item.ui.copy(
                change = if (item.fingerprint == matched.fingerprint) {
                    ContainerResourceChange.UNCHANGED
                } else {
                    ContainerResourceChange.MODIFIED
                }
            )
        } else if (candidates.isNotEmpty()) {
            result += item.ui.copy(change = ContainerResourceChange.UNRESOLVED)
        } else {
            val hasSameKindBefore = before.any { it.kind == item.kind && it.rowKey !in consumed }
            result += item.ui.copy(
                change = if (item.strongIdentity || !hasSameKindBefore) {
                    ContainerResourceChange.ADDED
                } else {
                    ContainerResourceChange.UNRESOLVED
                }
            )
        }
    }

    before.filterNot { it.rowKey in consumed }.forEach { item ->
        val sameKeyAfter = afterByKey[item.matchKey].orEmpty()
        if (sameKeyAfter.isEmpty()) {
            val hasSameKindAfter = after.any { it.kind == item.kind }
            result += item.ui.copy(
                change = if (item.strongIdentity || !hasSameKindAfter) {
                    ContainerResourceChange.REMOVED
                } else {
                    ContainerResourceChange.UNRESOLVED
                }
            )
        }
    }
    return result
}

internal fun MatroskaScanResult.trackPreservationSignature(): List<String> =
    trackInfos.map {
        listOf(
            it.number.toString(),
            it.uid?.toString().orEmpty(),
            it.typeCode.toString(),
            it.codecId,
            it.language,
            it.name,
            it.isDefault.toString(),
            it.isForced.toString(),
        ).joinToString("\u001f")
    }

internal fun verifyContainerTrackMutations(
    source: MatroskaScanResult,
    output: MatroskaScanResult,
    removals: List<PendingContainerTrackRemovalUi>,
    metadataEdits: List<PendingContainerTrackMetadataUi>,
    imports: List<PendingContainerTrackImportUi> = emptyList(),
) {
    val removalTargets = removals.mapTo(hashSetOf()) { it.target }
    val metadataByTarget = metadataEdits.associateBy { it.target }
    val survivors = source.trackInfos.filterNot { it.containerTrackTarget() in removalTargets }

    require(output.trackInfos.size == survivors.size + imports.size) {
        "写回验证失败：预期轨道数 ${survivors.size + imports.size}，实际 ${output.trackInfos.size}"
    }

    survivors.zip(output.trackInfos.take(survivors.size)).forEachIndexed { index, (before, after) ->
        val target = before.containerTrackTarget()
        require(after.number == before.number) {
            "写回验证失败：第 ${index + 1} 个未删除轨道的 TrackNumber 改变"
        }
        require(after.uid == before.uid) {
            "写回验证失败：Track #${before.number} 的 TrackUID 改变"
        }
        require(after.typeCode == before.typeCode && after.codecId == before.codecId) {
            "写回验证失败：Track #${before.number} 的类型或 codec 改变"
        }

        val edit = metadataByTarget[target]
        if (edit == null) {
            require(
                after.name == before.name &&
                    after.language == before.language &&
                    after.isDefault == before.isDefault &&
                    after.isForced == before.isForced
            ) {
                "写回验证失败：未计划修改的 Track #${before.number} 元数据发生变化"
            }
        } else {
            require(after.name == edit.name) {
                "写回验证失败：Track #${before.number} 名称修改未生效"
            }
            require(after.language == edit.language) {
                "写回验证失败：Track #${before.number} 语言修改未生效"
            }
            require(after.isDefault == edit.isDefault) {
                "写回验证失败：Track #${before.number} Default 标志修改未生效"
            }
            require(after.isForced == edit.isForced) {
                "写回验证失败：Track #${before.number} Forced 标志修改未生效"
            }
        }
    }

    removals.forEach { removal ->
        require(output.trackInfos.none { it.containerTrackTarget() == removal.target }) {
            "写回验证失败：计划删除的轨道仍存在：${removal.name}"
        }
    }

    val originalNumbers = source.trackInfos.mapTo(hashSetOf()) { it.number }
    val originalUids = source.trackInfos.mapNotNullTo(hashSetOf()) { it.uid }
    val importedOutput = output.trackInfos.drop(survivors.size)
    val importedNumbers = hashSetOf<Long>()
    val importedUids = hashSetOf<Long>()

    imports.zip(importedOutput).forEach { (planned, actual) ->
        require(actual.number !in originalNumbers && importedNumbers.add(actual.number)) {
            "写回验证失败：导入轨道复用了已有 TrackNumber #${actual.number}"
        }
        val uid = actual.uid
        require(uid != null && uid !in originalUids && importedUids.add(uid)) {
            "写回验证失败：导入轨道没有获得新的唯一 TrackUID"
        }
        require(actual.kind.toContainerResourceKind() == planned.kind) {
            "写回验证失败：导入轨道类型不匹配：${planned.sourceName} Track #${planned.sourceTrackNumber}"
        }
        require(actual.codecId == planned.codecId) {
            "写回验证失败：导入轨道 codec 不匹配：${planned.codecId} → ${actual.codecId}"
        }
        require(
            actual.name == planned.name &&
                actual.language == planned.language &&
                actual.isDefault == planned.isDefault &&
                actual.isForced == planned.isForced
        ) {
            "写回验证失败：导入轨道 metadata 与计划不一致：${planned.sourceName} Track #${planned.sourceTrackNumber}"
        }
    }
}

private fun MatroskaTrackKind.toContainerResourceKind(): ContainerResourceKind = when (this) {
    MatroskaTrackKind.VIDEO -> ContainerResourceKind.VIDEO
    MatroskaTrackKind.AUDIO -> ContainerResourceKind.AUDIO
    MatroskaTrackKind.SUBTITLE -> ContainerResourceKind.SUBTITLE
    else -> ContainerResourceKind.OTHER
}

private fun io.github.assworkbench.container.MatroskaTrackInfo.containerTrackTarget(): String =
    uid?.let { "uid:$it" } ?: "number:$number"

internal fun MatroskaScanResult.attachmentPreservationKeys(): List<String> =
    attachmentInfos.map { info ->
        info.uid?.let { "uid:$it" }
            ?: info.sha256?.let { "sha256:$it" }
            ?: "weak:${info.fileName}\u001f${info.mimeType}\u001f${info.sizeBytes ?: -1L}"
    }

private fun MatroskaScanResult.inventoryItems(): List<InventoryItem> {
    val assEvents = subtitleTracks.associateBy({ it.number }, { it.packets.size })
    val items = mutableListOf<InventoryItem>()

    trackInfos.forEach { info ->
        val kind = when (info.kind) {
            MatroskaTrackKind.VIDEO -> ContainerResourceKind.VIDEO
            MatroskaTrackKind.AUDIO -> ContainerResourceKind.AUDIO
            MatroskaTrackKind.SUBTITLE -> ContainerResourceKind.SUBTITLE
            else -> ContainerResourceKind.OTHER
        }
        val kindLabel = when (kind) {
            ContainerResourceKind.VIDEO -> "视频"
            ContainerResourceKind.AUDIO -> "音频"
            ContainerResourceKind.SUBTITLE -> "字幕"
            else -> "轨道"
        }
        val title = info.name.ifBlank {
            when {
                kind == ContainerResourceKind.SUBTITLE && info.codecId == "S_TEXT/ASS" -> "ASS 字幕"
                info.codecId.isNotBlank() -> info.codecId
                else -> kindLabel
            }
        }
        val detail = buildList {
            if (info.codecId.isNotBlank()) add(info.codecId)
            if (info.language.isNotBlank()) add(info.language)
            add("Track #${info.number}")
            if (info.isDefault) add("Default")
            if (info.isForced) add("Forced")
            assEvents[info.number]?.let { add("$it events") }
        }.joinToString(" · ")
        val strongKey = info.uid?.let { "track:uid:$it" }
        val weakKey = "track:weak:${info.kind}:${info.codecId}:${info.language}:${info.name}"
        val rowKey = strongKey ?: "track:${info.typeCode}:${info.number}:${info.codecId}"
        val fingerprint = listOf(
            info.kind.name,
            info.codecId,
            info.language,
            info.name,
            info.isDefault.toString(),
            info.isForced.toString(),
            info.contentHash.orEmpty(),
        ).joinToString("\u001f")
        items += InventoryItem(
            rowKey = rowKey,
            kind = kind,
            matchKey = strongKey ?: weakKey,
            strongIdentity = strongKey != null,
            fingerprint = fingerprint,
            ui = ContainerResourceUi(
                rowKey = rowKey,
                kind = kind,
                title = title,
                detail = detail,
                trackNumber = info.number,
                trackTarget = info.uid?.let { "uid:$it" } ?: "number:${info.number}",
                trackUid = info.uid,
                trackCodecId = info.codecId,
                trackName = info.name,
                trackLanguage = info.language,
                trackIsDefault = info.isDefault,
                trackIsForced = info.isForced,
                editableAss = info.kind == MatroskaTrackKind.SUBTITLE && info.codecId == "S_TEXT/ASS",
            ),
        )
    }

    val attachmentNameCounts = attachmentInfos.groupingBy { it.fileName }.eachCount()
    attachmentInfos.forEachIndexed { index, info ->
        val kind = if (info.isSupportedFont) ContainerResourceKind.FONT else ContainerResourceKind.ATTACHMENT
        val strongKey = info.uid?.let { "attachment:uid:$it" }
            ?: info.sha256?.let { "attachment:sha256:$it" }
        val weakKey = "attachment:weak:${info.fileName}:${info.mimeType}:${info.sizeBytes ?: -1L}"
        val rowKey = strongKey ?: "attachment:$index:${info.fileName}"
        val size = info.sizeBytes?.let(::formatBytes)
        val detail = buildList {
            if (info.mimeType.isNotBlank()) add(info.mimeType)
            if (size != null) add(size)
            if (info.description.isNotBlank()) add(info.description)
            if (!info.dataAvailable) add("payload 未载入")
        }.joinToString(" · ")
        items += InventoryItem(
            rowKey = rowKey,
            kind = kind,
            matchKey = strongKey ?: weakKey,
            strongIdentity = strongKey != null,
            fingerprint = listOf(
                info.fileName,
                info.mimeType,
                info.description,
                (info.sizeBytes ?: -1L).toString(),
                info.sha256.orEmpty(),
            ).joinToString("\u001f"),
            ui = ContainerResourceUi(
                rowKey = rowKey,
                kind = kind,
                title = info.fileName,
                detail = detail,
                attachmentTarget = info.uid?.toString()
                    ?: info.fileName.takeIf { attachmentNameCounts[it] == 1 },
                attachmentMimeType = info.mimeType,
                attachmentDescription = info.description,
                attachmentSizeBytes = info.sizeBytes,
                attachmentSha256 = info.sha256,
            ),
        )
    }

    if (chapterCount > 0) {
        items += InventoryItem(
            rowKey = "chapters",
            kind = ContainerResourceKind.CHAPTERS,
            matchKey = "chapters",
            strongIdentity = true,
            fingerprint = chapterCount.toString(),
            ui = ContainerResourceUi(
                rowKey = "chapters",
                kind = ContainerResourceKind.CHAPTERS,
                title = "章节",
                detail = "$chapterCount chapters",
            ),
        )
    }
    return items
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes.toDouble() / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes.toDouble() / 1024.0)
    else -> "$bytes B"
}

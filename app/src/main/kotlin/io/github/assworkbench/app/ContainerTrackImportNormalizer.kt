package io.github.assworkbench.app

import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssTextDecoder
import io.github.assworkbench.domain.SrtCodec
import java.security.MessageDigest

data class NormalizedSubtitleTrackSource(
    val sourceKind: ContainerTrackImportSourceKind,
    val normalizedAssText: String,
    val normalizedSha256: String,
    val eventCount: Int,
)

internal fun detectStandaloneSubtitleTrackSourceKind(
    sourceName: String,
): ContainerTrackImportSourceKind? = when (sourceName.substringAfterLast('.', "").lowercase()) {
    "ass", "ssa" -> ContainerTrackImportSourceKind.STANDALONE_ASS
    "srt" -> ContainerTrackImportSourceKind.STANDALONE_SRT
    else -> null
}

internal fun normalizeStandaloneSubtitleTrackSource(
    sourceName: String,
    raw: ByteArray,
): NormalizedSubtitleTrackSource? {
    val kind = detectStandaloneSubtitleTrackSourceKind(sourceName) ?: return null
    return normalizeStandaloneSubtitleTrackSource(kind, raw)
}

internal fun normalizeStandaloneSubtitleTrackSource(
    sourceKind: ContainerTrackImportSourceKind,
    raw: ByteArray,
): NormalizedSubtitleTrackSource {
    require(raw.isNotEmpty()) { "独立字幕来源为空" }
    val decoded = AssTextDecoder.decode(raw).text
    val document = when (sourceKind) {
        ContainerTrackImportSourceKind.STANDALONE_ASS -> AssCodec.parse(decoded)
        ContainerTrackImportSourceKind.STANDALONE_SRT -> SrtCodec.parse(decoded)
        ContainerTrackImportSourceKind.MATROSKA_TRACK ->
            error("Matroska Track 不经过独立字幕规范化器")
    }
    require(document.events.isNotEmpty()) { "独立字幕来源没有可导入的 Events" }

    val normalizedAssText = AssCodec.write(document)
    val normalizedBytes = normalizedAssText.toByteArray(Charsets.UTF_8)
    val sha256 = MessageDigest.getInstance("SHA-256")
        .digest(normalizedBytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    return NormalizedSubtitleTrackSource(
        sourceKind = sourceKind,
        normalizedAssText = normalizedAssText,
        normalizedSha256 = sha256,
        eventCount = document.events.size,
    )
}

internal fun ContainerTrackImportSourceKind.isStandaloneSubtitleSource(): Boolean =
    this == ContainerTrackImportSourceKind.STANDALONE_ASS ||
        this == ContainerTrackImportSourceKind.STANDALONE_SRT

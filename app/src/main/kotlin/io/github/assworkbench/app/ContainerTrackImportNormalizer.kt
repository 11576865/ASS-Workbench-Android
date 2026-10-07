package io.github.assworkbench.app

import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssTextDecoder
import io.github.assworkbench.domain.SrtCodec
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

private const val MEBIBYTE = 1024 * 1024
internal const val MAX_STANDALONE_SUBTITLE_BYTES = 32 * MEBIBYTE

data class NormalizedSubtitleTrackSource(
    val sourceKind: ContainerTrackImportSourceKind,
    val normalizedAssText: String,
    val normalizedSha256: String,
    val eventCount: Int,
)

internal fun detectStandaloneSubtitleTrackSourceKind(
    sourceName: String,
    mimeType: String? = null,
): ContainerTrackImportSourceKind? {
    val byExtension = when (sourceName.substringAfterLast('.', "").lowercase()) {
        "ass", "ssa" -> ContainerTrackImportSourceKind.STANDALONE_ASS
        "srt" -> ContainerTrackImportSourceKind.STANDALONE_SRT
        else -> null
    }
    if (byExtension != null) return byExtension

    return when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
        "application/x-ass",
        "application/x-ssa",
        "text/x-ass",
        "text/x-ssa" -> ContainerTrackImportSourceKind.STANDALONE_ASS

        "application/x-subrip",
        "text/srt" -> ContainerTrackImportSourceKind.STANDALONE_SRT

        else -> null
    }
}

internal fun readStandaloneSubtitleSourceBytes(
    input: InputStream,
    maxBytes: Int = MAX_STANDALONE_SUBTITLE_BYTES,
): ByteArray {
    require(maxBytes > 0) { "独立字幕读取上限必须大于 0" }
    val output = ByteArrayOutputStream(minOf(maxBytes, 256 * 1024))
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue

        val remaining = maxBytes - total
        val limitLabel = if (maxBytes % MEBIBYTE == 0) {
            "${maxBytes / MEBIBYTE} MiB"
        } else {
            "$maxBytes bytes"
        }
        require(count <= remaining) {
            "独立字幕来源超过 $limitLabel 读取上限"
        }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}

internal fun normalizeStandaloneSubtitleTrackSource(
    sourceName: String,
    raw: ByteArray,
    mimeType: String? = null,
): NormalizedSubtitleTrackSource? {
    val kind = detectStandaloneSubtitleTrackSourceKind(sourceName, mimeType) ?: return null
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
        ContainerTrackImportSourceKind.NORMALIZED_MEDIA_PACKETS ->
            error("规范化媒体 packet 不经过字幕规范化器")
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

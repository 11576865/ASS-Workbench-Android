package io.github.assworkbench.domain

import java.util.Base64

data class ReviewSidecar(
    val sourceStyle: String = "",
    val targetStyle: String = "",
    val confirmedKeys: Set<String> = emptySet(),
    val originalTextByKey: Map<String, String> = emptyMap(),
    val legacyConfirmedIds: Set<Long> = emptySet(),
    val legacyOriginalTextById: Map<Long, String> = emptyMap(),
)

object ReviewSidecarCodec {
    private const val HEADER_V2 = "ASSWB_REVIEW_V2"
    private const val HEADER_V1 = "ASSWB_REVIEW_V1"

    fun encode(value: ReviewSidecar): String = buildString {
        append(HEADER_V2).append('\n')
        append("S\t").append(enc(value.sourceStyle)).append('\n')
        append("T\t").append(enc(value.targetStyle)).append('\n')
        value.confirmedKeys.sorted().forEach { key ->
            append("C\t").append(enc(key)).append('\n')
        }
        value.originalTextByKey.toSortedMap().forEach { (key, text) ->
            append("O\t").append(enc(key)).append('\t').append(enc(text)).append('\n')
        }
    }

    fun decode(text: String): ReviewSidecar? {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        return when (lines.firstOrNull()?.trim()) {
            HEADER_V2 -> decodeV2(lines.drop(1))
            HEADER_V1 -> decodeV1(lines.drop(1))
            else -> null
        }
    }

    private fun decodeV2(lines: List<String>): ReviewSidecar {
        var source = ""
        var target = ""
        val confirmed = linkedSetOf<String>()
        val originals = linkedMapOf<String, String>()
        lines.forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "S" -> source = parts.getOrNull(1)?.let(::dec).orEmpty()
                "T" -> target = parts.getOrNull(1)?.let(::dec).orEmpty()
                "C" -> parts.getOrNull(1)?.let(::dec)?.takeIf(String::isNotBlank)?.let(confirmed::add)
                "O" -> {
                    val key = parts.getOrNull(1)?.let(::dec)
                    val value = parts.getOrNull(2)?.let(::dec)
                    if (!key.isNullOrBlank() && value != null) originals[key] = value
                }
            }
        }
        return ReviewSidecar(
            sourceStyle = source,
            targetStyle = target,
            confirmedKeys = confirmed,
            originalTextByKey = originals,
        )
    }

    private fun decodeV1(lines: List<String>): ReviewSidecar {
        var source = ""
        var target = ""
        val confirmed = linkedSetOf<Long>()
        val originals = linkedMapOf<Long, String>()
        lines.forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "S" -> source = parts.getOrNull(1)?.let(::dec).orEmpty()
                "T" -> target = parts.getOrNull(1)?.let(::dec).orEmpty()
                "C" -> parts.getOrNull(1).orEmpty().split(',')
                    .mapNotNull { it.trim().toLongOrNull() }
                    .forEach(confirmed::add)
                "O" -> {
                    val id = parts.getOrNull(1)?.toLongOrNull()
                    val value = parts.getOrNull(2)?.let(::dec)
                    if (id != null && value != null) originals[id] = value
                }
            }
        }
        return ReviewSidecar(
            sourceStyle = source,
            targetStyle = target,
            legacyConfirmedIds = confirmed,
            legacyOriginalTextById = originals,
        )
    }

    private fun enc(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun dec(value: String): String =
        runCatching { String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8) }.getOrDefault("")
}

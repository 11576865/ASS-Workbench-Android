package io.github.assworkbench.domain

import java.util.Base64

data class ReviewSidecar(
    val sourceStyle: String = "",
    val targetStyle: String = "",
    val confirmedIds: Set<Long> = emptySet(),
    val originalTextById: Map<Long, String> = emptyMap(),
)

object ReviewSidecarCodec {
    private const val HEADER = "ASSWB_REVIEW_V1"

    fun encode(value: ReviewSidecar): String = buildString {
        append(HEADER).append('\n')
        append("S\t").append(enc(value.sourceStyle)).append('\n')
        append("T\t").append(enc(value.targetStyle)).append('\n')
        append("C\t").append(value.confirmedIds.sorted().joinToString(",")).append('\n')
        value.originalTextById.toSortedMap().forEach { (id, text) ->
            append("O\t").append(id).append('\t').append(enc(text)).append('\n')
        }
    }

    fun decode(text: String): ReviewSidecar? {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        if (lines.firstOrNull()?.trim() != HEADER) return null
        var source = ""
        var target = ""
        val confirmed = linkedSetOf<Long>()
        val originals = linkedMapOf<Long, String>()
        lines.drop(1).forEach { line ->
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
        return ReviewSidecar(source, target, confirmed, originals)
    }

    private fun enc(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun dec(value: String): String =
        runCatching { String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8) }.getOrDefault("")
}

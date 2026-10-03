package io.github.assworkbench.domain

data class AssKaraokeRevealFxSpec(
    val revealMs: Long = 160L,
    val startBlur: Double = 3.5,
    val accel: Double? = null,
) {
    init {
        require(revealMs >= 0L) { "逐音节显现时长不能为负数。" }
        require(startBlur.isFinite() && startBlur in 0.0..20.0) {
            "逐音节起始 Blur 必须在 0..20 之间。"
        }
        if (accel != null) require(accel.isFinite() && accel > 0.0) {
            "Accel 必须大于 0。"
        }
    }
}

data class AssKaraokeRevealCompatibility(
    val compatible: Boolean,
    val segmentCount: Int,
    val reason: String? = null,
)

data class AssKaraokeRevealFxPlan(
    val sourceSegmentCount: Int,
    val generatedText: String,
)

/**
 * Conservative karaoke-FX compiler.
 *
 * This first slice stays inside one canonical Event: each karaoke marker receives a hidden/blurred
 * base state plus a time-scoped transform that reveals that syllable at its cumulative karaoke
 * time. Because the effect is span-local, no glyph measurement or synthetic per-syllable
 * positioning is required.
 *
 * Existing alpha/blur/transform tags in the controlled spans are rejected rather than guessed.
 */
object AssKaraokeFxAuthoring {
    private val conflictingTag = Regex(
        """\\(?:alpha|[1-4]a|blur|t)(?=[^A-Za-z]|$)""",
        RegexOption.IGNORE_CASE,
    )

    fun inspectProgressiveRevealCompatibility(text: String): AssKaraokeRevealCompatibility {
        val segments = AssKaraokeCodec.parse(text)
        if (segments.isEmpty()) {
            return AssKaraokeRevealCompatibility(
                compatible = false,
                segmentCount = 0,
                reason = "没有可用的 Karaoke 音节。",
            )
        }
        if (segments.any { it.mode == AssKaraokeMode.KT }) {
            return AssKaraokeRevealCompatibility(
                compatible = false,
                segmentCount = segments.size,
                reason = "\\kt 使用绝对 Karaoke 时间，不能按累计时长生成逐音节显现。",
            )
        }
        segments.forEachIndexed { index, segment ->
            val ownedSyntax = buildString {
                if (index == 0) append(segment.leadingText)
                append(segment.overridePrefix)
                append(segment.overrideSuffix)
                append(segment.text)
            }
            if (conflictingTag.containsMatchIn(ownedSyntax)) {
                return AssKaraokeRevealCompatibility(
                    compatible = false,
                    segmentCount = segments.size,
                    reason = "第 ${index + 1} 个音节已有 alpha / blur / transform。",
                )
            }
        }
        return AssKaraokeRevealCompatibility(
            compatible = true,
            segmentCount = segments.size,
        )
    }

    fun planProgressiveReveal(
        text: String,
        spec: AssKaraokeRevealFxSpec = AssKaraokeRevealFxSpec(),
    ): AssKaraokeRevealFxPlan {
        val compatibility = inspectProgressiveRevealCompatibility(text)
        require(compatibility.compatible) {
            compatibility.reason ?: "当前 Event 不能安全生成逐音节显现。"
        }

        val segments = AssKaraokeCodec.parse(text)
        var cursorMs = 0L
        val patched = segments.map { segment ->
            val segmentDurationMs = segment.centiseconds.toLong() * 10L
            val revealDuration = minOf(spec.revealMs, segmentDurationMs)
            val endMs = cursorMs + revealDuration
            val fx = buildString {
                if (revealDuration <= 0L) {
                    append("\\alpha&H00&")
                    append("\\blur0")
                } else {
                    append("\\alpha&HFF&")
                    if (spec.startBlur > 0.0) append("\\blur").append(format(spec.startBlur))
                    append("\\t(")
                    append(cursorMs).append(',').append(endMs)
                    spec.accel?.let { append(',').append(format(it)) }
                    append(",\\alpha&H00&\\blur0)")
                }
            }
            cursorMs += segmentDurationMs
            segment.copy(overrideSuffix = segment.overrideSuffix + fx)
        }

        return AssKaraokeRevealFxPlan(
            sourceSegmentCount = segments.size,
            generatedText = AssKaraokeCodec.write(patched),
        )
    }

    fun applyProgressiveReveal(
        text: String,
        spec: AssKaraokeRevealFxSpec = AssKaraokeRevealFxSpec(),
    ): String = planProgressiveReveal(text, spec).generatedText

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}

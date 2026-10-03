package io.github.assworkbench.domain

data class AssKaraokeFlipFxSpec(
    val startScalePercent: Double = 12.0,
    val overshootScalePercent: Double = 118.0,
    val startRotationXDegrees: Double = 86.0,
)

data class AssKaraokeRevealFxSpec(
    val revealMs: Long = 160L,
    val startBlur: Double = 3.5,
    val accel: Double? = null,
    val flip: AssKaraokeFlipFxSpec? = null,
)

data class AssKaraokeRevealFxPlan(
    val sourceSegmentCount: Int,
    val generatedText: String,
)

/**
 * Conservative karaoke-FX compiler.
 *
 * Effects stay span-local inside one canonical Event. Timing is derived from existing karaoke
 * markers, so glyph measurement and synthetic per-syllable positioning are unnecessary.
 *
 * Alpha/blur/transform conflicts are rejected rather than guessed. Optional flip/stretch motion
 * resolves against the Event's effective base scale/rotation and returns each syllable to that
 * same base state.
 */
object AssKaraokeFxAuthoring {
    private val revealConflictTag = Regex(
        """\\(?:alpha|[1-4]a|blur|t)(?=[^A-Za-z]|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val flipConflictTag = Regex(
        """\\(?:fscy|frx)(?=[^A-Za-z]|$)""",
        RegexOption.IGNORE_CASE,
    )

    fun planProgressiveReveal(
        document: AssDocument,
        eventId: Long,
        spec: AssKaraokeRevealFxSpec = AssKaraokeRevealFxSpec(),
    ): AssKaraokeRevealFxPlan {
        val event = document.events.firstOrNull { it.id == eventId } ?: error("源字幕不存在。")
        val style = document.styles.firstOrNull { it.name.equals(event.style, ignoreCase = true) }
        val geometry = AssGeometrySemantic.inspect(event.text)
        require(!geometry.malformedLeadingBlock) { "源字幕的前导 override block 不完整，不能安全生成 Karaoke FX。" }

        val baseScaleY = geometry.scaleY ?: style?.scaleY ?: 100.0
        val baseRotationX = geometry.rotationX ?: 0.0
        return planProgressiveReveal(
            text = event.text,
            spec = spec,
            baseScaleY = baseScaleY,
            baseRotationX = baseRotationX,
        )
    }

    fun planProgressiveReveal(
        text: String,
        spec: AssKaraokeRevealFxSpec = AssKaraokeRevealFxSpec(),
        baseScaleY: Double = 100.0,
        baseRotationX: Double = 0.0,
    ): AssKaraokeRevealFxPlan {
        validate(spec, baseScaleY, baseRotationX)

        val segments = AssKaraokeCodec.parse(text)
        require(segments.isNotEmpty()) { "当前 Event 没有可用的 Karaoke 音节。" }
        require(segments.none { it.mode == AssKaraokeMode.KT }) {
            "\\kt 使用绝对 Karaoke 时间；当前逐音节 FX 编译器不会把它误当累计时长。"
        }

        var cursorMs = 0L
        val patched = segments.mapIndexed { index, segment ->
            val revealOwnedSyntax = buildString {
                if (index == 0) append(segment.leadingText)
                append(segment.overridePrefix)
                append(segment.overrideSuffix)
                append(segment.text)
            }
            require(!revealConflictTag.containsMatchIn(revealOwnedSyntax)) {
                "第 ${index + 1} 个音节已有 alpha / blur / transform；自动 FX 已停止，避免覆盖原特效。"
            }

            if (spec.flip != null) {
                val spanOwnedSyntax = buildString {
                    append(segment.overridePrefix)
                    append(segment.overrideSuffix)
                    append(segment.text)
                }
                require(!flipConflictTag.containsMatchIn(spanOwnedSyntax)) {
                    "第 ${index + 1} 个音节已有 fscy / frx；翻转 FX 已停止，避免覆盖音节级几何。"
                }
            }

            val segmentDurationMs = segment.centiseconds.toLong() * 10L
            val revealDuration = minOf(spec.revealMs, segmentDurationMs)
            val endMs = cursorMs + revealDuration
            val fx = buildString {
                if (revealDuration <= 0L) {
                    append("\\alpha&H00&")
                    append("\\blur0")
                    spec.flip?.let {
                        append("\\fscy").append(format(baseScaleY))
                        append("\\frx").append(format(baseRotationX))
                    }
                } else {
                    append("\\alpha&HFF&")
                    if (spec.startBlur > 0.0) append("\\blur").append(format(spec.startBlur))
                    appendTransform(
                        fromMs = cursorMs,
                        toMs = endMs,
                        accel = spec.accel,
                        tags = "\\alpha&H00&\\blur0",
                    )

                    spec.flip?.let { flip ->
                        val middleMs = (cursorMs + (revealDuration * 2L / 3L))
                            .coerceIn(cursorMs + 1L, endMs)
                        val startScaleY = baseScaleY * flip.startScalePercent / 100.0
                        val overshootScaleY = baseScaleY * flip.overshootScalePercent / 100.0
                        val startRotationX = baseRotationX + flip.startRotationXDegrees

                        append("\\fscy").append(format(startScaleY))
                        append("\\frx").append(format(startRotationX))

                        if (middleMs < endMs) {
                            appendTransform(
                                fromMs = cursorMs,
                                toMs = middleMs,
                                accel = spec.accel,
                                tags = "\\fscy${format(overshootScaleY)}\\frx${format(baseRotationX)}",
                            )
                            appendTransform(
                                fromMs = middleMs,
                                toMs = endMs,
                                accel = spec.accel,
                                tags = "\\fscy${format(baseScaleY)}\\frx${format(baseRotationX)}",
                            )
                        } else {
                            appendTransform(
                                fromMs = cursorMs,
                                toMs = endMs,
                                accel = spec.accel,
                                tags = "\\fscy${format(baseScaleY)}\\frx${format(baseRotationX)}",
                            )
                        }
                    }
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
        baseScaleY: Double = 100.0,
        baseRotationX: Double = 0.0,
    ): String = planProgressiveReveal(text, spec, baseScaleY, baseRotationX).generatedText

    private fun validate(
        spec: AssKaraokeRevealFxSpec,
        baseScaleY: Double,
        baseRotationX: Double,
    ) {
        require(spec.revealMs >= 0L) { "逐音节显现时长不能为负数。" }
        require(spec.startBlur.isFinite() && spec.startBlur in 0.0..20.0) {
            "逐音节起始 Blur 必须在 0..20 之间。"
        }
        if (spec.accel != null) {
            require(spec.accel.isFinite() && spec.accel > 0.0) { "Accel 必须大于 0。" }
        }
        require(baseScaleY.isFinite() && baseScaleY > 0.0) { "基础 Scale Y 必须大于 0。" }
        require(baseRotationX.isFinite()) { "基础 Rotation X 必须是有限数字。" }

        spec.flip?.let { flip ->
            require(flip.startScalePercent.isFinite() && flip.startScalePercent > 0.0) {
                "翻转 FX 起始高度比例必须大于 0。"
            }
            require(flip.overshootScalePercent.isFinite() && flip.overshootScalePercent > 0.0) {
                "翻转 FX 回弹高度比例必须大于 0。"
            }
            require(flip.startRotationXDegrees.isFinite()) {
                "翻转 FX 起始 X 旋转必须是有限数字。"
            }
        }
    }

    private fun StringBuilder.appendTransform(
        fromMs: Long,
        toMs: Long,
        accel: Double?,
        tags: String,
    ) {
        append("\\t(")
        append(fromMs).append(',').append(toMs)
        accel?.let { append(',').append(format(it)) }
        append(',').append(tags).append(')')
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}

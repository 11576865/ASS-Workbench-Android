package io.github.assworkbench.domain

import kotlin.math.roundToInt

/**
 * Small, explicit composition layer for effects that require more than one ASS Event.
 *
 * The generated companion Event is ordinary canonical ASS after creation. No hidden linkage is
 * persisted between the source and generated Event; users can edit, move or delete either one.
 */
data class AssReflectionFxSpec(
    val offsetY: Double = 56.0,
    val verticalScalePercent: Double = 35.0,
    val opacityPercent: Double = 35.0,
    val blur: Double = 1.5,
)

data class AssFlipEntranceSpec(
    val durationMs: Long = 280L,
    val startScalePercent: Double = 8.0,
    val overshootScalePercent: Double = 118.0,
    val startRotationXDegrees: Double = 88.0,
    val accel: Double? = null,
)

data class AssGlowFxSpec(
    val opacityPercent: Double = 22.0,
    val blur: Double = 4.0,
    val border: Double = 3.0,
)

data class AssFxCompositionResult(
    val document: AssDocument,
    val sourceEventId: Long,
    val generatedEventIds: List<Long>,
) {
    val generatedEventId: Long get() = generatedEventIds.first()
}

data class AssFxBatchCompositionResult(
    val document: AssDocument,
    val sourceEventIds: List<Long>,
    val generatedEventIds: List<Long>,
)

object AssFxComposition {
    fun composeReflection(
        document: AssDocument,
        eventId: Long,
        reflection: AssReflectionFxSpec = AssReflectionFxSpec(),
        entrance: AssFlipEntranceSpec? = null,
    ): AssFxCompositionResult =
        composeMirrorStack(
            document = document,
            eventId = eventId,
            reflection = reflection,
            glow = null,
            entrance = entrance,
        )

    fun composeMirrorStackBatch(
        document: AssDocument,
        eventIds: Set<Long>,
        reflection: AssReflectionFxSpec = AssReflectionFxSpec(),
        glow: AssGlowFxSpec? = AssGlowFxSpec(),
        entrance: AssFlipEntranceSpec? = null,
    ): AssFxBatchCompositionResult {
        require(eventIds.isNotEmpty()) { "至少需要一个源字幕。" }
        val orderedSourceIds = document.events.map { it.id }.filter { it in eventIds }
        require(orderedSourceIds.size == eventIds.size) { "选择中包含已经不存在的字幕。" }

        var next = document
        val generated = mutableListOf<Long>()
        orderedSourceIds.forEach { sourceId ->
            val result = composeMirrorStack(
                document = next,
                eventId = sourceId,
                reflection = reflection,
                glow = glow,
                entrance = entrance,
            )
            next = result.document
            generated += result.generatedEventIds
        }
        return AssFxBatchCompositionResult(
            document = next,
            sourceEventIds = orderedSourceIds,
            generatedEventIds = generated,
        )
    }

    fun composeMirrorStack(
        document: AssDocument,
        eventId: Long,
        reflection: AssReflectionFxSpec = AssReflectionFxSpec(),
        glow: AssGlowFxSpec? = AssGlowFxSpec(),
        entrance: AssFlipEntranceSpec? = null,
    ): AssFxCompositionResult {
        var next = document
        val generated = mutableListOf<Long>()

        if (glow != null) {
            val glowResult = createGlow(next, eventId, glow)
            next = glowResult.document
            generated += glowResult.generatedEventIds
        }

        val reflectionResult = createReflection(next, eventId, reflection)
        next = reflectionResult.document
        generated += reflectionResult.generatedEventIds

        if (entrance != null) {
            // The generated layers are a visual stack, so entrance geometry must remain coherent.
            // Applying the same relative entrance to each layer preserves each layer's own base
            // scale/rotation (e.g. reflection ends at frx+180 rather than animating back to source).
            (listOf(eventId) + generated).forEach { targetId ->
                next = applyFlipEntrance(next, targetId, entrance)
            }
        }

        return AssFxCompositionResult(
            document = next,
            sourceEventId = eventId,
            generatedEventIds = generated,
        )
    }

    fun createGlow(
        document: AssDocument,
        eventId: Long,
        spec: AssGlowFxSpec = AssGlowFxSpec(),
    ): AssFxCompositionResult {
        require(spec.opacityPercent.isFinite() && spec.opacityPercent in 0.0..100.0) {
            "柔光不透明度必须在 0..100% 之间。"
        }
        require(spec.blur.isFinite() && spec.blur in 0.0..20.0) {
            "柔光 Blur 必须在 0..20 之间。"
        }
        require(spec.border.isFinite() && spec.border in 0.0..20.0) {
            "柔光 Border 必须在 0..20 之间。"
        }

        val sourceIndex = document.events.indexOfFirst { it.id == eventId }
        require(sourceIndex >= 0) { "源字幕不存在。" }
        val source = document.events[sourceIndex]
        require(!source.comment) { "Comment 事件不能直接生成可见柔光层。" }
        requireCompositionOwnershipCompatible(
            source.text,
            ownedTags = setOf("alpha", "1a", "2a", "3a", "4a", "blur", "bord"),
            effectLabel = "柔光层",
        )

        val alpha = ((1.0 - spec.opacityPercent / 100.0) * 255.0).roundToInt().coerceIn(0, 255)
        val glowText = appendLeadingOverride(
            source.text,
            buildString {
                append("\\alpha&H").append("%02X".format(alpha)).append("&")
                if (spec.blur > 0.0) append("\\blur").append(format(spec.blur))
                if (spec.border > 0.0) append("\\bord").append(format(spec.border))
            },
        )

        val newId = (document.events.maxOfOrNull { it.id } ?: 0L) + 1L
        val glowEvent = source.copy(
            id = newId,
            layer = (source.layer - 1).coerceAtLeast(0),
            text = glowText,
        )
        val events = document.events.toMutableList().apply {
            add(sourceIndex, glowEvent)
        }
        return AssFxCompositionResult(
            document = document.copy(events = events),
            sourceEventId = eventId,
            generatedEventIds = listOf(newId),
        )
    }

    fun createReflection(
        document: AssDocument,
        eventId: Long,
        spec: AssReflectionFxSpec = AssReflectionFxSpec(),
    ): AssFxCompositionResult {
        require(spec.offsetY.isFinite()) { "倒影 Y 偏移必须是有限数字。" }
        require(spec.verticalScalePercent.isFinite() && spec.verticalScalePercent > 0.0) {
            "倒影高度比例必须大于 0。"
        }
        require(spec.opacityPercent.isFinite() && spec.opacityPercent in 0.0..100.0) {
            "倒影不透明度必须在 0..100% 之间。"
        }
        require(spec.blur.isFinite() && spec.blur in 0.0..20.0) {
            "倒影 Blur 必须在 0..20 之间。"
        }

        val sourceIndex = document.events.indexOfFirst { it.id == eventId }
        require(sourceIndex >= 0) { "源字幕不存在。" }
        val source = document.events[sourceIndex]
        require(!source.comment) { "Comment 事件不能直接生成可见倒影。" }
        requireCompositionOwnershipCompatible(
            source.text,
            ownedTags = setOf("alpha", "1a", "2a", "3a", "4a", "blur", "fscy", "frx"),
            effectLabel = "倒影层",
        )

        val geometry = AssGeometrySemantic.inspect(source.text)
        require(!geometry.malformedLeadingBlock) { "源字幕的前导 override block 不完整，不能安全生成 FX。" }
        require(geometry.positionMode != AssPositionMode.CONFLICT) {
            "源字幕同时包含 \\pos 与 \\move；先消解位置冲突再生成 FX。"
        }

        val style = document.styles.firstOrNull { it.name.equals(source.style, ignoreCase = true) }
        val sourceScaleY = geometry.scaleY ?: style?.scaleY ?: 100.0
        val reflectionScaleY = (sourceScaleY * spec.verticalScalePercent / 100.0).coerceAtLeast(0.001)
        val sourceRotationX = geometry.rotationX ?: 0.0
        val alpha = ((1.0 - spec.opacityPercent / 100.0) * 255.0).roundToInt().coerceIn(0, 255)

        var reflectedText = source.text
        reflectedText = when (geometry.positionMode) {
            AssPositionMode.POSITION -> {
                val p = requireNotNull(geometry.position)
                AssGeometrySemantic.patchPosition(
                    reflectedText,
                    p.x.coerceIn(0.0, document.playResX.toDouble()),
                    (p.y + spec.offsetY).coerceIn(0.0, document.playResY.toDouble()),
                )
            }
            AssPositionMode.MOVE -> {
                val move = requireNotNull(geometry.move)
                AssGeometrySemantic.patchMove(
                    reflectedText,
                    start = AssPoint(
                        move.start.x.coerceIn(0.0, document.playResX.toDouble()),
                        (move.start.y + spec.offsetY).coerceIn(0.0, document.playResY.toDouble()),
                    ),
                    end = AssPoint(
                        move.end.x.coerceIn(0.0, document.playResX.toDouble()),
                        (move.end.y + spec.offsetY).coerceIn(0.0, document.playResY.toDouble()),
                    ),
                    startMs = move.startMs,
                    endMs = move.endMs,
                )
            }
            AssPositionMode.INHERITED -> {
                val anchor = inheritedAnchor(document, source, style)
                AssGeometrySemantic.patchPosition(
                    reflectedText,
                    anchor.x,
                    (anchor.y + spec.offsetY).coerceIn(0.0, document.playResY.toDouble()),
                )
            }
            AssPositionMode.CONFLICT -> error("unreachable")
        }

        geometry.origin?.let { origin ->
            reflectedText = AssGeometrySemantic.patchOrigin(
                reflectedText,
                origin.x.coerceIn(0.0, document.playResX.toDouble()),
                (origin.y + spec.offsetY).coerceIn(0.0, document.playResY.toDouble()),
            )
        }

        reflectedText = appendLeadingOverride(
            reflectedText,
            buildString {
                append("\\frx").append(format(sourceRotationX + 180.0))
                append("\\fscy").append(format(reflectionScaleY))
                append("\\alpha&H").append("%02X".format(alpha)).append("&")
                if (spec.blur > 0.0) append("\\blur").append(format(spec.blur))
            },
        )

        val newId = (document.events.maxOfOrNull { it.id } ?: 0L) + 1L
        val reflectionEvent = source.copy(
            id = newId,
            layer = (source.layer - 1).coerceAtLeast(0),
            text = reflectedText,
        )
        val events = document.events.toMutableList().apply {
            // Put a layer-0 companion before the source as an additional back-to-front safeguard.
            add(sourceIndex, reflectionEvent)
        }

        return AssFxCompositionResult(
            document = document.copy(events = events),
            sourceEventId = eventId,
            generatedEventIds = listOf(newId),
        )
    }

    fun applyFlipEntrance(
        document: AssDocument,
        eventId: Long,
        spec: AssFlipEntranceSpec = AssFlipEntranceSpec(),
    ): AssDocument {
        require(spec.durationMs >= 2L) { "翻转入场至少需要 2 ms。" }
        require(spec.startScalePercent.isFinite() && spec.startScalePercent > 0.0) {
            "起始高度比例必须大于 0。"
        }
        require(spec.overshootScalePercent.isFinite() && spec.overshootScalePercent > 0.0) {
            "回弹高度比例必须大于 0。"
        }
        require(spec.startRotationXDegrees.isFinite()) { "起始 X 旋转必须是有限数字。" }
        if (spec.accel != null) require(spec.accel.isFinite() && spec.accel > 0.0) {
            "Accel 必须大于 0。"
        }

        val source = document.events.firstOrNull { it.id == eventId } ?: error("源字幕不存在。")
        require(!source.comment) { "Comment 事件不能添加可见入场动画。" }
        requireCompositionOwnershipCompatible(
            source.text,
            ownedTags = setOf("fscy", "frx", "t"),
            effectLabel = "翻转入场",
        )
        val eventDuration = source.end.millis - source.start.millis
        require(eventDuration >= 2L) { "字幕持续时间过短，无法生成入场动画。" }
        val duration = spec.durationMs.coerceAtMost(eventDuration)
        val middle = (duration * 2L / 3L).coerceIn(1L, duration - 1L)

        val geometry = AssGeometrySemantic.inspect(source.text)
        require(!geometry.malformedLeadingBlock) { "源字幕的前导 override block 不完整，不能安全生成 FX。" }
        val style = document.styles.firstOrNull { it.name.equals(source.style, ignoreCase = true) }
        val baseScaleY = geometry.scaleY ?: style?.scaleY ?: 100.0
        val baseRotationX = geometry.rotationX ?: 0.0

        var text = AssAnimationAuthoring.applyNumericTrack(
            source.text,
            AssTransformVisualProperty.SCALE_Y,
            listOf(
                AssAnimationKeyframe(0L, baseScaleY * spec.startScalePercent / 100.0),
                AssAnimationKeyframe(middle, baseScaleY * spec.overshootScalePercent / 100.0),
                AssAnimationKeyframe(duration, baseScaleY),
            ),
            spec.accel,
        )
        text = AssAnimationAuthoring.applyNumericTrack(
            text,
            AssTransformVisualProperty.ROTATION_X,
            listOf(
                AssAnimationKeyframe(0L, baseRotationX + spec.startRotationXDegrees),
                AssAnimationKeyframe(duration, baseRotationX),
            ),
            spec.accel,
        )

        return document.copy(events = document.events.map { event ->
            if (event.id == eventId) event.copy(text = text) else event
        })
    }

    /**
     * Generated composition layers establish their own alpha/blur/geometry base state. Inline
     * span tags that re-own the same properties after karaoke timing begins would override that
     * generated base only for later syllables, producing a visually split stack.
     *
     * Leading Event-level overrides remain valid. The guard only owns the region beginning with
     * the first karaoke marker, which is where span-local karaoke authoring starts.
     */
    private fun requireCompositionOwnershipCompatible(
        text: String,
        ownedTags: Set<String>,
        effectLabel: String,
    ) {
        val analysis = AssInlineSyntax.analyze(text)
        require(!analysis.hasErrors) { "$effectLabel 无法安全处理包含损坏 override block 的字幕。" }

        val firstKaraoke = analysis.tags.firstOrNull { tag ->
            tag.name.equals("k", true) ||
                tag.name.equals("K", false) ||
                tag.name.equals("kf", true) ||
                tag.name.equals("ko", true) ||
                tag.name.equals("kt", true)
        } ?: return

        val conflicting = analysis.tags.firstOrNull { tag ->
            tag.start >= firstKaraoke.start &&
                ownedTags.any { it.equals(tag.name, ignoreCase = true) }
        } ?: return

        error(
            "$effectLabel 与 Karaoke 区域中的 \\${conflicting.name} 存在属性所有权冲突；" +
                "当前不会猜测覆盖顺序。请先移除该音节级效果，或等待 Layer-aware Karaoke FX 合成。"
        )
    }

    private fun inheritedAnchor(document: AssDocument, event: AssEvent, style: AssStyle?): AssPoint {
        val alignment = leadingAlignment(event.text) ?: style?.alignment ?: 2
        val marginL = if (event.marginL > 0) event.marginL else style?.marginL ?: 10
        val marginR = if (event.marginR > 0) event.marginR else style?.marginR ?: 10
        val marginV = if (event.marginV > 0) event.marginV else style?.marginV ?: 10
        val x = when (alignment) {
            1, 4, 7 -> marginL.toDouble()
            3, 6, 9 -> (document.playResX - marginR).toDouble()
            else -> document.playResX / 2.0
        }
        val y = when (alignment) {
            7, 8, 9 -> marginV.toDouble()
            4, 5, 6 -> document.playResY / 2.0
            else -> (document.playResY - marginV).toDouble()
        }
        return AssPoint(
            x.coerceIn(0.0, document.playResX.toDouble()),
            y.coerceIn(0.0, document.playResY.toDouble()),
        )
    }

    private fun leadingAlignment(text: String): Int? {
        val prefix = leadingOverridePrefix(text)
        return Regex("""\\an([1-9])""", RegexOption.IGNORE_CASE)
            .findAll(prefix)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun appendLeadingOverride(text: String, tags: String): String {
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) break
            val block = text.substring(cursor, close + 1)
            if ('\\' !in block) break
            cursor = close + 1
        }
        return text.substring(0, cursor) + "{$tags}" + text.substring(cursor)
    }

    private fun leadingOverridePrefix(text: String): String {
        var cursor = 0
        val out = StringBuilder()
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) break
            val block = text.substring(cursor, close + 1)
            if ('\\' !in block) break
            out.append(block)
            cursor = close + 1
        }
        return out.toString()
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}

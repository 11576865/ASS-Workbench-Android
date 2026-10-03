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

data class AssFxCompositionResult(
    val document: AssDocument,
    val sourceEventId: Long,
    val generatedEventId: Long,
)

object AssFxComposition {
    fun composeReflection(
        document: AssDocument,
        eventId: Long,
        reflection: AssReflectionFxSpec = AssReflectionFxSpec(),
        entrance: AssFlipEntranceSpec? = null,
    ): AssFxCompositionResult {
        val created = createReflection(document, eventId, reflection)
        val composed = entrance?.let {
            applyFlipEntrance(created.document, eventId, it)
        } ?: created.document
        return created.copy(document = composed)
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
            generatedEventId = newId,
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

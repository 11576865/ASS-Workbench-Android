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

enum class AssReflectionFadeDirection { AUTO, DOWN, UP }

data class AssReflectionFadeSpec(
    val bands: Int = 6,
    val depthPx: Double = 120.0,
    val farOpacityPercent: Double = 0.0,
    val direction: AssReflectionFadeDirection = AssReflectionFadeDirection.AUTO,
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
        fade: AssReflectionFadeSpec? = null,
        entrance: AssFlipEntranceSpec? = null,
    ): AssFxCompositionResult =
        composeMirrorStack(
            document = document,
            eventId = eventId,
            reflection = reflection,
            glow = null,
            fade = fade,
            entrance = entrance,
        )

    fun composeMirrorStackBatch(
        document: AssDocument,
        eventIds: Set<Long>,
        reflection: AssReflectionFxSpec = AssReflectionFxSpec(),
        glow: AssGlowFxSpec? = AssGlowFxSpec(),
        fade: AssReflectionFadeSpec? = null,
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
                fade = fade,
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
        fade: AssReflectionFadeSpec? = null,
        entrance: AssFlipEntranceSpec? = null,
    ): AssFxCompositionResult {
        require(fade == null || entrance == null) {
            "空间渐隐使用固定屏幕 Clip 分带，暂不能与翻转/拉伸入场同时启用；静态 Clip 无法可靠跟随旋转与缩放。"
        }

        var next = document
        val generated = mutableListOf<Long>()

        if (glow != null) {
            val glowResult = createGlow(next, eventId, glow)
            next = glowResult.document
            generated += glowResult.generatedEventIds
        }

        val reflectionResult = createReflection(next, eventId, reflection, fade)
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
        fade: AssReflectionFadeSpec? = null,
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

        fade?.let { value ->
            require(value.bands in 2..16) { "空间渐隐分段必须在 2..16 之间。" }
            require(value.depthPx.isFinite() && value.depthPx > 0.0) {
                "空间渐隐深度必须大于 0。"
            }
            require(value.farOpacityPercent.isFinite() && value.farOpacityPercent in 0.0..100.0) {
                "空间渐隐末端不透明度必须在 0..100% 之间。"
            }
            require(value.farOpacityPercent <= spec.opacityPercent) {
                "空间渐隐末端不透明度不能高于倒影起始不透明度。"
            }
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

        if (fade != null) {
            require(geometry.positionMode != AssPositionMode.MOVE) {
                "带空间渐隐的倒影暂不支持 \\move；固定屏幕 Clip 无法可靠跟随运动路径。"
            }
            require(!geometry.clipNonRectangular && !geometry.clipInverted) {
                "带空间渐隐的倒影暂不支持矢量 Clip / iClip；无法安全与渐隐分带合成。"
            }
            require(!containsSpatialFadeAlphaControl(source.text)) {
                "源字幕已有 alpha / fad / fade 控制；当前空间渐隐不能安全合成这些透明度语义。"
            }
            val allowedLeadingRectClipCount = if (geometry.clipRect != null) 1 else 0
            require(countOverrideClipTags(source.text) <= allowedLeadingRectClipCount) {
                "源字幕包含额外或行内 Clip；空间渐隐只支持一个前导矩形 Clip。"
            }
        }
        require(!geometry.clipNonRectangular && !geometry.clipInverted) {
            "倒影暂不自动平移矢量 Clip / iClip；请先转换为普通矩形 Clip 或移除裁剪。"
        }

        val style = document.styles.firstOrNull { it.name.equals(source.style, ignoreCase = true) }
        val sourceScaleY = geometry.scaleY ?: style?.scaleY ?: 100.0
        val reflectionScaleY = (sourceScaleY * spec.verticalScalePercent / 100.0).coerceAtLeast(0.001)
        val sourceRotationX = geometry.rotationX ?: 0.0
        val alpha = ((1.0 - spec.opacityPercent / 100.0) * 255.0).roundToInt().coerceIn(0, 255)
        val alignment = leadingAlignment(source.text) ?: style?.alignment ?: 2
        val inherited = if (geometry.positionMode == AssPositionMode.INHERITED) {
            inheritedAnchor(document, source, style)
        } else null
        val reflectionAnchorY = when (geometry.positionMode) {
            AssPositionMode.POSITION ->
                (requireNotNull(geometry.position).y + spec.offsetY)
                    .coerceIn(0.0, document.playResY.toDouble())
            AssPositionMode.INHERITED ->
                (requireNotNull(inherited).y + spec.offsetY)
                    .coerceIn(0.0, document.playResY.toDouble())
            AssPositionMode.MOVE, AssPositionMode.CONFLICT -> null
        }

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
                val anchor = requireNotNull(inherited)
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

        geometry.clipRect?.let { clip ->
            reflectedText = AssGeometrySemantic.patchRectClip(
                reflectedText,
                AssClipRect(
                    left = clip.left,
                    top = (clip.top + spec.offsetY).coerceIn(0.0, document.playResY.toDouble()),
                    right = clip.right,
                    bottom = (clip.bottom + spec.offsetY).coerceIn(0.0, document.playResY.toDouble()),
                ),
                inverted = false,
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

        val reflectionLayer = (source.layer - 1).coerceAtLeast(0)
        if (fade == null) {
            val newId = (document.events.maxOfOrNull { it.id } ?: 0L) + 1L
            val reflectionEvent = source.copy(
                id = newId,
                layer = reflectionLayer,
                text = reflectedText,
            )
            val events = document.events.toMutableList().apply {
                add(sourceIndex, reflectionEvent)
            }
            return AssFxCompositionResult(
                document = document.copy(events = events),
                sourceEventId = eventId,
                generatedEventIds = listOf(newId),
            )
        }

        val anchorY = requireNotNull(reflectionAnchorY)
        val direction = when (fade.direction) {
            AssReflectionFadeDirection.DOWN -> AssReflectionFadeDirection.DOWN
            AssReflectionFadeDirection.UP -> AssReflectionFadeDirection.UP
            AssReflectionFadeDirection.AUTO ->
                if (alignment in 7..9) AssReflectionFadeDirection.UP else AssReflectionFadeDirection.DOWN
        }
        val maxDepth = when (direction) {
            AssReflectionFadeDirection.DOWN -> document.playResY.toDouble() - anchorY
            AssReflectionFadeDirection.UP -> anchorY
            AssReflectionFadeDirection.AUTO -> error("unreachable")
        }
        require(maxDepth > 0.0) {
            "倒影锚点已位于画面边界，空间渐隐在所选方向没有可用区域。"
        }
        require(fade.depthPx <= maxDepth) {
            "空间渐隐深度 ${format(fade.depthPx)} px 超出所选方向可用的 ${format(maxDepth)} px；请缩短深度或改变方向。"
        }
        val depth = fade.depthPx

        val shiftedSourceClip = geometry.clipRect?.let { clip ->
            AssClipRect(
                left = clip.left.coerceIn(0.0, document.playResX.toDouble()),
                top = (clip.top + spec.offsetY).coerceIn(0.0, document.playResY.toDouble()),
                right = clip.right.coerceIn(0.0, document.playResX.toDouble()),
                bottom = (clip.bottom + spec.offsetY).coerceIn(0.0, document.playResY.toDouble()),
            ).normalized()
        }

        var nextId = (document.events.maxOfOrNull { it.id } ?: 0L) + 1L
        val bandEvents = buildList {
            repeat(fade.bands) { index ->
                val start = depth * index / fade.bands.toDouble()
                val end = depth * (index + 1) / fade.bands.toDouble()
                val rawBand = when (direction) {
                    AssReflectionFadeDirection.DOWN -> AssClipRect(
                        0.0, anchorY + start, document.playResX.toDouble(), anchorY + end
                    )
                    AssReflectionFadeDirection.UP -> AssClipRect(
                        0.0, anchorY - end, document.playResX.toDouble(), anchorY - start
                    )
                    AssReflectionFadeDirection.AUTO -> error("unreachable")
                }.normalized()
                val band = if (shiftedSourceClip != null) {
                    intersect(shiftedSourceClip, rawBand)
                } else {
                    rawBand
                }
                if (band == null || band.right <= band.left || band.bottom <= band.top) return@repeat

                val sample = (index + 0.5) / fade.bands.toDouble()
                val opacity = spec.opacityPercent +
                    (fade.farOpacityPercent - spec.opacityPercent) * sample
                val bandAlpha = ((1.0 - opacity / 100.0) * 255.0)
                    .roundToInt()
                    .coerceIn(0, 255)
                var bandText = AssGeometrySemantic.patchRectClip(reflectedText, band, inverted = false)
                bandText = appendLeadingOverride(
                    bandText,
                    "\\alpha&H" + "%02X".format(bandAlpha) + "&",
                )
                add(
                    source.copy(
                        id = nextId++,
                        layer = reflectionLayer,
                        text = bandText,
                    )
                )
            }
        }
        require(bandEvents.isNotEmpty()) {
            "空间渐隐与现有矩形 Clip 没有可见交集。"
        }

        val events = document.events.toMutableList().apply {
            addAll(sourceIndex, bandEvents)
        }
        return AssFxCompositionResult(
            document = document.copy(events = events),
            sourceEventId = eventId,
            generatedEventIds = bandEvents.map { it.id },
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

    private fun overrideBlocks(text: String): Sequence<String> =
        Regex("""\{[^}]*\}""").findAll(text).map { it.value }

    private fun containsSpatialFadeAlphaControl(text: String): Boolean {
        val alpha = Regex("""\\(?:alpha|[1-4]a)(?=[^A-Za-z]|$)""", RegexOption.IGNORE_CASE)
        val fade = Regex("""\\(?:fad|fade)\s*\(""", RegexOption.IGNORE_CASE)
        return overrideBlocks(text).any { block ->
            alpha.containsMatchIn(block) || fade.containsMatchIn(block)
        }
    }

    private fun countOverrideClipTags(text: String): Int {
        val clip = Regex("""\\(?:clip|iclip)\s*\(""", RegexOption.IGNORE_CASE)
        return overrideBlocks(text).sumOf { block -> clip.findAll(block).count() }
    }

    private fun intersect(a: AssClipRect, b: AssClipRect): AssClipRect? {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        return if (right > left && bottom > top) AssClipRect(left, top, right, bottom) else null
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}

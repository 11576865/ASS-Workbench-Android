package io.github.assworkbench.domain

import java.nio.charset.StandardCharsets
import java.util.Base64

data class AssFxTemplate(
    val name: String,
    val reflection: AssReflectionFxSpec = AssReflectionFxSpec(),
    val glow: AssGlowFxSpec? = AssGlowFxSpec(),
    val fade: AssReflectionFadeSpec? = null,
    val entrance: AssFlipEntranceSpec? = AssFlipEntranceSpec(),
)

object AssFxTemplateCodec {
    private const val HEADER_V1 = "ASSWB_FX_TEMPLATE_V1"
    private const val HEADER_V2 = "ASSWB_FX_TEMPLATE_V2"

    fun encode(template: AssFxTemplate): String {
        validate(template)
        return buildString {
            appendLine(HEADER_V2)
            append("name64=").appendLine(
                Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(template.name.toByteArray(StandardCharsets.UTF_8))
            )
            append("reflection.offsetY=").appendLine(format(template.reflection.offsetY))
            append("reflection.verticalScalePercent=").appendLine(format(template.reflection.verticalScalePercent))
            append("reflection.opacityPercent=").appendLine(format(template.reflection.opacityPercent))
            append("reflection.blur=").appendLine(format(template.reflection.blur))

            append("glow.enabled=").appendLine(template.glow != null)
            template.glow?.let { glow ->
                append("glow.opacityPercent=").appendLine(format(glow.opacityPercent))
                append("glow.blur=").appendLine(format(glow.blur))
                append("glow.border=").appendLine(format(glow.border))
            }

            append("fade.enabled=").appendLine(template.fade != null)
            template.fade?.let { fade ->
                append("fade.bands=").appendLine(fade.bands)
                append("fade.depthPx=").appendLine(format(fade.depthPx))
                append("fade.farOpacityPercent=").appendLine(format(fade.farOpacityPercent))
                append("fade.direction=").appendLine(fade.direction.name)
            }

            append("entrance.enabled=").appendLine(template.entrance != null)
            template.entrance?.let { entrance ->
                append("entrance.durationMs=").appendLine(entrance.durationMs)
                append("entrance.startScalePercent=").appendLine(format(entrance.startScalePercent))
                append("entrance.overshootScalePercent=").appendLine(format(entrance.overshootScalePercent))
                append("entrance.startRotationXDegrees=").appendLine(format(entrance.startRotationXDegrees))
                append("entrance.accel=").appendLine(entrance.accel?.let(::format) ?: "none")
            }
        }
    }

    fun decode(text: String): AssFxTemplate {
        val lines = text.lineSequence().map(String::trim).filter { it.isNotEmpty() }.toList()
        val header = lines.firstOrNull()
        require(header == HEADER_V1 || header == HEADER_V2) { "不是受支持的 FX 模板格式。" }
        val values = linkedMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val split = line.indexOf('=')
            if (split <= 0) return@forEach
            values[line.substring(0, split).trim()] = line.substring(split + 1).trim()
        }

        val nameEncoded = values.required("name64")
        val name = runCatching {
            String(Base64.getUrlDecoder().decode(nameEncoded), StandardCharsets.UTF_8)
        }.getOrElse { error("FX 模板名称编码损坏。") }

        val reflection = AssReflectionFxSpec(
            offsetY = values.requiredDouble("reflection.offsetY"),
            verticalScalePercent = values.requiredDouble("reflection.verticalScalePercent"),
            opacityPercent = values.requiredDouble("reflection.opacityPercent"),
            blur = values.requiredDouble("reflection.blur"),
        )

        val glow = if (values.requiredBoolean("glow.enabled")) {
            AssGlowFxSpec(
                opacityPercent = values.requiredDouble("glow.opacityPercent"),
                blur = values.requiredDouble("glow.blur"),
                border = values.requiredDouble("glow.border"),
            )
        } else null

        val fade = if (header == HEADER_V2 && values.requiredBoolean("fade.enabled")) {
            AssReflectionFadeSpec(
                bands = values.required("fade.bands").toIntOrNull()
                    ?: error("fade.bands 不是整数。"),
                depthPx = values.requiredDouble("fade.depthPx"),
                farOpacityPercent = values.requiredDouble("fade.farOpacityPercent"),
                direction = runCatching {
                    AssReflectionFadeDirection.valueOf(values.required("fade.direction"))
                }.getOrElse { error("fade.direction 不受支持。") },
            )
        } else null

        val entrance = if (values.requiredBoolean("entrance.enabled")) {
            AssFlipEntranceSpec(
                durationMs = values.required("entrance.durationMs").toLongOrNull()
                    ?: error("entrance.durationMs 不是整数。"),
                startScalePercent = values.requiredDouble("entrance.startScalePercent"),
                overshootScalePercent = values.requiredDouble("entrance.overshootScalePercent"),
                startRotationXDegrees = values.requiredDouble("entrance.startRotationXDegrees"),
                accel = values.required("entrance.accel").let { raw ->
                    if (raw == "none") null else raw.toDoubleOrNull() ?: error("entrance.accel 不是数字。")
                },
            )
        } else null

        return AssFxTemplate(
            name = name,
            reflection = reflection,
            glow = glow,
            fade = fade,
            entrance = entrance,
        ).also(::validate)
    }

    fun validate(template: AssFxTemplate) {
        require(template.name.isNotBlank()) { "FX 模板名称不能为空。" }
        require(template.name.length <= 80) { "FX 模板名称不能超过 80 个字符。" }

        val reflection = template.reflection
        require(reflection.offsetY.isFinite()) { "倒影 Y 偏移必须是有限数字。" }
        require(reflection.verticalScalePercent.isFinite() && reflection.verticalScalePercent > 0.0) {
            "倒影高度比例必须大于 0。"
        }
        require(reflection.opacityPercent.isFinite() && reflection.opacityPercent in 0.0..100.0) {
            "倒影不透明度必须在 0..100% 之间。"
        }
        require(reflection.blur.isFinite() && reflection.blur in 0.0..20.0) {
            "倒影 Blur 必须在 0..20 之间。"
        }

        template.glow?.let { glow ->
            require(glow.opacityPercent.isFinite() && glow.opacityPercent in 0.0..100.0) {
                "柔光不透明度必须在 0..100% 之间。"
            }
            require(glow.blur.isFinite() && glow.blur in 0.0..20.0) {
                "柔光 Blur 必须在 0..20 之间。"
            }
            require(glow.border.isFinite() && glow.border in 0.0..20.0) {
                "柔光 Border 必须在 0..20 之间。"
            }
        }

        template.fade?.let { fade ->
            require(fade.bands in 2..16) { "空间渐隐分段必须在 2..16 之间。" }
            require(fade.depthPx.isFinite() && fade.depthPx > 0.0) {
                "空间渐隐深度必须大于 0。"
            }
            require(fade.farOpacityPercent.isFinite() && fade.farOpacityPercent in 0.0..100.0) {
                "空间渐隐末端不透明度必须在 0..100% 之间。"
            }
            require(fade.farOpacityPercent <= reflection.opacityPercent) {
                "空间渐隐末端不透明度不能高于倒影起始不透明度。"
            }
        }

        template.entrance?.let { entrance ->
            require(entrance.durationMs >= 2L) { "翻转入场至少需要 2 ms。" }
            require(entrance.startScalePercent.isFinite() && entrance.startScalePercent > 0.0) {
                "起始高度比例必须大于 0。"
            }
            require(entrance.overshootScalePercent.isFinite() && entrance.overshootScalePercent > 0.0) {
                "回弹高度比例必须大于 0。"
            }
            require(entrance.startRotationXDegrees.isFinite()) { "起始 X 旋转必须是有限数字。" }
            if (entrance.accel != null) {
                require(entrance.accel.isFinite() && entrance.accel > 0.0) { "Accel 必须大于 0。" }
            }
        }
    }

    private fun Map<String, String>.required(key: String): String =
        get(key) ?: error("FX 模板缺少字段：$key")

    private fun Map<String, String>.requiredDouble(key: String): Double =
        required(key).toDoubleOrNull() ?: error("$key 不是数字。")

    private fun Map<String, String>.requiredBoolean(key: String): Boolean =
        when (val raw = required(key)) {
            "true" -> true
            "false" -> false
            else -> error("$key 不是布尔值：$raw")
        }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}

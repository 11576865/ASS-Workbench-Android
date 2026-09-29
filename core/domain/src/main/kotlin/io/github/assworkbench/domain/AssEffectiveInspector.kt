package io.github.assworkbench.domain

data class AssEffectiveValue(
    val name: String,
    val styleValue: String,
    val eventValue: String? = null,
    val overrideValue: String? = null,
    val effectiveValue: String,
)

object AssEffectiveInspector {
    fun inspect(document: AssDocument, event: AssEvent): List<AssEffectiveValue> {
        val style = document.styles.firstOrNull { it.name == event.style } ?: AssStyle(name = event.style)
        val analysis = AssInlineSyntax.analyze(event.text)
        fun lastTag(vararg names: String): String? =
            analysis.tags.lastOrNull { tag -> names.any { it.equals(tag.name, ignoreCase = true) } }?.value

        val fs = lastTag("fs")?.toDoubleOrNull()
        val fn = lastTag("fn")?.ifBlank { null }
        val bord = lastTag("bord")?.toDoubleOrNull()
        val shad = lastTag("shad")?.toDoubleOrNull()
        val an = lastTag("an")?.toIntOrNull()
        val bold = parseAssBool(lastTag("b"))
        val italic = parseAssBool(lastTag("i"))
        val underline = parseAssBool(lastTag("u"))
        val strike = parseAssBool(lastTag("s"))
        val spacing = lastTag("fsp")?.toDoubleOrNull()
        val geometry = AssGeometrySemantic.inspect(event.text)
        val scaleX = geometry.scaleX
        val scaleY = geometry.scaleY
        val shearX = geometry.shearX
        val shearY = geometry.shearY
        val angle = geometry.rotationZ
        val primaryColor = lastTag("1c", "c")
        val outlineColor = lastTag("3c")

        return listOf(
            AssEffectiveValue("Font", style.fontName, overrideValue = fn, effectiveValue = fn ?: style.fontName),
            AssEffectiveValue(
                "Size",
                format(style.fontSize),
                overrideValue = fs?.let(::format),
                effectiveValue = format(fs ?: style.fontSize),
            ),
            AssEffectiveValue(
                "Alignment",
                style.alignment.toString(),
                overrideValue = an?.toString(),
                effectiveValue = (an ?: style.alignment).toString(),
            ),
            AssEffectiveValue(
                "Margin L",
                style.marginL.toString(),
                eventValue = event.marginL.takeIf { it > 0 }?.toString(),
                effectiveValue = (event.marginL.takeIf { it > 0 } ?: style.marginL).toString(),
            ),
            AssEffectiveValue(
                "Margin R",
                style.marginR.toString(),
                eventValue = event.marginR.takeIf { it > 0 }?.toString(),
                effectiveValue = (event.marginR.takeIf { it > 0 } ?: style.marginR).toString(),
            ),
            AssEffectiveValue(
                "Margin V",
                style.marginV.toString(),
                eventValue = event.marginV.takeIf { it > 0 }?.toString(),
                effectiveValue = (event.marginV.takeIf { it > 0 } ?: style.marginV).toString(),
            ),
            AssEffectiveValue(
                "Border",
                format(style.outline),
                overrideValue = bord?.let(::format),
                effectiveValue = format(bord ?: style.outline),
            ),
            AssEffectiveValue(
                "Shadow",
                format(style.shadow),
                overrideValue = shad?.let(::format),
                effectiveValue = format(shad ?: style.shadow),
            ),
            AssEffectiveValue(
                "Bold",
                style.bold.toString(),
                overrideValue = bold?.toString(),
                effectiveValue = (bold ?: style.bold).toString(),
            ),
            AssEffectiveValue(
                "Italic",
                style.italic.toString(),
                overrideValue = italic?.toString(),
                effectiveValue = (italic ?: style.italic).toString(),
            ),
            AssEffectiveValue(
                "Underline",
                style.underline.toString(),
                overrideValue = underline?.toString(),
                effectiveValue = (underline ?: style.underline).toString(),
            ),
            AssEffectiveValue(
                "Strike",
                style.strikeOut.toString(),
                overrideValue = strike?.toString(),
                effectiveValue = (strike ?: style.strikeOut).toString(),
            ),
            AssEffectiveValue(
                "Spacing",
                format(style.spacing),
                overrideValue = spacing?.let(::format),
                effectiveValue = format(spacing ?: style.spacing),
            ),
            AssEffectiveValue(
                "Scale X",
                format(style.scaleX),
                overrideValue = scaleX?.let(::format),
                effectiveValue = format(scaleX ?: style.scaleX),
            ),
            AssEffectiveValue(
                "Scale Y",
                format(style.scaleY),
                overrideValue = scaleY?.let(::format),
                effectiveValue = format(scaleY ?: style.scaleY),
            ),
            AssEffectiveValue(
                "Angle",
                format(style.angle),
                overrideValue = angle?.let(::format),
                effectiveValue = format(angle ?: style.angle),
            ),
            AssEffectiveValue(
                "Shear X",
                "0",
                overrideValue = shearX?.let(::format),
                effectiveValue = format(shearX ?: 0.0),
            ),
            AssEffectiveValue(
                "Shear Y",
                "0",
                overrideValue = shearY?.let(::format),
                effectiveValue = format(shearY ?: 0.0),
            ),
            AssEffectiveValue(
                "Primary",
                style.primaryColor,
                overrideValue = primaryColor,
                effectiveValue = primaryColor ?: style.primaryColor,
            ),
            AssEffectiveValue(
                "Outline Color",
                style.outlineColor,
                overrideValue = outlineColor,
                effectiveValue = outlineColor ?: style.outlineColor,
            ),
            AssEffectiveValue(
                "Position",
                "alignment anchor",
                overrideValue = when (geometry.positionMode) {
                    AssPositionMode.POSITION -> geometry.position?.let { "${format(it.x)}, ${format(it.y)}" }
                    AssPositionMode.MOVE -> geometry.move?.let {
                        "move ${format(it.start.x)},${format(it.start.y)} → ${format(it.end.x)},${format(it.end.y)}"
                    }
                    AssPositionMode.CONFLICT -> "conflict: pos + move"
                    AssPositionMode.INHERITED -> null
                },
                effectiveValue = when (geometry.positionMode) {
                    AssPositionMode.POSITION -> geometry.position?.let { "${format(it.x)}, ${format(it.y)}" } ?: "alignment anchor"
                    AssPositionMode.MOVE -> geometry.move?.let {
                        "move ${format(it.start.x)},${format(it.start.y)} → ${format(it.end.x)},${format(it.end.y)}"
                    } ?: "alignment anchor"
                    AssPositionMode.CONFLICT -> "conflict: pos + move"
                    AssPositionMode.INHERITED -> "alignment anchor"
                },
            ),
        )
    }

    private fun parseAssBool(value: String?): Boolean? {
        val number = value?.toIntOrNull() ?: return null
        return number != 0
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}

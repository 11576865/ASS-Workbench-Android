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
        val pos = EventOverrideEditor.inspect(event.text)

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
                "Position",
                "alignment anchor",
                overrideValue = if (pos.x != null && pos.y != null) "${format(pos.x)}, ${format(pos.y)}" else null,
                effectiveValue = if (pos.x != null && pos.y != null) "${format(pos.x)}, ${format(pos.y)}" else "alignment anchor",
            ),
        )
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}

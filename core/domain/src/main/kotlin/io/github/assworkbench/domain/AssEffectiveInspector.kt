package io.github.assworkbench.domain

data class AssEffectiveValue(
    val name: String,
    val styleValue: String,
    val eventValue: String? = null,
    val overrideValue: String? = null,
    val effectiveValue: String,
    /**
     * Style that provides the baseline for the first rendered span.
     *
     * This can differ from the Event Style after a leading \rStyle reset.
     */
    val effectiveStyle: String? = null,
    /**
     * True when later spans or a transform can change this property after the initial span.
     *
     * effectiveValue describes the initial rendered span instead of pretending that a
     * mixed-format Event has one scalar value for its entire text.
     */
    val spanDependent: Boolean = false,
)

object AssEffectiveInspector {
    private val animatedStyleProperties = setOf(
        "Size", "Border", "Shadow", "Spacing", "Scale X", "Scale Y",
        "Angle", "Shear X", "Shear Y", "Primary", "Outline Color",
    )

    fun inspect(document: AssDocument, event: AssEvent): List<AssEffectiveValue> {
        val eventStyle = document.styles.firstOrNull { it.name == event.style }
            ?: error("Event 引用不存在的 Style：\${event.style}")
        val analysis = AssInlineSyntax.analyze(event.text)
        require(!analysis.hasErrors) { "Event 包含损坏的 ASS override block，无法可靠解析有效值。" }

        val leadingEnd = leadingOverridePrefixLength(event.text)
        val leadingTags = analysis.tags.filter { it.start < leadingEnd }
        val laterTags = analysis.tags.filter { it.start >= leadingEnd }

        var activeStyle = eventStyle
        val values = styleValues(activeStyle).toMutableMap()
        val directOverrides = mutableMapOf<String, String>()

        fun resetTo(style: AssStyle) {
            activeStyle = style
            values.clear()
            values.putAll(styleValues(style))
            directOverrides.clear()
        }

        leadingTags.forEach { tag ->
            when (tag.name.lowercase()) {
                "r" -> {
                    val requested = tag.value.trim()
                    val target = if (requested.isEmpty()) {
                        eventStyle
                    } else {
                        document.styles.firstOrNull { it.name == requested }
                            ?: error("前导 \\r 引用不存在的 Style：\$requested")
                    }
                    resetTo(target)
                }
                "fn" -> {
                    val value = tag.value.ifBlank { activeStyle.fontName }
                    values["Font"] = value
                    directOverrides["Font"] = value
                }
                "fs" -> setFiniteDouble(values, directOverrides, "Size", tag.value, "\\fs")
                "bord" -> setFiniteDouble(values, directOverrides, "Border", tag.value, "\\bord")
                "shad" -> setFiniteDouble(values, directOverrides, "Shadow", tag.value, "\\shad")
                "b" -> setBoolean(values, directOverrides, "Bold", tag.value, "\\b")
                "i" -> setBoolean(values, directOverrides, "Italic", tag.value, "\\i")
                "u" -> setBoolean(values, directOverrides, "Underline", tag.value, "\\u")
                "s" -> setBoolean(values, directOverrides, "Strike", tag.value, "\\s")
                "fsp" -> setFiniteDouble(values, directOverrides, "Spacing", tag.value, "\\fsp")
                "fscx" -> setFiniteDouble(values, directOverrides, "Scale X", tag.value, "\\fscx")
                "fscy" -> setFiniteDouble(values, directOverrides, "Scale Y", tag.value, "\\fscy")
                "fr", "frz" -> setFiniteDouble(values, directOverrides, "Angle", tag.value, "\\frz")
                "fax" -> setFiniteDouble(values, directOverrides, "Shear X", tag.value, "\\fax")
                "fay" -> setFiniteDouble(values, directOverrides, "Shear Y", tag.value, "\\fay")
                "c", "1c" -> {
                    require(tag.value.isNotBlank()) { "\\1c 颜色值不能为空。" }
                    values["Primary"] = tag.value
                    directOverrides["Primary"] = tag.value
                }
                "3c" -> {
                    require(tag.value.isNotBlank()) { "\\3c 颜色值不能为空。" }
                    values["Outline Color"] = tag.value
                    directOverrides["Outline Color"] = tag.value
                }
            }
        }

        val laterReset = laterTags.any { it.name.equals("r", ignoreCase = true) }
        val leadingTransform = leadingTags.any { it.name.equals("t", ignoreCase = true) }
        fun changesLater(property: String, vararg tagNames: String): Boolean =
            laterReset ||
                laterTags.any { tag -> tagNames.any { it.equals(tag.name, ignoreCase = true) } } ||
                (leadingTransform && property in animatedStyleProperties)

        val leadingAlignmentTags = leadingTags.filter { it.name.equals("an", ignoreCase = true) }
        val laterAlignment = laterTags.any { it.name.equals("an", ignoreCase = true) }
        val alignmentOverride = leadingAlignmentTags.lastOrNull()?.value?.toIntOrNull()
        if (leadingAlignmentTags.isNotEmpty()) {
            require(alignmentOverride in 1..9) { "\\an 必须在 1..9 之间。" }
        }

        val geometry = AssGeometrySemantic.inspect(event.text)
        require(!geometry.malformedLeadingBlock) {
            "Event 的前导 override block 不完整，无法可靠解析几何有效值。"
        }
        val clipSummary = when {
            geometry.clipRect != null -> {
                val rect = geometry.clipRect
                val name = if (geometry.clipInverted) "iclip" else "clip"
                "\$name \${format(rect.left)},\${format(rect.top)} → \${format(rect.right)},\${format(rect.bottom)}"
            }
            geometry.clipNonRectangular -> if (geometry.clipInverted) "iclip vector/raw" else "clip vector/raw"
            else -> null
        }

        fun styleValue(
            name: String,
            base: String,
            vararg tags: String,
        ) = AssEffectiveValue(
            name = name,
            styleValue = base,
            overrideValue = directOverrides[name],
            effectiveValue = values.getValue(name),
            effectiveStyle = activeStyle.name,
            spanDependent = changesLater(name, *tags),
        )

        return listOf(
            styleValue("Font", eventStyle.fontName, "fn"),
            styleValue("Size", format(eventStyle.fontSize), "fs"),
            AssEffectiveValue(
                "Alignment",
                eventStyle.alignment.toString(),
                overrideValue = alignmentOverride?.toString(),
                effectiveValue = (alignmentOverride ?: eventStyle.alignment).toString(),
                effectiveStyle = eventStyle.name,
                spanDependent = laterAlignment,
            ),
            AssEffectiveValue(
                "Margin L",
                eventStyle.marginL.toString(),
                eventValue = event.marginL.takeIf { it > 0 }?.toString(),
                effectiveValue = (event.marginL.takeIf { it > 0 } ?: eventStyle.marginL).toString(),
                effectiveStyle = eventStyle.name,
            ),
            AssEffectiveValue(
                "Margin R",
                eventStyle.marginR.toString(),
                eventValue = event.marginR.takeIf { it > 0 }?.toString(),
                effectiveValue = (event.marginR.takeIf { it > 0 } ?: eventStyle.marginR).toString(),
                effectiveStyle = eventStyle.name,
            ),
            AssEffectiveValue(
                "Margin V",
                eventStyle.marginV.toString(),
                eventValue = event.marginV.takeIf { it > 0 }?.toString(),
                effectiveValue = (event.marginV.takeIf { it > 0 } ?: eventStyle.marginV).toString(),
                effectiveStyle = eventStyle.name,
            ),
            styleValue("Border", format(eventStyle.outline), "bord"),
            styleValue("Shadow", format(eventStyle.shadow), "shad"),
            styleValue("Bold", eventStyle.bold.toString(), "b"),
            styleValue("Italic", eventStyle.italic.toString(), "i"),
            styleValue("Underline", eventStyle.underline.toString(), "u"),
            styleValue("Strike", eventStyle.strikeOut.toString(), "s"),
            styleValue("Spacing", format(eventStyle.spacing), "fsp"),
            styleValue("Scale X", format(eventStyle.scaleX), "fscx"),
            styleValue("Scale Y", format(eventStyle.scaleY), "fscy"),
            styleValue("Angle", format(eventStyle.angle), "fr", "frz"),
            styleValue("Shear X", "0", "fax"),
            styleValue("Shear Y", "0", "fay"),
            styleValue("Primary", eventStyle.primaryColor, "c", "1c"),
            styleValue("Outline Color", eventStyle.outlineColor, "3c"),
            AssEffectiveValue(
                "Clip",
                "none",
                overrideValue = clipSummary,
                effectiveValue = clipSummary ?: "none",
                spanDependent = laterTags.any {
                    it.name.equals("clip", true) || it.name.equals("iclip", true)
                } || leadingTransform,
            ),
            AssEffectiveValue(
                "Position",
                "alignment anchor",
                overrideValue = when (geometry.positionMode) {
                    AssPositionMode.POSITION -> geometry.position?.let { "\${format(it.x)}, \${format(it.y)}" }
                    AssPositionMode.MOVE -> geometry.move?.let {
                        "move \${format(it.start.x)},\${format(it.start.y)} → \${format(it.end.x)},\${format(it.end.y)}"
                    }
                    AssPositionMode.CONFLICT -> "conflict: pos + move"
                    AssPositionMode.INHERITED -> null
                },
                effectiveValue = when (geometry.positionMode) {
                    AssPositionMode.POSITION -> geometry.position?.let { "\${format(it.x)}, \${format(it.y)}" } ?: "alignment anchor"
                    AssPositionMode.MOVE -> geometry.move?.let {
                        "move \${format(it.start.x)},\${format(it.start.y)} → \${format(it.end.x)},\${format(it.end.y)}"
                    } ?: "alignment anchor"
                    AssPositionMode.CONFLICT -> "conflict: pos + move"
                    AssPositionMode.INHERITED -> "alignment anchor"
                },
                spanDependent = laterTags.any {
                    it.name.equals("pos", true) ||
                        it.name.equals("move", true) ||
                        it.name.equals("org", true)
                } || leadingTransform,
            ),
        )
    }

    private fun styleValues(style: AssStyle): Map<String, String> = linkedMapOf(
        "Font" to style.fontName,
        "Size" to format(style.fontSize),
        "Border" to format(style.outline),
        "Shadow" to format(style.shadow),
        "Bold" to style.bold.toString(),
        "Italic" to style.italic.toString(),
        "Underline" to style.underline.toString(),
        "Strike" to style.strikeOut.toString(),
        "Spacing" to format(style.spacing),
        "Scale X" to format(style.scaleX),
        "Scale Y" to format(style.scaleY),
        "Angle" to format(style.angle),
        "Shear X" to "0",
        "Shear Y" to "0",
        "Primary" to style.primaryColor,
        "Outline Color" to style.outlineColor,
    )

    private fun setFiniteDouble(
        values: MutableMap<String, String>,
        overrides: MutableMap<String, String>,
        property: String,
        raw: String,
        tag: String,
    ) {
        val parsed = raw.toDoubleOrNull()
        require(parsed?.isFinite() == true) { "\$tag 必须是有限数字。" }
        val formatted = format(parsed)
        values[property] = formatted
        overrides[property] = formatted
    }

    private fun setBoolean(
        values: MutableMap<String, String>,
        overrides: MutableMap<String, String>,
        property: String,
        raw: String,
        tag: String,
    ) {
        val parsed = raw.toIntOrNull()
        require(parsed != null) { "\$tag 必须是整数。" }
        val formatted = (parsed != 0).toString()
        values[property] = formatted
        overrides[property] = formatted
    }

    private fun leadingOverridePrefixLength(text: String): Int {
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return cursor
            cursor = close + 1
        }
        return cursor
    }

    private fun format(value: Double): String {
        require(value.isFinite()) { "有效值必须是有限数字。" }
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (
            rounded >= Long.MIN_VALUE.toDouble() &&
            rounded <= Long.MAX_VALUE.toDouble() &&
            rounded % 1.0 == 0.0
        ) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}

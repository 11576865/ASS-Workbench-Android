package io.github.assworkbench.domain

enum class AssTransformVisualProperty(
    val tag: String,
    val aliases: Set<String> = setOf(tag),
    val integerOnly: Boolean = false,
    val minimum: Double? = null,
) {
    FONT_SIZE("fs", integerOnly = true, minimum = 1.0),
    SPACING("fsp"),
    SCALE_X("fscx", minimum = 0.0),
    SCALE_Y("fscy", minimum = 0.0),
    ROTATION_X("frx"),
    ROTATION_Y("fry"),
    ROTATION_Z("frz", aliases = setOf("frz", "fr")),
    SHEAR_X("fax"),
    SHEAR_Y("fay"),
    BORDER("bord", minimum = 0.0),
    BORDER_X("xbord", minimum = 0.0),
    BORDER_Y("ybord", minimum = 0.0),
    SHADOW("shad", minimum = 0.0),
    SHADOW_X("xshad"),
    SHADOW_Y("yshad"),
    EDGE_BLUR("be", integerOnly = true, minimum = 0.0),
    GAUSSIAN_BLUR("blur", minimum = 0.0),
}

enum class AssTransformWarningKind {
    NON_ANIMATABLE_TAG,
    VECTOR_CLIP,
    CLIP_ICLIP_MIX,
    NESTED_TRANSFORM,
    FONT_SIZE_HINTING,
    DUPLICATE_PROPERTY,
}

data class AssTransformWarning(
    val kind: AssTransformWarningKind,
    val tag: String? = null,
    val property: AssTransformVisualProperty? = null,
)

data class AssTransformVisualSnapshot(
    val values: Map<AssTransformVisualProperty, Double> = emptyMap(),
    val warnings: List<AssTransformWarning> = emptyList(),
)

object AssTransformVisualSemantic {
    private data class TagRef(
        val name: String,
        val value: String,
        val start: Int,
        val endExclusive: Int,
    )

    private val animatableNames = setOf(
        "fs", "fsp",
        "c", "1c", "2c", "3c", "4c",
        "alpha", "1a", "2a", "3a", "4a",
        "fscx", "fscy",
        "frx", "fry", "frz", "fr",
        "fax", "fay",
        "bord", "xbord", "ybord",
        "shad", "xshad", "yshad",
        "clip", "iclip",
        "be", "blur",
    )

    fun inspect(tags: String): AssTransformVisualSnapshot {
        val refs = scan(tags)
        val values = linkedMapOf<AssTransformVisualProperty, Double>()
        val warnings = mutableListOf<AssTransformWarning>()

        AssTransformVisualProperty.entries.forEach { property ->
            val matches = refs.filter { ref ->
                property.aliases.any { it.equals(ref.name, ignoreCase = true) }
            }
            matches.lastOrNull()?.value?.trim()?.toDoubleOrNull()?.let { value ->
                values[property] = value
            }
            if (matches.size > 1) {
                warnings += AssTransformWarning(
                    kind = AssTransformWarningKind.DUPLICATE_PROPERTY,
                    property = property,
                )
            }
        }

        refs.forEach { ref ->
            val name = ref.name.lowercase()
            when {
                name == "t" -> warnings += AssTransformWarning(
                    kind = AssTransformWarningKind.NESTED_TRANSFORM,
                    tag = ref.name,
                )
                name !in animatableNames -> warnings += AssTransformWarning(
                    kind = AssTransformWarningKind.NON_ANIMATABLE_TAG,
                    tag = ref.name,
                )
            }
        }

        val clips = refs.filter { it.name.equals("clip", true) }
        val inverseClips = refs.filter { it.name.equals("iclip", true) }
        (clips + inverseClips).forEach { ref ->
            if (!isRectClip(ref.value)) {
                warnings += AssTransformWarning(
                    kind = AssTransformWarningKind.VECTOR_CLIP,
                    tag = ref.name,
                )
            }
        }
        if (clips.isNotEmpty() && inverseClips.isNotEmpty()) {
            warnings += AssTransformWarning(kind = AssTransformWarningKind.CLIP_ICLIP_MIX)
        }
        if (refs.any { it.name.equals("fs", true) }) {
            warnings += AssTransformWarning(kind = AssTransformWarningKind.FONT_SIZE_HINTING, tag = "fs")
        }

        return AssTransformVisualSnapshot(
            values = values,
            warnings = warnings.distinct(),
        )
    }

    fun patchNumeric(
        tags: String,
        property: AssTransformVisualProperty,
        value: Double?,
    ): String {
        val refs = scan(tags)
        val matches = refs.filter { ref ->
            property.aliases.any { it.equals(ref.name, ignoreCase = true) }
        }

        if (value == null) {
            var result = tags
            matches.sortedByDescending { it.start }.forEach { ref ->
                result = result.removeRange(ref.start, ref.endExclusive)
            }
            return result
        }

        if (!value.isFinite()) return tags
        if (property.minimum != null && value < property.minimum) return tags

        val normalized = if (property.integerOnly) kotlin.math.round(value) else value
        val target = matches.lastOrNull()
        val tagName = target?.name ?: property.tag
        val replacement = "\\" + tagName + formatNumber(normalized)

        return if (target != null) {
            tags.replaceRange(target.start, target.endExclusive, replacement)
        } else {
            tags + replacement
        }
    }

    private fun isRectClip(value: String): Boolean {
        val trimmed = value.trim()
        if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) return false
        val parts = splitTopLevel(trimmed.substring(1, trimmed.length - 1))
        return parts.size == 4 && parts.all { it.trim().toDoubleOrNull() != null }
    }

    private fun scan(tags: String): List<TagRef> {
        val out = mutableListOf<TagRef>()
        var cursor = 0
        while (cursor < tags.length) {
            if (tags[cursor] != '\\') {
                cursor++
                continue
            }
            val tagStart = cursor++
            val nameStart = cursor
            if (cursor < tags.length && tags[cursor].isDigit()) {
                cursor++
                while (cursor < tags.length && tags[cursor].isLetter()) cursor++
            } else {
                while (
                    cursor < tags.length &&
                    (tags[cursor].isLetter() || tags[cursor] == '-' || tags[cursor] == '_')
                ) {
                    cursor++
                }
            }
            if (cursor == nameStart) continue
            val name = tags.substring(nameStart, cursor)
            val valueStart = cursor
            var depth = 0
            while (cursor < tags.length) {
                when (tags[cursor]) {
                    '(' -> depth++
                    ')' -> if (depth > 0) depth--
                    '\\' -> if (depth == 0) break
                }
                cursor++
            }
            out += TagRef(
                name = name,
                value = tags.substring(valueStart, cursor),
                start = tagStart,
                endExclusive = cursor,
            )
        }
        return out
    }

    private fun splitTopLevel(value: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var depth = 0
        value.forEachIndexed { index, char ->
            when (char) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                ',' -> if (depth == 0) {
                    parts += value.substring(start, index)
                    start = index + 1
                }
            }
        }
        parts += value.substring(start)
        return parts
    }

    private fun formatNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}

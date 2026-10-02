package io.github.assworkbench.domain

enum class AssPositionMode {
    INHERITED,
    POSITION,
    MOVE,
    CONFLICT,
}

data class AssPoint(
    val x: Double,
    val y: Double,
)

data class AssMove(
    val start: AssPoint,
    val end: AssPoint,
    val startMs: Double? = null,
    val endMs: Double? = null,
)
data class AssClipRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    fun normalized(): AssClipRect = AssClipRect(
        left = minOf(left, right),
        top = minOf(top, bottom),
        right = maxOf(left, right),
        bottom = maxOf(top, bottom),
    )
}

data class AssGeometrySnapshot(
    val position: AssPoint? = null,
    val move: AssMove? = null,
    val origin: AssPoint? = null,
    val rotationX: Double? = null,
    val rotationY: Double? = null,
    val rotationZ: Double? = null,
    val scaleX: Double? = null,
    val scaleY: Double? = null,
    val shearX: Double? = null,
    val shearY: Double? = null,
    val clipRect: AssClipRect? = null,
    val clipInverted: Boolean = false,
    val clipNonRectangular: Boolean = false,
    val malformedLeadingBlock: Boolean = false,
) {
    val positionMode: AssPositionMode
        get() = when {
            position != null && move != null -> AssPositionMode.CONFLICT
            position != null -> AssPositionMode.POSITION
            move != null -> AssPositionMode.MOVE
            else -> AssPositionMode.INHERITED
        }
}

/**
 * Conservative semantic access to event-level ASS geometry.
 *
 * Only consecutive leading override blocks are owned by this editor. That keeps the geometry
 * workbench from rewriting span-local formatting later in the dialogue text. Transform payloads
 * such as \\t(...,\\frz30) are scanned as opaque values, so a top-level rotation edit never
 * patches a nested transform by accident.
 *
 * Patches replace exactly one existing top-level tag, or append a new tag to the last leading
 * override block that already contains tags. Unknown tags, ordering, whitespace and all visible
 * text are otherwise preserved.
 */
object AssGeometrySemantic {
    private data class TagRef(
        val name: String,
        val value: String,
        val start: Int,
        val endExclusive: Int,
    )

    private data class BlockRef(
        val closeIndex: Int,
        val hasTags: Boolean,
    )

    private data class LeadingScan(
        val tags: List<TagRef>,
        val blocks: List<BlockRef>,
        val malformed: Boolean,
    )

    fun inspect(text: String): AssGeometrySnapshot {
        val scan = scanLeading(text)
        fun last(vararg names: String): TagRef? = scan.tags.lastOrNull { tag ->
            names.any { it.equals(tag.name, ignoreCase = true) }
        }

        val position = last("pos")?.value?.let(::parsePoint)
        val move = last("move")?.value?.let(::parseMove)
        val origin = last("org")?.value?.let(::parsePoint)
        val rotationX = last("frx")?.value?.trim()?.toDoubleOrNull()
        val rotationY = last("fry")?.value?.trim()?.toDoubleOrNull()
        val rotation = last("frz", "fr")?.value?.trim()?.toDoubleOrNull()
        val scaleX = last("fscx")?.value?.trim()?.toDoubleOrNull()
        val scaleY = last("fscy")?.value?.trim()?.toDoubleOrNull()
        val shearX = last("fax")?.value?.trim()?.toDoubleOrNull()
        val shearY = last("fay")?.value?.trim()?.toDoubleOrNull()
        val clipTag = last("clip", "iclip")
        val clipRect = clipTag?.value?.let(::parseRectClip)
        val clipInverted = clipTag?.name?.equals("iclip", ignoreCase = true) == true
        val clipNonRectangular = clipTag != null && clipRect == null

        return AssGeometrySnapshot(
            position = position,
            move = move,
            origin = origin,
            rotationX = rotationX,
            rotationY = rotationY,
            rotationZ = rotation,
            scaleX = scaleX,
            scaleY = scaleY,
            shearX = shearX,
            shearY = shearY,
            clipRect = clipRect,
            clipInverted = clipInverted,
            clipNonRectangular = clipNonRectangular,
            malformedLeadingBlock = scan.malformed,
        )
    }

    /**
     * Patch or create a static position without silently destroying a motion path.
     * Existing \\move with no \\pos therefore makes this a no-op.
     */
    fun patchPosition(text: String, x: Double, y: Double): String {
        val scan = scanLeading(text)
        val positions = scan.tags.filter { it.name.equals("pos", ignoreCase = true) }
        val moves = scan.tags.filter { it.name.equals("move", ignoreCase = true) }
        if (positions.isEmpty() && moves.isNotEmpty()) return text
        return patchTag(
            text = text,
            scan = scan,
            names = setOf("pos"),
            replacement = "\\pos(${formatNumber(x)},${formatNumber(y)})",
        )
    }

    /**
     * Patch or create a motion path without silently replacing an existing static position.
     */
    fun patchMove(
        text: String,
        start: AssPoint,
        end: AssPoint,
        startMs: Double? = null,
        endMs: Double? = null,
    ): String {
        val scan = scanLeading(text)
        val moves = scan.tags.filter { it.name.equals("move", ignoreCase = true) }
        val positions = scan.tags.filter { it.name.equals("pos", ignoreCase = true) }
        if (moves.isEmpty() && positions.isNotEmpty()) return text
        val timing = if (startMs != null && endMs != null) {
            ",${formatNumber(startMs)},${formatNumber(endMs)}"
        } else {
            ""
        }
        return patchTag(
            text = text,
            scan = scan,
            names = setOf("move"),
            replacement = "\\move(${formatNumber(start.x)},${formatNumber(start.y)}," +
                "${formatNumber(end.x)},${formatNumber(end.y)}$timing)",
        )
    }

    fun patchOrigin(text: String, x: Double, y: Double): String = patchTag(
        text = text,
        scan = scanLeading(text),
        names = setOf("org"),
        replacement = "\\org(${formatNumber(x)},${formatNumber(y)})",
    )
    fun removeOrigin(text: String): String = removeTags(
        text = text,
        scan = scanLeading(text),
        names = setOf("org"),
    )

    fun patchRotationX(text: String, value: Double): String = patchScalar(
        text = text,
        name = "frx",
        value = value,
    )

    fun patchRotationY(text: String, value: Double): String = patchScalar(
        text = text,
        name = "fry",
        value = value,
    )

    fun patchRotationZ(text: String, value: Double): String {
        val scan = scanLeading(text)
        val existing = scan.tags.lastOrNull {
            it.name.equals("frz", ignoreCase = true) || it.name.equals("fr", ignoreCase = true)
        }
        val spelling = existing?.name ?: "frz"
        return patchTag(
            text = text,
            scan = scan,
            names = setOf("frz", "fr"),
            replacement = "\\$spelling${formatNumber(value)}",
        )
    }
    fun removeRotationX(text: String): String = removeTags(
        text = text,
        scan = scanLeading(text),
        names = setOf("frx"),
    )

    fun removeRotationY(text: String): String = removeTags(
        text = text,
        scan = scanLeading(text),
        names = setOf("fry"),
    )

    fun removeRotationZ(text: String): String = removeTags(
        text = text,
        scan = scanLeading(text),
        names = setOf("frz", "fr"),
    )

    fun patchRotation3D(text: String, x: Double, y: Double, z: Double): String =
        patchRotationZ(
            text = patchRotationY(
                text = patchRotationX(text, x),
                value = y,
            ),
            value = z,
        )

    fun removeRotation3D(text: String): String = removeTags(
        text = text,
        scan = scanLeading(text),
        names = setOf("frx", "fry", "frz", "fr"),
    )

    fun patchScaleX(text: String, value: Double): String = patchScalar(
        text = text,
        name = "fscx",
        value = value,
    )

    fun patchScaleY(text: String, value: Double): String = patchScalar(
        text = text,
        name = "fscy",
        value = value,
    )
    fun patchScale(text: String, scaleX: Double, scaleY: Double): String =
        patchScaleY(
            text = patchScaleX(text, scaleX),
            value = scaleY,
        )

    fun removeScale(text: String): String = removeTags(
        text = text,
        scan = scanLeading(text),
        names = setOf("fscx", "fscy"),
    )
    fun patchShearX(text: String, value: Double): String = patchScalar(
        text = text,
        name = "fax",
        value = value,
    )

    fun patchShearY(text: String, value: Double): String = patchScalar(
        text = text,
        name = "fay",
        value = value,
    )

    fun patchShear(text: String, shearX: Double, shearY: Double): String =
        patchShearY(
            text = patchShearX(text, shearX),
            value = shearY,
        )

    fun removeShear(text: String): String = removeTags(
        text = text,
        scan = scanLeading(text),
        names = setOf("fax", "fay"),
    )
    fun patchRectClip(text: String, rect: AssClipRect, inverted: Boolean): String {
        val normalized = rect.normalized()
        val name = if (inverted) "iclip" else "clip"
        return patchTag(
            text = text,
            scan = scanLeading(text),
            names = setOf("clip", "iclip"),
            replacement = "\\$name(${formatNumber(normalized.left)},${formatNumber(normalized.top)}," +
                "${formatNumber(normalized.right)},${formatNumber(normalized.bottom)})",
        )
    }

    fun removeClip(text: String): String = removeTags(
        text = text,
        scan = scanLeading(text),
        names = setOf("clip", "iclip"),
    )

    private fun patchScalar(text: String, name: String, value: Double): String {
        val scan = scanLeading(text)
        val existing = scan.tags.lastOrNull { it.name.equals(name, ignoreCase = true) }
        val spelling = existing?.name ?: name
        return patchTag(
            text = text,
            scan = scan,
            names = setOf(name),
            replacement = "\\$spelling${formatNumber(value)}",
        )
    }

    private fun patchTag(
        text: String,
        scan: LeadingScan,
        names: Set<String>,
        replacement: String,
    ): String {
        val existing = scan.tags.lastOrNull { tag -> names.any { it.equals(tag.name, true) } }
        if (existing != null) {
            return text.replaceRange(existing.start, existing.endExclusive, replacement)
        }
        if (scan.malformed) return text

        val targetBlock = scan.blocks.lastOrNull { it.hasTags }
        return if (targetBlock != null) {
            text.substring(0, targetBlock.closeIndex) + replacement + text.substring(targetBlock.closeIndex)
        } else {
            "{$replacement}$text"
        }
    }

    private fun removeTags(
        text: String,
        scan: LeadingScan,
        names: Set<String>,
    ): String {
        val targets = scan.tags.filter { tag -> names.any { it.equals(tag.name, true) } }
        if (targets.isEmpty()) return text
        var result = text
        targets.sortedByDescending { it.start }.forEach { target ->
            result = result.removeRange(target.start, target.endExclusive)
        }
        return removeEmptyLeadingBlocks(result)
    }

    private fun removeEmptyLeadingBlocks(text: String): String {
        if (text.isEmpty() || text[0] != '{') return text
        val kept = StringBuilder()
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return text
            val content = text.substring(cursor + 1, close)
            if (content.isNotBlank()) kept.append(text, cursor, close + 1)
            cursor = close + 1
        }
        kept.append(text.substring(cursor))
        return kept.toString()
    }
    private fun scanLeading(text: String): LeadingScan {
        if (text.isEmpty() || text[0] != '{') return LeadingScan(emptyList(), emptyList(), false)

        val tags = mutableListOf<TagRef>()
        val blocks = mutableListOf<BlockRef>()
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return LeadingScan(tags, blocks, true)
            val before = tags.size
            scanBlock(text, cursor + 1, close, tags)
            blocks += BlockRef(closeIndex = close, hasTags = tags.size > before)
            cursor = close + 1
        }
        return LeadingScan(tags, blocks, false)
    }

    private fun scanBlock(
        text: String,
        start: Int,
        endExclusive: Int,
        tags: MutableList<TagRef>,
    ) {
        var cursor = start
        while (cursor < endExclusive) {
            if (text[cursor] != '\\') {
                cursor++
                continue
            }

            val tagStart = cursor
            cursor++
            val nameStart = cursor
            if (cursor < endExclusive && text[cursor].isDigit()) {
                cursor++
                while (cursor < endExclusive && text[cursor].isLetter()) cursor++
            } else {
                while (
                    cursor < endExclusive &&
                    (text[cursor].isLetter() || text[cursor] == '-' || text[cursor] == '_')
                ) {
                    cursor++
                }
            }
            if (cursor > nameStart && cursor < endExclusive &&
                text[cursor - 1] == '-' && (text[cursor].isDigit() || text[cursor] == '.')
            ) {
                // A minus sign before a numeric scalar belongs to the value, not the tag name.
                // Keep internal hyphens intact so unknown tags such as \\x-custom still round-trip.
                cursor--
            }
            if (cursor == nameStart) continue

            val name = text.substring(nameStart, cursor)
            val valueStart = cursor
            var depth = 0
            while (cursor < endExclusive) {
                when (text[cursor]) {
                    '(' -> depth++
                    ')' -> if (depth > 0) depth--
                    '\\' -> if (depth == 0) break
                }
                cursor++
            }
            tags += TagRef(
                name = name,
                value = text.substring(valueStart, cursor),
                start = tagStart,
                endExclusive = cursor,
            )
        }
    }

    private fun parsePoint(value: String): AssPoint? {
        val parts = parenthesizedParts(value) ?: return null
        if (parts.size != 2) return null
        val x = parts[0].toDoubleOrNull() ?: return null
        val y = parts[1].toDoubleOrNull() ?: return null
        return AssPoint(x, y)
    }

    private fun parseMove(value: String): AssMove? {
        val parts = parenthesizedParts(value) ?: return null
        if (parts.size != 4 && parts.size != 6) return null
        val numbers = parts.map { it.toDoubleOrNull() ?: return null }
        return AssMove(
            start = AssPoint(numbers[0], numbers[1]),
            end = AssPoint(numbers[2], numbers[3]),
            startMs = numbers.getOrNull(4),
            endMs = numbers.getOrNull(5),
        )
    }
    private fun parseRectClip(value: String): AssClipRect? {
        val parts = parenthesizedParts(value) ?: return null
        if (parts.size != 4) return null
        val numbers = parts.map { it.toDoubleOrNull() ?: return null }
        if (numbers.any { !it.isFinite() }) return null
        return AssClipRect(
            left = numbers[0],
            top = numbers[1],
            right = numbers[2],
            bottom = numbers[3],
        ).normalized()
    }

    private fun parenthesizedParts(value: String): List<String>? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) return null
        return trimmed.substring(1, trimmed.length - 1)
            .split(',')
            .map(String::trim)
    }

    private fun formatNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}

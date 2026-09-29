package io.github.assworkbench.domain

enum class EventFormatPasteMode {
    ALL,
    STYLE,
    MARGINS,
    POSITION,
    OVERRIDES,
    EFFECTS,
}

data class EventFormatClipboard(
    val style: String,
    val marginL: Int,
    val marginR: Int,
    val marginV: Int,
    val leadingOverrides: String,
    val positionTags: String,
    val effectTags: String,
)

object EventFormatClipboardOps {
    private val positionNames = setOf("pos", "move")
    private val effectNames = setOf("fad", "fade", "blur", "t")

    private data class LeadingBlock(
        val raw: String,
        val isOverride: Boolean,
    )

    private data class LeadingPrefix(
        val blocks: List<LeadingBlock>,
        val body: String,
    )

    fun capture(event: AssEvent): EventFormatClipboard {
        val leading = splitLeading(event.text)
        val overrideBlocks = leading.blocks.filter { it.isOverride }
        return EventFormatClipboard(
            style = event.style,
            marginL = event.marginL,
            marginR = event.marginR,
            marginV = event.marginV,
            leadingOverrides = overrideBlocks.joinToString("") { it.raw },
            positionTags = collectManagedTags(overrideBlocks, positionNames),
            effectTags = collectManagedTags(overrideBlocks, effectNames),
        )
    }

    fun apply(event: AssEvent, clipboard: EventFormatClipboard, mode: EventFormatPasteMode): AssEvent =
        when (mode) {
            EventFormatPasteMode.ALL -> event.copy(
                style = clipboard.style,
                marginL = clipboard.marginL,
                marginR = clipboard.marginR,
                marginV = clipboard.marginV,
                text = replaceLeadingOverrides(event.text, clipboard.leadingOverrides),
            )
            EventFormatPasteMode.STYLE -> event.copy(style = clipboard.style)
            EventFormatPasteMode.MARGINS -> event.copy(
                marginL = clipboard.marginL,
                marginR = clipboard.marginR,
                marginV = clipboard.marginV,
            )
            EventFormatPasteMode.OVERRIDES -> event.copy(
                text = replaceLeadingOverrides(event.text, clipboard.leadingOverrides),
            )
            EventFormatPasteMode.POSITION -> event.copy(
                text = replaceManagedTags(event.text, positionNames, clipboard.positionTags),
            )
            EventFormatPasteMode.EFFECTS -> event.copy(
                text = replaceManagedTags(event.text, effectNames, clipboard.effectTags),
            )
        }

    private fun replaceLeadingOverrides(text: String, replacement: String): String {
        val leading = splitLeading(text)
        val firstOverride = leading.blocks.indexOfFirst { it.isOverride }
        val prefix = buildString {
            if (firstOverride < 0 && replacement.isNotEmpty()) append(replacement)
            leading.blocks.forEachIndexed { index, block ->
                when {
                    !block.isOverride -> append(block.raw)
                    index == firstOverride -> append(replacement)
                    else -> Unit
                }
            }
        }
        return prefix + leading.body
    }

    private fun replaceManagedTags(
        text: String,
        names: Set<String>,
        replacementTags: String,
    ): String {
        val leading = splitLeading(text)
        val prefix = buildString {
            leading.blocks.forEach { block ->
                if (!block.isOverride) {
                    append(block.raw)
                } else {
                    val cleaned = removeManagedTags(block.raw, names)
                    if (!isEmptyBlock(cleaned)) append(cleaned)
                }
            }
            if (replacementTags.isNotEmpty()) append("{").append(replacementTags).append("}")
        }
        return prefix + leading.body
    }

    private fun collectManagedTags(blocks: List<LeadingBlock>, names: Set<String>): String =
        buildString {
            blocks.forEach { block ->
                topLevelTagRanges(block.raw, names).forEach { range ->
                    append(block.raw.substring(range.first, range.last + 1))
                }
            }
        }

    private fun removeManagedTags(block: String, names: Set<String>): String {
        val ranges = topLevelTagRanges(block, names)
        if (ranges.isEmpty()) return block
        var output = block
        ranges.asReversed().forEach { range ->
            output = output.removeRange(range.first, range.last + 1)
        }
        return output
    }

    /**
     * Returns complete top-level tag spans inside one override block.
     * Parenthesized payloads stay opaque, so tags inside \t(...) are never
     * mistaken for ordinary top-level clipboard targets.
     */
    private fun topLevelTagRanges(block: String, names: Set<String>): List<IntRange> {
        if (block.length < 3 || block.first() != '{' || block.last() != '}') return emptyList()
        val contentEnd = block.lastIndex
        val sortedNames = names.sortedByDescending { it.length }
        val ranges = mutableListOf<IntRange>()
        var cursor = 1

        while (cursor < contentEnd) {
            if (block[cursor] != '\\') {
                cursor++
                continue
            }
            val start = cursor
            var end = cursor + 1
            var parenDepth = 0
            while (end < contentEnd) {
                when (block[end]) {
                    '(' -> parenDepth++
                    ')' -> if (parenDepth > 0) parenDepth--
                    '\\' -> if (parenDepth == 0) break
                }
                end++
            }

            val managed = sortedNames.any { name ->
                val nameStart = start + 1
                val nameEnd = nameStart + name.length
                nameEnd <= contentEnd &&
                    block.regionMatches(nameStart, name, 0, name.length, ignoreCase = true) &&
                    (nameEnd == contentEnd || !block[nameEnd].isLetter() && block[nameEnd] != '_')
            }
            if (managed) ranges += start until end
            cursor = end.coerceAtLeast(start + 1)
        }
        return ranges
    }

    private fun splitLeading(text: String): LeadingPrefix {
        val blocks = mutableListOf<LeadingBlock>()
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) break
            val raw = text.substring(cursor, close + 1)
            blocks += LeadingBlock(
                raw = raw,
                isOverride = raw.indexOf('\\') >= 0,
            )
            cursor = close + 1
        }
        return LeadingPrefix(blocks = blocks, body = text.substring(cursor))
    }

    private fun isEmptyBlock(block: String): Boolean =
        block.length >= 2 &&
            block.first() == '{' &&
            block.last() == '}' &&
            block.substring(1, block.lastIndex).isBlank()
}

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
    private val leadingBlocks = Regex("""^(?:\{[^}]*\})*""")
    private val posTag = Regex("""\\(?:pos|move)\([^}\\]*\)""", RegexOption.IGNORE_CASE)
    private val effectTag = Regex(
        """\\(?:fad|fade)\([^}\\]*\)|\\blur-?\d+(?:\.\d+)?|\\t\([^}]*?\)""",
        RegexOption.IGNORE_CASE,
    )

    fun capture(event: AssEvent): EventFormatClipboard {
        val leading = leadingBlocks.find(event.text)?.value.orEmpty()
        return EventFormatClipboard(
            style = event.style,
            marginL = event.marginL,
            marginR = event.marginR,
            marginV = event.marginV,
            leadingOverrides = leading,
            positionTags = posTag.findAll(leading).joinToString("") { it.value },
            effectTags = effectTag.findAll(leading).joinToString("") { it.value },
        )
    }

    fun apply(event: AssEvent, clipboard: EventFormatClipboard, mode: EventFormatPasteMode): AssEvent =
        when (mode) {
            EventFormatPasteMode.ALL -> event.copy(
                style = clipboard.style,
                marginL = clipboard.marginL,
                marginR = clipboard.marginR,
                marginV = clipboard.marginV,
                text = clipboard.leadingOverrides + stripLeading(event.text),
            )
            EventFormatPasteMode.STYLE -> event.copy(style = clipboard.style)
            EventFormatPasteMode.MARGINS -> event.copy(
                marginL = clipboard.marginL,
                marginR = clipboard.marginR,
                marginV = clipboard.marginV,
            )
            EventFormatPasteMode.OVERRIDES -> event.copy(
                text = clipboard.leadingOverrides + stripLeading(event.text),
            )
            EventFormatPasteMode.POSITION -> event.copy(
                text = replaceManagedTags(event.text, posTag, clipboard.positionTags),
            )
            EventFormatPasteMode.EFFECTS -> event.copy(
                text = replaceManagedTags(event.text, effectTag, clipboard.effectTags),
            )
        }

    private fun replaceManagedTags(text: String, regex: Regex, replacementTags: String): String {
        val leading = leadingBlocks.find(text)?.value.orEmpty()
        val body = stripLeading(text)
        val cleaned = leading
            .replace(regex, "")
            .replace(Regex("""\{\s*\}"""), "")
        val added = if (replacementTags.isBlank()) "" else "{$replacementTags}"
        return cleaned + added + body
    }

    private fun stripLeading(text: String): String =
        text.removePrefix(leadingBlocks.find(text)?.value.orEmpty())
}

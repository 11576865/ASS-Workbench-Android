package io.github.assworkbench.domain

/**
 * A release-gate view of ASS data that must survive parse -> write -> parse.
 *
 * Event IDs are editor-session identities and are intentionally excluded because
 * ASS does not persist them. Edge-only blank lines are formatting, while opaque
 * comments/metadata and unknown sections remain part of the protected payload.
 */
data class AssSemanticEvent(
    val layer: Int,
    val start: SubTime,
    val end: SubTime,
    val style: String,
    val name: String,
    val marginL: Int,
    val marginR: Int,
    val marginV: Int,
    val effect: String,
    val text: String,
    val comment: Boolean,
    val extraFields: Map<String, String>,
)

data class AssSemanticSnapshot(
    val preamble: List<String>,
    val scriptInfo: Map<String, String>,
    val styles: List<AssStyle>,
    val events: List<AssSemanticEvent>,
    val scriptInfoExtras: List<String>,
    val styleSectionExtras: List<String>,
    val eventSectionExtras: List<String>,
    val styleFormat: List<String>,
    val eventFormat: List<String>,
    val unknownSections: List<RawSection>,
)

data class AssRoundTripReport(
    val equivalent: Boolean,
    val mismatches: List<String>,
) {
    val summary: String
        get() = if (equivalent) "round-trip equivalent"
        else mismatches.take(4).joinToString("; ")
}

object AssRoundTripVerifier {
    fun snapshot(document: AssDocument): AssSemanticSnapshot = AssSemanticSnapshot(
        preamble = normalizeEdgeBlankLines(document.preamble),
        scriptInfo = document.scriptInfo.toMap(),
        styles = document.styles,
        events = document.events.map { event ->
            AssSemanticEvent(
                layer = event.layer,
                // ASS timestamps persist centiseconds only. Editor operations may
                // temporarily carry millisecond precision, so compare the semantic
                // value that the ASS format can actually store.
                start = normalizeAssTime(event.start),
                end = normalizeAssTime(event.end),
                style = event.style,
                name = event.name,
                marginL = event.marginL,
                marginR = event.marginR,
                marginV = event.marginV,
                effect = event.effect,
                text = event.text,
                comment = event.comment,
                extraFields = event.extraFields,
            )
        },
        scriptInfoExtras = normalizeEdgeBlankLines(document.scriptInfoExtras),
        styleSectionExtras = normalizeEdgeBlankLines(document.styleSectionExtras),
        eventSectionExtras = normalizeEdgeBlankLines(document.eventSectionExtras),
        styleFormat = document.styleFormat,
        eventFormat = document.eventFormat,
        unknownSections = document.unknownSections.map { section ->
            section.copy(lines = normalizeEdgeBlankLines(section.lines))
        },
    )

    fun verify(document: AssDocument, serialized: String): AssRoundTripReport =
        compare(snapshot(document), snapshot(AssCodec.parse(serialized)))

    fun requireEquivalent(document: AssDocument, serialized: String) {
        val report = verify(document, serialized)
        require(report.equivalent) {
            "ASS round-trip validation failed: " + report.summary
        }
    }

    fun compare(before: AssSemanticSnapshot, after: AssSemanticSnapshot): AssRoundTripReport {
        val mismatches = buildList {
            if (before.preamble != after.preamble) add("preamble changed")
            if (before.scriptInfo != after.scriptInfo) add("Script Info changed")
            if (before.styles != after.styles) add("Styles changed")
            if (before.events != after.events) add("Events changed")
            if (before.scriptInfoExtras != after.scriptInfoExtras) add("Script Info opaque lines changed")
            if (before.styleSectionExtras != after.styleSectionExtras) add("Style opaque lines changed")
            if (before.eventSectionExtras != after.eventSectionExtras) add("Event opaque lines changed")
            if (before.styleFormat != after.styleFormat) add("Style Format changed")
            if (before.eventFormat != after.eventFormat) add("Event Format changed")
            if (before.unknownSections != after.unknownSections) add("unknown sections changed")
        }
        return AssRoundTripReport(mismatches.isEmpty(), mismatches)
    }

    private fun normalizeAssTime(time: SubTime): SubTime =
        SubTime((time.millis / 10L) * 10L)

    private fun normalizeEdgeBlankLines(lines: List<String>): List<String> {
        var first = 0
        var last = lines.size
        while (first < last && lines[first].isBlank()) first++
        while (last > first && lines[last - 1].isBlank()) last--
        return lines.subList(first, last)
    }
}

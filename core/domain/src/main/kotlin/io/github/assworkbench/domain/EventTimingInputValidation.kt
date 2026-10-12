package io.github.assworkbench.domain

/** Uses the existing editable-time parser; validation does not mutate an Event. */
class EventTimingInputValidation private constructor(val start: SubTime?, val end: SubTime?) {
    val startInvalid: Boolean get() = start == null
    val endInvalid: Boolean get() = end == null
    val reversed: Boolean get() = start != null && end != null && end < start
    val canApply: Boolean get() = !startInvalid && !endInvalid && !reversed

    companion object {
        fun parse(startText: String, endText: String) = EventTimingInputValidation(
            runCatching { SubTime.fromEditable(startText) }.getOrNull(),
            runCatching { SubTime.fromEditable(endText) }.getOrNull(),
        )
    }
}

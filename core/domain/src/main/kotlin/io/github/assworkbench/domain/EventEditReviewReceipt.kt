package io.github.assworkbench.domain

/** Frozen proposals for one Event, applied together or not at all. No global tool or Style-definition drafts. */
data class EventEditReviewReceipt(
    val eventId: Long,
    val text: RawEventDraftBuffer,
    val timing: EventTimingDraftBuffer,
    val metadata: EventMetadataDraftBuffer,
    val style: RawEventDraftBuffer? = null,
    val margins: EventMarginDraftBuffer? = null,
) {
    val textChanged: Boolean get() = text.text != text.baseText
    val timingChanged: Boolean get() = timing.classify(timing.baseline) != RawEventDraftState.CLEAN
    val metadataChanged: Boolean get() = metadata.values != metadata.baseline
    val styleChanged: Boolean get() = style?.let { it.text != it.baseText } == true
    val marginsChanged: Boolean get() = margins?.let { it.values != it.baseline } == true
    val hasChanges: Boolean get() = textChanged || timingChanged || metadataChanged || styleChanged || marginsChanged
    val changedGroupCount: Int get() = listOf(textChanged, timingChanged, metadataChanged, styleChanged, marginsChanged).count { it }

    fun validationErrors(current: AssEvent?, styleNames: Set<String> = emptySet()): List<String> = buildList {
        if (current == null || current.id != eventId) {
            add("审核目标不存在或已经改变")
            return@buildList
        }
        if (styleChanged && style != null) {
            if (style.text !in styleNames) add("目标 Style 已不存在，请重新选择")
            if (style.classify(current.style) == RawEventDraftState.EXTERNAL_CONFLICT)
                add("Style 引用已被其他操作修改，请重新核对草稿")
        }
        if (marginsChanged && margins != null) {
            if (margins.classify(EventMarginValues.from(current)) == RawEventDraftState.EXTERNAL_CONFLICT)
                add("Event Margin 已被其他操作修改，请重新核对草稿")
            if (margins.values.parsed() == null)
                add("Margin L/R/V 必须是 0～9999 范围内的整数")
        }
        if (textChanged && text.classify(current.text) == RawEventDraftState.EXTERNAL_CONFLICT)
            add("正文已被其他操作修改，请重新核对草稿")
        if (timingChanged) {
            if (timing.classify(EventTimingCanonical.from(current)) == RawEventDraftState.EXTERNAL_CONFLICT)
                add("时间已被其他操作修改，请重新核对草稿")
            if (!EventTimingInputValidation.parse(timing.values.start, timing.values.end).canApply)
                add("Start/End 必须有效，且 End 不能早于 Start")
        }
        if (metadataChanged) {
            if (metadata.classify(EventMetadataValues.from(current)) == RawEventDraftState.EXTERNAL_CONFLICT)
                add("元数据已被其他操作修改，请重新核对草稿")
            if (metadata.values.layer.toIntOrNull()?.let { it in -999..999 } != true)
                add("Layer 必须是 -999～999 范围内的整数")
        }
    }

    /** Copy only edited groups onto the latest Event, preserving unrelated external changes. */
    fun applyTo(current: AssEvent?, styleNames: Set<String> = emptySet()): AssEvent? {
        if (validationErrors(current, styleNames).isNotEmpty() || current == null) return null
        val parsed = if (timingChanged) EventTimingInputValidation.parse(timing.values.start, timing.values.end) else null
        val marginValues = if (marginsChanged) checkNotNull(margins).values.parsed() else null
        return current.copy(
            style = if (styleChanged) checkNotNull(style).text else current.style,
            marginL = marginValues?.first ?: current.marginL,
            marginR = marginValues?.second ?: current.marginR,
            marginV = marginValues?.third ?: current.marginV,
            text = if (textChanged) text.text else current.text,
            start = parsed?.start ?: current.start,
            end = parsed?.end ?: current.end,
            layer = if (metadataChanged) checkNotNull(metadata.values.layer.toIntOrNull()) else current.layer,
            name = if (metadataChanged) metadata.values.actor else current.name,
            comment = if (metadataChanged) metadata.values.comment else current.comment,
        )
    }
}

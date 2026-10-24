package io.github.assworkbench.domain

/** Frozen proposals for one Event, applied together or not at all. No global tool/Style drafts. */
data class EventEditReviewReceipt(
    val eventId: Long,
    val text: RawEventDraftBuffer,
    val timing: EventTimingDraftBuffer,
    val metadata: EventMetadataDraftBuffer,
) {
    val textChanged: Boolean get() = text.text != text.baseText
    val timingChanged: Boolean get() = timing.classify(timing.baseline) != RawEventDraftState.CLEAN
    val metadataChanged: Boolean get() = metadata.values != metadata.baseline
    val hasChanges: Boolean get() = textChanged || timingChanged || metadataChanged
    val changedGroupCount: Int get() = listOf(textChanged, timingChanged, metadataChanged).count { it }

    fun validationErrors(current: AssEvent?): List<String> = buildList {
        if (current == null || current.id != eventId) {
            add("审核目标不存在或已经改变")
            return@buildList
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
    fun applyTo(current: AssEvent?): AssEvent? {
        if (validationErrors(current).isNotEmpty() || current == null) return null
        val parsed = if (timingChanged) EventTimingInputValidation.parse(timing.values.start, timing.values.end) else null
        return current.copy(
            text = if (textChanged) text.text else current.text,
            start = parsed?.start ?: current.start,
            end = parsed?.end ?: current.end,
            layer = if (metadataChanged) checkNotNull(metadata.values.layer.toIntOrNull()) else current.layer,
            name = if (metadataChanged) metadata.values.actor else current.name,
            comment = if (metadataChanged) metadata.values.comment else current.comment,
        )
    }
}

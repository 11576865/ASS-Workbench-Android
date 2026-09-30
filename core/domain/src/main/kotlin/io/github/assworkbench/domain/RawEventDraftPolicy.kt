package io.github.assworkbench.domain

enum class RawEventDraftState {
    CLEAN,
    DRAFT_ONLY,
    EXTERNAL_CONFLICT,
}

object RawEventDraftPolicy {
    fun classify(
        baseText: String,
        draftText: String,
        canonicalText: String,
    ): RawEventDraftState = when {
        draftText == canonicalText -> RawEventDraftState.CLEAN
        canonicalText != baseText -> RawEventDraftState.EXTERNAL_CONFLICT
        else -> RawEventDraftState.DRAFT_ONLY
    }
}

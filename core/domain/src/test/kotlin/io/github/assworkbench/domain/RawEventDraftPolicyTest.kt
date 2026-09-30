package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RawEventDraftPolicyTest {
    @Test
    fun cleanWhenDraftMatchesCanonicalEvenIfBaseIsOlder() {
        assertEquals(
            RawEventDraftState.CLEAN,
            RawEventDraftPolicy.classify(
                baseText = "old",
                draftText = "new canonical",
                canonicalText = "new canonical",
            ),
        )
    }

    @Test
    fun draftOnlyWhenCanonicalStillMatchesEditingBase() {
        assertEquals(
            RawEventDraftState.DRAFT_ONLY,
            RawEventDraftPolicy.classify(
                baseText = "source",
                draftText = "local draft",
                canonicalText = "source",
            ),
        )
    }

    @Test
    fun externalConflictWhenCanonicalChangesUnderLocalDraft() {
        assertEquals(
            RawEventDraftState.EXTERNAL_CONFLICT,
            RawEventDraftPolicy.classify(
                baseText = "source",
                draftText = "local draft",
                canonicalText = "other panel edit",
            ),
        )
    }
}

package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewSidecarCodecTest {
    @Test
    fun roundTripsReviewMetadataAndChineseReferenceText() {
        val input = ReviewSidecar(
            sourceStyle = "Source",
            targetStyle = "中文",
            confirmedIds = setOf(2L, 8L),
            originalTextById = mapOf(
                1L to "I remember this line.",
                2L to "我记得这一句。",
            ),
        )
        val output = ReviewSidecarCodec.decode(ReviewSidecarCodec.encode(input))
        assertEquals(input, output)
    }

    @Test
    fun rejectsUnknownHeader() {
        assertEquals(null, ReviewSidecarCodec.decode("not-a-sidecar"))
    }
}

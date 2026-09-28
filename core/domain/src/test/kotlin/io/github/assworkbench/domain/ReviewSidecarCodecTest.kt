package io.github.assworkbench.domain

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewSidecarCodecTest {
    @Test
    fun roundTripsV2StableKeysAndChineseReferenceText() {
        val input = ReviewSidecar(
            sourceStyle = "Source",
            targetStyle = "中文",
            confirmedKeys = setOf("e2:aaa", "e2:bbb"),
            originalTextByKey = mapOf(
                "e2:aaa" to "I remember this line.",
                "e2:bbb" to "我记得这一句。",
            ),
        )
        val encoded = ReviewSidecarCodec.encode(input)
        assertTrue(encoded.startsWith("ASSWB_REVIEW_V2\n"))
        assertEquals(input, ReviewSidecarCodec.decode(encoded))
    }

    @Test
    fun decodesLegacyV1ForMigration() {
        fun enc(value: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        val legacy = buildString {
            append("ASSWB_REVIEW_V1\n")
            append("S\t").append(enc("Source")).append('\n')
            append("T\t").append(enc("中文")).append('\n')
            append("C\t2,8\n")
            append("O\t2\t").append(enc("旧参考文本")).append('\n')
        }
        val output = ReviewSidecarCodec.decode(legacy)!!
        assertEquals("Source", output.sourceStyle)
        assertEquals("中文", output.targetStyle)
        assertEquals(setOf(2L, 8L), output.legacyConfirmedIds)
        assertEquals(mapOf(2L to "旧参考文本"), output.legacyOriginalTextById)
        assertTrue(output.confirmedKeys.isEmpty())
    }

    @Test
    fun rejectsUnknownHeader() {
        assertEquals(null, ReviewSidecarCodec.decode("not-a-sidecar"))
    }
}

package io.github.assworkbench.app

import io.github.assworkbench.domain.AssCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerTrackImportNormalizerTest {
    @Test
    fun detectsStandaloneSubtitleAdaptersByExtension() {
        assertEquals(
            ContainerTrackImportSourceKind.STANDALONE_ASS,
            detectStandaloneSubtitleTrackSourceKind("dialogue.ass"),
        )
        assertEquals(
            ContainerTrackImportSourceKind.STANDALONE_ASS,
            detectStandaloneSubtitleTrackSourceKind("legacy.SSA"),
        )
        assertEquals(
            ContainerTrackImportSourceKind.STANDALONE_SRT,
            detectStandaloneSubtitleTrackSourceKind("dialogue.SRT"),
        )
        assertEquals(null, detectStandaloneSubtitleTrackSourceKind("movie.mkv"))
    }

    @Test
    fun srtNormalizesToDeterministicAssExecutionRepresentation() {
        val srt = """
            1
            00:00:00,250 --> 00:00:01,500
            <i>Hello</i>
            second line

            2
            00:00:02.000 --> 00:00:03.250
            <b>World</b>
        """.trimIndent()

        val normalized = normalizeStandaloneSubtitleTrackSource(
            sourceKind = ContainerTrackImportSourceKind.STANDALONE_SRT,
            raw = srt.toByteArray(Charsets.UTF_8),
        )

        assertEquals(ContainerTrackImportSourceKind.STANDALONE_SRT, normalized.sourceKind)
        assertEquals(2, normalized.eventCount)
        assertTrue(normalized.normalizedAssText.contains("[Events]"))
        assertTrue(normalized.normalizedAssText.contains("{\\i1}Hello{\\i0}\\Nsecond line"))
        assertTrue(normalized.normalizedAssText.contains("{\\b1}World{\\b0}"))
        assertTrue(normalized.normalizedSha256.matches(Regex("[0-9a-f]{64}")))

        val document = AssCodec.parse(normalized.normalizedAssText)
        assertEquals(250L, document.events[0].start.millis)
        assertEquals(1500L, document.events[0].end.millis)
        assertEquals(2000L, document.events[1].start.millis)
        assertEquals(3250L, document.events[1].end.millis)
    }

    @Test
    fun srtLineEndingsAndBomDoNotChangeNormalizedIdentity() {
        val lf = "1\n00:00:00,000 --> 00:00:01,000\nHello\n"
        val crlfWithBom = "\uFEFF1\r\n00:00:00,000 --> 00:00:01,000\r\nHello\r\n"

        val a = normalizeStandaloneSubtitleTrackSource(
            ContainerTrackImportSourceKind.STANDALONE_SRT,
            lf.toByteArray(Charsets.UTF_8),
        )
        val b = normalizeStandaloneSubtitleTrackSource(
            ContainerTrackImportSourceKind.STANDALONE_SRT,
            crlfWithBom.toByteArray(Charsets.UTF_8),
        )

        assertEquals(a.normalizedAssText, b.normalizedAssText)
        assertEquals(a.normalizedSha256, b.normalizedSha256)
    }

    @Test
    fun semanticSrtChangeChangesNormalizedIdentity() {
        val a = normalizeStandaloneSubtitleTrackSource(
            ContainerTrackImportSourceKind.STANDALONE_SRT,
            "1\n00:00:00,000 --> 00:00:01,000\nHello\n".toByteArray(Charsets.UTF_8),
        )
        val b = normalizeStandaloneSubtitleTrackSource(
            ContainerTrackImportSourceKind.STANDALONE_SRT,
            "1\n00:00:00,000 --> 00:00:01,000\nGoodbye\n".toByteArray(Charsets.UTF_8),
        )

        assertNotEquals(a.normalizedSha256, b.normalizedSha256)
    }
}

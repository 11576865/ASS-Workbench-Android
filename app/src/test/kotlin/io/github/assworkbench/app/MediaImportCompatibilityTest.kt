package io.github.assworkbench.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaImportCompatibilityTest {
    @Test
    fun avcWithCodecConfigIsStreamCopyCompatibleButNotExecutableYet() {
        val result = MediaImportCompatibilityPlanner.assess(
            MediaImportTrackDescriptor(
                extractorIndex = 0,
                kind = MediaImportTrackKind.VIDEO,
                mime = "video/avc",
                codecPrivateKeys = setOf("csd-0", "csd-1"),
                decoderAvailable = true,
            )
        )

        assertEquals(MediaImportDisposition.STREAM_COPY_COMPATIBLE, result.disposition)
        assertEquals("V_MPEG4/ISO/AVC", result.matroskaCodecId)
        assertFalse(result.executionImplemented)
        assertTrue(result.reason.contains("尚未接线"))
    }

    @Test
    fun avcWithoutCodecConfigRemainsUnknownInsteadOfPretendingCopySupport() {
        val result = MediaImportCompatibilityPlanner.assess(
            MediaImportTrackDescriptor(
                extractorIndex = 0,
                kind = MediaImportTrackKind.VIDEO,
                mime = "video/avc",
                decoderAvailable = true,
            )
        )

        assertEquals(MediaImportDisposition.UNKNOWN, result.disposition)
        assertEquals("V_MPEG4/ISO/AVC", result.matroskaCodecId)
        assertTrue(result.reason.contains("csd-*"))
    }

    @Test
    fun aacWithCodecConfigMapsToMatroskaAac() {
        val result = MediaImportCompatibilityPlanner.assess(
            MediaImportTrackDescriptor(
                extractorIndex = 1,
                kind = MediaImportTrackKind.AUDIO,
                mime = "audio/mp4a-latm",
                codecPrivateKeys = setOf("csd-0"),
                decoderAvailable = true,
            )
        )

        assertEquals(MediaImportDisposition.STREAM_COPY_COMPATIBLE, result.disposition)
        assertEquals("A_AAC", result.matroskaCodecId)
        assertFalse(result.executionImplemented)
    }

    @Test
    fun mp3MapsWithoutCodecPrivateRequirement() {
        val result = MediaImportCompatibilityPlanner.assess(
            MediaImportTrackDescriptor(
                extractorIndex = 1,
                kind = MediaImportTrackKind.AUDIO,
                mime = "audio/mpeg",
                decoderAvailable = true,
            )
        )

        assertEquals(MediaImportDisposition.STREAM_COPY_COMPATIBLE, result.disposition)
        assertEquals("A_MPEG/L3", result.matroskaCodecId)
        assertTrue(result.executionImplemented)
        assertTrue(result.reason.contains("MediaExtractor"))
    }

    @Test
    fun opusRemainsUnknownUntilMatroskaHeaderMappingIsProven() {
        val result = MediaImportCompatibilityPlanner.assess(
            MediaImportTrackDescriptor(
                extractorIndex = 1,
                kind = MediaImportTrackKind.AUDIO,
                mime = "audio/opus",
                codecPrivateKeys = setOf("csd-0"),
                decoderAvailable = true,
            )
        )

        assertEquals(MediaImportDisposition.UNKNOWN, result.disposition)
        assertEquals("A_OPUS", result.matroskaCodecId)
        assertTrue(result.reason.contains("OpusHead"))
    }

    @Test
    fun decodableUnknownAudioRequiresExplicitTranscodeRatherThanSilentConversion() {
        val result = MediaImportCompatibilityPlanner.assess(
            MediaImportTrackDescriptor(
                extractorIndex = 1,
                kind = MediaImportTrackKind.AUDIO,
                mime = "audio/amr-wb",
                decoderAvailable = true,
            )
        )

        assertEquals(MediaImportDisposition.TRANSCODE_REQUIRED, result.disposition)
        assertNull(result.matroskaCodecId)
        assertFalse(result.executionImplemented)
        assertTrue(result.reason.contains("显式 Transcode"))
    }

    @Test
    fun unknownAndUndecodableMediaStaysUnknown() {
        val result = MediaImportCompatibilityPlanner.assess(
            MediaImportTrackDescriptor(
                extractorIndex = 3,
                kind = MediaImportTrackKind.VIDEO,
                mime = "video/vendor-private",
                decoderAvailable = false,
            )
        )

        assertEquals(MediaImportDisposition.UNKNOWN, result.disposition)
        assertFalse(result.executionImplemented)
    }

    @Test
    fun subtitleTrackIsRejectedFromMediaStreamCopyDomain() {
        val result = MediaImportCompatibilityPlanner.assess(
            MediaImportTrackDescriptor(
                extractorIndex = 2,
                kind = MediaImportTrackKind.SUBTITLE,
                mime = "application/x-subrip",
            )
        )

        assertEquals(MediaImportDisposition.UNSUPPORTED, result.disposition)
        assertTrue(result.reason.contains("字幕 adapter"))
    }

    @Test
    fun matroskaRoutingUsesExtensionOrMimeWithoutTreatingMp4AsMatroska() {
        assertTrue(isMatroskaFamilySource("movie.mkv", "application/octet-stream"))
        assertTrue(isMatroskaFamilySource("audio.bin", "audio/x-matroska"))
        assertTrue(isMatroskaFamilySource("clip.webm", "video/webm"))
        assertFalse(isMatroskaFamilySource("movie.mp4", "video/mp4"))
        assertFalse(isMatroskaFamilySource("track.mp3", "audio/mpeg"))
    }
}

package io.github.assworkbench.app

import io.github.assworkbench.container.MatroskaAttachmentInfo
import io.github.assworkbench.container.MatroskaScanResult
import io.github.assworkbench.container.MatroskaTrackInfo
import io.github.assworkbench.container.MatroskaTrackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerInventoryTest {
    @Test
    fun stableTrackUidSurvivesTrackNumberReorder() {
        val baseline = scan(
            tracks = listOf(
                track(1, 10, MatroskaTrackKind.VIDEO, "V_AV1"),
                track(2, 20, MatroskaTrackKind.AUDIO, "A_OPUS", language = "jpn"),
            ),
        )
        val reordered = scan(
            tracks = listOf(
                track(9, 10, MatroskaTrackKind.VIDEO, "V_AV1"),
                track(4, 20, MatroskaTrackKind.AUDIO, "A_OPUS", language = "jpn"),
            ),
        )

        val diff = diffContainerResources(baseline, reordered)

        assertEquals(2, diff.size)
        assertTrue(diff.all { it.change == ContainerResourceChange.UNCHANGED })
    }

    @Test
    fun reportsAddedRemovedAndModifiedResources() {
        val baseline = scan(
            tracks = listOf(
                track(1, 10, MatroskaTrackKind.VIDEO, "V_AV1"),
                track(3, 30, MatroskaTrackKind.SUBTITLE, "S_TEXT/ASS", contentHash = "old"),
            ),
            attachments = listOf(
                attachment(uid = 40, name = "old-font.ttf", sha = "aaaa"),
            ),
        )
        val current = scan(
            tracks = listOf(
                track(1, 10, MatroskaTrackKind.VIDEO, "V_AV1"),
                track(3, 30, MatroskaTrackKind.SUBTITLE, "S_TEXT/ASS", contentHash = "new"),
            ),
            attachments = listOf(
                attachment(uid = 41, name = "new-font.otf", sha = "bbbb"),
            ),
        )

        val diff = diffContainerResources(baseline, current)

        assertTrue(diff.any { it.change == ContainerResourceChange.MODIFIED && it.trackNumber == 3L })
        assertTrue(diff.any { it.change == ContainerResourceChange.ADDED && it.title == "new-font.otf" })
        assertTrue(diff.any { it.change == ContainerResourceChange.REMOVED && it.title == "old-font.ttf" })
    }

    @Test
    fun duplicateWeakIdentityFailsClosedAsUnresolved() {
        val baseline = scan(
            tracks = listOf(
                track(1, null, MatroskaTrackKind.AUDIO, "A_AAC", language = "eng"),
                track(2, null, MatroskaTrackKind.AUDIO, "A_AAC", language = "eng"),
            ),
        )
        val current = scan(
            tracks = listOf(
                track(7, null, MatroskaTrackKind.AUDIO, "A_AAC", language = "eng"),
            ),
        )

        val diff = diffContainerResources(baseline, current)

        assertTrue(diff.any { it.change == ContainerResourceChange.UNRESOLVED })
        assertTrue(diff.none { it.change == ContainerResourceChange.MODIFIED })
    }

    private fun scan(
        tracks: List<MatroskaTrackInfo> = emptyList(),
        attachments: List<MatroskaAttachmentInfo> = emptyList(),
    ) = MatroskaScanResult(
        subtitleTracks = emptyList(),
        attachments = emptyList(),
        timecodeScaleNs = 1_000_000L,
        trackInfos = tracks,
        attachmentInfos = attachments,
    )

    private fun track(
        number: Long,
        uid: Long?,
        kind: MatroskaTrackKind,
        codec: String,
        language: String = "",
        contentHash: String? = null,
    ) = MatroskaTrackInfo(
        number = number,
        uid = uid,
        typeCode = when (kind) {
            MatroskaTrackKind.VIDEO -> 0x01L
            MatroskaTrackKind.AUDIO -> 0x02L
            MatroskaTrackKind.SUBTITLE -> 0x11L
            else -> 0x21L
        },
        kind = kind,
        name = "",
        language = language,
        codecId = codec,
        isDefault = true,
        isForced = false,
        contentHash = contentHash,
    )

    private fun attachment(
        uid: Long?,
        name: String,
        sha: String?,
    ) = MatroskaAttachmentInfo(
        uid = uid,
        fileName = name,
        mimeType = "font/otf",
        description = "",
        sizeBytes = 1024L,
        sha256 = sha,
        dataAvailable = true,
    )
}

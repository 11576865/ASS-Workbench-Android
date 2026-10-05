package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.assworkbench.container.MatroskaReader
import io.github.assworkbench.container.MatroskaTrackKind
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MkvBridgeInstrumentedTest {
    private lateinit var application: Application
    private lateinit var workDir: File

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        workDir = File(application.cacheDir, "mkv-emulator-regression").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun nativeBridgeReplacesAssAndPreservesExistingFontAttachment() {
        val source = File(workDir, "source.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("mkv/source.mkv")
            .use { input -> source.outputStream().use { output -> input.copyTo(output) } }

        val sourceScan = source.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(1, sourceScan.subtitleTracks.size)
        assertEquals(
            listOf(MatroskaTrackKind.VIDEO, MatroskaTrackKind.SUBTITLE),
            sourceScan.trackInfos.map { it.kind },
        )
        assertEquals(2L, sourceScan.subtitleTracks.single().number)
        assertTrue(sourceScan.subtitleTracks.single().toAss().contains("Old line"))
        assertEquals(listOf("Fixture.ttf"), sourceScan.attachments.map { it.fileName })

        val edited = File(workDir, "edited.ass")
        edited.writeText(
            sourceScan.subtitleTracks.single().toAss()
                .replace("Old line", "Edited on Android Emulator"),
            Charsets.UTF_8,
        )
        val addedFont = File(workDir, "AddedFont.otf").apply {
            writeBytes(byteArrayOf(0x4f, 0x54, 0x54, 0x4f) + ByteArray(128) { 0x42 })
        }
        val cover = File(workDir, "cover.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47) + ByteArray(96) { 0x21 })
        }
        val output = File(workDir, "updated.mkv")

        val tool = MkvGoTool(application)
        assertTrue("x86_64 emulator APK must package the mkvgo helper", tool.isAvailable())
        tool.replaceAss(
            source = source,
            trackNumber = 2L,
            editedAss = edited,
            output = output,
            fonts = listOf(addedFont),
            attachments = listOf(cover),
        )

        assertTrue(output.isFile)
        assertTrue(output.length() > 0L)

        val outputScan = output.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(1, outputScan.subtitleTracks.size)
        assertEquals(2L, outputScan.subtitleTracks.single().number)

        val outputAss = outputScan.subtitleTracks.single().toAss()
        assertTrue(outputAss.contains("Edited on Android Emulator"))
        assertFalse(outputAss.contains("Old line"))

        val attachmentNames = outputScan.attachments.map { it.fileName }.toSet()
        assertTrue("Fixture.ttf" in attachmentNames)
        assertTrue("AddedFont.otf" in attachmentNames)
        assertTrue("cover.png" in attachmentNames)
        assertEquals("image/png", outputScan.attachments.first { it.fileName == "cover.png" }.mimeType)
        assertEquals(3, outputScan.attachments.size)

        val inventoryDiff = diffContainerResources(sourceScan, outputScan)
        assertTrue(
            inventoryDiff.any {
                it.trackNumber == 2L && it.change == ContainerResourceChange.MODIFIED
            }
        )
        assertTrue(
            inventoryDiff.any {
                it.title == "AddedFont.otf" && it.change == ContainerResourceChange.ADDED
            }
        )
        assertTrue(
            inventoryDiff.any {
                it.title == "cover.png" && it.change == ContainerResourceChange.ADDED
            }
        )
        assertTrue(inventoryDiff.none { it.change == ContainerResourceChange.REMOVED })
    }

    @Test
    fun nativeBridgeAddsGenericAttachmentWithoutEditingAss() {
        val source = File(workDir, "attachment-only-source.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("mkv/source.mkv")
            .use { input -> source.outputStream().use { output -> input.copyTo(output) } }
        val before = source.inputStream().use { MatroskaReader().scan(it) }

        val note = File(workDir, "notes.txt").apply {
            writeText("ASS Workbench attachment-only fixture", Charsets.UTF_8)
        }
        val output = File(workDir, "attachment-only-updated.mkv")

        val tool = MkvGoTool(application)
        assertTrue("x86_64 emulator APK must package the mkvgo helper", tool.isAvailable())
        tool.addAttachments(source, output, listOf(note))

        val after = output.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(before.trackPreservationSignature(), after.trackPreservationSignature())
        assertEquals(before.chapterCount, after.chapterCount)
        assertEquals(
            before.subtitleTracks.single().toAss(),
            after.subtitleTracks.single().toAss(),
        )
        assertTrue(after.attachments.any { it.fileName == "notes.txt" && it.mimeType == "text/plain" })
        assertEquals(before.attachments.size + 1, after.attachments.size)
    }


    @Test
    fun nativeBridgeReplacesAndRemovesAttachmentsWithoutTouchingTracks() {
        val source = File(workDir, "attachment-crud-source.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("mkv/source.mkv")
            .use { input -> source.outputStream().use { output -> input.copyTo(output) } }
        val before = source.inputStream().use { MatroskaReader().scan(it) }
        val original = before.attachmentInfos.single()
        val originalTarget = original.uid?.toString() ?: original.fileName

        val cover = File(workDir, "cover.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47) + ByteArray(64) { 0x2a })
        }
        val note = File(workDir, "notes.txt").apply {
            writeText("attachment CRUD regression", Charsets.UTF_8)
        }
        val replaced = File(workDir, "attachment-crud-replaced.mkv")

        val tool = MkvGoTool(application)
        assertTrue("x86_64 emulator APK must package the mkvgo helper", tool.isAvailable())
        tool.editAttachments(
            source = source,
            output = replaced,
            additions = listOf(note),
            replacements = listOf(
                AttachmentReplacementInput(
                    target = originalTarget,
                    file = cover,
                )
            ),
        )

        val middle = replaced.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(before.trackPreservationSignature(), middle.trackPreservationSignature())
        assertEquals(before.chapterCount, middle.chapterCount)
        assertEquals(before.attachmentInfos.size + 1, middle.attachmentInfos.size)
        assertTrue(middle.attachmentInfos.none { it.fileName == original.fileName })
        val replacedInfo = middle.attachmentInfos.first { it.fileName == "cover.png" }
        if (original.uid != null) {
            assertEquals(original.uid, replacedInfo.uid)
        }
        val noteInfo = middle.attachmentInfos.first { it.fileName == "notes.txt" }
        val noteTarget = noteInfo.uid?.toString() ?: noteInfo.fileName

        val removed = File(workDir, "attachment-crud-removed.mkv")
        tool.editAttachments(
            source = replaced,
            output = removed,
            removals = listOf(noteTarget),
        )

        val after = removed.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(middle.trackPreservationSignature(), after.trackPreservationSignature())
        assertEquals(middle.chapterCount, after.chapterCount)
        assertEquals(before.attachmentInfos.size, after.attachmentInfos.size)
        assertTrue(after.attachmentInfos.any { it.fileName == "cover.png" })
        assertTrue(after.attachmentInfos.none { it.fileName == "notes.txt" })
    }

    @Test
    fun nativeBridgeEditsAttachmentMetadataAndExtractsPayload() {
        val source = File(workDir, "attachment-meta-source.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("mkv/source.mkv")
            .use { input -> source.outputStream().use { output -> input.copyTo(output) } }

        val before = source.inputStream().use { MatroskaReader().scan(it) }
        val original = before.attachmentInfos.single()
        val target = original.uid?.toString() ?: original.fileName
        val extracted = File(workDir, "extracted-font.bin")

        val tool = MkvGoTool(application)
        assertTrue("x86_64 emulator APK must package the mkvgo helper", tool.isAvailable())
        tool.extractAttachment(source, target, extracted)
        assertTrue(extracted.isFile)
        assertTrue(extracted.length() > 0L)

        val output = File(workDir, "attachment-meta-updated.mkv")
        tool.editAttachments(
            source = source,
            output = output,
            metadataEdits = listOf(
                AttachmentMetadataEditInput(
                    target = target,
                    name = "RenamedFixture.ttf",
                    description = "renamed on Android emulator",
                )
            ),
        )

        val after = output.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(before.trackPreservationSignature(), after.trackPreservationSignature())
        assertEquals(before.chapterCount, after.chapterCount)
        assertEquals(before.attachmentInfos.size, after.attachmentInfos.size)

        val renamed = after.attachmentInfos.single()
        assertEquals("RenamedFixture.ttf", renamed.fileName)
        assertEquals("renamed on Android emulator", renamed.description)
        assertEquals(original.mimeType, renamed.mimeType)
        assertEquals(original.sizeBytes, renamed.sizeBytes)
        if (original.uid != null) {
            assertEquals(original.uid, renamed.uid)
        }
    }

    @Test
    fun nativeBridgeEditsTrackMetadataAndRemovesTrackWithoutRenumbering() {
        val source = File(workDir, "track-mutation-source.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("mkv/source.mkv")
            .use { input -> source.outputStream().use { output -> input.copyTo(output) } }

        val before = source.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(2, before.trackInfos.size)
        val video = before.trackInfos.single { it.kind == MatroskaTrackKind.VIDEO }
        val subtitle = before.trackInfos.single { it.kind == MatroskaTrackKind.SUBTITLE }
        val videoTarget = video.uid?.let { "uid:$it" } ?: "number:${video.number}"
        val subtitleTarget = subtitle.uid?.let { "uid:$it" } ?: "number:${subtitle.number}"

        val output = File(workDir, "track-mutation-updated.mkv")
        val tool = MkvGoTool(application)
        assertTrue("x86_64 emulator APK must package the mkvgo helper", tool.isAvailable())
        tool.editContainer(
            source = source,
            output = output,
            removeTracks = listOf(subtitleTarget),
            trackMetadataEdits = listOf(
                TrackMetadataEditInput(
                    target = videoTarget,
                    name = "Main picture",
                    language = "und",
                    isDefault = false,
                    isForced = true,
                    languageBcp47 = "ja-JP",
                    hearingImpaired = true,
                    visualImpaired = true,
                    textDescriptions = true,
                    original = true,
                    commentary = true,
                )
            ),
        )

        val after = output.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(1, after.trackInfos.size)
        val remaining = after.trackInfos.single()
        assertEquals(video.number, remaining.number)
        assertEquals(video.uid, remaining.uid)
        assertEquals(video.kind, remaining.kind)
        assertEquals(video.codecId, remaining.codecId)
        assertEquals("Main picture", remaining.name)
        assertEquals("und", remaining.language)
        assertEquals("ja-JP", remaining.languageBcp47)
        assertFalse(remaining.isDefault)
        assertTrue(remaining.isForced)
        assertTrue(remaining.hearingImpaired)
        assertTrue(remaining.visualImpaired)
        assertTrue(remaining.textDescriptions)
        assertTrue(remaining.original)
        assertTrue(remaining.commentary)
        assertTrue(after.trackInfos.none { it.number == subtitle.number })
        assertTrue(after.subtitleTracks.isEmpty())

        assertEquals(before.chapterCount, after.chapterCount)
        assertEquals(
            before.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
            after.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
        )
    }

    @Test
    fun nativeBridgeImportsExternalTrackWithFreshDestinationIdentity() {
        val source = File(workDir, "track-import-target.mkv")
        val external = File(workDir, "track-import-external.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("mkv/source.mkv")
            .use { input -> source.outputStream().use { output -> input.copyTo(output) } }
        // Reuse the same valid Matroska fixture as an external source. The
        // destination must assign fresh identity even when source IDs collide.
        source.inputStream().use { input ->
            external.outputStream().use { output -> input.copyTo(output) }
        }

        val before = source.inputStream().use { MatroskaReader().scan(it) }
        val externalScan = external.inputStream().use { MatroskaReader().scan(it) }
        val sourceVideo = externalScan.trackInfos.single { it.kind == MatroskaTrackKind.VIDEO }
        val originalNumbers = before.trackInfos.mapTo(hashSetOf()) { it.number }
        val originalUids = before.trackInfos.mapNotNullTo(hashSetOf()) { it.uid }

        val output = File(workDir, "track-import-updated.mkv")
        val tool = MkvGoTool(application)
        assertTrue("x86_64 emulator APK must package the mkvgo helper", tool.isAvailable())
        tool.editContainer(
            source = source,
            output = output,
            trackImports = listOf(
                TrackImportInput(
                    source = external,
                    trackNumber = sourceVideo.number,
                    sourceTrackUid = sourceVideo.uid,
                    name = "Second video",
                    language = "und",
                    isDefault = false,
                    isForced = false,
                    languageBcp47 = "en-US",
                    hearingImpaired = true,
                    commentary = true,
                )
            ),
        )

        val after = output.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(before.trackInfos.size + 1, after.trackInfos.size)
        assertEquals(
            before.trackPreservationSignature(),
            after.copy(trackInfos = after.trackInfos.take(before.trackInfos.size)).trackPreservationSignature(),
        )

        val imported = after.trackInfos.last()
        assertTrue(imported.number !in originalNumbers)
        imported.uid?.let { assertTrue(it !in originalUids) }
            ?: throw AssertionError("imported track must receive TrackUID")
        assertEquals(MatroskaTrackKind.VIDEO, imported.kind)
        assertEquals(sourceVideo.codecId, imported.codecId)
        assertEquals("Second video", imported.name)
        assertEquals("und", imported.language)
        assertEquals("en-US", imported.languageBcp47)
        assertFalse(imported.isDefault)
        assertFalse(imported.isForced)
        assertTrue(imported.hearingImpaired)
        assertTrue(imported.commentary)

        assertEquals(before.chapterCount, after.chapterCount)
        assertEquals(
            before.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
            after.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
        )
    }
    @Test
    fun nativeBridgeAddsStandaloneAssAsFreshSubtitleTrack() {
        val source = File(workDir, "standalone-ass-base.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("mkv/source.mkv")
            .use { input -> source.outputStream().use { output -> input.copyTo(output) } }

        val before = source.inputStream().use { MatroskaReader().scan(it) }
        val maxOriginalNumber = before.trackInfos.maxOf { it.number }
        val originalUids = before.trackInfos.mapNotNull { it.uid }.toSet()

        val importedAss = File(workDir, "imported.ass")
        val assText = """
            [Script Info]
            ScriptType: v4.00+

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:00.25,0:00:02.25,Default,,0,0,0,,Standalone Android import
        """.trimIndent()
        importedAss.writeText(assText, Charsets.UTF_8)
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(importedAss.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        val output = File(workDir, "standalone-ass-updated.mkv")
        val tool = MkvGoTool(application)
        assertTrue("x86_64 emulator APK must package the mkvgo helper", tool.isAvailable())
        tool.editContainer(
            source = source,
            output = output,
            trackImports = listOf(
                TrackImportInput(
                    sourceKind = ContainerTrackImportSourceKind.STANDALONE_ASS,
                    source = importedAss,
                    sourceSha256 = sha,
                    name = "Standalone ASS",
                    language = "eng",
                    isDefault = false,
                    isForced = false,
                )
            ),
        )

        val after = output.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(before.trackInfos.size + 1, after.trackInfos.size)
        assertEquals(
            before.trackPreservationSignature(),
            after.copy(trackInfos = after.trackInfos.take(before.trackInfos.size))
                .trackPreservationSignature(),
        )

        val added = after.trackInfos.last()
        assertEquals(MatroskaTrackKind.SUBTITLE, added.kind)
        assertEquals("S_TEXT/ASS", added.codecId)
        assertTrue(added.number > maxOriginalNumber)
        val addedUid = added.uid
        assertTrue(addedUid != null && addedUid !in originalUids)
        assertEquals("Standalone ASS", added.name)
        assertEquals("eng", added.language)
        assertFalse(added.isDefault)
        assertFalse(added.isForced)

        val importedTrack = after.subtitleTracks.single { it.number == added.number }
        assertTrue(importedTrack.toAss().contains("Standalone Android import"))
        assertEquals(before.chapterCount, after.chapterCount)
        assertEquals(
            before.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
            after.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
        )
    }


    @Test
    fun nativeBridgeAddsNormalizedSrtAsFreshSubtitleTrack() {
        val source = File(workDir, "standalone-srt-base.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("mkv/source.mkv")
            .use { input -> source.outputStream().use { output -> input.copyTo(output) } }

        val before = source.inputStream().use { MatroskaReader().scan(it) }
        val maxOriginalNumber = before.trackInfos.maxOf { it.number }
        val originalUids = before.trackInfos.mapNotNull { it.uid }.toSet()

        val srt = """
            1
            00:00:00,250 --> 00:00:02,250
            <i>SRT Android import</i>
        """.trimIndent()
        val normalized = normalizeStandaloneSubtitleTrackSource(
            sourceKind = ContainerTrackImportSourceKind.STANDALONE_SRT,
            raw = srt.toByteArray(Charsets.UTF_8),
        )
        val stagedAss = File(workDir, "normalized-from-srt.ass").apply {
            writeText(normalized.normalizedAssText, Charsets.UTF_8)
        }

        val output = File(workDir, "standalone-srt-updated.mkv")
        val tool = MkvGoTool(application)
        assertTrue("x86_64 emulator APK must package the mkvgo helper", tool.isAvailable())
        tool.editContainer(
            source = source,
            output = output,
            trackImports = listOf(
                TrackImportInput(
                    sourceKind = ContainerTrackImportSourceKind.STANDALONE_SRT,
                    source = stagedAss,
                    sourceSha256 = normalized.normalizedSha256,
                    name = "Imported SRT",
                    language = "eng",
                    isDefault = false,
                    isForced = false,
                )
            ),
        )

        val after = output.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(before.trackInfos.size + 1, after.trackInfos.size)
        assertEquals(
            before.trackPreservationSignature(),
            after.copy(trackInfos = after.trackInfos.take(before.trackInfos.size))
                .trackPreservationSignature(),
        )

        val added = after.trackInfos.last()
        assertEquals(MatroskaTrackKind.SUBTITLE, added.kind)
        assertEquals("S_TEXT/ASS", added.codecId)
        assertTrue(added.number > maxOriginalNumber)
        val addedUid = added.uid
        assertTrue(addedUid != null && addedUid !in originalUids)
        assertEquals("Imported SRT", added.name)
        assertEquals("eng", added.language)
        assertFalse(added.isDefault)
        assertFalse(added.isForced)

        val importedTrack = after.subtitleTracks.single { it.number == added.number }
        val importedAss = importedTrack.toAss()
        assertTrue(importedAss.contains("SRT Android import"))
        assertTrue(importedAss.contains("{\\i1}SRT Android import{\\i0}"))
        assertEquals(before.chapterCount, after.chapterCount)
        assertEquals(
            before.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
            after.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
        )
    }


    @Test
    fun nativeBridgeStreamCopiesRealMp3PacketsIntoFreshAudioTrack() = runBlocking {
        val source = File(workDir, "mp3-import-base.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("mkv/source.mkv")
            .use { input -> source.outputStream().use { output -> input.copyTo(output) } }

        val before = source.inputStream().use { MatroskaReader().scan(it) }
        val maxOriginalNumber = before.trackInfos.maxOf { it.number }
        val originalUids = before.trackInfos.mapNotNull { it.uid }.toSet()

        val mp3 = File(workDir, "silent-fixture.mp3").apply {
            writeBytes(Base64.decode(SILENT_MP3_BASE64, Base64.DEFAULT))
        }
        assertTrue(mp3.isFile && mp3.length() > 0L)

        val normalizedFile = File(workDir, "silent-fixture.awpkt")
        val normalized = AndroidMediaPacketNormalizer.normalizeMp3Track(
            context = application,
            uri = Uri.fromFile(mp3),
            extractorIndex = 0,
            output = normalizedFile,
        )
        assertEquals("audio/mpeg", normalized.mime)
        assertEquals(44_100, normalized.sampleRate)
        assertEquals(2, normalized.channelCount)
        assertTrue(normalized.packetCount > 0L)
        assertTrue(normalized.bundleSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(normalized.contentSha256.matches(Regex("[0-9a-f]{64}")))

        val output = File(workDir, "mp3-stream-copy-updated.mkv")
        val tool = MkvGoTool(application)
        assertTrue("x86_64 emulator APK must package the mkvgo helper", tool.isAvailable())
        tool.editContainer(
            source = source,
            output = output,
            trackImports = listOf(
                TrackImportInput(
                    sourceKind = ContainerTrackImportSourceKind.NORMALIZED_MEDIA_PACKETS,
                    source = normalized.file,
                    sourceSha256 = normalized.bundleSha256,
                    sourceCodecId = "A_MPEG/L3",
                    sampleRate = normalized.sampleRate,
                    channelCount = normalized.channelCount,
                    name = "Imported MP3",
                    language = "und",
                    isDefault = false,
                    isForced = false,
                )
            ),
        )

        val after = output.inputStream().use { MatroskaReader().scan(it) }
        assertEquals(before.trackInfos.size + 1, after.trackInfos.size)
        assertEquals(
            before.trackPreservationSignature(),
            after.copy(trackInfos = after.trackInfos.take(before.trackInfos.size))
                .trackPreservationSignature(),
        )

        val added = after.trackInfos.last()
        assertEquals(MatroskaTrackKind.AUDIO, added.kind)
        assertEquals("A_MPEG/L3", added.codecId)
        assertTrue(added.number > maxOriginalNumber)
        val addedUid = added.uid
        assertTrue(addedUid != null && addedUid !in originalUids)
        assertEquals("Imported MP3", added.name)
        assertEquals("und", added.language)
        assertFalse(added.isDefault)
        assertFalse(added.isForced)

        val digest = tool.digestTrackContent(output, added.number)
        assertEquals(normalized.contentSha256, digest.sha256)
        assertEquals(normalized.packetCount, digest.packetCount)
        assertEquals(0L, digest.firstTimecodeMs)

        assertEquals(before.chapterCount, after.chapterCount)
        assertEquals(
            before.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
            after.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
        )
    }


    private companion object {
        const val SILENT_MP3_BASE64 = "//tQZAAP8AAAaQAAAAgAAA0gAAABAAABpAAAACAAADSAAAAETEFNRVVVVUxBTUUzLjEwMFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVf/7UmRWD/AAAGkAAAAIAAANIAAAAQAAAaQAAAAgAAA0gAAABFVVVVVVVVVVTEFNRTMuMTAwVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV//tSZKkP8AAAaQAAAAgAAA0gAAABAAABpAAAACAAADSAAAAEVVVVVVVVVVVMQU1FMy4xMDBVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVX/+1JkqQ/wAABpAAAACAAADSAAAAEAAAGkAAAAIAAANIAAAARVVVVVVVVVVUxBTUUzLjEwMFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVf/7UmSpD/AAAGkAAAAIAAANIAAAAQAAAaQAAAAgAAA0gAAABFVVVVVVVVVVTEFNRTMuMTAwVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV//tSZKkP8AAAaQAAAAgAAA0gAAABAAABpAAAACAAADSAAAAEVVVVVVVVVVVMQU1FMy4xMDBVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVX/+1JkqQ/wAABpAAAACAAADSAAAAEAAAGkAAAAIAAANIAAAARVVVVVVVVVVUxBTUUzLjEwMFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVf/7UmSpD/AAAGkAAAAIAAANIAAAAQAAAaQAAAAgAAA0gAAABFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV//tSZKkP8AAAaQAAAAgAAA0gAAABAAABpAAAACAAADSAAAAEVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVU="
    }

}

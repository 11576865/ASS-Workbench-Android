package io.github.assworkbench.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.assworkbench.container.MatroskaReader
import io.github.assworkbench.container.MatroskaTrackKind
import java.io.File
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
        assertFalse(remaining.isDefault)
        assertTrue(remaining.isForced)
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
                    name = "Second video",
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
        assertFalse(imported.isDefault)
        assertFalse(imported.isForced)

        assertEquals(before.chapterCount, after.chapterCount)
        assertEquals(
            before.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
            after.attachmentInfos.map { listOf(it.uid, it.fileName, it.mimeType, it.sizeBytes) },
        )
    }
}

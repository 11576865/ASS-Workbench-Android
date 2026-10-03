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

}

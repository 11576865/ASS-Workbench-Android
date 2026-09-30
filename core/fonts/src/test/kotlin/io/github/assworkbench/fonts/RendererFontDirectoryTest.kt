package io.github.assworkbench.fonts

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RendererFontDirectoryTest {
    @Test
    fun publishesManualAndProjectFontsIntoOneStableDirectory() {
        val root = Files.createTempDirectory("asswb-font-union").toFile()
        try {
            val manual = File(root, "manual").apply { mkdirs() }
            val project = File(root, "project").apply { mkdirs() }
            val target = File(root, "renderer")
            File(manual, "Manual.ttf").writeBytes(byteArrayOf(1, 2, 3))
            File(project, "Project.otf").writeBytes(byteArrayOf(4, 5, 6))

            RendererFontDirectory.sync(target, listOf(manual, project))

            assertEquals(setOf("Manual.ttf", "Project.otf"), target.listFiles().orEmpty().map { it.name }.toSet())
            assertContentEquals(byteArrayOf(1, 2, 3), File(target, "Manual.ttf").readBytes())
            assertContentEquals(byteArrayOf(4, 5, 6), File(target, "Project.otf").readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun clearingProjectFontsRemovesOnlyProjectPublication() {
        val root = Files.createTempDirectory("asswb-font-union").toFile()
        try {
            val manual = File(root, "manual").apply { mkdirs() }
            val project = File(root, "project").apply { mkdirs() }
            val target = File(root, "renderer")
            File(manual, "Manual.ttf").writeBytes(byteArrayOf(1))
            File(project, "Project.ttf").writeBytes(byteArrayOf(2))
            RendererFontDirectory.sync(target, listOf(manual, project))

            File(project, "Project.ttf").delete()
            RendererFontDirectory.sync(target, listOf(manual, project))

            assertTrue(File(target, "Manual.ttf").isFile)
            assertFalse(File(target, "Project.ttf").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun laterSourceWinsFilenameCollisionAndUnsupportedFilesAreIgnored() {
        val root = Files.createTempDirectory("asswb-font-union").toFile()
        try {
            val manual = File(root, "manual").apply { mkdirs() }
            val project = File(root, "project").apply { mkdirs() }
            val target = File(root, "renderer")
            File(manual, "Same.ttf").writeBytes(byteArrayOf(1))
            File(project, "Same.ttf").writeBytes(byteArrayOf(9))
            File(project, "Notes.txt").writeText("not a font")

            RendererFontDirectory.sync(target, listOf(manual, project))

            assertContentEquals(byteArrayOf(9), File(target, "Same.ttf").readBytes())
            assertFalse(File(target, "Notes.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun replacingPublishedFontLeavesOnlyCompleteNewFile() {
        val root = Files.createTempDirectory("asswb-font-replace").toFile()
        try {
            val source = File(root, "manual").apply { mkdirs() }
            val target = File(root, "renderer")
            val font = File(source, "Updated.ttf")
            font.writeBytes(byteArrayOf(1, 2, 3))
            RendererFontDirectory.sync(target, listOf(source))

            font.writeBytes(byteArrayOf(4, 5, 6, 7))
            RendererFontDirectory.sync(target, listOf(source))

            assertContentEquals(font.readBytes(), File(target, font.name).readBytes())
            assertEquals(listOf(font.name), target.listFiles().orEmpty().map { it.name })
        } finally {
            root.deleteRecursively()
        }
    }
}

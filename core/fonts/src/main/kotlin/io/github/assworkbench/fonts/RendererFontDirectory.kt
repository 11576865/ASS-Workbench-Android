package io.github.assworkbench.fonts

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Publishes the union of several font source directories into one stable
 * mpv/libass directory. mpv's sub-fonts-dir accepts one non-recursive
 * directory, so switching between project and manual directories would hide
 * one source set from the renderer.
 */
object RendererFontDirectory {
    private val supportedExtensions = setOf("ttf", "otf")

    fun sync(
        targetDir: File,
        sourceDirs: List<File>,
    ) {
        targetDir.mkdirs()

        // Later source directories have priority on the extremely unlikely case
        // of an identical published filename with different content.
        val desired = linkedMapOf<String, File>()
        sourceDirs.forEach { dir ->
            dir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
                .sortedBy { it.name }
                .forEach { desired[it.name] = it }
        }

        desired.forEach { (name, source) ->
            val target = File(targetDir, name)
            if (!target.isFile || !sameBytes(source, target)) {
                atomicCopy(source, target)
            }
        }

        targetDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name !in desired }
            .forEach { runCatching { it.delete() } }
    }

    private fun sameBytes(a: File, b: File): Boolean {
        if (a.length() != b.length()) return false
        FileInputStream(a).use { left ->
            FileInputStream(b).use { right ->
                val l = ByteArray(DEFAULT_BUFFER_SIZE)
                val r = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val ln = left.read(l)
                    val rn = right.read(r)
                    if (ln != rn) return false
                    if (ln < 0) return true
                    for (i in 0 until ln) {
                        if (l[i] != r[i]) return false
                    }
                }
            }
        }
    }

    private fun atomicCopy(source: File, target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp-" + System.nanoTime())
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (target.exists() && !target.delete()) {
                error("Unable to replace renderer font: " + target.absolutePath)
            }
            if (!tmp.renameTo(target)) {
                FileInputStream(tmp).use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}

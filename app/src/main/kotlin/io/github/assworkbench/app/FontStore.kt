package io.github.assworkbench.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.assworkbench.fonts.FontAsset
import io.github.assworkbench.fonts.FontMetadata
import io.github.assworkbench.fonts.OpenTypeNameReader
import java.io.File

class FontStore(private val context: Context) {
    val rootDir: File = File(context.filesDir, "ass-fonts").apply { mkdirs() }
    val mpvConfigDir: File = File(context.filesDir, "mpv").apply { mkdirs() }
    // mpv/libass scans config-dir/fonts even when Android has no system font provider.
    // Keep imported project fonts here so the UI registry and renderer consume the same files.
    val importedDir: File = File(mpvConfigDir, "fonts").apply { mkdirs() }

    fun listImported(): List<FontAsset> = importedDir.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
        .mapNotNull { file ->
            runCatching {
                val bytes = file.readBytes()
                FontAsset(file.name, OpenTypeNameReader.sha256(bytes), OpenTypeNameReader.read(bytes))
            }.getOrNull()
        }
        .sortedBy { it.metadata.family.lowercase() }

    fun import(uri: Uri): FontAsset {
        val originalName = queryName(uri) ?: "font.ttf"
        val ext = originalName.substringAfterLast('.', "ttf").lowercase().takeIf { it in setOf("ttf", "otf") } ?: "ttf"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取字体")
        val metadata = OpenTypeNameReader.read(bytes)
        val sha = OpenTypeNameReader.sha256(bytes)
        val safeStem = metadata.family.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "font" }
        val target = File(importedDir, "${safeStem}-${sha.take(10)}.$ext")
        if (!target.exists()) target.writeBytes(bytes)

        // libmpvKt documents subfont.ttf as the reliable fallback when sub-font-provider=none.
        // Point fallback at the most recently imported font so CJK glyphs do not fall back
        // to the bundled Latin-only Roboto copy.
        val fallback = File(mpvConfigDir, "subfont.ttf")
        fallback.writeBytes(bytes)

        return FontAsset(target.name, sha, metadata)
    }

    fun ensureFallbackFont(): FontMetadata? {
        val target = File(mpvConfigDir, "subfont.ttf")
        if (!target.exists()) {
            val source = listOf(
                File("/system/fonts/Roboto-Regular.ttf"),
                File("/system/fonts/NotoSans-Regular.ttf"),
            ).firstOrNull { it.isFile && it.canRead() }
            if (source != null) runCatching {
                source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
        return if (target.isFile) runCatching { OpenTypeNameReader.read(target.readBytes()) }.getOrNull() else null
    }

    private fun queryName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }
}

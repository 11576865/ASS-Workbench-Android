package io.github.assworkbench.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.assworkbench.fonts.FontAsset
import io.github.assworkbench.fonts.FontGlyphDiagnostic
import io.github.assworkbench.fonts.FontMetadata
import io.github.assworkbench.fonts.OpenTypeCmap
import io.github.assworkbench.fonts.OpenTypeNameReader
import java.io.File

class FontStore(private val context: Context) {
    val rootDir: File = File(context.filesDir, "ass-fonts").apply { mkdirs() }
    val mpvConfigDir: File = File(context.filesDir, "mpv").apply { mkdirs() }
    // mpv/libass scans config-dir/fonts even when Android has no system font provider.
    // Keep imported project fonts here so the UI registry and renderer consume the same files.
    val importedDir: File = File(mpvConfigDir, "fonts").apply { mkdirs() }

    init {
        writeFontconfig()
    }

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

    fun importEmbeddedFont(fileName: String, bytes: ByteArray): FontAsset? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("ttf", "otf")) return null
        val metadata = OpenTypeNameReader.read(bytes)
        val sha = OpenTypeNameReader.sha256(bytes)
        val safeStem = metadata.family.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "font" }
        val target = File(importedDir, "${safeStem}-${sha.take(10)}.$ext")
        if (!target.exists()) target.writeBytes(bytes)
        return FontAsset(target.name, sha, metadata)
    }

    fun glyphDiagnostic(family: String, text: String): FontGlyphDiagnostic? {
        val normalized = family.trim().lowercase()
        val asset = listImported().firstOrNull { candidate ->
            val names = candidate.metadata.aliases + candidate.metadata.family + candidate.metadata.rendererFamily +
                listOfNotNull(candidate.metadata.legacyFamily, candidate.metadata.fullName, candidate.metadata.postScriptName)
            names.any { it.trim().lowercase() == normalized }
        } ?: return FontGlyphDiagnostic(family, null, 0, emptyList())

        val file = File(importedDir, asset.fileName)
        if (!file.isFile) return FontGlyphDiagnostic(family, asset.metadata.family, 0, emptyList())
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return FontGlyphDiagnostic(family, asset.metadata.family, 0, emptyList())

        val cps = text.codePoints()
            .filter { cp -> !Character.isWhitespace(cp) && !Character.isISOControl(cp) }
            .distinct()
            .limit(512)
            .toArray()
            .toList()

        return FontGlyphDiagnostic(
            requestedFamily = family,
            matchedFamily = asset.metadata.family,
            checkedCodePoints = cps.size,
            missingCodePoints = OpenTypeCmap.missingCodePoints(bytes, cps, limit = 24),
        )
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

    private fun writeFontconfig() {
        val cacheDir = File(context.cacheDir, "fontconfig").apply { mkdirs() }
        val config = buildString {
            appendLine("<?xml version=\"1.0\"?>")
            appendLine("<!DOCTYPE fontconfig SYSTEM \"fonts.dtd\">")
            appendLine("<fontconfig>")
            appendLine("  <dir>/system/fonts</dir>")
            appendLine("  <dir>/product/fonts</dir>")
            appendLine("  <dir>/system_ext/fonts</dir>")
            appendLine("  <dir>" + xmlEscape(importedDir.absolutePath) + "</dir>")
            appendLine("  <cachedir>" + xmlEscape(cacheDir.absolutePath) + "</cachedir>")
            appendLine("  <alias>")
            appendLine("    <family>sans-serif</family>")
            appendLine("    <prefer><family>Roboto</family><family>Noto Sans</family></prefer>")
            appendLine("  </alias>")
            appendLine("  <alias>")
            appendLine("    <family>serif</family>")
            appendLine("    <prefer><family>Noto Serif</family></prefer>")
            appendLine("  </alias>")
            appendLine("  <alias>")
            appendLine("    <family>monospace</family>")
            appendLine("    <prefer><family>Droid Sans Mono</family><family>Noto Sans Mono</family></prefer>")
            appendLine("  </alias>")
            appendLine("</fontconfig>")
        }
        File(mpvConfigDir, "fonts.conf").writeText(config, Charsets.UTF_8)
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun queryName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }
}

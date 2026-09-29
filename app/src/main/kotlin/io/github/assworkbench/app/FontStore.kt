package io.github.assworkbench.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.assworkbench.fonts.AndroidFontconfigPolicy
import io.github.assworkbench.fonts.FontAsset
import io.github.assworkbench.fonts.FontGlyphDiagnostic
import io.github.assworkbench.fonts.FontMetadata
import io.github.assworkbench.fonts.FontOrigin
import io.github.assworkbench.fonts.FontconfigPrepared
import io.github.assworkbench.fonts.OpenTypeCmap
import io.github.assworkbench.fonts.OpenTypeNameReader
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class FontImportBatchResult(
    val assets: List<FontAsset>,
    val failures: List<String>,
)

class FontStore(private val context: Context) {
    val rootDir: File = File(context.filesDir, "ass-fonts").apply { mkdirs() }
    val mpvConfigDir: File = File(context.filesDir, "mpv").apply { mkdirs() }
    // mpv/libass scans config-dir/fonts even when Android has no system font provider.
    // Keep imported project fonts here so the UI registry and renderer consume the same files.
    val importedDir: File = File(mpvConfigDir, "fonts").apply { mkdirs() }
    val projectFontDir: File = File(mpvConfigDir, "project-fonts").apply { mkdirs() }

    private val fontconfigCacheRoot: File = File(context.cacheDir, "fontconfig").apply { mkdirs() }

    lateinit var fontconfigPrepared: FontconfigPrepared
        private set

    @Volatile
    private var importedCache: List<FontAsset>? = null
    private val fontBytesCache = ConcurrentHashMap<String, ByteArray>()

    init {
        refreshFontconfig(pruneOldCaches = false)
    }

    fun listImported(): List<FontAsset> {
        importedCache?.let { return it }
        val scanned = (projectFontDir.listFiles()?.toList().orEmpty() + importedDir.listFiles()?.toList().orEmpty())
            .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
            .distinctBy { it.absolutePath }
            .mapNotNull { file ->
                runCatching {
                    val cacheKey = file.absolutePath
                    val bytes = fontBytesCache.computeIfAbsent(cacheKey) { file.readBytes() }
                    FontAsset(
                        fileName = file.name,
                        sha256 = OpenTypeNameReader.sha256(bytes),
                        metadata = OpenTypeNameReader.read(bytes),
                        origin = if (file.parentFile?.absolutePath == projectFontDir.absolutePath) {
                            FontOrigin.MKV_ATTACHMENT
                        } else {
                            FontOrigin.MANUAL
                        },
                    )
                }.getOrNull()
            }
            .sortedWith(compareBy<FontAsset> { it.metadata.family.lowercase() }.thenBy { it.sha256 })
        importedCache = scanned
        return scanned
    }

    private fun invalidateImportedCache() {
        importedCache = null
    }

    fun import(uri: Uri): FontAsset {
        // Live imports are consumed through mpv/libass sub-fonts-dir.
        // Avoid rebuilding native Fontconfig caches during an active editing session.
        return importOne(uri)
    }

    fun importAll(uris: List<Uri>): FontImportBatchResult {
        if (uris.isEmpty()) return FontImportBatchResult(emptyList(), emptyList())
        val assets = mutableListOf<FontAsset>()
        val failures = mutableListOf<String>()
        uris.forEach { uri ->
            runCatching { importOne(uri) }
                .onSuccess(assets::add)
                .onFailure { error ->
                    failures += (queryName(uri) ?: uri.lastPathSegment ?: "font") +
                        "：" + (error.message ?: error::class.java.simpleName)
                }
        }
        // Renderer reload is handled by the preview through sub-fonts-dir + sub-reload.
        // Native Fontconfig cache rebuild remains an explicit diagnostics action.
        return FontImportBatchResult(assets, failures)
    }

    private fun importOne(uri: Uri): FontAsset {
        val originalName = queryName(uri) ?: "font.ttf"
        val ext = originalName.substringAfterLast('.', "ttf").lowercase().takeIf { it in setOf("ttf", "otf") } ?: "ttf"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取字体")
        val metadata = OpenTypeNameReader.read(bytes)
        val sha = OpenTypeNameReader.sha256(bytes)
        val safeStem = safeFileStem(metadata.family)
        val target = File(importedDir, "${safeStem}-${sha.take(10)}.$ext")
        if (!target.exists()) atomicWrite(target, bytes)
        fontBytesCache[target.absolutePath] = bytes
        invalidateImportedCache()

        // Do not replace the live fallback file while libass is active.
        // Imported fonts are discovered through the stable fonts directory.
        return FontAsset(target.name, sha, metadata, FontOrigin.MANUAL)
    }

    fun importEmbeddedFont(fileName: String, bytes: ByteArray): FontAsset? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("ttf", "otf")) return null
        val metadata = OpenTypeNameReader.read(bytes)
        val sha = OpenTypeNameReader.sha256(bytes)
        val safeStem = safeFileStem(metadata.family)
        val target = File(projectFontDir, "${safeStem}-${sha.take(10)}.$ext")
        if (!target.exists()) atomicWrite(target, bytes)
        fontBytesCache[target.absolutePath] = bytes
        invalidateImportedCache()
        // Keep the fallback file immutable during an active renderer session.
        return FontAsset(target.name, sha, metadata, FontOrigin.MKV_ATTACHMENT)
    }

    fun rebuildFontconfigCache(): FontconfigPrepared {
        fontconfigCacheRoot.deleteRecursively()
        fontconfigCacheRoot.mkdirs()
        return refreshFontconfig(pruneOldCaches = true)
    }

    fun refreshFontconfig(pruneOldCaches: Boolean): FontconfigPrepared {
        val prepared = AndroidFontconfigPolicy.prepare(
            configDir = mpvConfigDir,
            importedDir = importedDir,
            cacheRoot = fontconfigCacheRoot,
            priorityFontDirs = listOf(projectFontDir),
            environmentFingerprint = android.os.Build.FINGERPRINT,
            pruneOldCaches = pruneOldCaches,
        )
        fontconfigPrepared = prepared
        return prepared
    }

    fun glyphDiagnostic(family: String, text: String): FontGlyphDiagnostic? {
        val normalized = family.trim().lowercase()
        val asset = listImported().firstOrNull { candidate ->
            val names = candidate.metadata.aliases + candidate.metadata.family + candidate.metadata.rendererFamily +
                listOfNotNull(candidate.metadata.legacyFamily, candidate.metadata.fullName, candidate.metadata.postScriptName)
            names.any { it.trim().lowercase() == normalized }
        } ?: return FontGlyphDiagnostic(family, null, 0, emptyList())

        val file = listOf(
            File(projectFontDir, asset.fileName),
            File(importedDir, asset.fileName),
        ).firstOrNull { it.isFile }
            ?: return FontGlyphDiagnostic(family, asset.metadata.family, 0, emptyList())
        val bytes = runCatching {
            fontBytesCache.computeIfAbsent(file.absolutePath) { file.readBytes() }
        }.getOrNull() ?: return FontGlyphDiagnostic(family, asset.metadata.family, 0, emptyList())

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

    fun clearProjectFonts(refresh: Boolean = true) {
        projectFontDir.listFiles().orEmpty().forEach { runCatching { it.delete() } }
        fontBytesCache.keys.removeAll { it.startsWith(projectFontDir.absolutePath) }
        invalidateImportedCache()
        if (refresh) refreshFontconfig(pruneOldCaches = true)
    }

    fun activeRendererFontsDir(): File =
        if (projectFontDir.listFiles().orEmpty().any { it.isFile }) projectFontDir else importedDir

    fun ensureFallbackFont(): FontMetadata? {
        val target = File(mpvConfigDir, "subfont.ttf")
        if (!target.exists()) {
            val systemFonts = File("/system/fonts").listFiles().orEmpty().filter { it.isFile && it.canRead() }
            val source = listOf(
                "NotoSansCJK-Regular.ttc",
                "NotoSansSC-Regular.otf",
                "NotoSansSC-Regular.ttf",
                "NotoSansCJKsc-Regular.otf",
                "DroidSansFallback.ttf",
                "NotoSans-Regular.ttf",
                "Roboto-Regular.ttf",
            ).asSequence()
                .map { name -> File("/system/fonts", name) }
                .firstOrNull { it.isFile && it.canRead() }
                ?: systemFonts.firstOrNull { file ->
                    val n = file.name.lowercase()
                    (n.contains("cjk") || n.contains("hans") || n.contains("sc") || n.contains("fallback")) &&
                        file.extension.lowercase() in setOf("ttf", "otf", "ttc")
                }
            if (source != null) runCatching {
                source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
        return if (target.isFile) {
            runCatching { OpenTypeNameReader.read(target.readBytes()) }.getOrNull()
                ?: FontMetadata(
                    family = "Android system fallback",
                    rendererFamily = "sans-serif",
                    aliases = setOf("sans-serif"),
                )
        } else null
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp-" + System.nanoTime())
        try {
            java.io.FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!temp.renameTo(target)) {
                java.io.FileInputStream(temp).use { input ->
                    java.io.FileOutputStream(target).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun safeFileStem(value: String): String {
        val out = StringBuilder(value.length)
        var previousUnderscore = false
        value.forEach { ch ->
            val keep = ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_'
            val next = if (keep) ch else '_'
            if (next == '_') {
                if (!previousUnderscore) out.append(next)
                previousUnderscore = true
            } else {
                out.append(next)
                previousUnderscore = false
            }
        }
        return out.toString().trim('_').ifBlank { "font" }
    }

    private fun queryName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }
}

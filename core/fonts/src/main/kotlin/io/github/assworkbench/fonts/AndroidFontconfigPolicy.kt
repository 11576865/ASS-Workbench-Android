package io.github.assworkbench.fonts

import java.io.File
import java.security.MessageDigest

data class FontconfigPrepared(
    val configFile: File,
    val cacheDir: File,
    val fingerprint: String,
    val configText: String,
)

object AndroidFontconfigPolicy {
    const val SCHEMA_VERSION: Int = 1
    private const val CACHE_PREFIX = "fontconfig-v1-"

    val defaultSystemFontDirs: List<String> = listOf(
        "/system/fonts/",
        "/product/fonts/",
        "/system_ext/fonts/",
    )

    fun prepare(
        configDir: File,
        importedDir: File,
        cacheRoot: File,
        systemFontDirs: List<String> = defaultSystemFontDirs,
        environmentFingerprint: String = "",
        pruneOldCaches: Boolean = false,
    ): FontconfigPrepared {
        configDir.mkdirs()
        importedDir.mkdirs()
        cacheRoot.mkdirs()

        val fingerprint = fingerprint(importedDir, environmentFingerprint)
        val cacheDir = File(cacheRoot, CACHE_PREFIX + fingerprint.take(12)).apply { mkdirs() }
        val configText = render(importedDir, cacheDir, systemFontDirs)
        val configFile = File(configDir, "fonts.conf")
        atomicWrite(configFile, configText)

        if (pruneOldCaches) {
            cacheRoot.listFiles()
                .orEmpty()
                .filter { it.isDirectory && it.name.startsWith(CACHE_PREFIX) && it != cacheDir }
                .forEach { runCatching { it.deleteRecursively() } }
        }

        return FontconfigPrepared(
            configFile = configFile,
            cacheDir = cacheDir,
            fingerprint = fingerprint,
            configText = configText,
        )
    }

    fun render(
        importedDir: File,
        cacheDir: File,
        systemFontDirs: List<String> = defaultSystemFontDirs,
    ): String = buildString {
        appendLine("<fontconfig>")
        systemFontDirs
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .forEach { appendLine("  <dir>" + xmlEscape(it) + "</dir>") }
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

    fun fingerprint(importedDir: File, environmentFingerprint: String = ""): String {
        val descriptor = buildString {
            append("schema=").append(SCHEMA_VERSION).append('\n')
            append("environment=").append(environmentFingerprint).append('\n')
            importedDir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "ttc", "otc") }
                .sortedBy { it.name }
                .forEach { file ->
                    append(file.name)
                        .append(':')
                        .append(file.length())
                        .append(':')
                        .append(file.lastModified())
                        .append('\n')
                }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(descriptor.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            error("Unable to replace ${target.absolutePath}")
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

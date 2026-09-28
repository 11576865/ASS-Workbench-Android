package io.github.assworkbench.fonts

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AndroidFontconfigPolicyTest {
    @Test
    fun renderIncludesSystemProjectAndVersionedCacheDirectories() {
        val root = createTempDirectory("fontconfig-policy").toFile()
        val config = File(root, "config")
        val fonts = File(root, "fonts & project").apply { mkdirs() }
        val cache = File(root, "cache")

        val prepared = AndroidFontconfigPolicy.prepare(
            configDir = config,
            importedDir = fonts,
            cacheRoot = cache,
            systemFontDirs = listOf("/system/fonts/", "/product/fonts/"),
        )

        assertTrue(prepared.configFile.isFile)
        assertTrue(prepared.cacheDir.isDirectory)
        assertTrue(prepared.configText.contains("<dir>/system/fonts/</dir>"))
        assertTrue(prepared.configText.contains("<dir>/product/fonts/</dir>"))
        assertTrue(prepared.configText.contains("fonts &amp; project"))
        assertTrue(prepared.configText.contains("<cachedir>"))
        assertFalse(prepared.configText.contains("<!DOCTYPE"))
    }

    @Test
    fun fingerprintChangesWhenProjectFontSetChanges() {
        val root = createTempDirectory("fontconfig-fingerprint").toFile()
        val fonts = File(root, "fonts").apply { mkdirs() }

        val empty = AndroidFontconfigPolicy.fingerprint(fonts, "os-a")
        File(fonts, "A.ttf").writeBytes(byteArrayOf(1, 2, 3))
        val first = AndroidFontconfigPolicy.fingerprint(fonts, "os-a")
        File(fonts, "B.otf").writeBytes(byteArrayOf(4, 5))
        val second = AndroidFontconfigPolicy.fingerprint(fonts, "os-a")
        val osChanged = AndroidFontconfigPolicy.fingerprint(fonts, "os-b")

        assertNotEquals(empty, first)
        assertNotEquals(first, second)
        assertNotEquals(second, osChanged)
        assertEquals(second, AndroidFontconfigPolicy.fingerprint(fonts, "os-a"))
    }

    @Test
    fun pruneOldCachesKeepsOnlyCurrentFingerprintDirectory() {
        val root = createTempDirectory("fontconfig-prune").toFile()
        val config = File(root, "config")
        val fonts = File(root, "fonts").apply { mkdirs() }
        val cache = File(root, "cache")

        val first = AndroidFontconfigPolicy.prepare(config, fonts, cache)
        File(fonts, "A.ttf").writeBytes(byteArrayOf(1))
        val second = AndroidFontconfigPolicy.prepare(
            configDir = config,
            importedDir = fonts,
            cacheRoot = cache,
            pruneOldCaches = true,
        )

        assertNotEquals(first.cacheDir, second.cacheDir)
        assertFalse(first.cacheDir.exists())
        assertTrue(second.cacheDir.isDirectory)
    }
}

package io.github.assworkbench.fonts

import kotlin.test.Test
import kotlin.test.assertEquals

class FontDiagnosticsTest {
    @Test
    fun exactAliasWinsOverFallback() {
        val asset = FontAsset(
            fileName = "demo.ttf",
            sha256 = "x",
            metadata = FontMetadata(family = "Demo Sans", aliases = setOf("Demo Sans", "演示黑体")),
        )
        val result = FontDiagnostics.diagnose(listOf("演示黑体", "Missing"), listOf(asset), "Roboto")
        assertEquals(FontMatchStatus.EXACT_IMPORTED, result[0].status)
        assertEquals(FontMatchStatus.FALLBACK_ONLY, result[1].status)
    }
}

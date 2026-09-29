package io.github.assworkbench.fonts

import kotlin.test.Test
import kotlin.test.assertEquals

class FontDiagnosticsTest {
    @Test
    fun rendererAliasIsExactButTypographicOnlyAliasIsMetadataAlias() {
        val asset = FontAsset(
            fileName = "demo.ttf",
            sha256 = "x",
            metadata = FontMetadata(
                family = "Demo Sans Display",
                rendererFamily = "Demo Sans",
                legacyFamily = "Demo Sans",
                typographicFamily = "Demo Sans Display",
                fullName = "Demo Sans Regular",
                postScriptName = "DemoSans-Regular",
                aliases = setOf("Demo Sans", "Demo Sans Display", "Demo Sans Regular", "DemoSans-Regular"),
                rendererAliases = setOf("Demo Sans", "Demo Sans Regular", "DemoSans-Regular"),
            ),
        )

        val result = FontDiagnostics.diagnose(
            listOf("Demo Sans", "Demo Sans Display", "Missing"),
            listOf(asset),
            "Roboto",
        )

        assertEquals(FontMatchStatus.EXACT_IMPORTED, result[0].status)
        assertEquals("Demo Sans", result[0].matchedFamily)
        assertEquals(FontMatchStatus.METADATA_ALIAS, result[1].status)
        assertEquals("Demo Sans", result[1].matchedFamily)
        assertEquals(FontMatchStatus.FALLBACK_ONLY, result[2].status)
    }
    @Test
    fun attachmentUsageMatchingAcceptsAliasesButRejectsUnrelatedFamilies() {
        val asset = FontAsset(
            fileName = "demo.ttf",
            sha256 = "x",
            metadata = FontMetadata(
                family = "Demo Sans Display",
                rendererFamily = "Demo Sans",
                typographicFamily = "Demo Sans Display",
                aliases = setOf("Demo Sans", "Demo Sans Display"),
                rendererAliases = setOf("Demo Sans"),
            ),
            origin = FontOrigin.MKV_ATTACHMENT,
        )

        assertEquals(true, FontDiagnostics.matchesRequestedFamily(asset, listOf("Demo Sans")))
        assertEquals(true, FontDiagnostics.matchesRequestedFamily(asset, listOf("Demo Sans Display")))
        assertEquals(false, FontDiagnostics.matchesRequestedFamily(asset, listOf("Other Face")))
    }
}

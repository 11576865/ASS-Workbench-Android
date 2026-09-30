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


class FontPackagingPlannerTest {
    private fun asset(
        sha: String,
        family: String,
        origin: FontOrigin,
        fileName: String = family.replace(" ", "") + ".ttf",
    ) = FontAsset(
        fileName = fileName,
        sha256 = sha,
        metadata = FontMetadata(
            family = family,
            rendererFamily = family,
            aliases = setOf(family),
            rendererAliases = setOf(family),
        ),
        origin = origin,
    )

    @Test
    fun identicalShaAlreadyEmbeddedIsNotPackageableEvenWithDifferentFilename() {
        val assets = listOf(
            asset("same-sha", "Demo Sans", FontOrigin.MKV_ATTACHMENT, "embedded.ttf"),
            asset("same-sha", "Demo Sans", FontOrigin.MANUAL, "renamed-local.ttf"),
        )
        assertEquals(emptySet(), FontPackagingPlanner.packageableShas(assets))
        assertEquals(emptySet(), FontPackagingPlanner.selectRequested(assets, listOf("Demo Sans")))
    }

    @Test
    fun requestedManualFaceIsSelectedButUnrelatedManualFaceIsNot() {
        val assets = listOf(
            asset("wanted", "Wanted Sans", FontOrigin.MANUAL),
            asset("other", "Other Sans", FontOrigin.MANUAL),
        )
        assertEquals(setOf("wanted"), FontPackagingPlanner.selectRequested(assets, listOf("Wanted Sans")))
    }

    @Test
    fun embeddedDifferentShaDoesNotBlockManualRequestedFace() {
        val assets = listOf(
            asset("embedded-a", "Old Face", FontOrigin.MKV_ATTACHMENT, "Face.ttf"),
            asset("manual-b", "Wanted Face", FontOrigin.MANUAL, "Face.ttf"),
        )
        assertEquals(setOf("manual-b"), FontPackagingPlanner.packageableShas(assets))
        assertEquals(setOf("manual-b"), FontPackagingPlanner.selectRequested(assets, listOf("Wanted Face")))
    }
}

package io.github.assworkbench.fonts

data class FontMetadata(
    /** Human-facing family. Prefer typographic family (name ID 16) when present. */
    val family: String,
    /**
     * Name to write into ASS for the current no-system-provider libass path.
     * libass's direct FreeType metadata path indexes legacy family/full/PostScript names,
     * not the typographic family as its primary family key.
     */
    val rendererFamily: String = family,
    val legacyFamily: String? = null,
    val typographicFamily: String? = null,
    val subfamily: String? = null,
    val fullName: String? = null,
    val postScriptName: String? = null,
    val aliases: Set<String> = emptySet(),
    val rendererAliases: Set<String> = emptySet(),
)

enum class FontOrigin { MANUAL, MKV_ATTACHMENT, UNKNOWN }

data class FontAsset(
    val fileName: String,
    val sha256: String,
    val metadata: FontMetadata,
    val origin: FontOrigin = FontOrigin.UNKNOWN,
    /** TTC/OTC faces. Empty for ordinary single-face TTF/OTF. */
    val collectionFaces: List<FontMetadata> = emptyList(),
    /** Absolute sfnt offsets in the collection; aligned with collectionFaces. */
    val collectionFaceOffsets: List<Int> = emptyList(),
)

enum class FontMatchStatus { EXACT_IMPORTED, METADATA_ALIAS, FALLBACK_ONLY, MISSING }

data class FontDiagnostic(
    val requestedFamily: String,
    val status: FontMatchStatus,
    val matchedFamily: String? = null,
)

object FontDiagnostics {
    private fun faces(asset: FontAsset): List<FontMetadata> =
        if (asset.collectionFaces.isEmpty()) listOf(asset.metadata) else asset.collectionFaces

    private fun rendererNames(face: FontMetadata): Set<String> =
        (face.rendererAliases + face.rendererFamily +
            listOfNotNull(face.legacyFamily, face.fullName, face.postScriptName))
            .filter { it.isNotBlank() }
            .map { it.trim().lowercase() }
            .toSet()

    private fun allNames(face: FontMetadata): Set<String> =
        (rendererNames(face) + face.aliases + face.family +
            listOfNotNull(face.typographicFamily))
            .filter { it.isNotBlank() }
            .map { it.trim().lowercase() }
            .toSet()

    fun diagnose(
        requestedFamilies: Collection<String>,
        imported: Collection<FontAsset>,
        fallbackFamily: String?,
    ): List<FontDiagnostic> {
        data class Match(val rendererFamily: String, val rendererExact: Boolean)
        val names = buildMap<String, Match> {
            imported.forEach { asset ->
                faces(asset).forEach { face ->
                    rendererNames(face).forEach { name ->
                        put(name, Match(face.rendererFamily, rendererExact = true))
                    }
                    allNames(face).forEach { name ->
                        putIfAbsent(name, Match(face.rendererFamily, rendererExact = false))
                    }
                }
            }
        }

        return requestedFamilies
            .filter { it.isNotBlank() }
            .distinctBy { it.trim().lowercase() }
            .map { requested ->
                val match = names[requested.trim().lowercase()]
                when {
                    match?.rendererExact == true ->
                        FontDiagnostic(requested, FontMatchStatus.EXACT_IMPORTED, match.rendererFamily)
                    match != null ->
                        FontDiagnostic(requested, FontMatchStatus.METADATA_ALIAS, match.rendererFamily)
                    !fallbackFamily.isNullOrBlank() ->
                        FontDiagnostic(requested, FontMatchStatus.FALLBACK_ONLY, fallbackFamily)
                    else ->
                        FontDiagnostic(requested, FontMatchStatus.MISSING)
                }
            }
    }

    fun matchesRequestedFamily(
        asset: FontAsset,
        requestedFamilies: Collection<String>,
    ): Boolean {
        val requested = requestedFamilies.asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (requested.isEmpty()) return false
        return faces(asset).any { face -> allNames(face).any { it in requested } }
    }
}

object FontPackagingPlanner {
    fun packageableShas(assets: Collection<FontAsset>): Set<String> {
        val embedded = assets.asSequence()
            .filter { it.origin == FontOrigin.MKV_ATTACHMENT }
            .map { it.sha256 }
            .toSet()
        return assets.asSequence()
            .filter { it.origin == FontOrigin.MANUAL && it.sha256 !in embedded }
            .map { it.sha256 }
            .toSet()
    }

    fun selectRequested(
        assets: Collection<FontAsset>,
        requestedFamilies: Collection<String>,
    ): Set<String> {
        val packageable = packageableShas(assets)
        return assets.asSequence()
            .filter { it.sha256 in packageable }
            .filter { FontDiagnostics.matchesRequestedFamily(it, requestedFamilies) }
            .map { it.sha256 }
            .toSet()
    }
}

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
    /** All useful OpenType aliases, including typographic family. */
    val aliases: Set<String> = emptySet(),
    /** Names libass can directly derive from the face in the current provider=none path. */
    val rendererAliases: Set<String> = emptySet(),
)

enum class FontOrigin {
    MANUAL,
    MKV_ATTACHMENT,
    UNKNOWN,
}

data class FontAsset(
    val fileName: String,
    val sha256: String,
    val metadata: FontMetadata,
    val origin: FontOrigin = FontOrigin.UNKNOWN,
)

enum class FontMatchStatus {
    EXACT_IMPORTED,
    METADATA_ALIAS,
    FALLBACK_ONLY,
    MISSING,
}

data class FontDiagnostic(
    val requestedFamily: String,
    val status: FontMatchStatus,
    val matchedFamily: String? = null,
)

object FontDiagnostics {
    fun diagnose(
        requestedFamilies: Collection<String>,
        imported: Collection<FontAsset>,
        fallbackFamily: String?,
    ): List<FontDiagnostic> {
        data class Match(val rendererFamily: String, val rendererExact: Boolean)

        val names = buildMap<String, Match> {
            imported.forEach { asset ->
                val metadata = asset.metadata
                val rendererNames = (
                    metadata.rendererAliases +
                        metadata.rendererFamily +
                        listOfNotNull(metadata.legacyFamily, metadata.fullName, metadata.postScriptName)
                    )
                    .filter { it.isNotBlank() }
                    .map { it.trim().lowercase() }
                    .toSet()

                rendererNames.forEach { name ->
                    put(name, Match(metadata.rendererFamily, rendererExact = true))
                }

                (metadata.aliases + metadata.family + listOfNotNull(metadata.typographicFamily))
                    .filter { it.isNotBlank() }
                    .forEach { alias ->
                        putIfAbsent(alias.trim().lowercase(), Match(metadata.rendererFamily, rendererExact = false))
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

    /**
     * Conservative inventory check used for "possibly unused attachment" reporting.
     * Any renderer or metadata alias match counts as requested; this intentionally
     * avoids declaring sibling font files unused when libass may resolve through aliases.
     */
    fun matchesRequestedFamily(
        asset: FontAsset,
        requestedFamilies: Collection<String>,
    ): Boolean {
        val requested = requestedFamilies.asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (requested.isEmpty()) return false

        val metadata = asset.metadata
        val names = (
            metadata.rendererAliases +
                metadata.aliases +
                metadata.family +
                metadata.rendererFamily +
                listOfNotNull(
                    metadata.legacyFamily,
                    metadata.typographicFamily,
                    metadata.fullName,
                    metadata.postScriptName,
                )
            )
            .asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        return names.any { it in requested }
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

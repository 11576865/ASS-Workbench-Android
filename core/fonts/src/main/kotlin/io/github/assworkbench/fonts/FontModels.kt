package io.github.assworkbench.fonts

data class FontMetadata(
    val family: String,
    val subfamily: String? = null,
    val fullName: String? = null,
    val postScriptName: String? = null,
    val aliases: Set<String> = emptySet(),
)

data class FontAsset(
    val fileName: String,
    val sha256: String,
    val metadata: FontMetadata,
)

enum class FontMatchStatus {
    EXACT_IMPORTED,
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
        val aliases = buildMap<String, String> {
            imported.forEach { asset ->
                (asset.metadata.aliases + asset.metadata.family + listOfNotNull(asset.metadata.fullName, asset.metadata.postScriptName))
                    .filter { it.isNotBlank() }
                    .forEach { alias -> put(alias.trim().lowercase(), asset.metadata.family) }
            }
        }
        return requestedFamilies
            .filter { it.isNotBlank() }
            .distinctBy { it.trim().lowercase() }
            .map { requested ->
                val match = aliases[requested.trim().lowercase()]
                when {
                    match != null -> FontDiagnostic(requested, FontMatchStatus.EXACT_IMPORTED, match)
                    !fallbackFamily.isNullOrBlank() -> FontDiagnostic(requested, FontMatchStatus.FALLBACK_ONLY, fallbackFamily)
                    else -> FontDiagnostic(requested, FontMatchStatus.MISSING)
                }
            }
    }
}

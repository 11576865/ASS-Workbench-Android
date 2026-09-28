package io.github.assworkbench.fonts

data class FontGlyphDiagnostic(
    val requestedFamily: String,
    val matchedFamily: String?,
    val checkedCodePoints: Int,
    val missingCodePoints: List<Int>,
) {
    val exactCoverage: Boolean get() = matchedFamily != null && missingCodePoints.isEmpty()
    val missingSampleText: String get() = missingCodePoints.joinToString("") { cp ->
        runCatching { String(Character.toChars(cp)) }.getOrDefault("�")
    }
}

package io.github.assworkbench.domain

object FontBindingRewriter {
    private val explicitFont = Regex("""\\fn([^\\}]*)""", RegexOption.IGNORE_CASE)

    /**
     * Compatibility mode matching the established MKV-Fast-Muxer behavior:
     * every Style Fontname and every non-empty explicit inline \fn request are
     * rewritten to one imported font family. Empty \fn resets are preserved.
     */
    fun forceFamily(document: AssDocument, family: String): AssDocument {
        val target = family.trim()
        if (target.isEmpty()) return document

        return document.copy(
            styles = document.styles.map { style ->
                if (style.fontName == target) style else style.copy(fontName = target)
            },
            events = document.events.map { event ->
                val rewritten = explicitFont.replace(event.text) { match ->
                    val requested = match.groupValues.getOrNull(1).orEmpty()
                    if (requested.trim().isEmpty()) match.value else "\\fn$target"
                }
                if (rewritten == event.text) event else event.copy(text = rewritten)
            },
        )
    }

    fun explicitFamilies(document: AssDocument): Set<String> =
        buildSet {
            document.events.forEach { event ->
                explicitFont.findAll(event.text).forEach { match ->
                    val requested = match.groupValues.getOrNull(1).orEmpty().trim()
                    if (requested.isNotEmpty()) add(requested)
                }
            }
        }
}

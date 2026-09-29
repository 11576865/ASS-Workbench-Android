package io.github.assworkbench.domain

object FontBindingRewriter {
    private val overrideBlock = Regex("""\{[^}]*\}""")
    private val explicitFont = Regex("""\\fn([^\\}]*)""", RegexOption.IGNORE_CASE)
    private val styleReset = Regex("""\\r([^\\}]*)""", RegexOption.IGNORE_CASE)

    /**
     * Compatibility mode matching the established MKV-Fast-Muxer behavior:
     * every Style Fontname and every non-empty explicit inline \fn request inside
     * ASS override blocks are rewritten to one imported renderer family.
     * Empty \fn resets and literal dialogue text are preserved.
     */
    fun forceFamily(document: AssDocument, family: String): AssDocument {
        val target = family.trim()
        if (target.isEmpty()) return document

        return document.copy(
            styles = document.styles.map { style ->
                if (style.fontName == target) style else style.copy(fontName = target)
            },
            events = document.events.map { event ->
                val rewritten = overrideBlock.replace(event.text) { block ->
                    explicitFont.replace(block.value) { match ->
                        val requested = match.groupValues.getOrNull(1).orEmpty()
                        if (requested.trim().isEmpty()) match.value else "\\fn$target"
                    }
                }
                if (rewritten == event.text) event else event.copy(text = rewritten)
            },
        )
    }

    fun explicitFamilies(document: AssDocument): Set<String> =
        buildSet {
            document.events.forEach { event ->
                overrideBlock.findAll(event.text).forEach { block ->
                    explicitFont.findAll(block.value).forEach { match ->
                        val requested = match.groupValues.getOrNull(1).orEmpty().trim()
                        if (requested.isNotEmpty()) add(requested)
                    }
                }
            }
        }

    /**
     * Styles that can actually become active for at least one Event:
     * the Event's base Style plus explicit inline \rStyle resets.
     * Bare \r resets are already covered by the Event's base Style.
     */
    fun referencedStyleNames(document: AssDocument): Set<String> =
        buildSet {
            document.events.forEach { event ->
                event.style.trim().takeIf { it.isNotEmpty() }?.let(::add)
                overrideBlock.findAll(event.text).forEach { block ->
                    styleReset.findAll(block.value).forEach { match ->
                        val requested = match.groupValues.getOrNull(1).orEmpty().trim()
                        if (requested.isNotEmpty()) add(requested)
                    }
                }
            }
        }

    /**
     * Font families explicitly requested by the current subtitle content.
     * Unused Style definitions do not make a font "required".
     */
    fun requestedFamilies(document: AssDocument): Set<String> {
        val stylesByName = document.styles.associateBy { it.name.trim().lowercase() }
        return buildSet {
            referencedStyleNames(document).forEach { styleName ->
                stylesByName[styleName.trim().lowercase()]
                    ?.fontName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::add)
            }
            addAll(explicitFamilies(document))
        }
    }
}

package io.github.assworkbench.domain

enum class FontRequestSource { STYLE, INLINE_FONT, STYLE_RESET }
data class FontRequirement(val family: String, val sources: Set<FontRequestSource>, val eventIds: Set<Long>)

object FontRequirementResolver {
    private val overrideBlock = Regex("""\{[^}]*\}""")
    private val font = Regex("""\\fn([^\\}]*)""", RegexOption.IGNORE_CASE)
    private val reset = Regex("""\\r([^\\}]*)""", RegexOption.IGNORE_CASE)

    fun resolve(document: AssDocument): List<FontRequirement> {
        data class MutableReq(val sources: MutableSet<FontRequestSource> = linkedSetOf(), val eventIds: MutableSet<Long> = linkedSetOf())
        val map = linkedMapOf<String, Pair<String, MutableReq>>()
        val styles = document.styles.associateBy { it.name }
        fun add(family: String, source: FontRequestSource, eventId: Long) {
            val displayFamily = family.trim()
            if (displayFamily.isEmpty()) return
            val normalized = displayFamily.lowercase()
            val (_, req) = map.getOrPut(normalized) { displayFamily to MutableReq() }
            req.sources += source
            req.eventIds += eventId
        }
        document.events.filterNot { it.comment }.forEach { event ->
            styles[event.style]?.fontName?.let { add(it, FontRequestSource.STYLE, event.id) }
            overrideBlock.findAll(event.text).forEach { block ->
                font.findAll(block.value).forEach { add(it.groupValues[1], FontRequestSource.INLINE_FONT, event.id) }
                reset.findAll(block.value).forEach {
                    val styleName = it.groupValues[1].trim()
                    if (styleName.isNotEmpty()) styles[styleName]?.fontName?.let { family -> add(family, FontRequestSource.STYLE_RESET, event.id) }
                }
            }
        }
        return map.values.map { (family, req) -> FontRequirement(family, req.sources, req.eventIds) }
    }

    fun missing(document: AssDocument, availableFamilies: Set<String>): List<FontRequirement> {
        val normalized = availableFamilies.mapTo(hashSetOf()) { it.trim().lowercase() }
        return resolve(document).filter { it.family.trim().lowercase() !in normalized }
    }
}

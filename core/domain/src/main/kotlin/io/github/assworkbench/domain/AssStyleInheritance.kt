package io.github.assworkbench.domain

/**
 * Managed direct overrides for the "inherit Style" operation.
 *
 * Only top-level/direct tags are removed. Nested transform payloads remain untouched so clearing
 * static overrides cannot corrupt or partially rewrite a time-dependent \t(...) animation.
 */
object AssStyleInheritance {
    val managedTags: Set<String> = setOf(
        "fn", "fs",
        "b", "i", "u", "s",
        "fsp",
        "bord", "xbord", "ybord",
        "shad", "xshad", "yshad",
        "an", "a",
        "pos", "move", "org",
        "c", "1c", "2c", "3c", "4c",
        "alpha", "1a", "2a", "3a", "4a",
        "fscx", "fscy",
        "fr", "frx", "fry", "frz",
        "fax", "fay",
        "r",
    )

    fun directManagedTagNames(text: String): Set<String> =
        AssTopLevelOverrideSyntax.tags(text)
            .mapNotNullTo(linkedSetOf()) { tag ->
                tag.name.lowercase().takeIf { it in managedTags }
            }

    fun clearDirectManagedOverrides(text: String): String =
        AssTopLevelOverrideSyntax.removeTags(text, managedTags)
}

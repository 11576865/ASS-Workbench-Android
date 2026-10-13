package io.github.assworkbench.app

import io.github.assworkbench.domain.AssAnimationSemantic
import io.github.assworkbench.domain.AssComplexFade

/**
 * One domain-backed proposal for both transient preview and explicit Apply.
 * null means unsupported/invalid; an unchanged string means a no-op.
 * Never mutate existing ASS source or silently clamp a numeric input.
 */
internal fun proposeEventFadeSource(
    source: String,
    mode: String,
    fadeInMs: Int? = null,
    fadeOutMs: Int? = null,
    complex: AssComplexFade? = null,
): String? {
    // Malformed leading overrides cannot be safely re-authored.
    if (AssAnimationSemantic.inspect(source).malformedLeadingBlock) return null
    return when (mode) {
    "none" -> AssAnimationSemantic.removeFade(source)
    "fad" -> {
        if (fadeInMs == null || fadeOutMs == null ||
            fadeInMs !in 0..1_000_000 || fadeOutMs !in 0..1_000_000
        ) null else AssAnimationSemantic.patchSimpleFade(source, fadeInMs, fadeOutMs)
    }
    "fade" -> {
        if (complex == null) null
        else {
            val alpha = listOf(complex.alpha1, complex.alpha2, complex.alpha3)
            val time = listOf(
                complex.time1Ms, complex.time2Ms,
                complex.time3Ms, complex.time4Ms,
            )
            if (alpha.any { it !in 0..255 } || time.any { it < 0 } ||
                time.zipWithNext().any { (left, right) -> left > right }
            ) null else AssAnimationSemantic.patchComplexFade(source, complex)
        }
    }
    else -> null
}
}

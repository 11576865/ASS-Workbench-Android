package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertTrue

class CompatibilityAnalyzerTest {
    @Test
    fun conservative_profile_flags_advanced_and_vector_features() {
        val doc = AssDocument(events = listOf(
            AssEvent(
                1,
                start = SubTime(0),
                end = SubTime(1000),
                text = "{\\blur2\\clip(m 0 0 l 10 10)}x",
            ),
        ))
        val issues = AssCompatibilityAnalyzer.inspect(doc, AssCompatibilityProfile.PORTABLE_CONSERVATIVE)
        assertTrue(issues.any { it.code == "advanced-tag" })
        assertTrue(issues.any { it.code == "vector-clip" })
    }
}

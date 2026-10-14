package io.github.assworkbench.app.ui

import io.github.assworkbench.domain.AssEffectiveValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Provenance is descriptive read evidence, not an implicit write operation. */
class R3InspectorProvenanceTest {
    @Test fun eventOverrideAndInheritedStyleKeepDifferentReadLabels() {
        val override = r3ValueEvidence(AssEffectiveValue(
            name = "Size", styleValue = "42", overrideValue = "52",
            effectiveValue = "52", effectiveStyle = "Opening",
        ))
        assertEquals("字号", override.label)
        assertEquals("52", override.effectiveValue)
        assertEquals("Event override", override.source)

        val style = r3ValueEvidence(AssEffectiveValue(
            name = "Font", styleValue = "Noto Sans SC",
            effectiveValue = "Noto Sans SC", effectiveStyle = "Opening",
        ))
        assertEquals("Style · Opening", style.source)
        assertFalse(style.variableAcrossSpanOrTime)
    }

    @Test fun timeOrSpanDependentReadCannotBePresentedAsOneStableScalar() {
        val animated = r3ValueEvidence(AssEffectiveValue(
            name = "Size", styleValue = "42", overrideValue = "52",
            effectiveValue = "52", effectiveStyle = "Opening", spanDependent = true,
        ))
        assertTrue(animated.variableAcrossSpanOrTime)
        assertEquals("Event override", animated.source)
    }

    @Test fun inheritedPositionIsDerivedAnchorNotSharedStyleWrite() {
        val derived = r3ValueEvidence(AssEffectiveValue(
            name = "Position", styleValue = "alignment anchor",
            effectiveValue = "alignment anchor",
        ))
        assertEquals("对齐锚点（派生）", derived.source)
    }
}

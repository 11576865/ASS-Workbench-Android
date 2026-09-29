package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssTransformVisualSemanticTest {
    @Test
    fun readsCommonNumericPropertiesAndRotationAlias() {
        val got = AssTransformVisualSemantic.inspect(
            "\\fscx120\\fscy80\\fr45\\blur2.5\\bord3\\fax0.2"
        )
        assertEquals(120.0, got.values[AssTransformVisualProperty.SCALE_X])
        assertEquals(80.0, got.values[AssTransformVisualProperty.SCALE_Y])
        assertEquals(45.0, got.values[AssTransformVisualProperty.ROTATION_Z])
        assertEquals(2.5, got.values[AssTransformVisualProperty.GAUSSIAN_BLUR])
        assertEquals(3.0, got.values[AssTransformVisualProperty.BORDER])
        assertEquals(0.2, got.values[AssTransformVisualProperty.SHEAR_X])
    }

    @Test
    fun patchRewritesOnlyLastEffectivePropertyAndPreservesUnknownPayloads() {
        val input = "\\x-custom(foo)\\fscx80\\clip(10,20,300,400)\\fscx90\\blur2"
        val output = AssTransformVisualSemantic.patchNumeric(
            input,
            AssTransformVisualProperty.SCALE_X,
            125.0,
        )
        assertTrue("\\x-custom(foo)" in output)
        assertTrue("\\clip(10,20,300,400)" in output)
        assertTrue("\\fscx80" in output)
        assertTrue("\\fscx125" in output)
        assertTrue("\\blur2" in output)
    }

    @Test
    fun removeClearsAllAliasesForProperty() {
        val output = AssTransformVisualSemantic.patchNumeric(
            "\\fr10\\blur2\\frz20",
            AssTransformVisualProperty.ROTATION_Z,
            null,
        )
        assertFalse("\\fr10" in output)
        assertFalse("\\frz20" in output)
        assertTrue("\\blur2" in output)
    }

    @Test
    fun compatibilityWarningsCoverKnownFailureCases() {
        val got = AssTransformVisualSemantic.inspect(
            "\\fs40\\pos(100,200)\\clip(m 0 0 l 10 10)\\iclip(0,0,100,100)\\t(0,100,\\blur2)\\blur1\\blur2"
        )
        assertTrue(got.warnings.any { it.kind == AssTransformWarningKind.FONT_SIZE_HINTING })
        assertTrue(got.warnings.any {
            it.kind == AssTransformWarningKind.NON_ANIMATABLE_TAG && it.tag.equals("pos", true)
        })
        assertTrue(got.warnings.any { it.kind == AssTransformWarningKind.VECTOR_CLIP })
        assertTrue(got.warnings.any { it.kind == AssTransformWarningKind.CLIP_ICLIP_MIX })
        assertTrue(got.warnings.any { it.kind == AssTransformWarningKind.NESTED_TRANSFORM })
        assertTrue(got.warnings.any {
            it.kind == AssTransformWarningKind.DUPLICATE_PROPERTY &&
                it.property == AssTransformVisualProperty.GAUSSIAN_BLUR
        })
    }

    @Test
    fun integerOnlyPropertiesAreRoundedButInvalidNegativeValuesAreRejected() {
        assertEquals(
            "\\fs41",
            AssTransformVisualSemantic.patchNumeric(
                "\\fs40",
                AssTransformVisualProperty.FONT_SIZE,
                40.6,
            ),
        )
        assertEquals(
            "\\bord2",
            AssTransformVisualSemantic.patchNumeric(
                "\\bord2",
                AssTransformVisualProperty.BORDER,
                -1.0,
            ),
        )
    }
    @Test
    fun readsAndPatchesAssBgrColorsWithoutTouchingSiblingTags() {
        val input = "\\1c&H332211&\\blur2\\3c&HCCBBAA&"
        val snapshot = AssTransformVisualSemantic.inspect(input)
        assertEquals(AssRgb(0x11, 0x22, 0x33), snapshot.colors[AssTransformColorChannel.PRIMARY])
        assertEquals(AssRgb(0xAA, 0xBB, 0xCC), snapshot.colors[AssTransformColorChannel.OUTLINE])

        val output = AssTransformVisualSemantic.patchColor(
            input,
            AssTransformColorChannel.PRIMARY,
            AssRgb(0xFE, 0x80, 0x01),
        )
        assertTrue("\\1c&H0180FE&" in output)
        assertTrue("\\blur2" in output)
        assertTrue("\\3c&HCCBBAA&" in output)
    }

    @Test
    fun primaryColorAliasAndAlphaChannelsArePreserved() {
        val colored = AssTransformVisualSemantic.patchColor(
            "\\c&H0000FF&\\bord2",
            AssTransformColorChannel.PRIMARY,
            AssRgb(0, 255, 0),
        )
        assertTrue("\\c&H00FF00&" in colored)

        val alpha = AssTransformVisualSemantic.patchAlpha(
            "\\alpha&H80&\\blur2",
            AssTransformAlphaChannel.ALL,
            32,
        )
        assertTrue("\\alpha&H20&" in alpha)
        assertTrue("\\blur2" in alpha)
    }

    @Test
    fun rectangularClipIsStructuredButVectorClipIsNeverOverwritten() {
        val input = "\\clip(10,20,300,400)\\blur2"
        val snapshot = AssTransformVisualSemantic.inspect(input)
        assertEquals(
            AssRectTransformClip(10.0, 20.0, 300.0, 400.0, false),
            snapshot.rectClip,
        )
        val updated = AssTransformVisualSemantic.patchRectClip(
            input,
            AssRectTransformClip(15.0, 25.0, 320.0, 420.0, true),
        )
        assertTrue("\\iclip(15,25,320,420)" in updated)
        assertTrue("\\blur2" in updated)

        val vector = "\\clip(m 0 0 l 10 10)\\blur2"
        assertTrue(AssTransformVisualSemantic.inspect(vector).vectorClipPresent)
        assertEquals(
            vector,
            AssTransformVisualSemantic.patchRectClip(
                vector,
                AssRectTransformClip(0.0, 0.0, 100.0, 100.0, false),
            ),
        )
    }
}

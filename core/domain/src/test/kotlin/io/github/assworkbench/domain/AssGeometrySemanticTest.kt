package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssGeometrySemanticTest {
    @Test
    fun patchesOnlyPositionAndPreservesSurroundingSyntax() {
        val source = "{\\bord4\\c&HFFFFFF&\\pos(100,200)\\x-custom(foo)}Hello"
        val output = AssGeometrySemantic.patchPosition(source, 300.0, 400.0)
        assertEquals("{\\bord4\\c&HFFFFFF&\\pos(300,400)\\x-custom(foo)}Hello", output)
    }

    @Test
    fun topLevelRotationPatchDoesNotTouchTransformPayload() {
        val source = "{\\t(0,500,\\frz30\\fscx120)\\frz10\\bord2}Text"
        val snapshot = AssGeometrySemantic.inspect(source)
        assertEquals(10.0, snapshot.rotationZ)
        val output = AssGeometrySemantic.patchRotationZ(source, 25.0)
        assertEquals("{\\t(0,500,\\frz30\\fscx120)\\frz25\\bord2}Text", output)
    }

    @Test
    fun moveIsNotSilentlyConvertedToPosition() {
        val source = "{\\move(100,200,300,400)\\bord2}Text"
        val snapshot = AssGeometrySemantic.inspect(source)
        assertEquals(AssPositionMode.MOVE, snapshot.positionMode)
        assertEquals(100.0, snapshot.move?.start?.x)
        assertEquals(400.0, snapshot.move?.end?.y)
        assertEquals(source, AssGeometrySemantic.patchPosition(source, 500.0, 600.0))
    }

    @Test
    fun readsTimedMoveAndOrigin() {
        val source = "{\\move(10,20,30,40,100,900)\\org(960,540)}Text"
        val snapshot = AssGeometrySemantic.inspect(source)
        assertEquals(100.0, snapshot.move?.startMs)
        assertEquals(900.0, snapshot.move?.endMs)
        assertEquals(960.0, snapshot.origin?.x)
        assertEquals(540.0, snapshot.origin?.y)
    }

    @Test
    fun endpointPatchPreservesTimedMoveWindowAndUnknownNeighbors() {
        val source = "{\\bord3\\move(10,20,30,40,125,875)\\x-custom(foo)}Text"
        val snapshot = AssGeometrySemantic.inspect(source)
        val move = requireNotNull(snapshot.move)
        val output = AssGeometrySemantic.patchMove(
            text = source,
            start = AssPoint(100.0, 200.0),
            end = AssPoint(700.0, 800.0),
            startMs = move.startMs,
            endMs = move.endMs,
        )
        assertEquals("{\\bord3\\move(100,200,700,800,125,875)\\x-custom(foo)}Text", output)
    }
    @Test
    fun originPatchPreservesUnknownTagsAndNestedTransform() {
        val source = "{\\t(0,400,\\org(1,2))\\bord3\\org(100,200)\\x-custom(foo)}Text"
        val output = AssGeometrySemantic.patchOrigin(source, 960.0, 540.0)
        assertEquals("{\\t(0,400,\\org(1,2))\\bord3\\org(960,540)\\x-custom(foo)}Text", output)
    }

    @Test
    fun removingOriginRemovesAllTopLevelCopiesWithoutTouchingNestedTransform() {
        val source = "{\\org(10,20)\\t(0,400,\\org(1,2))}{\\org(30,40)}Text"
        val output = AssGeometrySemantic.removeOrigin(source)
        assertEquals("{\\t(0,400,\\org(1,2))}Text", output)
        assertNull(AssGeometrySemantic.inspect(output).origin)
    }
    @Test
    fun removingRotationPreservesNestedTransformAndUnknownTags() {
        val source = "{\\fr10\\t(0,500,\\frz30)\\x-custom(foo)\\frz20}Text"
        val output = AssGeometrySemantic.removeRotationZ(source)
        assertEquals("{\\t(0,500,\\frz30)\\x-custom(foo)}Text", output)
        assertNull(AssGeometrySemantic.inspect(output).rotationZ)
    }

    @Test
    fun addingRotationUsesFrzWithoutNormalizingNeighbors() {
        val source = "{\\bord2\\x-custom(foo)}Text"
        val output = AssGeometrySemantic.patchRotationZ(source, -32.5)
        assertEquals("{\\bord2\\x-custom(foo)\\frz-32.5}Text", output)
    }
    @Test
    fun preservesFrAliasWhenPatchingRotation() {
        val source = "{\\fr15\\bord2}Text"
        assertEquals("{\\fr27.5\\bord2}Text", AssGeometrySemantic.patchRotationZ(source, 27.5))
    }

    @Test
    fun commentBlockIsNotConvertedIntoOverrideBlock() {
        val source = "{editor note}Text"
        val output = AssGeometrySemantic.patchOrigin(source, 960.0, 540.0)
        assertEquals("{\\org(960,540)}{editor note}Text", output)
    }

    @Test
    fun inheritedGeometryStaysExplicitlyEmpty() {
        val snapshot = AssGeometrySemantic.inspect("{\\bord2}Text")
        assertEquals(AssPositionMode.INHERITED, snapshot.positionMode)
        assertNull(snapshot.position)
        assertNull(snapshot.move)
    }
}

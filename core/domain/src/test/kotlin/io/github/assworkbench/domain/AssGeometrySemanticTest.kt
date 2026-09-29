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

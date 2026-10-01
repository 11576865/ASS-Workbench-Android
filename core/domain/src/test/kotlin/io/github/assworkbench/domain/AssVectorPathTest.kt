package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AssVectorPathTest {
    @Test
    fun round_trips_scaled_vector_clip_payload() {
        val path = AssVectorPathCodec.parse("2,m 0 0 l 100 0 b 100 0 100 100 0 100")
        assertEquals(2, path.scale)
        assertEquals(listOf('m', 'l', 'b'), path.commands.map { it.command })
        val moved = AssVectorPathCodec.movePoint(path, 1, 0, 120.0, 4.0)
        assertEquals("2, m 0 0 l 120 4 b 100 0 100 100 0 100", AssVectorPathCodec.write(moved))
    }
    @Test
    fun ignores_vector_clip_nested_inside_transform() {
        val text = "{\\t(0,500,\\clip(m 0 0 l 10 10))\\clip(m 1 1 l 20 20)}x"
        val clip = AssVectorClipSemantic.inspectLeading(text)!!
        assertEquals(1.0, clip.path.commands.first().coordinates.first())
        val patched = AssVectorClipSemantic.patchLeading(
            text,
            clip.copy(path = AssVectorPathCodec.parse("m 2 2 l 30 30")),
        )
        kotlin.test.assertTrue(patched.contains("\\t(0,500,\\clip(m 0 0 l 10 10))"))
        kotlin.test.assertTrue(patched.contains("\\clip(m 2 2 l 30 30)"))
    }
}

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
}

package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssTextEncodingTest {
    private val sample = "[Script Info]\nTitle: 测试\n"

    @Test
    fun decodes_and_reencodes_utf8_bom() {
        val encoded = AssTextEncoding.UTF8_BOM.encode(sample)
        val decoded = AssTextDecoder.decode(encoded)
        assertEquals(AssTextEncoding.UTF8_BOM, decoded.encoding)
        assertEquals(sample, decoded.text)
        assertTrue(encoded.contentEquals(decoded.encoding.encode(decoded.text)))
    }

    @Test
    fun decodes_and_reencodes_utf16le() {
        val encoded = AssTextEncoding.UTF16_LE.encode(sample)
        val decoded = AssTextDecoder.decode(encoded)
        assertEquals(AssTextEncoding.UTF16_LE, decoded.encoding)
        assertEquals(sample, decoded.text)
        assertTrue(encoded.contentEquals(decoded.encoding.encode(decoded.text)))
    }

    @Test
    fun defaults_bomless_text_to_utf8() {
        val encoded = AssTextEncoding.UTF8.encode(sample)
        val decoded = AssTextDecoder.decode(encoded)
        assertEquals(AssTextEncoding.UTF8, decoded.encoding)
        assertEquals(sample, decoded.text)
    }
}

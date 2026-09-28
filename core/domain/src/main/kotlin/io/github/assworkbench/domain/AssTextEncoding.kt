package io.github.assworkbench.domain

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

enum class AssTextEncoding(
    val displayName: String,
) {
    UTF8("UTF-8"),
    UTF8_BOM("UTF-8 BOM"),
    UTF16_LE("UTF-16 LE"),
    UTF16_BE("UTF-16 BE");

    fun encode(text: String): ByteArray = when (this) {
        UTF8 -> text.toByteArray(StandardCharsets.UTF_8)
        UTF8_BOM -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(StandardCharsets.UTF_8)
        UTF16_LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            text.toByteArray(Charset.forName("UTF-16LE"))
        UTF16_BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            text.toByteArray(Charset.forName("UTF-16BE"))
    }
}

data class DecodedAssText(
    val text: String,
    val encoding: AssTextEncoding,
)

object AssTextDecoder {
    fun decode(bytes: ByteArray): DecodedAssText {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return DecodedAssText(
                String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8),
                AssTextEncoding.UTF8_BOM,
            )
        }

        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return DecodedAssText(
                String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE")),
                AssTextEncoding.UTF16_LE,
            )
        }

        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return DecodedAssText(
                String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16BE")),
                AssTextEncoding.UTF16_BE,
            )
        }

        return DecodedAssText(
            String(bytes, StandardCharsets.UTF_8),
            AssTextEncoding.UTF8,
        )
    }
}

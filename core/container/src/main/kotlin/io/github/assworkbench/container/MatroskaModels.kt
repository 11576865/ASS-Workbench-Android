package io.github.assworkbench.container

data class MatroskaSubtitlePacket(
    val startMs: Long,
    val durationMs: Long?,
    val payload: String,
)

data class MatroskaSubtitleTrack(
    val number: Long,
    val uid: Long?,
    val name: String,
    val language: String,
    val codecId: String,
    val codecPrivate: String,
    val packets: List<MatroskaSubtitlePacket>,
) {
    val displayName: String
        get() = buildString {
            append(if (name.isBlank()) "ASS Track #$number" else name)
            if (language.isNotBlank()) append(" · ").append(language)
        }

    fun toAss(): String {
        require(codecId == "S_TEXT/ASS") { "Only S_TEXT/ASS is supported for editing" }
        val resolved = packets.mapIndexed { index, packet ->
            val nextStart = packets.getOrNull(index + 1)?.startMs
            val end = packet.durationMs?.let { packet.startMs + it }
                ?: nextStart?.takeIf { it > packet.startMs }
                ?: (packet.startMs + 2000L)
            packet to end
        }
        val header = codecPrivate.trimEnd()
        return buildString {
            if (header.isNotBlank()) {
                append(header)
                append("\n\n")
            } else {
                append("[Script Info]\nScriptType: v4.00+\nPlayResX: 1920\nPlayResY: 1080\n\n")
                append("[V4+ Styles]\n")
                append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
                append("Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1\n\n")
            }
            if (!header.contains("[Events]", ignoreCase = true)) {
                append("[Events]\n")
                append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
            }
            for ((packet, endMs) in resolved) {
                val fields = packet.payload.split(',', limit = 9)
                if (fields.size < 9) continue
                append("Dialogue: ")
                append(fields[1].trim()).append(',')
                append(assTime(packet.startMs)).append(',')
                append(assTime(endMs)).append(',')
                append(fields.drop(2).joinToString(","))
                append('\n')
            }
        }
    }

    private fun assTime(ms: Long): String {
        val totalCs = ms.coerceAtLeast(0) / 10
        val cs = totalCs % 100
        val totalSeconds = totalCs / 100
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return "%d:%02d:%02d.%02d".format(hours, minutes, seconds, cs)
    }
}

data class MatroskaAttachment(
    val uid: Long?,
    val fileName: String,
    val mimeType: String,
    val description: String,
    val data: ByteArray,
) {
    val isSupportedFont: Boolean
        get() {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return ext in setOf("ttf", "otf") ||
                mimeType.equals("font/ttf", true) ||
                mimeType.equals("font/otf", true) ||
                mimeType.equals("font/sfnt", true) ||
                mimeType.equals("application/x-truetype-font", true) ||
                mimeType.equals("application/vnd.ms-opentype", true)
        }
}

data class MatroskaScanResult(
    val subtitleTracks: List<MatroskaSubtitleTrack>,
    val attachments: List<MatroskaAttachment>,
    val timecodeScaleNs: Long,
    /** Attachments skipped by the bounded reader before they could be surfaced. */
    val skippedAttachmentCount: Int = 0,
)

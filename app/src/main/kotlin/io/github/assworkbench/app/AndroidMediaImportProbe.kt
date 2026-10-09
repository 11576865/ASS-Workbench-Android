package io.github.assworkbench.app

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

internal object AndroidMediaImportProbe {
    fun probe(
        context: Context,
        uri: Uri,
        sourceName: String,
    ): MediaImportSourceAssessment {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, emptyMap())
            val assessments = buildList {
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.stringOrNull(MediaFormat.KEY_MIME) ?: continue
                    val kind = when {
                        mime.startsWith("video/") -> MediaImportTrackKind.VIDEO
                        mime.startsWith("audio/") -> MediaImportTrackKind.AUDIO
                        mime.startsWith("text/") ||
                            mime.contains("subtitle", ignoreCase = true) ||
                            mime.contains("subrip", ignoreCase = true) ||
                            mime.contains("ttml", ignoreCase = true) ->
                            MediaImportTrackKind.SUBTITLE
                        else -> MediaImportTrackKind.OTHER
                    }
                    val codecPrivateKeys = buildSet {
                        repeat(4) { csdIndex ->
                            val key = "csd-$csdIndex"
                            val size = format.byteBufferSizeOrNull(key)
                            if (size != null && size > 0) add(key)
                        }
                    }
                    val decoderAvailable = if (
                        kind == MediaImportTrackKind.VIDEO ||
                        kind == MediaImportTrackKind.AUDIO
                    ) {
                        runCatching {
                            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                                .findDecoderForFormat(format) != null
                        }.getOrNull()
                    } else {
                        null
                    }
                    val descriptor = MediaImportTrackDescriptor(
                                extractorIndex = index,
                                kind = kind,
                                mime = mime,
                                language = format.stringOrNull(MediaFormat.KEY_LANGUAGE),
                                durationUs = format.longOrNull(MediaFormat.KEY_DURATION),
                                width = format.intOrNull(MediaFormat.KEY_WIDTH),
                                height = format.intOrNull(MediaFormat.KEY_HEIGHT),
                                channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT),
                                sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE),
                                codecPrivateKeys = codecPrivateKeys,
                        decoderAvailable = decoderAvailable,
                    )
                    add(
                        promoteSourceSpecificMediaExecution(
                            assessment = MediaImportCompatibilityPlanner.assess(descriptor),
                            sourceName = sourceName,
                        )
                    )
                }
            }
            require(assessments.isNotEmpty()) {
                "Android MediaExtractor 未发现可检测的 Track"
            }
            return MediaImportSourceAssessment(
                sourceUri = uri.toString(),
                sourceName = sourceName,
                tracks = assessments,
            )
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun MediaFormat.longOrNull(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null

    private fun MediaFormat.stringOrNull(key: String): String? =
        if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null

    private fun MediaFormat.byteBufferSizeOrNull(key: String): Int? =
        if (containsKey(key)) {
            runCatching { getByteBuffer(key)?.duplicate()?.remaining() }.getOrNull()
        } else {
            null
        }
}


internal fun promoteSourceSpecificMediaExecution(
    assessment: MediaImportTrackAssessment,
    sourceName: String,
): MediaImportTrackAssessment {
    val extension = sourceName.substringAfterLast('.', "").lowercase()
    val descriptor = assessment.descriptor
    val isIsoBmffAac =
        descriptor.mime.equals("audio/mp4a-latm", ignoreCase = true) &&
            assessment.matroskaCodecId == "A_AAC" &&
            assessment.disposition == MediaImportDisposition.STREAM_COPY_COMPATIBLE &&
            "csd-0" in descriptor.codecPrivateKeys &&
            extension in setOf("m4a", "mp4", "3gp", "3g2")
    return if (isIsoBmffAac) {
        assessment.copy(
            executionImplemented = true,
            reason = "AAC 位于已验证的 ISO-BMFF 来源；MediaExtractor packet 可直接写入 A_AAC，csd-0 作为 Matroska CodecPrivate，并在输出后独立验证。",
        )
    } else {
        assessment
    }
}

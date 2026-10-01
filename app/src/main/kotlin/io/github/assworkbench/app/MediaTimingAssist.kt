package io.github.assworkbench.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

data class MediaAudioTrackInfo(
    val extractorIndex: Int,
    val ordinal: Int,
    val mime: String,
    val language: String?,
    val channelCount: Int?,
    val sampleRate: Int?,
) {
    val label: String
        get() = buildString {
            append("Audio ").append(ordinal + 1)
            language?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
            channelCount?.let { append(" · ").append(it).append("ch") }
            sampleRate?.let { append(" · ").append(it).append("Hz") }
            append(" · ").append(mime.substringAfter('/'))
        }
}

internal object MediaTrackCatalog {
    fun audioTracks(context: Context, uri: Uri): List<MediaAudioTrackInfo> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, emptyMap())
            var ordinal = 0
            buildList {
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (!mime.startsWith("audio/")) continue
                    add(
                        MediaAudioTrackInfo(
                            extractorIndex = index,
                            ordinal = ordinal++,
                            mime = mime,
                            language = format.stringOrNull(MediaFormat.KEY_LANGUAGE),
                            channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT),
                            sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE),
                        )
                    )
                }
            }
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
    private fun MediaFormat.stringOrNull(key: String): String? =
        if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null
}

/**
 * Bounded visual scene-cut detector. It samples reference-video frames and measures
 * coarse luminance change. Results are timing candidates, not semantic scene labels.
 */
internal object SceneCutAnalyzer {
    suspend fun analyze(
        context: Context,
        uri: Uri,
        sampleIntervalMs: Long = 300,
        threshold: Double = 0.28,
        minimumGapMs: Long = 500,
    ): List<Long> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: return emptyList()
            val cuts = mutableListOf<Long>()
            var previous: DoubleArray? = null
            var lastCut = -minimumGapMs
            var t = 0L
            while (t <= durationMs) {
                currentCoroutineContext().ensureActive()
                val frame = retriever.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val signature = lumaSignature(frame)
                    frame.recycle()
                    val before = previous
                    if (before != null) {
                        var diff = 0.0
                        for (i in signature.indices) diff += abs(signature[i] - before[i])
                        diff /= signature.size
                        if (diff >= threshold && t - lastCut >= minimumGapMs) {
                            cuts += t
                            lastCut = t
                        }
                    }
                    previous = signature
                }
                t += sampleIntervalMs
            }
            return cuts
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun lumaSignature(source: Bitmap): DoubleArray {
        val bitmap = Bitmap.createScaledBitmap(source, 16, 9, true)
        val out = DoubleArray(16 * 9)
        val pixels = IntArray(out.size)
        bitmap.getPixels(pixels, 0, 16, 0, 0, 16, 9)
        pixels.forEachIndexed { i, c ->
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            out[i] = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
        }
        if (bitmap !== source) bitmap.recycle()
        return out
    }
}

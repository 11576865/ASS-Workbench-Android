package io.github.assworkbench.app

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri
import io.github.assworkbench.domain.FrameTimeMap

enum class FrameTimelineStatus {
    IDLE, ANALYZING, READY, UNAVAILABLE,
}

data class FrameTimelineState(
    val sourceUri: String? = null,
    val status: FrameTimelineStatus = FrameTimelineStatus.IDLE,
    val map: FrameTimeMap? = null,
    val frameCount: Int = 0,
    val error: String? = null,
)

object FrameTimelineAnalyzer {
    private const val MAX_FRAMES = 1_000_000

    fun analyze(context: Context, uri: Uri): FrameTimeMap.Vfr {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            } ?: error("没有可分析的视频轨")
            extractor.selectTrack(track)

            val timestamps = ArrayList<Long>(16_384)
            while (true) {
                val sampleUs = extractor.sampleTime
                if (sampleUs < 0L) break
                timestamps += sampleUs / 1000L
                if (timestamps.size > MAX_FRAMES) {
                    error("视频帧数超过 $MAX_FRAMES；停止建立完整帧边界表")
                }
                if (!extractor.advance()) break
            }
            require(timestamps.isNotEmpty()) { "视频轨没有可用 frame timestamp" }
            val sorted = timestamps.asSequence().distinct().sorted().toList()
            return FrameTimeMap.Vfr(sorted.toLongArray())
        } finally {
            extractor.release()
        }
    }
}

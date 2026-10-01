package io.github.assworkbench.app

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri
import io.github.assworkbench.domain.FrameTimebase

object VideoFrameTimebaseAnalyzer {
    fun analyze(context: Context, uri: Uri, maxFrames: Int = 1_000_000): FrameTimebase.Vfr {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            } ?: error("参考媒体没有可解析的视频轨")
            extractor.selectTrack(videoTrack)
            val pts = ArrayList<Long>(120_000)
            var count = 0
            while (count < maxFrames) {
                val timeUs = extractor.sampleTime
                if (timeUs < 0L) break
                pts += (timeUs / 1000L).coerceAtLeast(0L)
                count++
                if (!extractor.advance()) break
            }
            require(pts.isNotEmpty()) { "未读取到视频帧时间戳" }
            val ordered = pts.distinct().sorted()
            require(ordered.isNotEmpty()) { "视频帧时间戳为空" }
            return FrameTimebase.Vfr(ordered)
        } finally {
            extractor.release()
        }
    }
}

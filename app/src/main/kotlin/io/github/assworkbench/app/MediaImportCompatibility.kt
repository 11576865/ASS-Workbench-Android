package io.github.assworkbench.app

enum class MediaImportTrackKind {
    VIDEO,
    AUDIO,
    SUBTITLE,
    OTHER,
}

enum class MediaImportDisposition {
    STREAM_COPY_COMPATIBLE,
    TRANSCODE_REQUIRED,
    UNSUPPORTED,
    UNKNOWN,
}

data class MediaImportTrackDescriptor(
    val extractorIndex: Int,
    val kind: MediaImportTrackKind,
    val mime: String,
    val language: String? = null,
    val durationUs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val channelCount: Int? = null,
    val sampleRate: Int? = null,
    val codecPrivateKeys: Set<String> = emptySet(),
    val decoderAvailable: Boolean? = null,
)

data class MediaImportTrackAssessment(
    val descriptor: MediaImportTrackDescriptor,
    val disposition: MediaImportDisposition,
    val matroskaCodecId: String? = null,
    /**
     * False in the first compatibility-probe slice. A codec-level STREAM_COPY_COMPATIBLE
     * result must not be presented as an executable import until a demux adapter exists.
     */
    val executionImplemented: Boolean = false,
    val reason: String,
)

data class MediaImportSourceAssessment(
    val sourceUri: String,
    val sourceName: String,
    val tracks: List<MediaImportTrackAssessment>,
)

internal object MediaImportCompatibilityPlanner {
    fun assess(track: MediaImportTrackDescriptor): MediaImportTrackAssessment {
        val mime = track.mime.lowercase()

        if (track.kind == MediaImportTrackKind.SUBTITLE || mime.startsWith("text/")) {
            return MediaImportTrackAssessment(
                descriptor = track,
                disposition = MediaImportDisposition.UNSUPPORTED,
                reason = "该轨属于文本/字幕导入域；应经过字幕 adapter，而不是媒体 stream-copy 路径。",
            )
        }

        return when (mime) {
            "video/avc" -> codecPrivateRequired(track, "V_MPEG4/ISO/AVC", "H.264/AVC")
            "video/hevc" -> codecPrivateRequired(track, "V_MPEGH/ISO/HEVC", "H.265/HEVC")
            "video/x-vnd.on2.vp8" -> streamCopy(track, "V_VP8", "VP8 可直接映射到 Matroska CodecID。")
            "video/x-vnd.on2.vp9" -> streamCopy(track, "V_VP9", "VP9 可直接映射到 Matroska CodecID。")

            "audio/mp4a-latm" -> codecPrivateRequired(track, "A_AAC", "AAC")
            "audio/mpeg" -> streamCopy(
                track,
                "A_MPEG/L3",
                "MPEG Layer III 可直接映射到 Matroska CodecID；当前已实现 MediaExtractor packet stream-copy adapter。",
                executionImplemented = true,
            )
            "audio/flac" -> codecPrivateRequired(track, "A_FLAC", "FLAC")
            "audio/ac3" -> streamCopy(track, "A_AC3", "AC-3 可直接映射到 Matroska CodecID。")
            "audio/eac3" -> streamCopy(track, "A_EAC3", "E-AC-3 可直接映射到 Matroska CodecID。")

            // These codecs can exist in Matroska, but Android extractor framing/config
            // to Matroska CodecPrivate has not been proven by this project yet.
            "audio/opus" -> unknown(
                track,
                "A_OPUS",
                "Opus 需要正确构造 OpusHead / CodecDelay / SeekPreRoll；当前 Android extractor → Matroska 映射尚未验证。",
            )
            "audio/vorbis" -> unknown(
                track,
                "A_VORBIS",
                "Vorbis 需要 Matroska/Xiph CodecPrivate 头部打包；当前 extractor 输出映射尚未验证。",
            )
            "video/av01" -> unknown(
                track,
                "V_AV1",
                "AV1 的 Matroska CodecPrivate / bitstream framing 尚未在本工程中验证。",
            )

            else -> when {
                track.kind in setOf(MediaImportTrackKind.VIDEO, MediaImportTrackKind.AUDIO) &&
                    track.decoderAvailable == true -> MediaImportTrackAssessment(
                    descriptor = track,
                    disposition = MediaImportDisposition.TRANSCODE_REQUIRED,
                    executionImplemented = false,
                    reason = "当前没有可信的 Matroska stream-copy 映射；系统可解码该轨，因此若要导入必须进入显式 Transcode 工作流。当前转码器未接入。",
                )
                track.kind in setOf(MediaImportTrackKind.VIDEO, MediaImportTrackKind.AUDIO) ->
                    unknown(
                        track,
                        null,
                        "当前没有可信的 Matroska stream-copy 映射，也没有足够证据证明可用的解码/转码路径。",
                    )
                else -> MediaImportTrackAssessment(
                    descriptor = track,
                    disposition = MediaImportDisposition.UNSUPPORTED,
                    executionImplemented = false,
                    reason = "该 Track 类型不属于当前 Video / Audio / Subtitle 导入边界。",
                )
            }
        }
    }

    private fun codecPrivateRequired(
        track: MediaImportTrackDescriptor,
        codecId: String,
        codecName: String,
    ): MediaImportTrackAssessment =
        if (track.codecPrivateKeys.isNotEmpty()) {
            streamCopy(
                track,
                codecId,
                "$codecName 有 extractor codec-config 证据，可作为 stream-copy 候选；仍需 demux adapter 实现与输出验证。",
            )
        } else {
            unknown(
                track,
                codecId,
                "$codecName 的 Matroska stream-copy 需要 codec configuration；当前探测未观察到 csd-*，不能把“可解码”误当成“可复制”。",
            )
        }

    private fun streamCopy(
        track: MediaImportTrackDescriptor,
        codecId: String,
        reason: String,
        executionImplemented: Boolean = false,
    ) = MediaImportTrackAssessment(
        descriptor = track,
        disposition = MediaImportDisposition.STREAM_COPY_COMPATIBLE,
        matroskaCodecId = codecId,
        executionImplemented = executionImplemented,
        reason = if (executionImplemented) {
            reason
        } else {
            reason + " 当前仅完成兼容性判定，实际外部 demux → Matroska 写入尚未接线。"
        },
    )

    private fun unknown(
        track: MediaImportTrackDescriptor,
        codecId: String?,
        reason: String,
    ) = MediaImportTrackAssessment(
        descriptor = track,
        disposition = MediaImportDisposition.UNKNOWN,
        matroskaCodecId = codecId,
        executionImplemented = false,
        reason = reason,
    )
}


internal fun isMatroskaFamilySource(
    sourceName: String,
    mimeType: String?,
): Boolean {
    val extension = sourceName.substringAfterLast('.', "").lowercase()
    val mime = mimeType.orEmpty().lowercase()
    return extension in setOf("mkv", "mka", "mks", "webm") ||
        "matroska" in mime ||
        mime == "video/webm" ||
        mime == "audio/webm" ||
        mime == "application/webm"
}

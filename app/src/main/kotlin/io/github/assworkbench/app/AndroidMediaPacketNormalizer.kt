package io.github.assworkbench.app

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.max

data class NormalizedMediaPacketSource(
    val file: File,
    val bundleSha256: String,
    val contentSha256: String,
    val codecPrivateSha256: String? = null,
    val mime: String,
    val sampleRate: Int,
    val channelCount: Int,
    val packetCount: Long,
    val durationUs: Long,
)

internal object AndroidMediaPacketNormalizer {
    private val MAGIC_V1 = "AWPKT001".toByteArray(Charsets.US_ASCII)
    private val MAGIC_V2 = "AWPKT002".toByteArray(Charsets.US_ASCII)
    private const val BASE_HEADER_BYTES = 24L
    private const val DEFAULT_SAMPLE_BUFFER = 256 * 1024
    private const val MAX_SAMPLE_BUFFER = 4 * 1024 * 1024
    private const val MAX_CODEC_PRIVATE_BYTES = 1024 * 1024

    suspend fun normalizeMp3Track(
        context: Context,
        uri: Uri,
        extractorIndex: Int,
        output: File,
    ): NormalizedMediaPacketSource =
        normalizeAudioTrack(
            context = context,
            uri = uri,
            extractorIndex = extractorIndex,
            output = output,
            expectedMime = "audio/mpeg",
            label = "MP3",
            includeCodecPrivate = false,
        )

    suspend fun normalizeAacTrack(
        context: Context,
        uri: Uri,
        extractorIndex: Int,
        output: File,
    ): NormalizedMediaPacketSource =
        normalizeAudioTrack(
            context = context,
            uri = uri,
            extractorIndex = extractorIndex,
            output = output,
            expectedMime = "audio/mp4a-latm",
            label = "AAC",
            includeCodecPrivate = true,
        )

    private suspend fun normalizeAudioTrack(
        context: Context,
        uri: Uri,
        extractorIndex: Int,
        output: File,
        expectedMime: String,
        label: String,
        includeCodecPrivate: Boolean,
    ): NormalizedMediaPacketSource {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, emptyMap())
            require(extractorIndex in 0 until extractor.trackCount) {
                "媒体来源 Track index 已变化；请重新检测"
            }
            val format = extractor.getTrackFormat(extractorIndex)
            val mime = format.stringOrNull(MediaFormat.KEY_MIME)
                ?: error("媒体 Track MIME 缺失")
            require(mime.equals(expectedMime, ignoreCase = true)) {
                "当前 packet adapter 期望 $expectedMime；实际为 $mime"
            }
            val sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
                ?.takeIf { it > 0 }
                ?: error("$label Track 缺少 sample rate")
            val channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                ?.takeIf { it in 1..255 }
                ?: error("$label Track 缺少有效 channel count")
            val codecPrivate = if (includeCodecPrivate) {
                format.byteArrayOrNull("csd-0")
                    ?.takeIf { it.isNotEmpty() }
                    ?: error("$label Track 缺少 csd-0 / CodecPrivate")
            } else {
                null
            }
            codecPrivate?.let {
                require(it.size <= MAX_CODEC_PRIVATE_BYTES) {
                    "$label CodecPrivate 过大：${it.size} bytes"
                }
            }

            val declaredDurationUs = format.longOrNull(MediaFormat.KEY_DURATION)
                ?.coerceAtLeast(0L)
                ?: 0L
            val requestedCapacity = format.intOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
                ?.coerceAtLeast(DEFAULT_SAMPLE_BUFFER)
                ?: DEFAULT_SAMPLE_BUFFER
            val capacity = requestedCapacity.coerceAtMost(MAX_SAMPLE_BUFFER)

            output.parentFile?.mkdirs() ?: error("packet bundle 输出目录不可用")
            output.delete()

            extractor.selectTrack(extractorIndex)
            var packetCount = 0L
            var firstSourcePtsUs = -1L
            var previousPtsUs = -1L
            var lastPtsUs = 0L
            var minimumGapUs = Long.MAX_VALUE
            val contentDigest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteBuffer.allocateDirect(capacity)

            DataOutputStream(output.outputStream().buffered()).use { out ->
                if (codecPrivate == null) {
                    out.write(MAGIC_V1)
                } else {
                    out.write(MAGIC_V2)
                }
                out.writeLong(0L) // durationUs, patched after streaming
                out.writeLong(0L) // packetCount, patched after streaming
                if (codecPrivate != null) {
                    out.writeInt(codecPrivate.size)
                    out.write(codecPrivate)
                }

                while (true) {
                    currentCoroutineContext().ensureActive()
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    require(size in 1..capacity) {
                        "$label sample size $size exceeds packet adapter bound $capacity"
                    }
                    val sourcePtsUs = extractor.sampleTime
                    require(sourcePtsUs >= 0L) {
                        "$label sample 缺少有效 presentation timestamp"
                    }
                    if (firstSourcePtsUs < 0L) firstSourcePtsUs = sourcePtsUs
                    val ptsUs = (sourcePtsUs - firstSourcePtsUs).coerceAtLeast(0L)
                    require(previousPtsUs <= ptsUs) {
                        "$label sample timestamp 非单调；当前 stream-copy adapter 拒绝重排"
                    }
                    if (previousPtsUs >= 0L && ptsUs > previousPtsUs) {
                        minimumGapUs = minOf(minimumGapUs, ptsUs - previousPtsUs)
                    }

                    buffer.position(0)
                    buffer.limit(size)
                    val payload = ByteArray(size)
                    buffer.get(payload)

                    out.writeLong(ptsUs)
                    out.writeInt(extractor.sampleFlags)
                    out.writeInt(size)
                    out.write(payload)

                    updateContentDigest(contentDigest, ptsUs / 1000L, payload)
                    packetCount++
                    previousPtsUs = ptsUs
                    lastPtsUs = ptsUs

                    if (!extractor.advance()) break
                }
            }

            require(packetCount > 0L) { "$label Track 没有可导入的压缩 packet" }
            val measuredEndUs = lastPtsUs + when {
                minimumGapUs != Long.MAX_VALUE -> minimumGapUs
                declaredDurationUs > 0L && firstSourcePtsUs >= 0L ->
                    (declaredDurationUs - firstSourcePtsUs).coerceAtLeast(0L)
                else -> 1000L
            }
            val rebasedDeclaredUs = if (declaredDurationUs > 0L && firstSourcePtsUs >= 0L) {
                (declaredDurationUs - firstSourcePtsUs).coerceAtLeast(0L)
            } else {
                0L
            }
            val durationUs = max(measuredEndUs, rebasedDeclaredUs).coerceAtLeast(1000L)

            val minimumHeaderBytes = BASE_HEADER_BYTES +
                if (codecPrivate != null) 4L + codecPrivate.size else 0L
            RandomAccessFile(output, "rw").use { raf ->
                require(raf.length() >= minimumHeaderBytes) {
                    "packet bundle header 写入不完整"
                }
                raf.seek(MAGIC_V1.size.toLong())
                raf.writeLong(durationUs)
                raf.writeLong(packetCount)
            }

            return NormalizedMediaPacketSource(
                file = output,
                bundleSha256 = sha256File(output),
                contentSha256 = contentDigest.digest().toHex(),
                codecPrivateSha256 = codecPrivate?.let(::sha256Bytes),
                mime = mime,
                sampleRate = sampleRate,
                channelCount = channelCount,
                packetCount = packetCount,
                durationUs = durationUs,
            )
        } finally {
            extractor.release()
        }
    }

    private fun updateContentDigest(
        digest: MessageDigest,
        timecodeMs: Long,
        payload: ByteArray,
    ) {
        val header = ByteBuffer.allocate(12)
            .putLong(timecodeMs)
            .putInt(payload.size)
            .array()
        digest.update(header)
        digest.update(payload)
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        BufferedInputStream(file.inputStream()).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun MediaFormat.longOrNull(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null

    private fun MediaFormat.stringOrNull(key: String): String? =
        if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null

    private fun MediaFormat.byteArrayOrNull(key: String): ByteArray? =
        if (containsKey(key)) {
            runCatching {
                getByteBuffer(key)?.duplicate()?.let { buffer ->
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                }
            }.getOrNull()
        } else {
            null
        }
}

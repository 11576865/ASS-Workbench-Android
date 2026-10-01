package io.github.assworkbench.app

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.github.assworkbench.domain.WaveformEnvelope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder
import java.security.MessageDigest

internal object WaveformLiteAnalyzer {
    const val BUCKET_MS = 20
    private const val MAGIC = 0x41574631
    private const val MAX_BUCKETS = 10_000_000

    suspend fun loadOrAnalyze(context: Context, uri: Uri, audioTrackIndex: Int? = null): WaveformEnvelope {
        val key = cacheKey(context, uri, audioTrackIndex)
        readCache(context, key)?.let { return it }
        val result = analyze(context, uri, audioTrackIndex)
        currentCoroutineContext().ensureActive()
        writeCache(context, key, result)
        return result
    }

    private suspend fun analyze(context: Context, uri: Uri, audioTrackIndex: Int?): WaveformEnvelope {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, emptyMap())
            val track = audioTrackIndex
                ?.takeIf { index ->
                    index in 0 until extractor.trackCount &&
                        extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                ?: (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                ?: error("没有可分析的音轨")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("音轨 MIME 缺失")
            extractor.selectTrack(track)

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 48_000
            var channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 2
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val peaks = Peaks()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val input = decoder.getInputBuffer(index) ?: error("decoder input buffer unavailable")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime.coerceAtLeast(0), 0)
                            extractor.advance()
                        }
                    }
                }

                when (val index = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        sampleRate = out.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: sampleRate
                        channels = out.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: channels
                        encoding = out.intOrNull(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT
                    }
                    else -> if (index >= 0) {
                        if (info.size > 0) {
                            val buffer = decoder.getOutputBuffer(index) ?: error("decoder output buffer unavailable")
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val pcm = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                            val bytesPerSample = when (encoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> 4
                                AudioFormat.ENCODING_PCM_16BIT -> 2
                                else -> error("不支持的 PCM 输出格式：$encoding")
                            }
                            val frameBytes = bytesPerSample * channels.coerceAtLeast(1)
                            val frames = pcm.remaining() / frameBytes
                            repeat(frames) { frame ->
                                if (frame and 0x3FFF == 0) {
                                    currentCoroutineContext().ensureActive()
                                }
                                var low = Short.MAX_VALUE
                                var high = Short.MIN_VALUE
                                repeat(channels.coerceAtLeast(1)) {
                                    val sample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                        (pcm.float.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                                    } else pcm.short
                                    if (sample < low) low = sample
                                    if (sample > high) high = sample
                                }
                                val timeUs = info.presentationTimeUs.coerceAtLeast(0) +
                                    frame.toLong() * 1_000_000L / sampleRate.coerceAtLeast(1)
                                peaks.add((timeUs / (BUCKET_MS * 1000L)).toInt(), low, high)
                            }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(index, false)
                    }
                }
            }
            return peaks.finish()
        } catch (cancelled: CancellationException) {
            // Cancellation is expected when the user changes media/project.
            // Drop the decoder promptly; never translate this into "waveform unavailable".
            throw cancelled
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun cacheKey(context: Context, uri: Uri, audioTrackIndex: Int?): String {
        val length = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        val source = uri.toString() + "|" + length + "|audio=" + (audioTrackIndex ?: -1)
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun cacheDir(context: Context) = File(context.cacheDir, "waveform-lite").apply { mkdirs() }

    private fun readCache(context: Context, key: String): WaveformEnvelope? {
        val file = File(cacheDir(context), "$key.awf")
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readInt() == MAGIC)
                val bucketMs = input.readInt()
                val durationMs = input.readLong()
                val count = input.readInt()
                require(bucketMs > 0 && durationMs >= 0 && count in 0..MAX_BUCKETS)
                val lows = ShortArray(count)
                val highs = ShortArray(count)
                repeat(count) { i -> lows[i] = input.readShort(); highs[i] = input.readShort() }
                file.setLastModified(System.currentTimeMillis())
                WaveformEnvelope(bucketMs, durationMs, lows, highs)
            }
        }.getOrNull()
    }

    private suspend fun writeCache(context: Context, key: String, data: WaveformEnvelope) {
        val dir = cacheDir(context)
        val tmp = File(dir, "$key.tmp-" + System.nanoTime())
        try {
            currentCoroutineContext().ensureActive()
            DataOutputStream(tmp.outputStream().buffered()).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(data.bucketDurationMs)
                output.writeLong(data.durationMs)
                output.writeInt(data.bucketCount)
                repeat(data.bucketCount) { i ->
                    if (i and 0x3FFF == 0) currentCoroutineContext().ensureActive()
                    output.writeShort(data.minimums[i].toInt())
                    output.writeShort(data.maximums[i].toInt())
                }
            }
            currentCoroutineContext().ensureActive()
            val dst = File(dir, "$key.awf")
            if (!tmp.renameTo(dst)) {
                tmp.copyTo(dst, overwrite = true)
            }
            dst.setLastModified(System.currentTimeMillis())
            dir.listFiles()
                ?.filter { it.extension == "awf" }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(6)
                ?.forEach(File::delete)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private class Peaks {
        private var lows = ShortArray(4096) { Short.MAX_VALUE }
        private var highs = ShortArray(4096) { Short.MIN_VALUE }
        private var seen = BooleanArray(4096)
        private var last = -1

        fun add(index: Int, low: Short, high: Short) {
            if (index !in 0 until MAX_BUCKETS) return
            ensure(index + 1)
            if (!seen[index]) {
                lows[index] = low; highs[index] = high; seen[index] = true
            } else {
                if (low < lows[index]) lows[index] = low
                if (high > highs[index]) highs[index] = high
            }
            if (index > last) last = index
        }

        fun finish(): WaveformEnvelope {
            if (last < 0) error("音轨没有可解码 PCM 数据")
            val count = last + 1
            val outLow = ShortArray(count)
            val outHigh = ShortArray(count)
            repeat(count) { i ->
                if (seen[i]) { outLow[i] = lows[i]; outHigh[i] = highs[i] }
            }
            return WaveformEnvelope(BUCKET_MS, count.toLong() * BUCKET_MS, outLow, outHigh)
        }

        private fun ensure(required: Int) {
            if (required <= lows.size) return
            var size = lows.size
            while (size < required && size < MAX_BUCKETS) size = (size * 2).coerceAtMost(MAX_BUCKETS)
            if (size < required) error("Waveform 过长")
            val old = lows.size
            lows = lows.copyOf(size); highs = highs.copyOf(size); seen = seen.copyOf(size)
            for (i in old until size) { lows[i] = Short.MAX_VALUE; highs[i] = Short.MIN_VALUE }
        }
    }
}

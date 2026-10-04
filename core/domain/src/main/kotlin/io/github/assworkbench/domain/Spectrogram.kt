package io.github.assworkbench.domain

import java.io.ByteArrayOutputStream
import kotlin.math.*

/** Mono Hann-window STFT magnitude: 128 linear frequency bands, -80..0 dBFS. */
data class Spectrogram(val timesMs: LongArray, val levels: ByteArray, val bandCount: Int, val maxFrequencyHz: Double, val frameIntervalMs: Long = 40L) {
    init { require(bandCount > 1 && levels.size == timesMs.size * bandCount) }
    val frameCount: Int get() = timesMs.size
    fun frequencyAt(band: Int): Double = band * maxFrequencyHz / (bandCount - 1)
    fun levelAt(frame: Int, band: Int): Int = levels[frame * bandCount + band].toInt() and 255
    fun frameAt(timeMs: Long): Int {
        val found = timesMs.binarySearch(timeMs)
        val index = if (found >= 0) found else -found - 2
        return if (index >= 0 && timeMs - timesMs[index] <= frameIntervalMs) index else -1
    }
}

class StreamingSpectrogram(private val sampleRate: Int, private val maxFrames: Int = 131072) {
    init { require(sampleRate > 0 && maxFrames > 0) }
    private val size = 2048
    private val bands = 128
    private val hop = (sampleRate / 25).coerceAtLeast(1)
    private val ring = DoubleArray(size)
    private val window = DoubleArray(size) { 0.5 - 0.5 * cos(2 * PI * it / (size - 1)) }
    private val normalization = window.sum()
    private val real = DoubleArray(size)
    private val imag = DoubleArray(size)
    private val bytes = ByteArrayOutputStream()
    private val times = ArrayList<Long>()
    private var count = 0L
    private var nextFrame = size.toLong()
    private var previousUs: Long? = null
    private val maxHz = minOf(8000.0, sampleRate / 2.0)

    fun add(sample: Float, timeUs: Long) {
        val previous = previousUs
        if (previous != null && (timeUs < previous || timeUs - previous > maxOf(1000L, 2_000_000L / sampleRate))) {
            count = 0L; nextFrame = size.toLong()
        }
        previousUs = timeUs
        ring[(count % size).toInt()] = if (sample.isFinite()) sample.coerceIn(-1f, 1f).toDouble() else 0.0
        count++
        if (count >= nextFrame) {
            check(times.size < maxFrames) { "声谱图超过分析容量，请使用较短媒体" }
            for (i in 0 until size) {
                real[i] = ring[((count + i) % size).toInt()] * window[i]
                imag[i] = 0.0
            }
            fft()
            times.add(((timeUs - (size / 2 - 1) * 1_000_000L / sampleRate).coerceAtLeast(0)) / 1000)
            for (band in 0 until bands) {
                val center = band * maxHz / (bands - 1)
                val halfWidth = maxHz / (bands - 1) / 2
                val low = ceil((center - halfWidth) * size / sampleRate).toInt().coerceIn(0, size / 2)
                val high = floor((center + halfWidth) * size / sampleRate).toInt().coerceIn(low, size / 2)
                var magnitude = 0.0
                for (bin in low..high) {
                    magnitude = maxOf(magnitude, hypot(real[bin], imag[bin]) * (if (bin == 0 || bin == size / 2) 1 else 2) / normalization)
                }
                val db = 20 * log10(magnitude.coerceAtLeast(1e-12))
                bytes.write(((db + 80) / 80 * 255).roundToInt().coerceIn(0, 255))
            }
            nextFrame += hop
        }
    }

    fun finish(): Spectrogram = Spectrogram(times.toLongArray(), bytes.toByteArray(), bands, maxHz)

    private fun fft() {
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val r = real[i]; real[i] = real[j]; real[j] = r }
        }
        var length = 2
        while (length <= size) {
            val angle = -2 * PI / length
            val wrStep = cos(angle); val wiStep = sin(angle)
            for (start in 0 until size step length) {
                var wr = 1.0; var wi = 0.0
                for (offset in 0 until length / 2) {
                    val a = start + offset; val b = a + length / 2
                    val br = real[b] * wr - imag[b] * wi
                    val bi = real[b] * wi + imag[b] * wr
                    real[b] = real[a] - br; imag[b] = imag[a] - bi
                    real[a] += br; imag[a] += bi
                    val nextWr = wr * wrStep - wi * wiStep
                    wi = wr * wiStep + wi * wrStep; wr = nextWr
                }
            }
            length *= 2
        }
    }
}

package io.github.assworkbench.domain

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class SpectrogramTest {
    @Test fun realToneHasAnEnergyPeakAtItsFrequency() {
        val analyzer = StreamingSpectrogram(16000)
        repeat(8000) { i -> analyzer.add(sin(2 * PI * 1000 * i / 16000).toFloat(), i * 1_000_000L / 16000) }
        val spectrum = analyzer.finish()
        assertTrue(spectrum.frameCount > 0)
        val peak = (0 until spectrum.bandCount).maxBy { spectrum.levelAt(0, it) }
        assertEquals(1000.0, spectrum.frequencyAt(peak), 80.0)
        assertTrue(spectrum.levelAt(0, peak) > 240)
    }

    @Test fun silenceIsTransparentFloorRatherThanArtificialEnergy() {
        val analyzer = StreamingSpectrogram(16000)
        repeat(4096) { i -> analyzer.add(0f, i * 1_000_000L / 16000) }
        assertTrue(analyzer.finish().levels.all { it == 0.toByte() })
    }

    @Test fun nonzeroMediaTimestampIsPreserved() {
        val analyzer = StreamingSpectrogram(16000)
        repeat(4096) { i -> analyzer.add(0f, 5_000_000L + i * 1_000_000L / 16000) }
        val spectrum = analyzer.finish()
        assertEquals(5064L, spectrum.timesMs.first())
        assertEquals(-1, spectrum.frameAt(4000))
        assertEquals(0, spectrum.frameAt(5064))
    }

    @Test fun discontinuousPcmDoesNotInventSignalAcrossAGap() {
        val analyzer = StreamingSpectrogram(16000)
        repeat(4096) { i -> analyzer.add(0f, i * 1_000_000L / 16000) }
        repeat(4096) { i -> analyzer.add(0f, 10_000_000L + i * 1_000_000L / 16000) }
        assertEquals(-1, analyzer.finish().frameAt(5000))
    }

    @Test fun memoryLimitFailsExplicitly() {
        val analyzer = StreamingSpectrogram(16000, maxFrames = 1)
        var failed = false
        try { repeat(4096) { i -> analyzer.add(0f, i * 1_000_000L / 16000) } }
        catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
    }
}

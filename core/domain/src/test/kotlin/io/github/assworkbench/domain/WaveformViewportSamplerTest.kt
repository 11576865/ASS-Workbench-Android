package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class WaveformViewportSamplerTest {
    @Test
    fun aggregatesBucketsIntoViewportColumns() {
        val envelope = WaveformEnvelope(
            bucketDurationMs = 20,
            durationMs = 80,
            minimums = shortArrayOf(-10, -100, -20, -50),
            maximums = shortArrayOf(20, 80, 30, 60),
        )
        assertEquals(
            listOf(WaveformBucket(-100, 80), WaveformBucket(-50, 60)),
            WaveformViewportSampler.sample(envelope, 0, 80, 2),
        )
    }

    @Test
    fun columnsOutsideAudioDurationAreSilentInsteadOfRepeatingLastPeak() {
        val envelope = WaveformEnvelope(
            bucketDurationMs = 20,
            durationMs = 40,
            minimums = shortArrayOf(-10, -20),
            maximums = shortArrayOf(10, 20),
        )
        assertEquals(
            listOf(
                WaveformBucket(-20, 20),
                WaveformBucket(0, 0),
                WaveformBucket(0, 0),
                WaveformBucket(0, 0),
            ),
            WaveformViewportSampler.sample(envelope, 20, 100, 4),
        )
    }

    @Test
    fun emptyEnvelopeProducesNoColumns() {
        val envelope = WaveformEnvelope(20, 0, shortArrayOf(), shortArrayOf())
        assertEquals(emptyList(), WaveformViewportSampler.sample(envelope, 0, 1000, 100))
    }
}

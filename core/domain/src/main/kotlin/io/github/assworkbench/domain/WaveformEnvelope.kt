package io.github.assworkbench.domain

data class WaveformEnvelope(
    val bucketDurationMs: Int,
    val durationMs: Long,
    val minimums: ShortArray,
    val maximums: ShortArray,
) {
    init {
        require(bucketDurationMs > 0)
        require(durationMs >= 0L)
        require(minimums.size == maximums.size)
    }

    val bucketCount: Int get() = minimums.size
}

data class WaveformBucket(
    val minimum: Short,
    val maximum: Short,
)

object WaveformViewportSampler {
    fun sample(
        envelope: WaveformEnvelope,
        startMs: Long,
        endMs: Long,
        columns: Int,
    ): List<WaveformBucket> {
        if (columns <= 0 || envelope.bucketCount == 0 || endMs <= startMs) return emptyList()

        val result = ArrayList<WaveformBucket>(columns)
        val span = (endMs - startMs).coerceAtLeast(1L)
        repeat(columns) { column ->
            val columnStartMs = startMs + span * column / columns
            val columnEndMs = startMs + span * (column + 1L) / columns
            val first = (columnStartMs / envelope.bucketDurationMs)
                .toInt()
                .coerceIn(0, envelope.bucketCount - 1)
            val lastExclusive = (
                (columnEndMs + envelope.bucketDurationMs - 1L) / envelope.bucketDurationMs
            ).toInt().coerceIn(first + 1, envelope.bucketCount)

            var minimum = Short.MAX_VALUE
            var maximum = Short.MIN_VALUE
            var seen = false
            for (index in first until lastExclusive) {
                val low = envelope.minimums[index]
                val high = envelope.maximums[index]
                if (low < minimum) minimum = low
                if (high > maximum) maximum = high
                seen = true
            }
            result += if (seen) WaveformBucket(minimum, maximum) else WaveformBucket(0, 0)
        }
        return result
    }
}

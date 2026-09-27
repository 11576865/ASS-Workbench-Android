package io.github.assworkbench.domain

@JvmInline
value class SubTime(val millis: Long) : Comparable<SubTime> {
    init {
        require(millis >= 0) { "Subtitle time must be non-negative" }
    }

    override fun compareTo(other: SubTime): Int = millis.compareTo(other.millis)

    fun toAss(): String {
        val totalCentis = millis / 10
        val centis = totalCentis % 100
        val totalSeconds = totalCentis / 100
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centis)
    }

    companion object {
        val ZERO = SubTime(0)

        fun parseAss(value: String): SubTime {
            val parts = value.trim().split(':')
            require(parts.size == 3) { "Invalid ASS time: $value" }
            val hours = parts[0].toLong()
            val minutes = parts[1].toLong()
            val secParts = parts[2].split('.', limit = 2)
            require(secParts.size == 2) { "Invalid ASS time: $value" }
            val seconds = secParts[0].toLong()
            val fraction = secParts[1].padEnd(2, '0').take(2).toLong()
            return SubTime((((hours * 60 + minutes) * 60 + seconds) * 1000) + fraction * 10)
        }

        fun fromEditable(value: String): SubTime {
            val trimmed = value.trim()
            if (trimmed.contains(':')) return parseAss(trimmed)
            return SubTime(trimmed.toLong())
        }
    }
}

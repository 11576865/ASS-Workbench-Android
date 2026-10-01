package io.github.assworkbench.domain

object VfrTimecodes {
    fun parseV2(input: String): FrameTimebase.Vfr {
        val pts = input.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { value ->
                val millis = value.toDoubleOrNull() ?: error("无效 timecode：${value}")
                require(millis >= 0.0) { "timecode 不能为负数" }
                kotlin.math.round(millis).toLong()
            }
            .toList()
        require(pts.isNotEmpty()) { "没有可用的 VFR timecode" }
        return FrameTimebase.Vfr(pts)
    }
}

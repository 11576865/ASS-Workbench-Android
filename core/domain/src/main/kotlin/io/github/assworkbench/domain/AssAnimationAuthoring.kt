package io.github.assworkbench.domain

data class AssAnimationKeyframe(val timeMs: Long, val value: Double)
data class AssNumericAnimationPlan(
    val property: AssTransformVisualProperty,
    val keyframes: List<AssAnimationKeyframe>,
    val accel: Double?,
    val generatedOverrideBlock: String,
)

object AssAnimationAuthoring {
    fun parseKeyframes(text: String): List<AssAnimationKeyframe> {
        val frames = text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.mapIndexed { index, line ->
            val parts = line.split('=', limit = 2)
            require(parts.size == 2) { "第 ${index + 1} 行应为 time=value" }
            val time = parts[0].trim().toLongOrNull() ?: error("第 ${index + 1} 行时间不是整数毫秒")
            val value = parts[1].trim().toDoubleOrNull() ?: error("第 ${index + 1} 行值不是数字")
            AssAnimationKeyframe(time, value)
        }.toList()
        return normalize(frames)
    }

    fun planNumericTrack(property: AssTransformVisualProperty, keyframes: Collection<AssAnimationKeyframe>, accel: Double? = null): AssNumericAnimationPlan {
        val frames = normalize(keyframes)
        require(frames.size >= 2) { "动画至少需要两个关键帧" }
        if (accel != null) require(accel.isFinite() && accel > 0.0)
        frames.forEach { frame ->
            require(frame.value.isFinite())
            property.minimum?.let { require(frame.value >= it) }
        }
        val base = AssTransformVisualSemantic.patchNumeric("", property, frames.first().value)
        val transforms = frames.zipWithNext().joinToString("") { (from, to) ->
            val target = AssTransformVisualSemantic.patchNumeric("", property, to.value)
            "\\t(" + buildList {
                add(from.timeMs.toString()); add(to.timeMs.toString())
                if (accel != null) add(format(accel))
                add(target)
            }.joinToString(",") + ")"
        }
        return AssNumericAnimationPlan(property, frames, accel, "{$base$transforms}")
    }

    fun applyNumericTrack(text: String, property: AssTransformVisualProperty, keyframes: Collection<AssAnimationKeyframe>, accel: Double? = null): String {
        val plan = planNumericTrack(property, keyframes, accel)
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return text
            cursor = close + 1
        }
        return text.substring(0, cursor) + plan.generatedOverrideBlock + text.substring(cursor)
    }

    fun scaleKeyframes(keyframes: Collection<AssAnimationKeyframe>, sourceDurationMs: Long, targetDurationMs: Long): List<AssAnimationKeyframe> {
        require(sourceDurationMs > 0 && targetDurationMs >= 0)
        return normalize(keyframes).map { it.copy(timeMs = kotlin.math.round(it.timeMs * targetDurationMs.toDouble() / sourceDurationMs).toLong()) }
    }

    private fun normalize(frames: Collection<AssAnimationKeyframe>): List<AssAnimationKeyframe> {
        val sorted = frames.sortedBy { it.timeMs }
        require(sorted.isNotEmpty()) { "没有关键帧" }
        require(sorted.first().timeMs >= 0)
        require(sorted.zipWithNext().all { (a, b) -> b.timeMs > a.timeMs }) { "关键帧时间必须严格递增" }
        return sorted
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}

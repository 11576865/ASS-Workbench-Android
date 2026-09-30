package io.github.assworkbench.domain

import kotlin.math.abs

enum class AssRendererRiskKind {
    EXTREME_NUMERIC,
    EXTREME_DRAWING_COORDINATE,
    OVERSIZED_DRAWING,
}

data class AssRendererRisk(
    val eventId: Long,
    val kind: AssRendererRiskKind,
    val message: String,
)

/**
 * Conservative preflight for inputs that are known to exercise dangerous native
 * renderer paths. This is deliberately separate from parsing and persistence:
 * risky Raw ASS stays text-preservable and editable; only authoritative preview
 * is gated.
 *
 * Thresholds below are ASS Workbench safety policy, not claims about a precise
 * libass crash boundary.
 */
object AssRendererRiskAnalyzer {
    internal const val EXTREME_SCALAR_LIMIT = 1_000_000.0
    internal const val EXTREME_COORDINATE_LIMIT = 1_000_000.0
    internal const val MAX_DRAWING_TEXT_CHARS = 256_000
    internal const val MAX_DRAWING_NUMBERS = 50_000

    private val overrideBlock = Regex("""\{[^}]*\}""")
    private val number = """[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?"""
    private val scalarTag = Regex(
        """\\(frx|fry|frz|fr|fax|fay|fscx|fscy)\s*(?:\(\s*)?($number)""",
        RegexOption.IGNORE_CASE,
    )
    private val coordinateTag = Regex(
        """\\(pos|org|move)\s*\(([^)]*)\)""",
        RegexOption.IGNORE_CASE,
    )
    private val drawingModeTag = Regex("""\\p\s*([+-]?\d+)""", RegexOption.IGNORE_CASE)
    private val vectorClipTag = Regex("""\\i?clip\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
    private val numberToken = Regex(number)
    private val drawingCommand = Regex("""(?:^|[\s,])(?:m|n|l|b|s|p|c)(?:[\s,]|$)""", RegexOption.IGNORE_CASE)

    fun inspect(document: AssDocument): List<AssRendererRisk> {
        val styleByName = document.styles.associateBy { it.name }
        val reportedStyles = hashSetOf<String>()
        val out = mutableListOf<AssRendererRisk>()

        document.events.forEach { event ->
            if (event.comment) return@forEach

            val style = styleByName[event.style]
            if (style != null && reportedStyles.add(style.name)) {
                val styleRisk = listOf(
                    "Style Angle" to style.angle,
                    "Style ScaleX" to style.scaleX,
                    "Style ScaleY" to style.scaleY,
                ).firstOrNull { (_, value) -> !value.isFinite() || abs(value) >= EXTREME_SCALAR_LIMIT }
                if (styleRisk != null) {
                    out += AssRendererRisk(
                        eventId = event.id,
                        kind = AssRendererRiskKind.EXTREME_NUMERIC,
                        message = styleRisk.first + "=" + styleRisk.second +
                            " 超出预览安全阈值；Raw ASS 保留，但 native 预览应暂停",
                    )
                }
            }

            inspectEvent(event, out)
        }
        return out
    }

    private fun inspectEvent(event: AssEvent, out: MutableList<AssRendererRisk>) {
        var numericReported = false
        var drawingCoordinateReported = false
        var oversizedDrawingReported = false
        var drawingChars = 0
        var drawingNumbers = 0

        fun reportNumeric(label: String, value: Double) {
            if (numericReported) return
            numericReported = true
            out += AssRendererRisk(
                eventId = event.id,
                kind = AssRendererRiskKind.EXTREME_NUMERIC,
                message = label + "=" + value + " 超出预览安全阈值；原始字幕不会被改写",
            )
        }

        fun scanDrawingPayload(payload: String, label: String) {
            if (oversizedDrawingReported) return

            drawingChars += payload.length
            if (drawingChars > MAX_DRAWING_TEXT_CHARS) {
                oversizedDrawingReported = true
                out += AssRendererRisk(
                    eventId = event.id,
                    kind = AssRendererRiskKind.OVERSIZED_DRAWING,
                    message = label + " 数据超过 " + MAX_DRAWING_TEXT_CHARS +
                        " 字符；为避免 native renderer 内存失控，预览暂停",
                )
                // The document is already blocked. Avoid tokenizing an
                // arbitrarily large remainder merely for a second diagnostic.
                return
            }

            for (match in numberToken.findAll(payload)) {
                val value = match.value.toDoubleOrNull() ?: continue
                if (!drawingCoordinateReported && (!value.isFinite() || abs(value) >= EXTREME_COORDINATE_LIMIT)) {
                    drawingCoordinateReported = true
                    out += AssRendererRisk(
                        eventId = event.id,
                        kind = AssRendererRiskKind.EXTREME_DRAWING_COORDINATE,
                        message = label + " 坐标 " + value + " 超出预览安全阈值；原始 Drawing 保留",
                    )
                }

                drawingNumbers++
                if (drawingNumbers > MAX_DRAWING_NUMBERS) {
                    oversizedDrawingReported = true
                    out += AssRendererRisk(
                        eventId = event.id,
                        kind = AssRendererRiskKind.OVERSIZED_DRAWING,
                        message = label + " 数值 token 超过 " + MAX_DRAWING_NUMBERS +
                            "；为避免 native renderer 内存失控，预览暂停",
                    )
                    return
                }
            }
        }

        val text = event.text
        val blocks = overrideBlock.findAll(text).toList()

        for (block in blocks) {
            for (match in scalarTag.findAll(block.value)) {
                val value = match.groupValues[2].toDoubleOrNull() ?: continue
                if (!value.isFinite() || abs(value) >= EXTREME_SCALAR_LIMIT) {
                    reportNumeric("\\" + match.groupValues[1], value)
                    break
                }
            }
            if (!numericReported) {
                for (match in coordinateTag.findAll(block.value)) {
                    val tag = match.groupValues[1].lowercase()
                    val values = numberToken.findAll(match.groupValues[2])
                        .mapNotNull { it.value.toDoubleOrNull() }
                        .toList()
                    val coordinates = if (tag == "move") values.take(4) else values.take(2)
                    val extreme = coordinates.firstOrNull {
                        !it.isFinite() || abs(it) >= EXTREME_COORDINATE_LIMIT
                    }
                    if (extreme != null) {
                        reportNumeric("\\" + tag, extreme)
                        break
                    }
                }
            }
            for (match in vectorClipTag.findAll(block.value)) {
                val payload = match.groupValues[1]
                if (drawingCommand.containsMatchIn(payload)) {
                    scanDrawingPayload(payload, "Vector clip")
                }
            }
        }

        var drawingMode = 0
        var cursor = 0
        for (block in blocks) {
            if (drawingMode > 0 && block.range.first > cursor) {
                scanDrawingPayload(text.substring(cursor, block.range.first), "ASS Drawing")
            }
            drawingModeTag.findAll(block.value).forEach { match ->
                drawingMode = match.groupValues[1].toIntOrNull()?.coerceAtLeast(0) ?: drawingMode
            }
            cursor = block.range.last + 1
        }
        if (drawingMode > 0 && cursor < text.length) {
            scanDrawingPayload(text.substring(cursor), "ASS Drawing")
        }
    }
}

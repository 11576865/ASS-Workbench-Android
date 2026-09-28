package io.github.assworkbench.fonts

data class RendererFontSelection(
    val requestedFamily: String,
    val weight: Int?,
    val slant: Int?,
    val selectedSource: String,
    val faceIndex: Int?,
    val selectedName: String,
    val raw: String,
)

data class RendererDiagnosticSnapshot(
    val requestedProvider: String,
    val detectedProvider: String?,
    val setupStarted: Boolean,
    val setupCompleted: Boolean,
    val fontSelections: List<RendererFontSelection>,
    val glyphFallbackRequests: Int,
    val fallbackFailures: Int,
    val warnings: List<String>,
    val rawTail: List<String>,
) {
    val providerMatchesRequest: Boolean
        get() = detectedProvider?.contains(requestedProvider, ignoreCase = true) == true

    val providerReady: Boolean
        get() = requestedProvider.equals("none", ignoreCase = true) ||
            (providerMatchesRequest && setupCompleted)

    fun summaryLines(limitSelections: Int = 3): List<String> = buildList {
        val detected = detectedProvider ?: "未检测到"
        add(
            "Provider：requested=$requestedProvider · detected=$detected · " +
                if (providerReady) "ready" else "not-ready",
        )
        add(
            "Fontselect：${fontSelections.size} matches · " +
                "$glyphFallbackRequests glyph fallback · $fallbackFailures failed",
        )
        fontSelections.takeLast(limitSelections).forEach { selection ->
            add(
                "Font：${selection.requestedFamily} → ${selection.selectedName} " +
                    "@ ${selection.selectedSource}" +
                    (selection.faceIndex?.let { " [face=$it]" } ?: ""),
            )
        }
        warnings.takeLast(2).forEach { add("Warning：$it") }
    }
}

object RendererLogParser {
    private val provider = Regex("""Using font provider\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val fontSelect = Regex(
        """fontselect:\s*\((.*),\s*(\d+),\s*(\d+)\)\s*->\s*(.*),\s*(-?\d+),\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(
        lines: Iterable<String>,
        requestedProvider: String,
        rawTailLimit: Int = 24,
    ): RendererDiagnosticSnapshot {
        var detectedProvider: String? = null
        var setupStarted = false
        var setupCompleted = false
        var glyphFallbackRequests = 0
        var fallbackFailures = 0
        val selections = mutableListOf<RendererFontSelection>()
        val warnings = mutableListOf<String>()
        val raw = mutableListOf<String>()

        for (line0 in lines) {
            val line = line0.trim()
            if (line.isBlank()) continue
            raw += line

            if (line.contains("Setting up fonts", ignoreCase = true)) {
                setupStarted = true
            }
            if (setupStarted && line.endsWith("Done.", ignoreCase = true)) {
                setupCompleted = true
            }

            provider.find(line)?.let { match ->
                detectedProvider = match.groupValues[1].trim()
            }

            if (line.contains("Glyph 0x", ignoreCase = true) &&
                line.contains("selecting one more font", ignoreCase = true)
            ) {
                glyphFallbackRequests++
            }

            if (line.contains("fontselect: failed to find any fallback", ignoreCase = true)) {
                fallbackFailures++
                warnings += line.substringAfter("fontselect:", line).trim()
            }

            fontSelect.find(line)?.let { match ->
                selections += RendererFontSelection(
                    requestedFamily = match.groupValues[1].trim(),
                    weight = match.groupValues[2].toIntOrNull(),
                    slant = match.groupValues[3].toIntOrNull(),
                    selectedSource = match.groupValues[4].trim(),
                    faceIndex = match.groupValues[5].toIntOrNull(),
                    selectedName = match.groupValues[6].trim(),
                    raw = line,
                )
            }
        }

        return RendererDiagnosticSnapshot(
            requestedProvider = requestedProvider,
            detectedProvider = detectedProvider,
            setupStarted = setupStarted,
            setupCompleted = setupCompleted,
            fontSelections = selections,
            glyphFallbackRequests = glyphFallbackRequests,
            fallbackFailures = fallbackFailures,
            warnings = warnings,
            rawTail = raw.takeLast(rawTailLimit),
        )
    }
}

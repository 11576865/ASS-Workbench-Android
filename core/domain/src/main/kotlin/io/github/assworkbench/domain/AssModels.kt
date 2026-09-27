package io.github.assworkbench.domain

data class AssStyle(
    val name: String = "Default",
    val fontName: String = "Arial",
    val fontSize: Double = 48.0,
    val primaryColor: String = "&H00FFFFFF",
    val secondaryColor: String = "&H000000FF",
    val outlineColor: String = "&H00000000",
    val backColor: String = "&H64000000",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeOut: Boolean = false,
    val scaleX: Double = 100.0,
    val scaleY: Double = 100.0,
    val spacing: Double = 0.0,
    val angle: Double = 0.0,
    val borderStyle: Int = 1,
    val outline: Double = 2.0,
    val shadow: Double = 2.0,
    val alignment: Int = 2,
    val marginL: Int = 10,
    val marginR: Int = 10,
    val marginV: Int = 10,
    val encoding: Int = 1,
)

data class AssEvent(
    val id: Long,
    val layer: Int = 0,
    val start: SubTime,
    val end: SubTime,
    val style: String = "Default",
    val name: String = "",
    val marginL: Int = 0,
    val marginR: Int = 0,
    val marginV: Int = 0,
    val effect: String = "",
    val text: String,
    val comment: Boolean = false,
) {
    init {
        require(end >= start) { "Subtitle end must not precede start" }
    }
}

data class RawSection(
    val name: String,
    val lines: List<String>,
)

data class AssDocument(
    val preamble: List<String> = emptyList(),
    val scriptInfo: LinkedHashMap<String, String> = linkedMapOf(
        "ScriptType" to "v4.00+",
        "PlayResX" to "1920",
        "PlayResY" to "1080",
        "ScaledBorderAndShadow" to "yes",
    ),
    val styles: List<AssStyle> = listOf(AssStyle()),
    val events: List<AssEvent> = emptyList(),
    val unknownSections: List<RawSection> = emptyList(),
) {
    val playResX: Int get() = scriptInfo["PlayResX"]?.toIntOrNull() ?: 1920
    val playResY: Int get() = scriptInfo["PlayResY"]?.toIntOrNull() ?: 1080

    fun activeEvents(position: SubTime): List<AssEvent> =
        events.asSequence()
            .filter { position >= it.start && position < it.end }
            .sortedWith(compareBy<AssEvent> { it.layer }.thenBy { it.id })
            .toList()
}

data class SubtitleProject(
    val title: String = "Untitled",
    val videoUri: String? = null,
    val subtitleUri: String? = null,
    val splitRatio: Float = 0.56f,
)

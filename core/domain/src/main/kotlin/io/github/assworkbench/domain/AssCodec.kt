package io.github.assworkbench.domain

import java.util.Locale

object AssCodec {
    private val defaultStyleFormat = listOf(
        "Name", "Fontname", "Fontsize", "PrimaryColour", "SecondaryColour", "OutlineColour", "BackColour",
        "Bold", "Italic", "Underline", "StrikeOut", "ScaleX", "ScaleY", "Spacing", "Angle", "BorderStyle",
        "Outline", "Shadow", "Alignment", "MarginL", "MarginR", "MarginV", "Encoding",
    )
    private val defaultEventFormat = listOf(
        "Layer", "Start", "End", "Style", "Name", "MarginL", "MarginR", "MarginV", "Effect", "Text",
    )

    fun parse(text: String): AssDocument {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val preamble = mutableListOf<String>()
        val sections = mutableListOf<RawSection>()
        var sectionName: String? = null
        var sectionLines = mutableListOf<String>()

        fun flush() {
            val name = sectionName ?: return
            sections += RawSection(name, sectionLines.toList())
            sectionLines = mutableListOf()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) {
                flush()
                sectionName = trimmed.substring(1, trimmed.length - 1)
            } else if (sectionName == null) {
                preamble += line
            } else {
                sectionLines += line
            }
        }
        flush()

        val scriptInfo = linkedMapOf<String, String>()
        val styles = mutableListOf<AssStyle>()
        val events = mutableListOf<AssEvent>()
        val unknown = mutableListOf<RawSection>()
        var nextEventId = 1L

        for (section in sections) {
            when (section.name.lowercase(Locale.ROOT)) {
                "script info" -> parseScriptInfo(section.lines, scriptInfo)
                "v4+ styles" -> styles += parseStyles(section.lines)
                "events" -> {
                    val parsed = parseEvents(section.lines, nextEventId)
                    events += parsed
                    nextEventId += parsed.size
                }
                else -> unknown += section
            }
        }

        if (scriptInfo.isEmpty()) {
            scriptInfo["ScriptType"] = "v4.00+"
            scriptInfo["PlayResX"] = "1920"
            scriptInfo["PlayResY"] = "1080"
            scriptInfo["ScaledBorderAndShadow"] = "yes"
        }

        return AssDocument(
            preamble = preamble,
            scriptInfo = LinkedHashMap(scriptInfo),
            styles = styles.ifEmpty { listOf(AssStyle()) },
            events = events,
            unknownSections = unknown,
        )
    }

    fun write(document: AssDocument): String = buildString {
        if (document.preamble.isNotEmpty()) {
            append(document.preamble.joinToString("\n").trimEnd())
            append("\n")
        }
        append("[Script Info]\n")
        document.scriptInfo.forEach { (key, value) -> append(key).append(": ").append(value).append('\n') }
        append("\n[V4+ Styles]\n")
        append("Format: ").append(defaultStyleFormat.joinToString(", ")).append('\n')
        document.styles.forEach { style -> append(styleToLine(style)).append('\n') }
        append("\n[Events]\n")
        append("Format: ").append(defaultEventFormat.joinToString(", ")).append('\n')
        document.events.forEach { event -> append(eventToLine(event)).append('\n') }
        document.unknownSections.forEach { section ->
            append('\n').append('[').append(section.name).append("]\n")
            if (section.lines.isNotEmpty()) append(section.lines.joinToString("\n")).append('\n')
        }
    }

    private fun parseScriptInfo(lines: List<String>, target: LinkedHashMap<String, String>) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith(';')) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            target[line.substring(0, colon).trim()] = line.substring(colon + 1).trim()
        }
    }

    private fun parseStyles(lines: List<String>): List<AssStyle> {
        var format = defaultStyleFormat
        val result = mutableListOf<AssStyle>()
        for (line in lines) {
            when {
                line.startsWith("Format:", ignoreCase = true) ->
                    format = line.substringAfter(':').split(',').map(String::trim)
                line.startsWith("Style:", ignoreCase = true) -> {
                    val values = splitCsvLimit(line.substringAfter(':'), format.size)
                    val map = format.zip(values).associate { it.first.lowercase(Locale.ROOT) to it.second.trim() }
                    result += AssStyle(
                        name = map["name"].orEmpty().ifBlank { "Default" },
                        fontName = map["fontname"].orEmpty().ifBlank { "Arial" },
                        fontSize = map["fontsize"]?.toDoubleOrNull() ?: 48.0,
                        primaryColor = map["primarycolour"].orEmpty().ifBlank { "&H00FFFFFF" },
                        secondaryColor = map["secondarycolour"].orEmpty().ifBlank { "&H000000FF" },
                        outlineColor = map["outlinecolour"].orEmpty().ifBlank { "&H00000000" },
                        backColor = map["backcolour"].orEmpty().ifBlank { "&H64000000" },
                        bold = parseAssBool(map["bold"]),
                        italic = parseAssBool(map["italic"]),
                        underline = parseAssBool(map["underline"]),
                        strikeOut = parseAssBool(map["strikeout"]),
                        scaleX = map["scalex"]?.toDoubleOrNull() ?: 100.0,
                        scaleY = map["scaley"]?.toDoubleOrNull() ?: 100.0,
                        spacing = map["spacing"]?.toDoubleOrNull() ?: 0.0,
                        angle = map["angle"]?.toDoubleOrNull() ?: 0.0,
                        borderStyle = map["borderstyle"]?.toIntOrNull() ?: 1,
                        outline = map["outline"]?.toDoubleOrNull() ?: 2.0,
                        shadow = map["shadow"]?.toDoubleOrNull() ?: 2.0,
                        alignment = map["alignment"]?.toIntOrNull()?.coerceIn(1, 9) ?: 2,
                        marginL = map["marginl"]?.toIntOrNull() ?: 10,
                        marginR = map["marginr"]?.toIntOrNull() ?: 10,
                        marginV = map["marginv"]?.toIntOrNull() ?: 10,
                        encoding = map["encoding"]?.toIntOrNull() ?: 1,
                    )
                }
            }
        }
        return result
    }

    private fun parseEvents(lines: List<String>, firstId: Long): List<AssEvent> {
        var format = defaultEventFormat
        val result = mutableListOf<AssEvent>()
        var id = firstId
        for (line in lines) {
            when {
                line.startsWith("Format:", ignoreCase = true) ->
                    format = line.substringAfter(':').split(',').map(String::trim)
                line.startsWith("Dialogue:", ignoreCase = true) || line.startsWith("Comment:", ignoreCase = true) -> {
                    val comment = line.startsWith("Comment:", ignoreCase = true)
                    val values = splitCsvLimit(line.substringAfter(':'), format.size)
                    val map = format.zip(values).associate { it.first.lowercase(Locale.ROOT) to it.second.trim() }
                    val start = runCatching { SubTime.parseAss(map["start"].orEmpty()) }.getOrElse { SubTime.ZERO }
                    val end = runCatching { SubTime.parseAss(map["end"].orEmpty()) }.getOrElse { start }
                    result += AssEvent(
                        id = id++,
                        layer = map["layer"]?.toIntOrNull() ?: map["marked"]?.toIntOrNull() ?: 0,
                        start = start,
                        end = if (end >= start) end else start,
                        style = map["style"].orEmpty().ifBlank { "Default" },
                        name = map["name"].orEmpty(),
                        marginL = map["marginl"]?.toIntOrNull() ?: 0,
                        marginR = map["marginr"]?.toIntOrNull() ?: 0,
                        marginV = map["marginv"]?.toIntOrNull() ?: 0,
                        effect = map["effect"].orEmpty(),
                        text = map["text"].orEmpty(),
                        comment = comment,
                    )
                }
            }
        }
        return result
    }

    private fun styleToLine(s: AssStyle): String = "Style: " + listOf(
        s.name, s.fontName, trimDouble(s.fontSize), s.primaryColor, s.secondaryColor, s.outlineColor, s.backColor,
        assBool(s.bold), assBool(s.italic), assBool(s.underline), assBool(s.strikeOut), trimDouble(s.scaleX), trimDouble(s.scaleY),
        trimDouble(s.spacing), trimDouble(s.angle), s.borderStyle.toString(), trimDouble(s.outline), trimDouble(s.shadow),
        s.alignment.toString(), s.marginL.toString(), s.marginR.toString(), s.marginV.toString(), s.encoding.toString(),
    ).joinToString(",")

    private fun eventToLine(e: AssEvent): String = (if (e.comment) "Comment: " else "Dialogue: ") + listOf(
        e.layer.toString(), e.start.toAss(), e.end.toAss(), e.style, e.name, e.marginL.toString(), e.marginR.toString(),
        e.marginV.toString(), e.effect, e.text,
    ).joinToString(",")

    private fun splitCsvLimit(value: String, fieldCount: Int): List<String> {
        if (fieldCount <= 1) return listOf(value)
        val result = ArrayList<String>(fieldCount)
        var remaining = value
        repeat(fieldCount - 1) {
            val index = remaining.indexOf(',')
            if (index < 0) {
                result += remaining
                remaining = ""
            } else {
                result += remaining.substring(0, index)
                remaining = remaining.substring(index + 1)
            }
        }
        result += remaining
        while (result.size < fieldCount) result += ""
        return result
    }

    private fun parseAssBool(value: String?): Boolean = value?.trim() in setOf("-1", "1", "true", "True")
    private fun assBool(value: Boolean): String = if (value) "-1" else "0"
    private fun trimDouble(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

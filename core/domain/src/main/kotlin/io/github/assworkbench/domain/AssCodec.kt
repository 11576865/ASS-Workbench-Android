package io.github.assworkbench.domain

import java.util.Locale

object AssCodec {
    private val knownStyleFields = setOf(
        "name", "fontname", "fontsize", "primarycolour", "secondarycolour", "outlinecolour", "backcolour",
        "bold", "italic", "underline", "strikeout", "scalex", "scaley", "spacing", "angle", "borderstyle",
        "outline", "shadow", "alignment", "marginl", "marginr", "marginv", "encoding",
    )
    private val knownEventFields = setOf(
        "layer", "marked", "start", "end", "style", "name", "actor", "marginl", "marginr", "marginv", "effect", "text",
    )

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
        val scriptInfoExtras = mutableListOf<String>()
        val styleExtras = mutableListOf<String>()
        val eventExtras = mutableListOf<String>()
        var styleFormat: List<String> = emptyList()
        var eventFormat: List<String> = emptyList()
        val unknown = mutableListOf<RawSection>()
        var nextEventId = 1L

        for (section in sections) {
            when (section.name.lowercase(Locale.ROOT)) {
                "script info" -> parseScriptInfo(section.lines, scriptInfo, scriptInfoExtras)
                "v4+ styles" -> styles += parseStyles(section.lines, styleExtras) { styleFormat = it }
                "events" -> {
                    val parsed = parseEvents(section.lines, nextEventId, eventExtras) { eventFormat = it }
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
            scriptInfoExtras = scriptInfoExtras,
            styleSectionExtras = styleExtras,
            eventSectionExtras = eventExtras,
            styleFormat = styleFormat,
            eventFormat = eventFormat,
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
        appendExtras(document.scriptInfoExtras)
        append("\n[V4+ Styles]\n")
        val styleFormat = document.styleFormat.ifEmpty { defaultStyleFormat }
        append("Format: ").append(styleFormat.joinToString(", ")).append('\n')
        document.styles.forEach { style -> append(styleToLine(style, styleFormat)).append('\n') }
        appendExtras(document.styleSectionExtras)
        append("\n[Events]\n")
        val eventFormat = document.eventFormat.ifEmpty { defaultEventFormat }
        append("Format: ").append(eventFormat.joinToString(", ")).append('\n')
        document.events.forEach { event -> append(eventToLine(event, eventFormat)).append('\n') }
        appendExtras(document.eventSectionExtras)
        document.unknownSections.forEach { section ->
            append('\n').append('[').append(section.name).append("]\n")
            if (section.lines.isNotEmpty()) append(section.lines.joinToString("\n")).append('\n')
        }
    }

    private fun parseScriptInfo(
        lines: List<String>,
        target: LinkedHashMap<String, String>,
        extras: MutableList<String>,
    ) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith(';')) {
                extras += line
                continue
            }
            val colon = line.indexOf(':')
            if (colon <= 0) {
                extras += line
                continue
            }
            target[line.substring(0, colon).trim()] = line.substring(colon + 1).trim()
        }
    }

    private fun parseStyles(
        lines: List<String>,
        extras: MutableList<String>,
        onFormat: (List<String>) -> Unit,
    ): List<AssStyle> {
        var format = defaultStyleFormat
        val result = mutableListOf<AssStyle>()
        for (line in lines) {
            when {
                line.startsWith("Format:", ignoreCase = true) -> {
                    format = line.substringAfter(':').split(',').map(String::trim)
                    onFormat(format)
                }
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
                        extraFields = map.filterKeys { it !in knownStyleFields },
                    )
                }
                else -> extras += line
            }
        }
        return result
    }

    private fun parseEvents(
        lines: List<String>,
        firstId: Long,
        extras: MutableList<String>,
        onFormat: (List<String>) -> Unit,
    ): List<AssEvent> {
        var format = defaultEventFormat
        val result = mutableListOf<AssEvent>()
        var id = firstId
        for (line in lines) {
            when {
                line.startsWith("Format:", ignoreCase = true) -> {
                    format = line.substringAfter(':').split(',').map(String::trim)
                    onFormat(format)
                }
                line.startsWith("Dialogue:", ignoreCase = true) || line.startsWith("Comment:", ignoreCase = true) -> {
                    val comment = line.startsWith("Comment:", ignoreCase = true)
                    val values = splitEventFields(line.substringAfter(':'), format)
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
                        extraFields = map.filterKeys { it !in knownEventFields },
                    )
                }
                else -> extras += line
            }
        }
        return result
    }

    private fun StringBuilder.appendExtras(lines: List<String>) {
        if (lines.isEmpty()) return
        lines.forEach { append(it).append('\n') }
    }

    private fun styleToLine(s: AssStyle, format: List<String>): String =
        "Style: " + format.joinToString(",") { field -> styleFieldValue(s, field) }

    private fun styleFieldValue(s: AssStyle, field: String): String = when (field.lowercase(Locale.ROOT)) {
        "name" -> s.name
        "fontname" -> s.fontName
        "fontsize" -> trimDouble(s.fontSize)
        "primarycolour" -> s.primaryColor
        "secondarycolour" -> s.secondaryColor
        "outlinecolour" -> s.outlineColor
        "backcolour" -> s.backColor
        "bold" -> assBool(s.bold)
        "italic" -> assBool(s.italic)
        "underline" -> assBool(s.underline)
        "strikeout" -> assBool(s.strikeOut)
        "scalex" -> trimDouble(s.scaleX)
        "scaley" -> trimDouble(s.scaleY)
        "spacing" -> trimDouble(s.spacing)
        "angle" -> trimDouble(s.angle)
        "borderstyle" -> s.borderStyle.toString()
        "outline" -> trimDouble(s.outline)
        "shadow" -> trimDouble(s.shadow)
        "alignment" -> s.alignment.toString()
        "marginl" -> s.marginL.toString()
        "marginr" -> s.marginR.toString()
        "marginv" -> s.marginV.toString()
        "encoding" -> s.encoding.toString()
        else -> s.extraFields[field.lowercase(Locale.ROOT)].orEmpty()
    }

    private fun eventToLine(e: AssEvent, format: List<String>): String =
        (if (e.comment) "Comment: " else "Dialogue: ") +
            format.joinToString(",") { field -> eventFieldValue(e, field) }

    private fun eventFieldValue(e: AssEvent, field: String): String = when (field.lowercase(Locale.ROOT)) {
        "layer", "marked" -> e.layer.toString()
        "start" -> e.start.toAss()
        "end" -> e.end.toAss()
        "style" -> e.style
        "name", "actor" -> e.name
        "marginl" -> e.marginL.toString()
        "marginr" -> e.marginR.toString()
        "marginv" -> e.marginV.toString()
        "effect" -> e.effect
        "text" -> e.text
        else -> e.extraFields[field.lowercase(Locale.ROOT)].orEmpty()
    }

    /**
     * Event Text is the one ASS field that routinely contains literal commas.
     * Standard ASS keeps Text last, but preserved vendor/custom columns may
     * legally appear after it in the observed Format line. In that case,
     * consume fields before Text from the left and trailing custom fields from
     * the right so commas inside Text stay part of the subtitle payload.
     */
    private fun splitEventFields(value: String, format: List<String>): List<String> {
        val textIndex = format.indexOfFirst { it.equals("Text", ignoreCase = true) }
        if (textIndex < 0 || textIndex == format.lastIndex) {
            return splitCsvLimit(value, format.size)
        }

        val prefix = ArrayList<String>(textIndex)
        var remaining = value
        repeat(textIndex) {
            val comma = remaining.indexOf(',')
            if (comma < 0) {
                prefix += remaining
                remaining = ""
            } else {
                prefix += remaining.substring(0, comma)
                remaining = remaining.substring(comma + 1)
            }
        }

        val suffixCount = format.size - textIndex - 1
        val suffix = ArrayDeque<String>()
        repeat(suffixCount) {
            val comma = remaining.lastIndexOf(',')
            if (comma < 0) {
                suffix.addFirst(remaining)
                remaining = ""
            } else {
                suffix.addFirst(remaining.substring(comma + 1))
                remaining = remaining.substring(0, comma)
            }
        }

        return buildList(format.size) {
            addAll(prefix)
            add(remaining)
            addAll(suffix)
            while (size < format.size) add("")
        }
    }

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

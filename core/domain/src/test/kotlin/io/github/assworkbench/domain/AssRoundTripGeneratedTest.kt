package io.github.assworkbench.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class AssRoundTripGeneratedTest {
    @Test
    fun deterministicGeneratedDocumentsRemainSemanticallyStable() {
        val random = Random(0xA551)
        repeat(128) { caseIndex ->
            val styles = (0 until 1 + random.nextInt(4)).map { index ->
                AssStyle(
                    name = "Style_" + index,
                    fontName = listOf("Arial", "Noto Sans CJK SC", "Noto Sans CJK JP")[random.nextInt(3)],
                    fontSize = 20.0 + random.nextInt(70),
                    bold = random.nextBoolean(),
                    italic = random.nextBoolean(),
                    spacing = random.nextInt(-4, 10).toDouble(),
                    outline = random.nextInt(0, 8).toDouble(),
                    shadow = random.nextInt(0, 5).toDouble(),
                    alignment = random.nextInt(1, 10),
                    extraFields = mapOf("vendormeta" to ("style-" + caseIndex + "-" + index)),
                )
            }
            val events = (0 until 1 + random.nextInt(12)).map { index ->
                val start = random.nextLong(0, 120_000)
                val duration = random.nextLong(100, 8_000)
                val style = styles[random.nextInt(styles.size)].name
                val tag = when (random.nextInt(6)) {
                    0 -> "{\\pos(" + random.nextInt(0, 1920) + "," + random.nextInt(0, 1080) + ")}"
                    1 -> "{\\bord" + random.nextInt(0, 6) + "\\blur" + random.nextInt(0, 4) + "}"
                    2 -> "{\\t(0,500,\\fscx" + (80 + random.nextInt(60)) + "\\fscy" + (80 + random.nextInt(60)) + ")}"
                    3 -> "{\\clip(0,0," + (200 + random.nextInt(800)) + "," + (100 + random.nextInt(500)) + ")}"
                    4 -> "{\\unknown" + random.nextInt(1, 99) + "(x,y)}"
                    else -> ""
                }
                AssEvent(
                    id = index + 1L,
                    layer = random.nextInt(-2, 8),
                    start = SubTime(start),
                    end = SubTime(start + duration),
                    style = style,
                    name = "actor_" + index,
                    marginL = random.nextInt(0, 80),
                    marginR = random.nextInt(0, 80),
                    marginV = random.nextInt(0, 80),
                    effect = if (index % 3 == 0) "fx-" + index else "",
                    text = tag + listOf("Hello", "中文", "日本語", "العربية", "emoji 😀")[random.nextInt(5)] + " #" + index,
                    comment = random.nextBoolean(),
                    extraFields = mapOf("vendorid" to ("event-" + caseIndex + "-" + index)),
                )
            }
            val document = AssDocument(
                preamble = listOf("; generated " + caseIndex),
                scriptInfo = linkedMapOf(
                    "ScriptType" to "v4.00+",
                    "PlayResX" to "1920",
                    "PlayResY" to "1080",
                    "ScaledBorderAndShadow" to "yes",
                    "Title" to ("Generated " + caseIndex),
                ),
                styles = styles,
                events = events,
                scriptInfoExtras = listOf("; script opaque " + caseIndex),
                styleSectionExtras = listOf("; style opaque " + caseIndex),
                eventSectionExtras = listOf("; event opaque " + caseIndex),
                styleFormat = listOf(
                    "Name","Fontname","Fontsize","PrimaryColour","SecondaryColour","OutlineColour","BackColour",
                    "Bold","Italic","Underline","StrikeOut","ScaleX","ScaleY","Spacing","Angle","BorderStyle",
                    "Outline","Shadow","Alignment","MarginL","MarginR","MarginV","Encoding","VendorMeta"
                ),
                eventFormat = listOf(
                    "Layer","Start","End","Style","Name","MarginL","MarginR","MarginV","Effect","Text","VendorID"
                ),
                unknownSections = listOf(
                    RawSection("Vendor Section " + caseIndex, listOf("Opaque: keep-" + caseIndex, "NoColonLine"))
                ),
            )

            val serialized = AssCodec.write(document)
            val report = AssRoundTripVerifier.verify(document, serialized)
            assertTrue(report.equivalent, "case " + caseIndex + ": " + report.summary)
        }
    }
}

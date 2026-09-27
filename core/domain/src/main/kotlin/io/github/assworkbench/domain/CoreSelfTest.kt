package io.github.assworkbench.domain

fun main() {
    val input = """
        [Script Info]
        ScriptType: v4.00+
        PlayResX: 1920
        PlayResY: 1080

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Noto Sans,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,3,2,2,30,30,30,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello, world
        Dialogue: 2,0:00:01.50,0:00:02.50,Default,B,0,0,0,,Overlap

        [Custom]
        Keep: this
    """.trimIndent()
    val doc = AssCodec.parse(input)
    check(doc.events.size == 2)
    check(doc.events.first().text == "Hello, world")
    check(doc.activeEvents(SubTime(1_750)).size == 2)
    check(AssCodec.write(doc).contains("[Custom]"))
    val history = UndoHistory(doc)
    history.commit(doc.copy(events = doc.events.dropLast(1)))
    check(history.undo().events.size == 2)
    check(history.redo().events.size == 1)
    println("ASS Workbench core self-test: OK")
}

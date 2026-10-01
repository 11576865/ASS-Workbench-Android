package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectManifestTest {
    @Test
    fun round_trips_project_manifest() {
        val input = AssWorkbenchProjectManifest(
            title = "双语 project",
            subtitleUri = "content://subtitle/a.ass",
            sourceFormat = SubtitleSourceFormat.ASS,
            videoUri = "content://video/a.mkv",
            workspaceMode = WorkspacePresentationMode.CANVAS_EXPERIMENTAL,
            importedFontUris = listOf("content://font/a.otf"),
        )
        assertEquals(input, AssWorkbenchProjectCodec.parse(AssWorkbenchProjectCodec.write(input)))
    }
}

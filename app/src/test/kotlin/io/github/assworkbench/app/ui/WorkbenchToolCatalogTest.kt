package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbenchToolCatalogTest {
    @Test fun mainAuthoringTasksHaveStableOrderAndKeepTheCompleteDirectory() {
        assertEquals(listOf(WorkbenchTool.SUBTITLES, WorkbenchTool.TEXT, WorkbenchTool.STYLE,
            WorkbenchTool.TIMELINE, WorkbenchTool.CAPABILITIES), mainAuthoringTools())
        assertEquals(5, mainAuthoringTools().distinct().size)
    }

    @Test fun specializedToolsKeepTheirTaskSelected() {
        assertEquals(WorkbenchTask.TYPESETTING, WorkbenchTool.POSITION.task)
        assertEquals(WorkbenchTask.TYPESETTING, WorkbenchTool.VECTOR_CLIP.task)
        assertEquals(WorkbenchTask.TIME, WorkbenchTool.FRAMES.task)
        assertEquals(WorkbenchTask.EFFECTS, WorkbenchTool.KARAOKE.task)
        assertEquals(WorkbenchTask.TEXT, WorkbenchTool.EVENT.task)
        assertTrue(WorkbenchTool.entries.all { it.task in WorkbenchTask.entries })
    }

    @Test fun selectingCurrentTaskKeepsTheSpecificTool() {
        assertEquals(WorkbenchTool.POSITION, WorkbenchTask.TYPESETTING.toolFor(WorkbenchTool.POSITION))
        assertEquals(WorkbenchTool.FRAMES, WorkbenchTask.TIME.toolFor(WorkbenchTool.FRAMES))
        assertEquals(WorkbenchTool.STYLE, WorkbenchTask.TYPESETTING.toolFor(WorkbenchTool.TEXT))
    }

    @Test fun subtitleNavigationDoesNotOfferThePreviousInspectorTools() {
        assertEquals(listOf(WorkbenchTool.SUBTITLES), fixedNavigationTools(WorkbenchToolGroup.NAVIGATION))
    }

    @Test fun fixedNavigationKeepsTheWorkspaceDirectoryReachable() {
        assertEquals(listOf(WorkbenchTool.CAPABILITIES), fixedNavigationTools(WorkbenchToolGroup.WORKSPACE))
        assertEquals(listOf(WorkbenchTool.STYLE, WorkbenchTool.POSITION, WorkbenchTool.VECTOR_CLIP),
            fixedNavigationTools(WorkbenchToolGroup.TYPESETTING))
    }

    @Test
    fun reclassificationRetainsEveryPersistedToolKey() {
        assertEquals(
            setOf("SUBTITLES", "TEXT", "TIMELINE", "FRAMES", "STYLE", "POSITION", "EFFECTS", "EVENT",
                "KARAOKE", "VECTOR_CLIP", "FONTS", "FONT_REQUIREMENTS", "QC", "COMPATIBILITY", "BATCH",
                "PROJECT", "DIAGNOSTICS", "CAPABILITIES"),
            WorkbenchTool.entries.map { it.name }.toSet(),
        )
        assertTrue(WorkbenchToolGroup.entries.all { group -> WorkbenchTool.entries.any { it.group == group } })
        assertTrue(WorkbenchTool.entries.all { it.initialGeometry(0f, 0f).isValid() })
    }

    @Test
    fun eventBindingAndDuplicationAreDeclaredByDescriptor() {
        assertTrue(WorkbenchTool.STYLE.descriptor.supportsPinnedEvent)
        assertTrue(WorkbenchTool.POSITION.descriptor.supportsPinnedEvent)
        assertTrue(WorkbenchTool.STYLE.descriptor.canDuplicate)
        assertTrue(WorkbenchTool.POSITION.descriptor.canDuplicate)
        assertTrue(!WorkbenchTool.PROJECT.descriptor.eventBindable)
        assertTrue(!WorkbenchTool.FONTS.descriptor.canDuplicate)
    }

    @Test
    fun selectionToolsDefaultToSelectionBinding() {
        assertEquals(
            io.github.assworkbench.app.ui.workspace.WorkspaceBinding.FollowSelection,
            WorkbenchTool.BATCH.descriptor.defaultBinding,
        )
        assertEquals(
            io.github.assworkbench.app.ui.workspace.WorkspaceBinding.FollowFocus,
            WorkbenchTool.POSITION.descriptor.defaultBinding,
        )
    }
}

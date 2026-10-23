package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedV11ScenePolicyTest {
    @Test fun geometryAndEffectsOpenExistingToolsAndKeepTheirSpecialists() {
        assertEquals(WorkbenchTool.STYLE, FixedV11WorkspaceMode.GEOMETRY.toolFor(WorkbenchTool.TEXT))
        assertEquals(WorkbenchTool.POSITION, FixedV11WorkspaceMode.GEOMETRY.toolFor(WorkbenchTool.POSITION))
        assertEquals(WorkbenchTool.VECTOR_CLIP, FixedV11WorkspaceMode.GEOMETRY.toolFor(WorkbenchTool.VECTOR_CLIP))
        assertEquals(WorkbenchTool.EFFECTS, FixedV11WorkspaceMode.EFFECTS.toolFor(WorkbenchTool.TEXT))
        assertEquals(WorkbenchTool.KARAOKE, FixedV11WorkspaceMode.EFFECTS.toolFor(WorkbenchTool.KARAOKE))
    }

    @Test fun returningFromSpecialistSceneReachesRealAuthoringTools() {
        assertEquals(WorkbenchTool.TEXT, FixedV11WorkspaceMode.SUBTITLE_EDIT.toolFor(WorkbenchTool.POSITION))
        assertEquals(WorkbenchTool.TIMELINE, FixedV11WorkspaceMode.PRECISE_TIMING.toolFor(WorkbenchTool.KARAOKE))
    }

    @Test fun originalSubtitleTimingSceneSwitchStillKeepsCurrentInspector() {
        for (tool in listOf(WorkbenchTool.TEXT, WorkbenchTool.EVENT, WorkbenchTool.FRAMES)) {
            assertEquals(tool, FixedV11WorkspaceMode.SUBTITLE_EDIT.toolFor(tool))
            assertEquals(tool, FixedV11WorkspaceMode.PRECISE_TIMING.toolFor(tool))
        }
    }

    @Test fun externalSpecialistSelectionProjectsItsOwnScene() {
        assertEquals(FixedV11WorkspaceMode.GEOMETRY,
            fixedV11SceneForTool(WorkbenchTool.VECTOR_CLIP, FixedV11WorkspaceMode.SUBTITLE_EDIT))
        assertEquals(FixedV11WorkspaceMode.EFFECTS,
            fixedV11SceneForTool(WorkbenchTool.KARAOKE, FixedV11WorkspaceMode.PRECISE_TIMING))
        assertEquals(FixedV11WorkspaceMode.SUBTITLE_EDIT,
            fixedV11SceneForTool(WorkbenchTool.TEXT, FixedV11WorkspaceMode.GEOMETRY))
        assertEquals(FixedV11WorkspaceMode.PRECISE_TIMING,
            fixedV11SceneForTool(WorkbenchTool.FRAMES, FixedV11WorkspaceMode.EFFECTS))
    }
}

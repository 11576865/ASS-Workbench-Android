package io.github.assworkbench.app.ui

import io.github.assworkbench.app.ui.workspace.WorkspaceBinding
import org.junit.Assert.*
import org.junit.Test

class FixedV11InspectorTabsTest {
    @Test fun switchingRequiresAnExistingTargetEqualToCurrentFocus() {
        assertTrue(fixedV11InspectorCanSwitch(WorkspaceBinding.FollowFocus, 39L, 39L))
        assertFalse(fixedV11InspectorCanSwitch(WorkspaceBinding.FollowFocus, null, 39L))
        assertFalse(fixedV11InspectorCanSwitch(WorkspaceBinding.FollowFocus, 38L, 39L))
        assertFalse(fixedV11InspectorCanSwitch(WorkspaceBinding.FollowFocus, 39L, null))
        assertFalse(fixedV11InspectorCanSwitch(WorkspaceBinding.FollowSelection, 39L, 39L))
        assertFalse(fixedV11InspectorCanSwitch(WorkspaceBinding.PinnedEvent(39L), 39L, 39L))
    }

    @Test fun sharedAndSpecialistEditorsProjectTheCorrectInspectorTab() {
        assertEquals(WorkbenchTool.EVENT, fixedV11InspectorTabFor(WorkbenchTool.TEXT))
        assertEquals(WorkbenchTool.EVENT, fixedV11InspectorTabFor(WorkbenchTool.EVENT))
        assertEquals(WorkbenchTool.STYLE, fixedV11InspectorTabFor(WorkbenchTool.STYLE))
        assertEquals(WorkbenchTool.POSITION, fixedV11InspectorTabFor(WorkbenchTool.VECTOR_CLIP))
        assertNull(fixedV11InspectorTabFor(WorkbenchTool.EFFECTS))
    }
}

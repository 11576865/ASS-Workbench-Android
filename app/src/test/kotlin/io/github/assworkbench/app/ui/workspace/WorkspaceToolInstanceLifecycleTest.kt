package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceToolInstanceLifecycleTest {
    @Test
    fun presenceAndDensityRoundTripThroughWorkspacePersistence() {
        val source = WorkspaceState(sessionId = 7L)
            .openPrimary("STYLE", WorkspaceBinding.PinnedEvent(42L))
            .updatePresence(WorkspaceState.primaryInstanceId("STYLE"), WorkspaceToolPresence.RESIDENT)
            .cycleContentDensity(WorkspaceState.primaryInstanceId("STYLE"))

        val restored = WorkspaceState.fromSaveableList(source.toSaveableList())
        val instance = restored.primary("STYLE")!!

        assertEquals(WorkspaceToolPresence.RESIDENT, instance.presence)
        assertEquals(ToolContentDensity.PRECISION, instance.contentDensity)
        assertEquals(WorkspaceBinding.PinnedEvent(42L), instance.binding)
        assertEquals(7L, restored.sessionId)
    }

    @Test
    fun legacyV2RowsStillRestoreWithDefaultLifecycle() {
        val restored = WorkspaceState.fromSaveableList(
            listOf(
                "workspace-v2",
                "0",
                "STYLE:primary",
                "9",
                listOf("STYLE:primary", "STYLE", "focus", "").joinToString("\u001F"),
            )
        )

        val instance = restored.primary("STYLE")!!
        assertEquals(WorkspaceToolPresence.TEMPORARY, instance.presence)
        assertEquals(ToolContentDensity.STANDARD, instance.contentDensity)
    }

    @Test
    fun hidingOtherTemporaryToolsKeepsResidentsVisible() {
        val base = WorkspaceState()
            .openPrimary("STYLE")
            .updatePresence(WorkspaceState.primaryInstanceId("STYLE"), WorkspaceToolPresence.RESIDENT)
            .openPrimary("POSITION")
            .openPrimary("TEXT")

        val next = base.hideOtherTemporary(WorkspaceState.primaryInstanceId("TEXT"))

        assertEquals(
            WorkspaceToolPresence.RESIDENT,
            next.primary("STYLE")!!.presence,
        )
        assertEquals(
            WorkspaceToolPresence.HIDDEN,
            next.primary("POSITION")!!.presence,
        )
        assertEquals(
            WorkspaceToolPresence.TEMPORARY,
            next.primary("TEXT")!!.presence,
        )
        assertTrue(next.tools.size == 3)
    }
}

package io.github.assworkbench.app.ui.workspace

import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.toEditorUiState
import io.github.assworkbench.app.ui.WorkbenchTool
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssStyle
import io.github.assworkbench.domain.SubTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceEditScopeResolverTest {
    private val document = AssDocument(
        styles = listOf(
            AssStyle(name = "Default"),
            AssStyle(name = "Signs"),
        ),
        events = listOf(
            AssEvent(41, start = SubTime(0), end = SubTime(1000), style = "Default", text = "A"),
            AssEvent(42, start = SubTime(0), end = SubTime(1000), style = "Default", text = "B"),
            AssEvent(87, start = SubTime(0), end = SubTime(1000), style = "Signs", text = "C"),
        ),
    )

    @Test
    fun sharedStyleExplainsContextAndAffectedCount() {
        val instance = WorkspaceToolInstance(
            id = "STYLE:primary",
            toolKey = "STYLE",
            binding = WorkspaceBinding.PinnedEvent(41),
        )

        val summary = WorkspaceEditScopeResolver.resolve(
            WorkbenchTool.STYLE,
            instance,
            document,
            editorUiState = uiState(focusedEventId = 87),
        )!!

        assertEquals("Style · Default", summary.who)
        assertEquals("共享 Style", summary.where)
        assertEquals(2, summary.howMany)
        assertEquals("Pinned #41", summary.binding)
        assertFalse(summary.unresolved)
    }

    @Test
    fun pinnedPositionDoesNotFollowGlobalFocus() {
        val instance = WorkspaceToolInstance(
            id = "POSITION:primary",
            toolKey = "POSITION",
            binding = WorkspaceBinding.PinnedEvent(41),
        )

        val summary = WorkspaceEditScopeResolver.resolve(
            WorkbenchTool.POSITION,
            instance,
            document,
            editorUiState = uiState(focusedEventId = 87),
        )!!

        assertEquals("Event #41", summary.who)
        assertEquals("Event Override", summary.where)
        assertEquals(1, summary.howMany)
    }

    @Test
    fun missingPinnedTargetIsExplicitlyUnresolved() {
        val instance = WorkspaceToolInstance(
            id = "POSITION:primary",
            toolKey = "POSITION",
            binding = WorkspaceBinding.PinnedEvent(404),
        )

        val summary = WorkspaceEditScopeResolver.resolve(
            WorkbenchTool.POSITION,
            instance,
            document,
            editorUiState = uiState(focusedEventId = 41),
        )!!

        assertTrue(summary.unresolved)
        assertEquals(0, summary.howMany)
        assertEquals("#404（目标已失效）", summary.who)
    }

    @Test
    fun batchUsesSelectionInsteadOfFocus() {
        val instance = WorkspaceToolInstance(
            id = "BATCH:primary",
            toolKey = "BATCH",
            binding = WorkspaceBinding.FollowSelection,
        )

        val summary = WorkspaceEditScopeResolver.resolve(
            WorkbenchTool.BATCH,
            instance,
            document,
            editorUiState = uiState(
                focusedEventId = 87,
                selectedEventIds = setOf(41, 42),
            ),
        )!!

        assertEquals("已选 2 条 Event", summary.who)
        assertEquals("批量 Event 变换", summary.where)
        assertEquals(2, summary.howMany)
    }

    private fun uiState(
        focusedEventId: Long?,
        selectedEventIds: Set<Long> = emptySet(),
    ) = EditorState(
        document = document,
        focusedEventId = focusedEventId,
        selectedEventIds = selectedEventIds,
        workspaceSessionId = 7L,
    ).toEditorUiState()

    @Test
    fun nonMutatingDirectoryHasNoEditScopeBanner() {
        val instance = WorkspaceToolInstance(
            id = "CAPABILITIES:primary",
            toolKey = "CAPABILITIES",
        )
        assertEquals(
            null,
            WorkspaceEditScopeResolver.resolve(
                WorkbenchTool.CAPABILITIES,
                instance,
                document,
                editorUiState = uiState(focusedEventId = 41),
            ),
        )
    }
}

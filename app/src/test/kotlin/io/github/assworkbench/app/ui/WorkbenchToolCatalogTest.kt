package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbenchToolCatalogTest {
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
}

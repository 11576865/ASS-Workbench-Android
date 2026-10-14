package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R3ToolCatalogTest {
    @Test fun designGroupsCoverExactlyEveryPersistedToolId() {
        val tools = r3CatalogGroups.flatMap { it.tools }
        assertEquals(18, tools.size)
        assertEquals(tools.size, tools.distinct().size)
        assertEquals(WorkbenchTool.entries.toSet(), tools.toSet())
        assertTrue(r3CatalogGroups.all { it.tools.isNotEmpty() })
    }
}

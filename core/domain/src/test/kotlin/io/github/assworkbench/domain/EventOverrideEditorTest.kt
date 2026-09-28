package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventOverrideEditorTest {
    @Test
    fun preservesUnknownTagsWhileReplacingManagedOnes() {
        val input = "{\\bord4\\pos(100,200)\\blur2}Hello"
        val output = EventOverrideEditor.update(
            text = input,
            x = 300.0,
            y = 400.0,
            blurRadius = 1.5,
            fadeInMs = 120,
            fadeOutMs = 180,
            enableSoftEntry = false,
        )
        assertTrue(output.contains("\\bord4"))
        assertTrue(output.contains("\\pos(300,400)"))
        assertTrue(output.contains("\\blur1.5"))
        assertTrue(output.contains("\\fad(120,180)"))
        assertFalse(output.contains("\\pos(100,200)"))
        assertTrue(output.endsWith("Hello"))
    }

    @Test
    fun inspectReadsManagedValues() {
        val text = "{\\pos(12.5,44)\\blur0.8\\fad(100,200)}Hi"
        val got = EventOverrideEditor.inspect(text)
        assertEquals(12.5, got.x)
        assertEquals(44.0, got.y)
        assertEquals(0.8, got.blur)
        assertEquals(100, got.fadeInMs)
        assertEquals(200, got.fadeOutMs)
    }

    @Test
    fun softEntryRoundTripsAsManagedSequence() {
        val withSoft = EventOverrideEditor.update(
            text = "Hello",
            x = null,
            y = null,
            blurRadius = null,
            fadeInMs = null,
            fadeOutMs = null,
            enableSoftEntry = true,
            softEntryMs = 160,
        )
        assertTrue(EventOverrideEditor.inspect(withSoft).softEntry)
        val removed = EventOverrideEditor.update(
            text = withSoft,
            x = null,
            y = null,
            blurRadius = null,
            fadeInMs = null,
            fadeOutMs = null,
            enableSoftEntry = false,
        )
        assertFalse(EventOverrideEditor.inspect(removed).softEntry)
        assertEquals("Hello", removed)
    }
}

package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssStyleInheritanceTest {
    @Test
    fun clearsDirectManagedOverridesButPreservesNestedTransformPayloads() {
        val source = "{\\fs56\\bord3\\t(0,500,\\fs80\\bord8)\\x-custom(foo)}A{\\fscx120\\1c&H00FF00&}B"

        val cleared = AssStyleInheritance.clearDirectManagedOverrides(source)

        assertEquals("{\\t(0,500,\\fs80\\bord8)\\x-custom(foo)}AB", cleared)
        assertTrue("\\t(0,500,\\fs80\\bord8)" in cleared)
        assertTrue("\\x-custom(foo)" in cleared)
    }

    @Test
    fun directManagedTagNamesExcludeTransformPayloadTags() {
        val source = "{\\t(0,500,\\fs80\\frz20)}A{\\bord4}B"

        val names = AssStyleInheritance.directManagedTagNames(source)

        assertEquals(setOf("bord"), names)
        assertFalse("fs" in names)
        assertFalse("frz" in names)
    }
}

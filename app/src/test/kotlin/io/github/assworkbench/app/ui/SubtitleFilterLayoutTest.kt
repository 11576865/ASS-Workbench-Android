package io.github.assworkbench.app.ui

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SubtitleFilterLayoutTest {
    @Test fun shortDualPaneAndImeReserveOnlyOneButtonForDialog() {
        for (height in listOf(191f, 240f, 319f)) {
            val policy = SubtitleFilterLayout.resolve(height)
            assertTrue(policy.useDialog)
            assertEquals(48f, policy.inlineHeightDp)
        }
    }
    @Test fun inlineFilterReservesMostOfThePaneForResultsAndList() {
        assertEquals(144f, SubtitleFilterLayout.resolve(360f).inlineHeightDp)
        assertEquals(260f, SubtitleFilterLayout.resolve(900f).inlineHeightDp)
        assertTrue(!SubtitleFilterLayout.resolve(360f).useDialog)
    }
}

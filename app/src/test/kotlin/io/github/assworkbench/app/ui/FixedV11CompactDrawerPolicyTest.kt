package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure policy checks for keyboard-driven height changes in a compact tablet. */
class FixedV11CompactDrawerPolicyTest {
    @Test fun openTabletDrawerSurvivesImeRelayoutIntoCompactHeight() {
        assertEquals(
            WorkbenchLayoutProfile.DUAL_PANE,
            FixedV11CompactDrawerPolicy.effectiveProfile(
                adaptive = WorkbenchLayoutProfile.COMPACT,
                widthDp = 1024f,
                drawerOpen = true,
            )
        )
    }

    @Test fun closingDrawerReturnsToActualAdaptiveProfile() {
        assertEquals(
            WorkbenchLayoutProfile.COMPACT,
            FixedV11CompactDrawerPolicy.effectiveProfile(
                adaptive = WorkbenchLayoutProfile.COMPACT,
                widthDp = 1024f,
                drawerOpen = false,
            )
        )
    }

    @Test fun phonesAndWideDesktopDoNotAcquireTemporaryDrawerProfile() {
        assertEquals(
            WorkbenchLayoutProfile.COMPACT,
            FixedV11CompactDrawerPolicy.effectiveProfile(
                adaptive = WorkbenchLayoutProfile.COMPACT,
                widthDp = 600f,
                drawerOpen = true,
            )
        )
        assertEquals(
            WorkbenchLayoutProfile.THREE_PANE,
            FixedV11CompactDrawerPolicy.effectiveProfile(
                adaptive = WorkbenchLayoutProfile.THREE_PANE,
                widthDp = 1440f,
                drawerOpen = true,
            )
        )
    }

    @Test fun normalDualPaneRemainsUnchanged() {
        assertEquals(
            WorkbenchLayoutProfile.DUAL_PANE,
            FixedV11CompactDrawerPolicy.effectiveProfile(
                adaptive = WorkbenchLayoutProfile.DUAL_PANE,
                widthDp = 900f,
                drawerOpen = false,
            )
        )
    }
}

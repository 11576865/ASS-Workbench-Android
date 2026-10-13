package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransformTimingUiPolicyTest {
    @Test fun fullEventAndOrdinaryIntegerTimingExposeHandles() {
        assertEquals(
            TransformTimingHandleRange(0, 3300),
            transformTimingHandleRange("0", "3300", 3300),
        )
        assertEquals(
            TransformTimingHandleRange(120, 780),
            transformTimingHandleRange(" 120 ", "780", 3300),
        )
        assertEquals(
            TransformTimingHandleRange(350, 350),
            transformTimingHandleRange("350", "350", 3300),
        )
    }

    @Test fun shorthandAndFractionalValuesRemainExactTextWithoutSlider() {
        assertNull(transformTimingHandleRange("", "", 3300))
        assertNull(transformTimingHandleRange("0", "", 3300))
        assertNull(transformTimingHandleRange("", "900", 3300))
        assertNull(transformTimingHandleRange("0.5", "900.5", 3300))
    }

    @Test fun outOfBoundsInvalidAndLongIntervalsNeverGetClamped() {
        assertNull(transformTimingHandleRange("-50", "900", 3300))
        assertNull(transformTimingHandleRange("0", "4500", 3300))
        assertNull(transformTimingHandleRange("950", "800", 3300))
        assertNull(transformTimingHandleRange("abc", "900", 3300))
        assertNull(transformTimingHandleRange("0", "200", 0))
        assertNull(transformTimingHandleRange("0", "200", 16_000_001))
        assertNull(transformTimingHandleRange("0", "9223372036854775808", 3300))
    }

    @Test fun handlesRecalculateWithFocusedEventDuration() {
        assertEquals(TransformTimingHandleRange(200, 3200), transformTimingHandleRange("200", "3200", 3300))
        assertNull(transformTimingHandleRange("200", "3200", 3100))
    }
}

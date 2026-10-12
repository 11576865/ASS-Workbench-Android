package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventTimingInputValidationTest {
    @Test fun validAssTimesAndMillisecondsKeepExistingEditableSyntax() {
        val input = EventTimingInputValidation.parse("0:00:01.25", "2000")
        assertTrue(input.canApply)
        assertEquals(SubTime(1250), input.start)
        assertEquals(SubTime(2000), input.end)
    }

    @Test fun malformedStartIsAttributedToStartAndCannotApply() {
        val input = EventTimingInputValidation.parse("broken", "2000")
        assertTrue(input.startInvalid)
        assertFalse(input.endInvalid)
        assertFalse(input.canApply)
    }

    @Test fun negativeOrOverflowingEndIsAttributedToEnd() {
        for (end in listOf("-1", "9223372036854775808", "")) {
            val input = EventTimingInputValidation.parse("0", end)
            assertFalse(input.startInvalid)
            assertTrue(input.endInvalid)
            assertFalse(input.canApply)
        }
    }

    @Test fun reversedTimesAreDistinguishedFromMalformedTimes() {
        val input = EventTimingInputValidation.parse("2000", "1000")
        assertFalse(input.startInvalid)
        assertFalse(input.endInvalid)
        assertTrue(input.reversed)
        assertFalse(input.canApply)
    }

    @Test fun equalTimesRemainAcceptedIncludingZero() {
        assertTrue(EventTimingInputValidation.parse("0", "0").canApply)
    }
}

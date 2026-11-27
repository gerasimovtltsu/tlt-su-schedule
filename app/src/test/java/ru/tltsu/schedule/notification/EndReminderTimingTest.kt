package ru.tltsu.schedule.notification

import org.junit.Assert.*
import org.junit.Test

class EndReminderTimingTest {
    private val start = 1_000_000L
    private val end = start + 90 * 60_000L
    @Test fun exactEnd() {
        assertEquals(end, EndReminderTiming.trigger(start, end, 0))
        assertTrue(EndReminderTiming.eligible(end, start, end, 0))
        assertFalse(EndReminderTiming.eligible(end - 1, start, end, 0))
    }
    @Test fun advanceWarning() {
        assertEquals(end - 5 * 60_000L, EndReminderTiming.trigger(start, end, 5))
        assertTrue(EndReminderTiming.eligible(end - 3 * 60_000L, start, end, 5))
        assertFalse(EndReminderTiming.eligible(end, start, end, 5))
    }
    @Test fun shortLesson() {
        assertEquals(start, EndReminderTiming.trigger(start, start + 60_000L, 30))
    }
    @Test fun staleWarning() {
        assertFalse(EndReminderTiming.eligible(end + 600_001L, start, end, 0))
    }
}

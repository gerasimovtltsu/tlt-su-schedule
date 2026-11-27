package ru.tltsu.schedule.domain.model

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class DaySlotsTest {
    private fun lesson(number: Int, hour: Int, id: String = "$number") = Lesson(
        id, "Test", "Практика", number, LocalDate.of(2026, 11, 27),
        LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), "101", "Group", ""
    )
    private fun gaps(vararg lessons: Lesson) = daySlots(lessons.toList())
        .filterIsInstance<DaySlot.Empty>().map { it.number }

    @Test fun internalGap() { assertEquals(listOf(4), gaps(lesson(3, 12), lesson(5, 16))) }
    @Test fun multipleGaps() { assertEquals(listOf(2, 3), gaps(lesson(1, 8), lesson(4, 14))) }
    @Test fun noLeadingOrTrailingGaps() { assertTrue(gaps(lesson(5, 16)).isEmpty()) }
    @Test fun consecutivePairs() { assertTrue(gaps(lesson(2, 10), lesson(3, 12)).isEmpty()) }
    @Test fun unknownNumber() { assertTrue(gaps(lesson(0, 10), lesson(5, 16)).isEmpty()) }
    @Test fun duplicates() { assertEquals(listOf(3), gaps(lesson(2, 10), lesson(2, 10, "duplicate"), lesson(4, 14))) }
    @Test fun emptyDay() { assertTrue(daySlots(emptyList()).isEmpty()) }
}

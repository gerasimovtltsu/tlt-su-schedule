package ru.tltsu.schedule.util

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tltsu.schedule.domain.model.Lesson

class DateTimeUtilsTest {
    @Test
    fun universityWeekIsConvertedToUtc() {
        assertEquals(
            "2026-11-22T20:00:00.000Z" to "2026-11-29T19:59:59.999Z",
            weekBounds(LocalDate.of(2026, 11, 24))
        )
    }

    @Test
    fun weekAcrossMonthBoundary() {
        assertEquals(
            "2026-11-29T20:00:00.000Z" to "2026-12-06T19:59:59.999Z",
            weekBounds(LocalDate.of(2026, 12, 1))
        )
    }

    @Test
    fun reminderBeforeLesson() {
        assertEquals(1_100_000L, reminderTime(2_000_000L, 15, 100_000L))
    }

    @Test
    fun lateReminderFiresSoon() {
        assertEquals(1_500_000L, reminderTime(2_000_000L, 15, 1_499_000L))
    }

    @Test
    fun lessonUsesUniversityLocalTime() {
        val lesson = Lesson(
            "id", "Test", "Лекция", 1, LocalDate.of(2026, 11, 24),
            LocalTime.of(8, 30), LocalTime.of(10, 0), "101", "Group", ""
        )
        assertEquals("2026-11-24T04:30:00Z", java.time.Instant.ofEpochMilli(lesson.begins()).toString())
    }
}

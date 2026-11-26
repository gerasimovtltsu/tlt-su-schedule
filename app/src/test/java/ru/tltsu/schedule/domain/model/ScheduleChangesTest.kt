package ru.tltsu.schedule.domain.model

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleChangesTest {
    private val lesson = Lesson("1", "Python", "Лекция", 1, LocalDate.of(2030, 1, 1), LocalTime.of(8, 30), LocalTime.of(10, 0), "101", "A", "")
    @Test fun unchanged() = assertTrue(ScheduleChanges.compare(listOf(lesson), listOf(lesson), 0).isEmpty())
    @Test fun roomChanged() = assertTrue(ScheduleChanges.compare(listOf(lesson), listOf(lesson.copy(room = "102")), 0).single().contains("101 → 102"))
    @Test fun addedAndRemoved() {
        assertTrue(ScheduleChanges.compare(emptyList(), listOf(lesson), 0).single().startsWith("Добавлено"))
        assertTrue(ScheduleChanges.compare(listOf(lesson), emptyList(), 0).single().startsWith("Отменено"))
    }
    @Test fun pastIgnored() = assertTrue(ScheduleChanges.compare(listOf(lesson), emptyList(), lesson.begins() + 1).isEmpty())
}

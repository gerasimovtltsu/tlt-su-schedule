package ru.tltsu.schedule.domain.model

internal sealed interface DaySlot {
    data class Occupied(val lesson: Lesson) : DaySlot
    data class Empty(val number: Int) : DaySlot
}

/** Leading and internal gaps are shown; no trailing gaps. Unknown pair numbers never imply missing classes. */
internal fun daySlots(lessons: List<Lesson>): List<DaySlot> {
    val sorted = lessons.sortedWith(compareBy<Lesson> { it.start }.thenBy { it.number })
    val occupied = sorted.map { it.number }.filter { it in 1..16 }.toSet()
    val shown = mutableSetOf<Int>()
    val result = mutableListOf<DaySlot>()
    val first = sorted.firstOrNull()
    if (first != null && first.number in 1..16) {
        for (number in 1 until first.number) {
            if (number !in occupied && shown.add(number)) result += DaySlot.Empty(number)
        }
    }
    var previous: Lesson? = null
    for (lesson in sorted) {
        val before = previous
        if (before != null && before.number in 1..16 && lesson.number in 1..16 &&
            lesson.number > before.number + 1 && lesson.start >= before.end) {
            for (number in before.number + 1 until lesson.number) {
                if (number !in occupied && shown.add(number)) result += DaySlot.Empty(number)
            }
        }
        result += DaySlot.Occupied(lesson)
        previous = lesson
    }
    return result
}

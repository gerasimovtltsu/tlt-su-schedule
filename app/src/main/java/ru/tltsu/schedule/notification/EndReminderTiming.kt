package ru.tltsu.schedule.notification

internal object EndReminderTiming {
    val options = intArrayOf(0, 1, 3, 5, 10, 15, 20, 30)
    fun normalize(minutes: Int): Int = if (minutes in options) minutes else 0
    fun label(minutes: Int): String = if (minutes == 0) "В момент окончания" else "За $minutes мин. до окончания"
    fun trigger(start: Long, end: Long, minutes: Int): Long =
        maxOf(start, end - normalize(minutes) * 60_000L)
    fun eligible(now: Long, start: Long, end: Long, minutes: Int): Boolean {
        val at = trigger(start, end, minutes)
        return end > start && now >= at && if (minutes == 0) now - end <= 600_000L else now < end
    }
}

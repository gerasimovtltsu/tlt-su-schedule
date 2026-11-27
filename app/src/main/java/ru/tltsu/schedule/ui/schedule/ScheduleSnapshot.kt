package ru.tltsu.schedule.ui.schedule

import android.content.Context
import java.time.LocalDate
import ru.tltsu.schedule.data.local.ScheduleCache
import ru.tltsu.schedule.data.repository.ScheduleRepository
import ru.tltsu.schedule.domain.model.Lesson
import ru.tltsu.schedule.util.today
import ru.tltsu.schedule.util.week

/** Immutable render data assembled off the UI thread; null lessons means no valid cache. */
internal data class ScheduleSnapshot(
    val profile: Int,
    val monday: LocalDate,
    val lessons: List<Lesson>?,
    val upcoming: List<Lesson>,
    val cacheExists: Boolean
) {
    companion object {
        fun load(context: Context, date: LocalDate, id: Int): ScheduleSnapshot {
            val monday = week(date)
            val lessons = ScheduleRepository.readCached(context, monday, id)
            val current = week(today())
            val upcoming = listOf(current, current.plusWeeks(1)).flatMap {
                if (it == monday) lessons.orEmpty() else ScheduleRepository.cached(context, it, id)
            }
            return ScheduleSnapshot(id, monday, lessons, upcoming, ScheduleCache.exists(context, id, monday))
        }
    }
}

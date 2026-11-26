package ru.tltsu.schedule.data.repository

import android.content.Context
import java.time.LocalDate
import java.util.concurrent.Executors
import ru.tltsu.schedule.data.local.ScheduleCache
import ru.tltsu.schedule.data.local.teacher
import ru.tltsu.schedule.data.remote.ScheduleApi
import ru.tltsu.schedule.data.remote.ScheduleParser
import ru.tltsu.schedule.domain.model.Lesson
import ru.tltsu.schedule.util.today
import ru.tltsu.schedule.util.week

internal object ScheduleRepository {
    val executor = Executors.newSingleThreadExecutor()

    fun cached(context: Context, date: LocalDate, teacherId: Int = teacher(context)): List<Lesson> =
        runCatching { ScheduleParser.parse(ScheduleCache.read(context, teacherId, week(date))) }
            .getOrDefault(emptyList())

    fun fetch(context: Context, date: LocalDate, teacherId: Int = teacher(context)): List<Lesson> {
        val raw = ScheduleApi.load(teacherId, week(date))
        val lessons = ScheduleParser.parse(raw)
        ScheduleCache.save(context, teacherId, week(date), raw, ScheduleParser.teacherName(raw, teacherId))
        return lessons
    }

    fun upcoming(context: Context): List<Lesson> {
        val id = teacher(context)
        val monday = week(today())
        return cached(context, monday, id) + cached(context, monday.plusWeeks(1), id)
    }
}

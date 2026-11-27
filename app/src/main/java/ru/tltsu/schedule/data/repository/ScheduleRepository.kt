package ru.tltsu.schedule.data.repository

import android.content.Context
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.CancellationException
import android.util.Log
import ru.tltsu.schedule.data.local.ScheduleCache
import ru.tltsu.schedule.data.local.teacher
import ru.tltsu.schedule.data.remote.ScheduleApi
import ru.tltsu.schedule.data.remote.ScheduleParser
import ru.tltsu.schedule.domain.model.Lesson
import ru.tltsu.schedule.domain.model.ScheduleChanges
import ru.tltsu.schedule.notification.ScheduleChangeNotifier
import ru.tltsu.schedule.util.today
import ru.tltsu.schedule.util.week

internal object ScheduleRepository {
    val executor = Executors.newSingleThreadExecutor()

    private data class CacheEntry(val raw: String, val lessons: List<Lesson>)
    private val parsed = object : LinkedHashMap<Pair<Int, LocalDate>, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, LocalDate>, CacheEntry>?): Boolean = size > 24
    }

    fun cached(context: Context, date: LocalDate, teacherId: Int = teacher(context)): List<Lesson> =
        readCached(context, date, teacherId) ?: emptyList()

    fun readCached(context: Context, date: LocalDate, teacherId: Int = teacher(context)): List<Lesson>? {
        val monday = week(date)
        if (!ScheduleCache.exists(context, teacherId, monday)) return null
        val key = teacherId to monday
        val raw = ScheduleCache.read(context, teacherId, monday)
        synchronized(parsed) { parsed[key]?.takeIf { it.raw == raw }?.let { return it.lessons } }
        return try {
            ScheduleParser.parse(raw).also { synchronized(parsed) { parsed[key] = CacheEntry(raw, it) } }
        } catch (e: Exception) {
            Log.w("ScheduleRepository", "Invalid cached week", e)
            null
        }
    }

    fun fetch(context: Context, date: LocalDate, teacherId: Int = teacher(context), valid: () -> Boolean = { true }): List<Lesson> {
        val monday = week(date)
        if (!valid()) throw CancellationException()
        val previous = readCached(context, monday, teacherId)
        val raw = ScheduleApi.load(teacherId, monday)
        val lessons = ScheduleParser.parse(raw)
        if (!valid()) throw CancellationException()
        ScheduleCache.save(context, teacherId, monday, raw, ScheduleParser.teacherName(raw, teacherId))
        synchronized(parsed) { parsed[teacherId to monday] = CacheEntry(raw, lessons) }
        if (previous != null && valid() && teacher(context) == teacherId) {
            ScheduleChangeNotifier.send(context, teacherId, monday, ScheduleChanges.compare(previous, lessons, System.currentTimeMillis()))
        }
        return lessons
    }

    fun upcoming(context: Context): List<Lesson> {
        if (!ru.tltsu.schedule.data.local.profileConfigured(context)) return emptyList()
        val id = teacher(context)
        val monday = week(today())
        return cached(context, monday, id) + cached(context, monday.plusWeeks(1), id)
    }
}

package ru.tltsu.schedule.data.local

import android.content.Context
import java.time.LocalDate

internal object ScheduleCache {
    fun read(context: Context, teacherId: Int, date: LocalDate): String =
        prefs(context).getString(cacheKey(teacherId, date), "[]") ?: "[]"

    fun save(context: Context, teacherId: Int, date: LocalDate, raw: String, name: String?) {
        val edit = prefs(context).edit()
            .putString(cacheKey(teacherId, date), raw)
            .putLong(updateKey(teacherId, date), System.currentTimeMillis())
        if (!name.isNullOrBlank()) edit.putString("teacher_name_$teacherId", name)
        edit.apply()
    }

    fun exists(context: Context, teacherId: Int, date: LocalDate): Boolean =
        prefs(context).contains(cacheKey(teacherId, date))

    fun updatedAt(context: Context, teacherId: Int, date: LocalDate): Long =
        prefs(context).getLong(updateKey(teacherId, date), 0)
}

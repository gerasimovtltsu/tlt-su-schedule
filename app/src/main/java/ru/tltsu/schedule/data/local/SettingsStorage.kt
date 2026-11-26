package ru.tltsu.schedule.data.local

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Keep the existing preferences and cache keys compatible with installed versions. */
internal fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences("schedule", Context.MODE_PRIVATE)

internal fun teacher(context: Context): Int = prefs(context).getInt("teacher_id", 15943)

internal fun teacherName(context: Context): String =
    prefs(context).getString("teacher_name_" + teacher(context), null)
        ?: "Преподаватель №" + teacher(context)

internal fun cacheKey(id: Int, date: LocalDate): String =
    if (id == 15943) "week_$date" else "week_${id}_$date"

internal fun updateKey(id: Int, date: LocalDate): String =
    if (id == 15943) "updated_$date" else "updated_${id}_$date"

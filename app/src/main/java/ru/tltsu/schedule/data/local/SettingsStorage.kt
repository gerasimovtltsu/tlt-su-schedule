package ru.tltsu.schedule.data.local

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Keep the existing preferences and cache keys compatible with installed versions. */
internal fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences("schedule", Context.MODE_PRIVATE)

/** Positive keys are teachers, negative keys are groups. Existing teacher storage is preserved. */
internal fun profileKey(context: Context): Int {
    val p = prefs(context)
    return p.getInt("profile_key", p.getInt("teacher_id", 15943))
}

internal fun profileConfigured(context: Context): Boolean {
    val p = prefs(context)
    return p.contains("profile_key") || p.contains("teacher_id") ||
        p.all.keys.any { it.startsWith("week_") || it.startsWith("teacher_name_") }
}

internal fun saveProfile(context: Context, key: Int) {
    val profile = ru.tltsu.schedule.domain.model.ScheduleProfile.fromKey(key)
    val edit = prefs(context).edit().putInt("profile_key", key)
    if (profile.role == ru.tltsu.schedule.domain.model.ProfileRole.TEACHER) edit.putInt("teacher_id", profile.id)
    edit.apply()
}

internal fun teacher(context: Context): Int = profileKey(context)

internal fun profileName(context: Context): String {
    val key = profileKey(context)
    val prefix = if (key < 0) "Группа №" else "Преподаватель №"
    return prefs(context).getString("teacher_name_" + key, null) ?: prefix + kotlin.math.abs(key)
}

internal fun teacherName(context: Context): String = profileName(context)

internal fun cacheKey(id: Int, date: LocalDate): String =
    if (id == 15943) "week_$date" else "week_${id}_$date"

internal fun updateKey(id: Int, date: LocalDate): String =
    if (id == 15943) "updated_$date" else "updated_${id}_$date"

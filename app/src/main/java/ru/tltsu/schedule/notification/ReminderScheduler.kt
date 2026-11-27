package ru.tltsu.schedule.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import ru.tltsu.schedule.ReminderReceiver
import ru.tltsu.schedule.data.local.prefs
import ru.tltsu.schedule.data.repository.ScheduleRepository
import ru.tltsu.schedule.util.displayTimestamp
import ru.tltsu.schedule.util.reminderTime

internal object ReminderScheduler {
    private val planner = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun request(context: Context, reset: Boolean = false) {
        val app = context.applicationContext
        planner.execute {
            try {
                if (reset) cancelAll(app)
                reschedule(app)
            } catch (e: Exception) {
                android.util.Log.w("ReminderScheduler", "Planning failed", e)
            }
        }
    }

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun intent(context: Context, id: String): Intent =
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("schedule://reminder/$id"))

    private fun endIntent(context: Context, id: String): Intent =
        Intent(context, ReminderReceiver::class.java).setData(Uri.Builder().scheme("schedule").authority("lesson-end").appendPath(id).build())

    private fun cancel(context: Context, id: String, end: Boolean = false) {
        val pending = PendingIntent.getBroadcast(
            context, 0, if (end) endIntent(context, id) else intent(context, id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    @Synchronized
    fun cancelAll(context: Context) {
        val storage = prefs(context)
        storage.getStringSet("alarm_ids", emptySet()).orEmpty().toSet().forEach { cancel(context, it) }
        storage.getStringSet("end_alarm_ids", emptySet()).orEmpty().toSet().forEach { cancel(context, it, true) }
        storage.edit().remove("alarm_ids").remove("end_alarm_ids").remove("planned_list").apply()
    }

    /** Rebuild alarms from current and next week without changing their identity or channel. */
    @Synchronized
    fun reschedule(context: Context) {
        val storage = prefs(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val lead = storage.getInt("lead", 15)
        val profile = ru.tltsu.schedule.data.local.profileKey(context)
        val upcoming = ScheduleRepository.upcoming(context)
        if (profile != ru.tltsu.schedule.data.local.profileKey(context)) return
        val desired = upcoming
            .filter { it.begins() > now }
            .associateBy { it.id }
        val previous = storage.getStringSet("alarm_ids", emptySet()).orEmpty().toSet()
        (previous - desired.keys).forEach { cancel(context, it) }

        val planned = mutableListOf<Pair<Long, String>>()
        for ((id, lesson) in desired) {
            if (storage.getLong("sent_$id", 0) == lesson.begins()) {
                cancel(context, id)
                continue
            }
            val fireAt = reminderTime(lesson.begins(), lead, now)
            val reminder = intent(context, id)
                .putExtra("name", lesson.name)
                .putExtra("detail", lesson.time() + " · " + lesson.room + " · " + lesson.groups)
                .putExtra("start", lesson.begins())
                .putExtra("profile_key", profile)
            val pending = PendingIntent.getBroadcast(context, 0, reminder, FLAGS)
            try {
                if (exactAllowed(context)) {
                    manager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(fireAt, NotificationHelper.openApp(context)), pending
                    )
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
                }
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            }
            planned += fireAt to lesson.name
        }
        val endLead = EndReminderTiming.normalize(storage.getInt("end_lead", 0))
        val endDesired = if (storage.getBoolean("notify_lesson_end", false))
            upcoming.filter { it.ends() > now && it.ends() > it.begins() }.associateBy { it.id }
        else emptyMap()
        val previousEnds = storage.getStringSet("end_alarm_ids", emptySet()).orEmpty().toSet()
        (previousEnds - endDesired.keys).forEach { cancel(context, it, true) }
        for ((id, lesson) in endDesired) {
            val endAt = lesson.ends()
            if (storage.getLong("end_sent_$id", 0) == endAt) {
                cancel(context, id, true)
                continue
            }
            val target = EndReminderTiming.trigger(lesson.begins(), endAt, endLead)
            val fireAt = maxOf(target, now + 1_000L)
            val reminder = endIntent(context, id)
                .putExtra("end", endAt)
                .putExtra("end_lead", endLead)
                .putExtra("teacher_id", profile)
                .putExtra("profile_key", profile)
            val pending = PendingIntent.getBroadcast(context, 0, reminder, FLAGS)
            try {
                if (exactAllowed(context)) {
                    manager.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, NotificationHelper.openApp(context)), pending)
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
                }
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            }
            planned += fireAt to ("Окончание (" + EndReminderTiming.label(endLead) + "): " + lesson.name)
        }
        val edit = storage.edit()
        storage.all.forEach { (key, value) ->
            if (key.startsWith("end_sent_") && value is Long && value < now - 14 * 86_400_000L) edit.remove(key)
        }
        edit.putStringSet("end_alarm_ids", endDesired.keys.toSet())
            .putStringSet("alarm_ids", desired.keys.toSet())
            .putLong("planned_at", now)
            .putString("planned_list", planned.sortedBy { it.first }.joinToString("\n") {
                displayTimestamp(it.first) + " — " + it.second
            })
            .apply()
    }

    fun test(context: Context, delayed: Boolean) {
        val at = System.currentTimeMillis() + if (delayed) 30_000L else 0L
        val reminder = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse(if (delayed) "schedule://test/alarm" else "schedule://test"))
            .putExtra("name", if (delayed) "Проверка будильника" else "Тестовое уведомление")
            .putExtra("detail", if (delayed) "Фоновое напоминание через AlarmManager" else "Напоминания о парах включены")
            .putExtra("start", at + 600_000L)
        if (delayed) {
            val pending = PendingIntent.getBroadcast(context, 0, reminder, FLAGS)
            context.getSystemService(AlarmManager::class.java).setAlarmClock(
                AlarmManager.AlarmClockInfo(at, NotificationHelper.openApp(context)), pending
            )
        } else {
            context.sendBroadcast(reminder)
        }
    }
}

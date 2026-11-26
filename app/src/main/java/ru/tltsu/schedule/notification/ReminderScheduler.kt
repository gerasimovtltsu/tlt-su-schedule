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
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun intent(context: Context, id: String): Intent =
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("schedule://reminder/$id"))

    private fun cancel(context: Context, id: String) {
        val pending = PendingIntent.getBroadcast(
            context, 0, intent(context, id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    @Synchronized
    fun cancelAll(context: Context) {
        val storage = prefs(context)
        storage.getStringSet("alarm_ids", emptySet()).orEmpty().toSet().forEach { cancel(context, it) }
        storage.edit().remove("alarm_ids").remove("planned_list").apply()
    }

    /** Rebuild alarms from current and next week without changing their identity or channel. */
    @Synchronized
    fun reschedule(context: Context) {
        val storage = prefs(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val lead = storage.getInt("lead", 15)
        val desired = ScheduleRepository.upcoming(context)
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
        storage.edit()
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

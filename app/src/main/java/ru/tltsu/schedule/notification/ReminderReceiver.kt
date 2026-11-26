package ru.tltsu.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import ru.tltsu.schedule.data.local.prefs
import ru.tltsu.schedule.data.repository.ScheduleRepository
import ru.tltsu.schedule.notification.NotificationHelper
import ru.tltsu.schedule.notification.ReminderScheduler
import ru.tltsu.schedule.util.zone

/** Keep the original component name so pending alarms survive application updates. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val start = intent.getLongExtra("start", 0)
        val id = intent.data?.lastPathSegment
        val test = intent.data?.host == "test"
        val storage = prefs(context)
        val stamp = Instant.ofEpochMilli(now).atZone(zone).toString()
        storage.edit().putString("last_alarm_event", "$stamp · " + intent.data).apply()
        if (now >= start) return

        if (!test) {
            if (id == null) return
            val lesson = ScheduleRepository.upcoming(context)
                .firstOrNull { it.id == id && it.begins() == start } ?: return
            if (now < lesson.begins() - storage.getInt("lead", 15) * 60_000L) {
                ReminderScheduler.reschedule(context)
                return
            }
            if (storage.getLong("sent_$id", 0) == start) return
        }

        val name = intent.getStringExtra("name").orEmpty()
        val detail = intent.getStringExtra("detail").orEmpty()
        val sent = NotificationHelper.send(
            context, intent.data.toString().hashCode(),
            if (test) name else "Скоро пара: $name", detail
        )
        if (sent && !test && id != null) {
            storage.edit().putLong("sent_$id", start)
                .putString("last_lesson_alert", "$stamp · $name")
                .apply()
        }
    }
}

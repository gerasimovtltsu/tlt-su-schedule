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

        val lesson = if (!test) {
            if (id == null) return
            val lesson = ScheduleRepository.upcoming(context)
                .firstOrNull { it.id == id && it.begins() == start } ?: return
            if (now < lesson.begins() - storage.getInt("lead", 15) * 60_000L) {
                ReminderScheduler.request(context)
                return
            }
            if (storage.getLong("sent_$id", 0) == start) return
            lesson
        } else null

        val name = lesson?.name ?: intent.getStringExtra("name").orEmpty()
        val detail = intent.getStringExtra("detail").orEmpty()
        val title = if (lesson != null) (if (lesson.number > 0) "${lesson.number}-я пара · " else "Скоро пара · ") + lesson.start + " · " + lesson.room else name
        val compact = if (lesson != null) lesson.name else detail
        val expanded = if (lesson != null) listOf(lesson.name, lesson.time() + " · " + lesson.room, lesson.type, lesson.groups).filter { it.isNotBlank() }.joinToString("\n") else detail
        val sent = NotificationHelper.send(
            context, intent.data.toString().hashCode(),
            title, compact, expanded
        )
        if (sent && !test && id != null) {
            storage.edit().putLong("sent_$id", start)
                .putString("last_lesson_alert", "$stamp · $name")
                .apply()
        }
    }
}

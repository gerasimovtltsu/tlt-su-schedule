package ru.tltsu.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import ru.tltsu.schedule.data.local.prefs
import ru.tltsu.schedule.data.repository.ScheduleRepository
import ru.tltsu.schedule.notification.NotificationHelper
import ru.tltsu.schedule.notification.EndReminderTiming
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
        if (intent.data?.host == "lesson-end") {
            handleEnd(context, intent, now)
            return
        }
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

    private fun handleEnd(context: Context, intent: Intent, now: Long) {
        val storage = prefs(context)
        if (!storage.getBoolean("notify_lesson_end", false)) return
        if (intent.getIntExtra("teacher_id", -1) != ru.tltsu.schedule.data.local.teacher(context)) return
        val id = intent.data?.lastPathSegment ?: return
        val endAt = intent.getLongExtra("end", 0)
        val lead = EndReminderTiming.normalize(storage.getInt("end_lead", 0))
        if (endAt <= 0 || intent.getIntExtra("end_lead", 0) != lead) return
        val lesson = ScheduleRepository.upcoming(context)
            .firstOrNull { it.id == id && it.ends() == endAt } ?: return
        if (!EndReminderTiming.eligible(now, lesson.begins(), endAt, lead)) return
        if (storage.getLong("end_sent_$id", 0) == endAt) return
        val title = if (lead == 0) {
            if (lesson.number > 0) "${lesson.number}-я пара окончена · ${lesson.end}" else "Пара окончена · ${lesson.end}"
        } else {
            val remaining = ((endAt - now + 59_999L) / 60_000L).coerceAtLeast(1L)
            "До конца пары $remaining мин. · ${lesson.end}"
        }
        val expanded = listOf(lesson.name, lesson.time() + " · " + lesson.room, lesson.type, lesson.groups)
            .filter { it.isNotBlank() }.joinToString("\n")
        if (NotificationHelper.send(context, intent.data.toString().hashCode(), title, lesson.name, expanded,
                NotificationHelper.END_CHANNEL_ID)) {
            storage.edit().putLong("end_sent_$id", endAt).apply()
        }
    }
}

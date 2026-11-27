package ru.tltsu.schedule.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import ru.tltsu.schedule.R
import ru.tltsu.schedule.data.local.prefs

internal object ScheduleChangeNotifier {
    private const val CHANNEL = "schedule_changes"

    fun send(context: Context, teacherId: Int, week: java.time.LocalDate, changes: List<String>) {
        if (changes.isEmpty() || !prefs(context).getBoolean("notify_changes", true)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Изменения расписания", NotificationManager.IMPORTANCE_HIGH))
        if (!manager.areNotificationsEnabled()) return
        val detail = changes.joinToString("\n").take(3500)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Изменения расписания · ${changes.size}")
            .setContentText(changes.first())
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setContentIntent(NotificationHelper.openApp(context))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify("changes:$teacherId:$week", 1802, notification) }
    }
}

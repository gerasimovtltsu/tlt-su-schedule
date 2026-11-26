package ru.tltsu.schedule.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import ru.tltsu.schedule.data.local.prefs
import ru.tltsu.schedule.notification.NotificationHelper
import ru.tltsu.schedule.notification.ReminderScheduler

internal object DiagnosticsDialog {
    fun show(activity: Activity) {
        val storage = prefs(activity)
        val manager = activity.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        val info = buildString {
            appendLine("Точные будильники: " + if (ReminderScheduler.exactAllowed(activity)) "разрешены" else "НЕТ — возможна задержка")
            appendLine("Уведомления: " + if (manager.areNotificationsEnabled()) "включены" else "выключены")
            appendLine("Канал: " + if (channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE) "включён" else "выключен")
            appendLine("Последнее срабатывание: " + storage.getString("last_alarm_event", "нет"))
            appendLine("Последнее уведомление о паре: " + storage.getString("last_lesson_alert", "нет"))
            appendLine()
            appendLine("Запланировано (время Тольятти):")
            append(storage.getString("planned_list", "нет данных"))
        }
        AlertDialog.Builder(activity)
            .setTitle("Диагностика")
            .setMessage(info)
            .setPositiveButton("Закрыть", null)
            .setNeutralButton("Копировать") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Диагностика", info))
            }.show()
    }
}

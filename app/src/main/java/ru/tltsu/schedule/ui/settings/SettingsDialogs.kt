package ru.tltsu.schedule.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import ru.tltsu.schedule.data.local.prefs
import ru.tltsu.schedule.data.local.teacher
import ru.tltsu.schedule.data.local.teacherName
import ru.tltsu.schedule.notification.ReminderScheduler
import ru.tltsu.schedule.update.UpdateManager

internal class SettingsDialogs(
    private val activity: Activity,
    private val onTeacherChanged: (Int) -> Unit
) {
    fun chooseTeacher() {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "ID или ссылка на расписание"
            setText(teacher(activity).toString())
            selectAll()
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Преподаватель")
            .setMessage("Введите числовой ID или ссылку rasp.tltsu.ru/teacher/…")
            .setView(input)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text.toString().trim()
                val uri = Uri.parse(raw)
                val id = raw.toIntOrNull() ?: if (
                    uri.host == "rasp.tltsu.ru" && uri.pathSegments.firstOrNull() == "teacher"
                ) uri.pathSegments.getOrNull(1)?.toIntOrNull() else null
                if (id == null || id <= 0) {
                    input.error = "Введите корректный ID преподавателя"
                } else {
                    onTeacherChanged(id)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    fun show() {
        val lead = prefs(activity).getInt("lead", 15)
        val exact = ReminderScheduler.exactAllowed(activity)
        val enabled = activity.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val options = arrayOf(
            "За $lead мин. · изменить",
            "Тестовое уведомление",
            "Точные будильники · " + if (exact) "разрешены" else "не разрешены",
            "Уведомления · " + if (enabled) "включены" else "выключены",
            "Настройки приложения",
            "Тест будильника через 30 секунд",
            "Преподаватель · " + teacherName(activity),
            "Диагностика напоминаний",
            "Перепланировать напоминания",
            "Проверить обновления · " + UpdateManager.installed(activity)
        )
        AlertDialog.Builder(activity).setTitle("Напоминания")
            .setItems(options) { _, index ->
                when (index) {
                    0 -> chooseLead(lead)
                    1 -> test(false)
                    2 -> if (Build.VERSION.SDK_INT >= 31) {
                        runCatching { activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri())) }
                    } else Unit
                    3 -> activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
                    4 -> activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()))
                    5 -> test(true)
                    6 -> chooseTeacher()
                    7 -> DiagnosticsDialog.show(activity)
                    9 -> UpdateManager.check(activity, manual = true)
                    8 -> {
                        ReminderScheduler.reschedule(activity)
                        toast("Напоминания перепланированы")
                    }
                }
            }.setNegativeButton("Закрыть", null).show()
    }

    private fun chooseLead(lead: Int) {
        val values = intArrayOf(5, 10, 15, 20, 30)
        AlertDialog.Builder(activity).setTitle("Напоминать до пары")
            .setSingleChoiceItems(values.map { "$it мин." }.toTypedArray(), values.indexOf(lead)) { dialog, index ->
                prefs(activity).edit().putInt("lead", values[index]).apply()
                ReminderScheduler.reschedule(activity)
                dialog.dismiss()
            }.setNegativeButton("Отмена", null).show()
    }

    private fun test(delayed: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            return
        }
        if (delayed && !ReminderScheduler.exactAllowed(activity)) {
            toast("Разрешите точные будильники")
            return
        }
        try {
            ReminderScheduler.test(activity, delayed)
            if (delayed) toast("Заблокируйте экран. Проверка через 30 секунд")
        } catch (_: SecurityException) {
            toast("Нет разрешения на точные будильники")
        }
    }

    private fun packageUri(): Uri = Uri.parse("package:" + activity.packageName)
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
}

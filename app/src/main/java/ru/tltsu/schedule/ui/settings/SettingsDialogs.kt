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
    fun chooseTeacher() = chooseProfile(false)

    fun chooseProfile(firstLaunch: Boolean) {
        val configured = ru.tltsu.schedule.data.local.profileConfigured(activity)
        val current = if (configured) ru.tltsu.schedule.domain.model.ScheduleProfile.fromKey(teacher(activity)) else null
        val roles = ru.tltsu.schedule.domain.model.ProfileRole.values()
        val picker = AlertDialog.Builder(activity)
            .setTitle(if (firstLaunch) "Кто вы?" else "Выбор профиля")
            .setItems(roles.map { it.title }.toTypedArray()) { _, index ->
                enterProfile(roles[index], current, firstLaunch)
            }
        if (firstLaunch) picker.setCancelable(false) else picker.setNegativeButton("Отмена", null)
        picker.show()
    }

    private fun enterProfile(role: ru.tltsu.schedule.domain.model.ProfileRole,
        current: ru.tltsu.schedule.domain.model.ScheduleProfile?, firstLaunch: Boolean) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "ID или ссылка"
            if (current?.role == role) setText(current.id.toString())
        }
        val label = if (role == ru.tltsu.schedule.domain.model.ProfileRole.STUDENT) "группы" else "преподавателя"
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Расписание $label")
            .setMessage("Введите ID $label или ссылку с официального сайта: rasp.tltsu.ru/" + role.path + "/…")
            .setView(input)
            .setPositiveButton("Продолжить", null)
            .setNegativeButton("Назад") { _, _ -> chooseProfile(firstLaunch) }
            .setCancelable(!firstLaunch)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val profile = ru.tltsu.schedule.domain.model.ScheduleProfile.parse(role, input.text.toString())
                if (profile == null) input.error = "Введите корректный ID или ссылку $label"
                else {
                    dialog.dismiss()
                    onTeacherChanged(profile.key)
                }
            }
        }
        dialog.show()
    }

    fun show() {
        activity.startActivityForResult(Intent(activity, SettingsActivity::class.java), 208)
    }

    fun chooseLead(lead: Int) {
        val values = intArrayOf(5, 10, 15, 20, 30)
        AlertDialog.Builder(activity).setTitle("Напоминать до пары")
            .setSingleChoiceItems(values.map { "$it мин." }.toTypedArray(), values.indexOf(lead)) { dialog, index ->
                prefs(activity).edit().putInt("lead", values[index]).apply()
                ReminderScheduler.request(activity)
                dialog.dismiss()
            }.setNegativeButton("Отмена", null).show()
    }

    fun test(delayed: Boolean) {
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

package ru.tltsu.schedule.ui.settings

import android.app.Activity
import android.content.Context
import ru.tltsu.schedule.ui.InterfaceSize
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import ru.tltsu.schedule.data.local.prefs
import ru.tltsu.schedule.data.local.teacherName
import ru.tltsu.schedule.notification.ReminderScheduler
import ru.tltsu.schedule.update.UpdateManager
import ru.tltsu.schedule.update.GitHubReleaseApi

class SettingsActivity : Activity() {
    private lateinit var body: LinearLayout
    private var scroll: ScrollView? = null
    private val accent = Color.parseColor("#8EB5FF")
    private val muted = Color.parseColor("#B4C1D4")
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(InterfaceSize.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val savedY = scroll?.scrollY ?: 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(dp(16), dp(12) + insets.systemWindowInsetTop, dp(16), dp(8) + insets.systemWindowInsetBottom)
            insets
        }
        val header = TextView(this).apply {
            text = "‹   Настройки"
            textSize = 23f
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(14))
            setOnClickListener { finish() }
        }
        root.addView(header)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val contentScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(body)
        }
        scroll = contentScroll
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        contentScroll.post { contentScroll.scrollTo(0, savedY) }
        setContentView(root)
        val actions = SettingsDialogs(this) { id ->
            val p = prefs(this)
            if (id != p.getInt("teacher_id", 15943)) {
                setResult(RESULT_OK, Intent().putExtra("teacher_id", id))
                finish()
            }
        }
        section("РАСПИСАНИЕ")
        item("Преподаватель", teacherName(this)) { actions.chooseTeacher() }
        item("Автообновление расписания", "Примерно каждый час") {
            AlertDialog.Builder(this).setMessage("Android выполняет фоновое обновление примерно раз в час при наличии сети.").setPositiveButton("OK", null).show()
        }
        section("УВЕДОМЛЕНИЯ")
        item("Напоминания о парах", "За ${prefs(this).getInt("lead", 15)} мин.") { actions.chooseLead(prefs(this).getInt("lead", 15)) }
        body.addView(Switch(this).apply {
            text = "Уведомлять об окончании пары"
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(12))
            isChecked = prefs(this@SettingsActivity).getBoolean("notify_lesson_end", false)
            setOnCheckedChangeListener { _, checked ->
                val activity = this@SettingsActivity
                prefs(activity).edit().putBoolean("notify_lesson_end", checked).apply()
                if (checked) ru.tltsu.schedule.notification.NotificationHelper.createChannel(
                    activity, ru.tltsu.schedule.notification.NotificationHelper.END_CHANNEL_ID)
                ReminderScheduler.request(activity)
            }
        })
        body.addView(Switch(this).apply {
            text = "Сообщать об изменениях расписания"
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(12))
            isChecked = prefs(this@SettingsActivity).getBoolean("notify_changes", true)
            setOnCheckedChangeListener { _, checked -> prefs(this@SettingsActivity).edit().putBoolean("notify_changes", checked).apply() }
        })
        item("Точные будильники", if (ReminderScheduler.exactAllowed(this)) "Разрешены" else "Требуется разрешение") {
            if (Build.VERSION.SDK_INT >= 31) runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
        }
        item("Системные уведомления", "Настройки каналов") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
        item("Тестовое уведомление", "Отправить сейчас") { actions.test(false) }
        item("Тест будильника", "Через 30 секунд") { actions.test(true) }
        item("Диагностика", "Запланированные напоминания") { DiagnosticsDialog.show(this) }
        item("Перепланировать напоминания", "Пересчитать будильники") {
            ReminderScheduler.request(this)
            android.widget.Toast.makeText(this, "Напоминания перепланированы", android.widget.Toast.LENGTH_SHORT).show()
        }
        item("Фоновая работа", "Системные настройки приложения") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        section("ОФОРМЛЕНИЕ")
        item("Масштаб текста", InterfaceSize.current(this).title) { chooseInterfaceSize() }
        section("ПРИЛОЖЕНИЕ")
        item("Проверить обновления", "Версия " + UpdateManager.installed(this)) { UpdateManager.check(this, true) }
        item("О приложении", "Расписание ТГУ · GitHub") {
            AlertDialog.Builder(this).setTitle("Расписание ТГУ").setMessage("Версия " + UpdateManager.installed(this) + "\nНеофициальное приложение для преподавателей ТГУ.\nНативный Android · Kotlin")
                .setPositiveButton("GitHub") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GitHubReleaseApi.RELEASES))) }
                .setNegativeButton("Закрыть", null).show()
        }
    }

    private fun chooseInterfaceSize() {
        val current = InterfaceSize.current(this)
        val systemScale = applicationContext.resources.configuration.fontScale
        var selected = current.percent ?: (kotlin.math.round(systemScale * 100 / InterfaceSize.STEP).toInt() * InterfaceSize.STEP)
            .coerceIn(InterfaceSize.MIN, InterfaceSize.MAX)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        val system = android.widget.CheckBox(this).apply {
            text = "Использовать системный размер"
            isChecked = current.percent == null
        }
        val value = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(accent)
            textSize = 20f
        }
        val slider = android.widget.SeekBar(this).apply {
            max = (InterfaceSize.MAX - InterfaceSize.MIN) / InterfaceSize.STEP
            progress = (selected - InterfaceSize.MIN) / InterfaceSize.STEP
            contentDescription = "Масштаб текста"
            minimumHeight = dp(48)
        }
        val bounds = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@SettingsActivity).apply { text = "70%"; setTextColor(muted) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@SettingsActivity).apply { text = "150%"; setTextColor(muted); gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val time = TextView(this).apply { text = "2-я пара · 10:15 – 11:45"; setTextColor(muted) }
        val name = TextView(this).apply {
            text = "Средства программной разработки"
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val room = TextView(this).apply { text = "УЛК-203 · Практика"; setTextColor(accent) }
        preview.addView(time)
        preview.addView(name)
        preview.addView(room)

        fun updatePreview() {
            slider.isEnabled = !system.isChecked
            val factor = if (system.isChecked) systemScale else selected / 100f
            value.text = if (system.isChecked) "Системный · ${kotlin.math.round(systemScale * 100).toInt()}%" else "$selected%"
            val config = android.content.res.Configuration(resources.configuration).apply { fontScale = factor }
            val metrics = createConfigurationContext(config).resources.displayMetrics
            fun resize(view: TextView, sp: Float) {
                view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp, metrics))
            }
            resize(time, 12f)
            resize(name, 16f)
            resize(room, 14f)
        }

        system.setOnCheckedChangeListener { _, _ -> updatePreview() }
        slider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                selected = InterfaceSize.MIN + progress * InterfaceSize.STEP
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) {}
        })
        content.addView(system)
        content.addView(value)
        content.addView(slider)
        content.addView(bounds)
        content.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        updatePreview()
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        AlertDialog.Builder(this)
            .setTitle("Масштаб текста")
            .setView(scrollView)
            .setNeutralButton("100%") { _, _ ->
                val size = InterfaceSize(100)
                if (size != current) { InterfaceSize.save(this, size); recreate() }
            }
            .setPositiveButton("Применить") { _, _ ->
                val size = InterfaceSize(if (system.isChecked) null else selected)
                if (size != current) { InterfaceSize.save(this, size); recreate() }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun section(title: String) {
        body.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(accent)
            setPadding(0, dp(20), 0, dp(8))
        })
    }

    private fun item(title: String, subtitle: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { action() }
        }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(this).apply { this.text = title; textSize = 16f; setTextColor(Color.WHITE) })
        text.addView(TextView(this).apply { this.text = subtitle; textSize = 13f; setTextColor(muted) })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(android.widget.ImageView(this).apply {
            setImageResource(ru.tltsu.schedule.R.drawable.ic_chevron_right)
            setColorFilter(accent)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(8) })
        body.addView(row)
        body.addView(android.view.View(this).apply { setBackgroundColor(Color.parseColor("#263449")) }, LinearLayout.LayoutParams(-1, dp(1)))
    }
}

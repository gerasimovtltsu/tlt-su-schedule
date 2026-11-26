package ru.tltsu.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import kotlin.math.abs
import ru.tltsu.schedule.notification.NotificationHelper
import ru.tltsu.schedule.notification.ReminderScheduler
import ru.tltsu.schedule.ui.schedule.ScheduleScreen
import ru.tltsu.schedule.ui.schedule.ScheduleViewModel
import ru.tltsu.schedule.ui.settings.SettingsDialogs
import ru.tltsu.schedule.worker.ScheduleSync

class MainActivity : ComponentActivity() {
    private lateinit var model: ScheduleViewModel
    private lateinit var screen: ScheduleScreen
    private val handler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private val ticker = object : Runnable {
        override fun run() {
            model.state.value?.takeUnless { it.loading }?.let(screen::render)
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        ScheduleSync.schedule(this)
        setContentView(R.layout.activity_main)
        model = ViewModelProvider(this)[ScheduleViewModel::class.java]
        val dialogs = SettingsDialogs(this, model::changeTeacher)
        screen = ScheduleScreen(
            this, findViewById<LinearLayout>(R.id.schedule_root),
            model::selectDate, model::toggleWeek, { model.refresh(true) },
            dialogs::show, dialogs::chooseTeacher
        )
        model.state.observe(this) { screen.render(it) }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        if (savedInstanceState == null) model.refresh(true)
    }

    override fun onResume() {
        super.onResume()
        ReminderScheduler.reschedule(this)
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 60_000L)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
            downTime = event.eventTime
        }
        val state = if (::model.isInitialized) model.state.value else null
        val density = resources.displayMetrics.density
        if (event.actionMasked == MotionEvent.ACTION_UP && state != null && !state.allWeek &&
            abs(event.x - downX) > 90 * density &&
            abs(event.y - downY) < 55 * density &&
            event.eventTime - downTime < 700
        ) {
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            model.selectDate(state.date.plusDays(if (event.x < downX) 1L else -1L))
            return true
        }
        return super.dispatchTouchEvent(event)
    }
}

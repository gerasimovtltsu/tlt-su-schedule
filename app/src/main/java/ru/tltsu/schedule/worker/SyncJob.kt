package ru.tltsu.schedule

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import ru.tltsu.schedule.data.local.teacher
import ru.tltsu.schedule.data.repository.ScheduleRepository
import ru.tltsu.schedule.notification.ReminderScheduler
import ru.tltsu.schedule.util.today
import ru.tltsu.schedule.util.week
import ru.tltsu.schedule.update.UpdateManager

class SyncJob : JobService() {
    private class Run(val params: JobParameters) {
        val cancelled = AtomicBoolean(false)
        var future: Future<*>? = null
    }
    private var active: Run? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onStartJob(params: JobParameters): Boolean {
        stopActive()
        if (!ru.tltsu.schedule.data.local.profileConfigured(this)) return false
        val run = Run(params)
        active = run
        val context = applicationContext
        run.future = ScheduleRepository.executor.submit {
            val id = teacher(context)
            val monday = week(today())
            var success = true
            val valid = { !run.cancelled.get() && !Thread.currentThread().isInterrupted && teacher(context) == id }
            try {
                for (date in listOf(monday, monday.plusWeeks(1))) {
                    if (!valid()) break
                    if (runCatching { ScheduleRepository.fetch(context, date, id, valid) }.isFailure) success = false
                }
                if (valid()) {
                    ReminderScheduler.reschedule(context)
                    UpdateManager.enqueueBackground(context)
                }
            } catch (e: Exception) {
                android.util.Log.w("ScheduleSync", "Sync failed", e)
                success = false
            } finally {
                val retry = !success
                main.post {
                    if (active === run && !run.cancelled.get()) {
                        active = null
                        jobFinished(params, retry)
                    }
                }
            }
        }
        return true
    }

    private fun stopActive() {
        active?.let { it.cancelled.set(true); it.future?.cancel(true) }
        active = null
    }

    override fun onStopJob(params: JobParameters): Boolean {
        if (active?.params == params) stopActive()
        return true
    }

    override fun onDestroy() {
        stopActive()
        super.onDestroy()
    }
}

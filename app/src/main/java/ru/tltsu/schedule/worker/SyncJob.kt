package ru.tltsu.schedule

import android.app.job.JobParameters
import android.app.job.JobService
import ru.tltsu.schedule.data.local.teacher
import ru.tltsu.schedule.data.repository.ScheduleRepository
import ru.tltsu.schedule.notification.ReminderScheduler
import ru.tltsu.schedule.util.today
import ru.tltsu.schedule.util.week

class SyncJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        ScheduleRepository.executor.execute {
            val id = teacher(this)
            val monday = week(today())
            var success = true
            for (date in listOf(monday, monday.plusWeeks(1))) {
                if (runCatching { ScheduleRepository.fetch(this, date, id) }.isFailure) success = false
            }
            ReminderScheduler.reschedule(this)
            jobFinished(params, !success)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

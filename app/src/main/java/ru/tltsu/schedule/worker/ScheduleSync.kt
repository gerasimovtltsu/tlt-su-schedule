package ru.tltsu.schedule.worker

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import ru.tltsu.schedule.SyncJob

internal object ScheduleSync {
    private const val JOB_ID = 15943
    private const val PERIOD = 3_600_000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, SyncJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(PERIOD)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }
}

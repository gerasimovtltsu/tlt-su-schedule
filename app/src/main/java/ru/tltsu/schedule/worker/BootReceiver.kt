package ru.tltsu.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.tltsu.schedule.notification.ReminderScheduler
import ru.tltsu.schedule.worker.ScheduleSync

/** Keep the original manifest component name and intent filters. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ScheduleSync.schedule(context)
        ReminderScheduler.reschedule(context)
    }
}

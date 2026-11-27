package ru.tltsu.schedule.ui.schedule

import android.app.Application
import android.app.NotificationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.time.LocalDate
import ru.tltsu.schedule.data.local.ScheduleCache
import ru.tltsu.schedule.data.local.prefs
import ru.tltsu.schedule.data.local.teacher
import ru.tltsu.schedule.data.repository.ScheduleRepository
import ru.tltsu.schedule.notification.ReminderScheduler
import ru.tltsu.schedule.util.today
import ru.tltsu.schedule.util.week

internal data class ScheduleState(
    val date: LocalDate = today(),
    val allWeek: Boolean = false,
    val loading: Boolean = false,
    val syncError: Boolean = false,
    val snapshot: ScheduleSnapshot? = null
)

internal class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val mutableState = MutableLiveData(ScheduleState())
    val state: LiveData<ScheduleState> = mutableState
    private val current: ScheduleState get() = mutableState.value ?: ScheduleState()
    @Volatile private var generation = 0
    private var request: java.util.concurrent.Future<*>? = null
    private val cacheExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var cacheRequest: java.util.concurrent.Future<*>? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var cacheGeneration = 0

    private fun loadSnapshot() {
        val date = current.date
        val id = teacher(context)
        val token = ++cacheGeneration
        cacheRequest?.cancel(true)
        cacheRequest = cacheExecutor.submit {
            android.os.Trace.beginSection("Schedule.cacheSnapshot")
            val snapshot = try { ScheduleSnapshot.load(context, date, id) } finally { android.os.Trace.endSection() }
            main.post {
                if (token == cacheGeneration && teacher(context) == id && week(current.date) == week(date)) {
                    mutableState.value = current.copy(snapshot = snapshot)
                }
            }
        }
    }

    fun initialize(refreshNetwork: Boolean) {
        if (!ru.tltsu.schedule.data.local.profileConfigured(context)) return
        if (current.snapshot == null) loadSnapshot()
        if (refreshNetwork) refresh(true)
    }

    fun selectDate(date: LocalDate, dayMode: Boolean = false) {
        val changedWeek = week(date) != week(current.date)
        mutableState.value = current.copy(date = date, allWeek = if (dayMode) false else current.allWeek,
            snapshot = if (changedWeek) null else current.snapshot)
        if (changedWeek) loadSnapshot()
        if (!ScheduleCache.exists(context, teacher(context), week(date))) refresh()
    }

    fun toggleWeek() {
        mutableState.value = current.copy(allWeek = !current.allWeek)
    }

    fun changeTeacher(id: Int) {
        if (id == 0 || id == Int.MIN_VALUE) return
        if (ru.tltsu.schedule.data.local.profileConfigured(context) && teacher(context) == id) return
        ru.tltsu.schedule.data.local.saveProfile(context, id)
        generation++
        request?.cancel(true)
        context.getSystemService(NotificationManager::class.java).cancelAll()
        ReminderScheduler.request(context, reset = true)
        mutableState.value = ScheduleState()
        loadSnapshot()
        refresh(true)
    }

    fun refresh(includeUpcoming: Boolean = false) {
        if (current.loading || !ru.tltsu.schedule.data.local.profileConfigured(context)) return
        val selectedWeek = week(current.date)
        val id = teacher(context)
        val token = ++generation
        mutableState.value = current.copy(loading = true, syncError = false)
        request = ScheduleRepository.executor.submit {
            val valid = { token == generation && !Thread.currentThread().isInterrupted && teacher(context) == id }
            if (!valid()) return@submit
            android.os.Trace.beginSection("Schedule.fetchVisibleWeek")
            val result = try { runCatching { ScheduleRepository.fetch(context, selectedWeek, id, valid) } }
                finally { android.os.Trace.endSection() }
            if (!valid()) return@submit
            main.post {
                if (token == generation) {
                    mutableState.value = current.copy(loading = false, syncError = result.isFailure)
                    loadSnapshot()
                    if (week(current.date) != selectedWeek &&
                        !ScheduleCache.exists(context, id, week(current.date))) refresh()
                }
            }
            ReminderScheduler.request(context)
            if (includeUpcoming && valid()) {
                val monday = week(today())
                for (other in listOf(monday, monday.plusWeeks(1)).filter { it != selectedWeek }) {
                    if (!valid()) break
                    android.os.Trace.beginSection("Schedule.prefetchWeek")
                    try { runCatching { ScheduleRepository.fetch(context, other, id, valid) } }
                    finally { android.os.Trace.endSection() }
                }
                if (valid()) {
                    ReminderScheduler.request(context)
                    main.post { if (token == generation) loadSnapshot() }
                }
            }
        }
    }

    override fun onCleared() {
        generation++
        request?.cancel(true)
        cacheGeneration++
        cacheRequest?.cancel(true)
        cacheExecutor.shutdownNow()
        super.onCleared()
    }
}

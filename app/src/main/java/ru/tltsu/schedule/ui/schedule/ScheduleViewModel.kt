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
    val syncError: Boolean = false
)

internal class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val mutableState = MutableLiveData(ScheduleState())
    val state: LiveData<ScheduleState> = mutableState
    private val current: ScheduleState get() = mutableState.value ?: ScheduleState()
    @Volatile private var generation = 0
    private var request: java.util.concurrent.Future<*>? = null

    fun selectDate(date: LocalDate, dayMode: Boolean = false) {
        mutableState.value = current.copy(date = date, allWeek = if (dayMode) false else current.allWeek)
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
            val result = runCatching { ScheduleRepository.fetch(context, selectedWeek, id, valid) }
            if (includeUpcoming && valid()) {
                val monday = week(today())
                for (other in listOf(monday, monday.plusWeeks(1)).filter { it != selectedWeek }) {
                    if (!valid()) break
                    runCatching { ScheduleRepository.fetch(context, other, id, valid) }
                }
            }
            if (!valid()) return@submit
            ReminderScheduler.request(context)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (token == generation) {
                    mutableState.value = current.copy(loading = false, syncError = result.isFailure)
                    if (week(current.date) != selectedWeek &&
                        !ScheduleCache.exists(context, id, week(current.date))
                    ) refresh()
                }
            }
        }
    }

    override fun onCleared() {
        generation++
        request?.cancel(true)
        super.onCleared()
    }
}

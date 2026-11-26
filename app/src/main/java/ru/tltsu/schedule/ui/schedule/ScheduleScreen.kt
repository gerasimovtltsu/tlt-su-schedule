package ru.tltsu.schedule.ui.schedule

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import ru.tltsu.schedule.data.local.ScheduleCache
import ru.tltsu.schedule.data.local.teacher
import ru.tltsu.schedule.data.local.teacherName
import ru.tltsu.schedule.data.repository.ScheduleRepository
import ru.tltsu.schedule.domain.model.Lesson
import ru.tltsu.schedule.util.dayFmt
import ru.tltsu.schedule.util.ru
import ru.tltsu.schedule.util.today
import ru.tltsu.schedule.util.week
import ru.tltsu.schedule.util.zone

/** Renders the native Android views; all navigation and loading decisions belong to the ViewModel. */
internal class ScheduleScreen(
    private val context: Context,
    private val root: LinearLayout,
    private val onDate: (LocalDate, Boolean) -> Unit,
    private val onToggleWeek: () -> Unit,
    private val onRefresh: () -> Unit,
    private val onSettings: () -> Unit,
    private val onTeacher: () -> Unit
) {
    private val bg = Color.parseColor("#111827")
    private val surface = Color.parseColor("#1E293B")
    private val muted = Color.parseColor("#B4C1D4")
    private val accent = Color.parseColor("#8EB5FF")

    init {
        root.setBackgroundColor(bg)
        root.setPadding(dp(14), dp(5), dp(14), dp(4))
    }

    fun render(state: ScheduleState) {
        val monday = week(state.date)
        val id = teacher(context)
        val lessons = ScheduleRepository.cached(context, monday, id)
        val now = System.currentTimeMillis()
        root.removeAllViews()
        renderHeader(state, monday, id)
        renderCalendar(state, monday, lessons)
        val content = column()
        val scroll = ScrollView(context).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val next = ScheduleRepository.upcoming(context).firstOrNull { it.ends() > now }
        if (next != null) {
            content.addView(nextCard(next, now))
            content.gap(10)
        }

        val dates = if (state.allWeek) (0..6).map { monday.plusDays(it.toLong()) } else listOf(state.date)
        for (date in dates) {
            val day = lessons.filter { it.date == date }
            content.addView(label(date.format(dayFmt).replaceFirstChar { it.uppercase(ru) } + " · " + day.size + " пар", 17f, Color.WHITE, true))
            content.gap(8)
            if (day.isEmpty()) {
                val message = if (ScheduleCache.exists(context, id, monday)) "Занятий нет" else "Расписание не загружено"
                content.addView(label(message, 15f, muted))
                content.gap(16)
            }
            for (lesson in day) {
                content.addView(lessonCard(lesson, now))
                content.gap(6)
            }
            content.gap(8)
        }
        renderBottom(state)
    }

    private fun renderHeader(state: ScheduleState, monday: LocalDate, id: Int) {
        val top = row()
        top.addView(label("Расписание ТГУ", 21f, Color.WHITE, true), weight())
        top.addView(iconButton(android.R.drawable.ic_popup_sync, "Обновить", onRefresh))
        top.addView(iconButton(android.R.drawable.ic_menu_preferences, "Настройки", onSettings))
        root.addView(top)
        root.addView(label(teacherName(context), 12f, accent).apply { setOnClickListener { onTeacher() } })
        val updated = ScheduleCache.updatedAt(context, id, monday)
        val status = when {
            state.loading -> "Обновление…"
            state.syncError -> "Нет связи · сохранённые данные"
            updated > 0 -> "Обновлено " + Instant.ofEpochMilli(updated).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
            else -> "Нет загруженных данных"
        }
        root.addView(label(status, 11f, muted))
        val nav = row()
        val format = DateTimeFormatter.ofPattern("d MMM", ru)
        nav.addView(button("‹") { onDate(state.date.minusWeeks(1), false) })
        nav.addView(label(monday.format(format) + " — " + monday.plusDays(6).format(format), 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER }, weight())
        nav.addView(button("›") { onDate(state.date.plusWeeks(1), false) })
        root.addView(nav)
    }

    private fun renderCalendar(state: ScheduleState, monday: LocalDate, lessons: List<Lesson>) {
        val days = row()
        val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        for (index in names.indices) {
            val date = monday.plusDays(index.toLong())
            val item = column().apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(5))
                background = rounded(if (!state.allWeek && date == state.date) Color.parseColor("#315DA8") else Color.TRANSPARENT, 10)
                setOnClickListener { onDate(date, true) }
            }
            item.addView(label(names[index], 11f, muted).apply { gravity = Gravity.CENTER })
            item.addView(label(date.dayOfMonth.toString(), 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
            item.addView(label(if (lessons.any { it.date == date }) "●" else " ", 8f, accent).apply { gravity = Gravity.CENTER })
            days.addView(item, weight().apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        root.addView(days)
        root.gap(8)
    }

    private fun nextCard(lesson: Lesson, now: Long): LinearLayout {
        val banner = column().apply {
            background = rounded(Color.parseColor("#233A5C"))
            setPadding(dp(11), dp(8), dp(11), dp(8))
        }
        val title = when {
            lesson.begins() <= now -> "Сейчас идёт пара"
            lesson.date == today() -> "Через " + ((lesson.begins() - now + 59_999) / 60_000) + " мин."
            lesson.date == today().plusDays(1) -> "Завтра в " + lesson.start
            else -> lesson.date.format(dayFmt) + " · " + lesson.start
        }
        banner.addView(label(title, 13f, accent, true))
        banner.gap(2)
        banner.addView(label(lesson.name, 15f, Color.WHITE, true))
        banner.gap(2)
        banner.addView(label(lesson.type + " · " + lesson.room + " · " + lesson.time(), 12f, muted))
        return banner
    }

    private fun lessonCard(lesson: Lesson, now: Long): LinearLayout {
        val line = row().apply { gravity = Gravity.TOP }
        val times = column()
        times.addView(label(lesson.start.toString(), 15f, Color.WHITE, true))
        times.addView(label(lesson.end.toString(), 12f, muted))
        line.addView(times, LinearLayout.LayoutParams(dp(54), -2))
        val card = column().apply {
            background = rounded(surface, 10)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        val typeColor = when (lesson.type) {
            "Лекция" -> accent
            "Лабораторная" -> Color.parseColor("#72D9BA")
            else -> Color.parseColor("#F2C581")
        }
        card.addView(label(lesson.name, 15f, Color.WHITE, true))
        card.gap(2)
        card.addView(label(lesson.type + " · " + lesson.room, 12f, typeColor, true))
        card.gap(2)
        card.addView(label(lesson.groups, 12f, muted))
        if (lesson.note.isNotBlank()) card.addView(label(lesson.note, 11f, muted))
        if (lesson.ends() < now) line.alpha = 0.68f
        line.addView(card, weight())
        return line
    }

    private fun renderBottom(state: ScheduleState) {
        val bottom = row()
        bottom.addView(button("Сегодня") { onDate(today(), true) }, weight())
        bottom.addView(button(if (state.allWeek) "● Неделя" else "Неделя", onToggleWeek), weight())
        bottom.addView(button("Напоминания", onSettings), weight())
        root.addView(bottom)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun rounded(color: Int, radius: Int = 16) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun label(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(value: String, action: () -> Unit) = label(value, 14f, accent).apply {
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(12), dp(10), dp(12))
        setOnClickListener { action() }
    }
    private fun iconButton(resource: Int, description: String, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(resource)
        setColorFilter(accent)
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = description
        setPadding(dp(11), dp(11), dp(11), dp(11))
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        setOnClickListener { action() }
    }
    private fun LinearLayout.gap(value: Int) {
        addView(Space(context), LinearLayout.LayoutParams(1, dp(value)))
    }
}

package ru.tltsu.schedule.ui.schedule

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageButton
import android.view.View
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import ru.tltsu.schedule.R
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

    private val compactLayout: Boolean
        get() = context.resources.configuration.fontScale >= 1.25f ||
            context.resources.configuration.screenWidthDp < 360 ||
            context.resources.configuration.screenHeightDp < 600

    private var previousKey: String? = null
    private var previousScroll: ScrollView? = null
    private var lessonArea: LinearLayout? = null

    fun isLessonSwipeArea(x: Float, y: Float): Boolean {
        val area = lessonArea ?: return false
        val rect = android.graphics.Rect()
        return area.getGlobalVisibleRect(rect) && rect.contains(x.toInt(), y.toInt())
    }

    init {
        root.setBackgroundColor(bg)
        root.setPadding(dp(14), dp(5), dp(14), dp(4))
    }

    fun render(state: ScheduleState) {
        val monday = week(state.date)
        val id = teacher(context)
        val lessons = ScheduleRepository.cached(context, monday, id)
        val now = System.currentTimeMillis()
        val key = "$id/${state.date}/${state.allWeek}"
        val scrollY = if (key == previousKey) previousScroll?.scrollY ?: 0 else 0
        previousKey = key
        root.removeAllViews()
        val content = column()
        val headerParent = if (compactLayout) content else root
        renderHeader(headerParent, state, monday, id)
        renderCalendar(headerParent, state, monday, lessons)
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        previousScroll = scroll
        scroll.post { scroll.scrollTo(0, scrollY) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val lessonContent = column()
        content.addView(lessonContent)
        lessonArea = lessonContent
        val next = ScheduleRepository.upcoming(context).filter { it.ends() > now }.minByOrNull { it.begins() }
        if (next != null && (state.date == today() || (state.allWeek && monday == week(today())))) {
            lessonContent.addView(nextCard(next, now))
            lessonContent.gap(16)
        }

        val dates = if (state.allWeek) (0..6).map { monday.plusDays(it.toLong()) } else listOf(state.date)
        for (date in dates) {
            val day = lessons.filter { it.date == date }.sortedBy { it.start }
            lessonContent.addView(label(date.format(dayFmt).replaceFirstChar { it.uppercase(ru) } + " · " + lessonCount(day.size), 17f, Color.WHITE, true))
            lessonContent.gap(8)
            if (day.isEmpty()) {
                val message = when {
                    ScheduleRepository.readCached(context, monday, id) != null -> "Занятий нет"
                    ScheduleCache.exists(context, id, monday) -> "Не удалось прочитать расписание"
                    else -> "Расписание не загружено"
                }
                lessonContent.addView(column().apply {
                    background = rounded(surface, 14)
                    setPadding(dp(16), dp(20), dp(16), dp(20))
                    addView(label(message, 16f, Color.WHITE, true))
                    gap(5)
                    addView(label(if (state.loading) "Получаем данные…" else if (ScheduleRepository.readCached(context, monday, id) != null) "На этот день пары не запланированы" else "Нажмите кнопку обновления", 13f, muted))
                })
                lessonContent.gap(16)
            }
            for (lesson in day) {
                lessonContent.addView(lessonCard(lesson, now))
                lessonContent.gap(10)
            }
            lessonContent.gap(8)
        }
        renderBottom(state)
    }

    private fun renderHeader(parent: LinearLayout, state: ScheduleState, monday: LocalDate, id: Int) {
        val top = row()
        top.addView(label("Расписание ТГУ", if (compactLayout) 19f else 22f, Color.WHITE, true), weight())
        top.addView(iconButton(R.drawable.ic_refresh_modern, "Обновить", onRefresh))
        top.addView(iconButton(R.drawable.ic_settings_modern, "Настройки", onSettings).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(4)
        })
        parent.addView(top)
        parent.addView(label(teacherName(context) + "  ▾", 14f, accent).apply {
            minHeight = dp(32)
            gravity = Gravity.CENTER_VERTICAL
            background = ripple(Color.TRANSPARENT, 8)
            contentDescription = "Выбрать преподавателя: " + teacherName(context)
            setOnClickListener { onTeacher() }
        })
        val updated = ScheduleCache.updatedAt(context, id, monday)
        val status = when {
            state.loading -> "Обновление…"
            state.syncError -> "Нет связи · сохранённые данные"
            updated > 0 -> {
                val stamp = Instant.ofEpochMilli(updated).atZone(zone)
                "Обновлено " + stamp.format(DateTimeFormatter.ofPattern(if (stamp.toLocalDate() == today()) "HH:mm" else "dd.MM HH:mm"))
            }
            else -> "Нет загруженных данных"
        }
        parent.addView(label(status, 12f, if (state.syncError) Color.parseColor("#F2C581") else muted))
        parent.gap(6)
        val nav = row()
        val format = DateTimeFormatter.ofPattern("d MMM", ru)
        nav.addView(iconButton(R.drawable.ic_chevron_left, "Предыдущая неделя") { onDate(state.date.minusWeeks(1), false) })
        nav.addView(label(monday.format(format) + " — " + monday.plusDays(6).format(format), 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER }, weight())
        nav.addView(iconButton(R.drawable.ic_chevron_right, "Следующая неделя") { onDate(state.date.plusWeeks(1), false) })
        parent.addView(nav)
        parent.gap(6)
    }

    private fun renderCalendar(parent: LinearLayout, state: ScheduleState, monday: LocalDate, lessons: List<Lesson>) {
        val days = row()
        val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        for (index in names.indices) {
            val date = monday.plusDays(index.toLong())
            val selected = !state.allWeek && date == state.date
            val isToday = date == today()
            val count = lessons.count { it.date == date }
            val item = column().apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(5))
                minimumHeight = dp(60)
                background = ripple(if (selected) Color.parseColor("#315DA8") else surface, 10, if (isToday) accent else null)
                isSelected = selected
                contentDescription = date.format(dayFmt) + ", " + lessonCount(count) + if (isToday) ", сегодня" else ""
                setOnClickListener { onDate(date, true) }
            }
            item.addView(label(names[index], 12f, if (selected) Color.WHITE else muted).apply { gravity = Gravity.CENTER })
            item.addView(label(date.dayOfMonth.toString(), 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
            item.addView(label(if (count > 0) count.toString() else "–", 11f, if (selected) Color.WHITE else accent).apply { gravity = Gravity.CENTER })
            days.addView(item, if (compactLayout) LinearLayout.LayoutParams(dp(54), -2).apply { setMargins(dp(2), 0, dp(2), 0) } else weight().apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        if (compactLayout) {
            val horizontal = android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                addView(days)
            }
            parent.addView(horizontal)
            horizontal.post { horizontal.smoothScrollTo((state.date.dayOfWeek.value - 1) * dp(58) - horizontal.width / 2 + dp(29), 0) }
        } else parent.addView(days)
        parent.gap(14)
    }

    private fun nextCard(lesson: Lesson, now: Long): LinearLayout {
        val banner = column().apply {
            background = rounded(Color.parseColor("#233A5C"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val title = when {
            lesson.begins() <= now -> if (lesson.number > 0) "Сейчас идёт ${lesson.number}-я пара" else "Сейчас идёт пара"
            lesson.date == today() -> "Через " + duration((lesson.begins() - now + 59_999) / 60_000)
            lesson.date == today().plusDays(1) -> "Завтра в " + lesson.start
            else -> lesson.date.format(dayFmt) + " · " + lesson.start
        }
        if (lesson.begins() > now) {
            banner.addView(label(if (lesson.number > 0) "БЛИЖАЙШАЯ ПАРА · №${lesson.number}" else "БЛИЖАЙШАЯ ПАРА", 11f, accent, true))
            banner.gap(4)
        }
        banner.addView(label(title, 18f, Color.WHITE, true))
        banner.gap(2)
        banner.addView(label(lesson.name, 16f, Color.WHITE, true))
        banner.gap(2)
        banner.addView(label(lesson.room + "  ·  " + lesson.time() + "  ·  " + lesson.type, 13f, muted))
        return banner
    }

    private fun lessonCard(lesson: Lesson, now: Long): LinearLayout {
        val active = lesson.begins() <= now && lesson.ends() > now
        val past = lesson.ends() <= now
        val typeColor = when (lesson.type) {
            "Лекция" -> accent
            "Лабораторная" -> Color.parseColor("#72D9BA")
            else -> Color.parseColor("#F2C581")
        }
        val line = if (compactLayout) column() else row().apply { gravity = Gravity.TOP }
        val times = column().apply { setPadding(0, dp(11), 0, 0) }
        if (lesson.number > 0) {
            times.addView(label("№${lesson.number}", 12f, accent, true))
            times.gap(4)
        }
        times.addView(label(lesson.start.toString(), 16f, Color.WHITE, true))
        times.gap(3)
        times.addView(label(lesson.end.toString(), 13f, muted))
        if (!compactLayout) line.addView(times, LinearLayout.LayoutParams(dp(57), -2))
        val card = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(if (active) Color.parseColor("#233A5C") else surface, 14).apply {
                if (active) setStroke(dp(1), accent)
            }
            setPadding(dp(9), dp(11), dp(12), dp(11))
        }
        card.addView(View(context).apply { background = rounded(typeColor, 3) }, LinearLayout.LayoutParams(dp(3), dp(44)).apply { marginEnd = dp(10) })
        val details = column()
        if (compactLayout) {
            details.addView(label((if (lesson.number > 0) "${lesson.number}-я пара · " else "") + lesson.time(), 14f, muted, true))
            details.gap(4)
        }
        if (active) {
            details.addView(label("ИДЁТ СЕЙЧАС", 11f, accent, true))
            details.gap(4)
        }
        details.addView(label(lesson.name, 16f, Color.WHITE, true).apply { setLineSpacing(dp(2).toFloat(), 1f) })
        details.gap(6)
        details.addView(label(lesson.room.ifBlank { "Аудитория не указана" }, 15f, Color.WHITE, true))
        details.gap(4)
        details.addView(label(lesson.type, 13f, typeColor, true))
        if (lesson.groups.isNotBlank()) {
            details.gap(3)
            details.addView(label(lesson.groups, 13f, muted))
        }
        if (lesson.note.isNotBlank()) {
            details.gap(4)
            details.addView(label(lesson.note, 12f, muted))
        }
        card.addView(details, weight())
        if (past) line.alpha = 0.78f
        line.addView(card, if (compactLayout) LinearLayout.LayoutParams(-1, -2) else weight())
        return line
    }

    private fun renderBottom(state: ScheduleState) {
        val bottom = row().apply {
            setPadding(0, dp(8), 0, dp(6))
        }
        bottom.addView(
            navigationButton("Сегодня", R.drawable.ic_calendar_day, !state.allWeek && state.date == today()) {
                onDate(today(), true)
            },
            weight().apply { marginEnd = dp(5) }
        )
        bottom.addView(
            navigationButton("Неделя", R.drawable.ic_calendar_week, state.allWeek, onToggleWeek),
            weight().apply { marginStart = dp(5) }
        )
        root.addView(bottom)
    }

    private fun navigationButton(value: String, icon: Int, active: Boolean, action: () -> Unit) =
        android.widget.Button(context).apply {
            text = value
            textSize = 15f
            isAllCaps = false
            isSelected = active
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (active) Color.WHITE else accent)
            val glyph = context.getDrawable(icon)?.mutate()
            glyph?.setTint(if (active) Color.WHITE else accent)
            glyph?.setBounds(0, 0, dp(20), dp(20))
            if (!compactLayout) setCompoundDrawablesRelative(glyph, null, null, null)
            compoundDrawablePadding = dp(6)
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(dp(if (compactLayout) 6 else 12), dp(10), dp(if (compactLayout) 6 else 12), dp(10))
            stateListAnimator = null
            backgroundTintList = null
            val shape = rounded(if (active) Color.parseColor("#315DA8") else surface, 12).apply {
                setStroke(dp(1), if (active) accent else Color.parseColor("#40536E"))
            }
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                shape,
                null
            )
            setOnClickListener { action() }
        }

    private fun ripple(color: Int, radius: Int, stroke: Int? = null) =
        RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(color, radius).apply {
            if (stroke != null) setStroke(dp(1), stroke)
        }, rounded(Color.WHITE, radius))

    private fun lessonCount(n: Int): String {
        val word = when {
            n % 100 in 11..14 -> "пар"
            n % 10 == 1 -> "пара"
            n % 10 in 2..4 -> "пары"
            else -> "пар"
        }
        return "$n $word"
    }

    private fun duration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h == 0L) "$m мин." else if (m == 0L) "$h ч" else "$h ч $m мин."
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
        background = ripple(Color.TRANSPARENT, 24)
        contentDescription = description
        setPadding(dp(12), dp(12), dp(12), dp(12))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener { action() }
    }
    private fun LinearLayout.gap(value: Int) {
        addView(Space(context), LinearLayout.LayoutParams(1, dp(value)))
    }
}

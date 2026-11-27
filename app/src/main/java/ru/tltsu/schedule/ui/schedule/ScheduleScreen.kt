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
import ru.tltsu.schedule.ui.AppPalette
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
import ru.tltsu.schedule.domain.model.DaySlot
import ru.tltsu.schedule.domain.model.daySlots
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
    private val palette = AppPalette.current(context)
    private val bg = palette.bg
    private val surface = palette.surface
    private val muted = palette.muted
    private val accent = palette.accent

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
            lessonContent.addView(label(date.format(dayFmt).replaceFirstChar { it.uppercase(ru) } + " · " + lessonCount(day.size), 17f, palette.text, true))
            lessonContent.gap(8)
            if (day.isEmpty()) {
                val message = when {
                    ScheduleRepository.readCached(context, monday, id) != null -> "Занятий нет"
                    ScheduleCache.exists(context, id, monday) -> "Не удалось прочитать расписание"
                    else -> "Расписание не загружено"
                }
                lessonContent.addView(column().apply {
                    background = rounded(surface, palette.radius).apply { if (palette.showBorders) setStroke(dp(1), palette.border) }
                    setPadding(dp(16), dp(20), dp(16), dp(20))
                    addView(label(message, 16f, palette.text, true))
                    gap(5)
                    addView(label(if (state.loading) "Получаем данные…" else if (ScheduleRepository.readCached(context, monday, id) != null) "На этот день пары не запланированы" else "Нажмите кнопку обновления", 13f, muted))
                })
                lessonContent.gap(16)
            }
            for (slot in daySlots(day)) {
                when (slot) {
                    is DaySlot.Occupied -> lessonContent.addView(lessonCard(slot.lesson, now))
                    is DaySlot.Empty -> lessonContent.addView(emptySlot(slot.number))
                }
                lessonContent.gap(if (compactLayout) 7 else 10)
            }
            lessonContent.gap(8)
        }
        renderBottom(state)
    }

    private fun renderHeader(parent: LinearLayout, state: ScheduleState, monday: LocalDate, id: Int) {
        val top = row()
        top.addView(label("Расписание ТГУ", if (compactLayout) 19f else 22f, palette.text, true), weight())
        top.addView(iconButton(R.drawable.ic_refresh_modern, "Обновить", onRefresh))
        top.addView(iconButton(R.drawable.ic_settings_modern, "Настройки", onSettings).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(4)
        })
        parent.addView(top)
        parent.addView(label(teacherName(context) + "  ▾", 14f, accent).apply {
            minHeight = dp(32)
            gravity = Gravity.CENTER_VERTICAL
            background = ripple(Color.TRANSPARENT, 8)
            contentDescription = "Выбрать профиль: " + teacherName(context)
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
        parent.addView(label(status, 12f, if (state.syncError) palette.practice else muted))
        parent.gap(6)
        val nav = row()
        val format = DateTimeFormatter.ofPattern("d MMM", ru)
        nav.addView(iconButton(R.drawable.ic_chevron_left, "Предыдущая неделя") { onDate(state.date.minusWeeks(1), false) })
        nav.addView(label(monday.format(format) + " — " + monday.plusDays(6).format(format), 17f, palette.text, true).apply { gravity = Gravity.CENTER }, weight())
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
                background = ripple(if (selected) palette.selected else surface, palette.radius, if (isToday) accent else if (palette.showBorders) palette.border else null)
                isSelected = selected
                contentDescription = date.format(dayFmt) + ", " + lessonCount(count) + if (isToday) ", сегодня" else ""
                setOnClickListener { onDate(date, true) }
            }
            item.addView(label(names[index], 12f, if (selected) palette.onSelected else muted).apply { gravity = Gravity.CENTER })
            item.addView(label(date.dayOfMonth.toString(), 16f, if (selected) palette.onSelected else palette.text, true).apply { gravity = Gravity.CENTER })
            item.addView(label(if (count > 0) count.toString() else "–", 11f, if (selected) palette.onSelected else accent).apply { gravity = Gravity.CENTER })
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
            background = rounded(palette.highlight, palette.radius).apply { if (palette.showBorders) setStroke(dp(1), palette.border) }
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
        banner.addView(label(title, 18f, palette.text, true))
        banner.gap(2)
        banner.addView(label(lesson.name, 16f, palette.text, true))
        banner.gap(2)
        banner.addView(label(lesson.room + "  ·  " + lesson.time() + "  ·  " + lesson.type, 13f, muted))
        return banner
    }

    private fun emptySlot(number: Int): LinearLayout {
        val line = row()
        if (!compactLayout) {
            line.addView(label("№$number", 12f, muted, true), LinearLayout.LayoutParams(dp(57), -2))
        }
        val empty = row().apply {
            minimumHeight = dp(32)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(bg, palette.radius).apply {
                setStroke(dp(1), palette.border, dp(4).toFloat(), dp(3).toFloat())
            }
            contentDescription = "$number-я пара: нет занятия"
            addView(label(if (compactLayout) "$number-я пара · Нет пары" else "Нет пары", 12f, muted))
        }
        line.addView(empty, weight())
        return line
    }

    private fun lessonCard(lesson: Lesson, now: Long): LinearLayout {
        val active = lesson.begins() <= now && lesson.ends() > now
        val past = lesson.ends() <= now
        val typeColor = when (lesson.type) {
            "Лекция" -> palette.lecture
            "Лабораторная" -> palette.lab
            else -> palette.practice
        }
        val line = if (compactLayout) column() else row().apply { gravity = Gravity.TOP }
        val times = column().apply { setPadding(0, dp(11), 0, 0) }
        if (lesson.number > 0) {
            times.addView(label("№${lesson.number}", 12f, accent, true))
            times.gap(4)
        }
        times.addView(label(lesson.start.toString(), 16f, palette.text, true))
        times.gap(3)
        times.addView(label(lesson.end.toString(), 13f, muted))
        if (!compactLayout) line.addView(times, LinearLayout.LayoutParams(dp(57), -2))
        val card = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(if (active) palette.highlight else surface, palette.radius).apply {
                if (active || palette.showBorders) setStroke(dp(1), if (active) accent else palette.border)
            }
            setPadding(dp(if (compactLayout) 8 else 9), dp(if (compactLayout) 7 else 11), dp(if (compactLayout) 10 else 12), dp(if (compactLayout) 7 else 11))
        }
        card.addView(View(context).apply { background = rounded(typeColor, 3) }, LinearLayout.LayoutParams(dp(3), dp(44)).apply { marginEnd = dp(if (compactLayout) 7 else 10) })
        val details = column()
        if (compactLayout) {
            details.addView(label((if (lesson.number > 0) "${lesson.number}-я пара · " else "") + lesson.time() + if (active) " · Сейчас" else "", 12f, if (active) accent else muted))
            details.gap(3)
        }
        if (active && !compactLayout) {
            details.addView(label("ИДЁТ СЕЙЧАС", 11f, accent, true))
            details.gap(4)
        }
        details.addView(label(lesson.name, if (compactLayout) 15f else 16f, palette.text, true).apply { setLineSpacing(dp(if (compactLayout) 0 else 2).toFloat(), 1f) })
        details.gap(if (compactLayout) 3 else 6)
        details.addView(label(lesson.room.ifBlank { "Аудитория не указана" }, 15f, palette.text, true))
        details.gap(if (compactLayout) 2 else 4)
        if (compactLayout) {
            val combined = listOf(lesson.type, lesson.groups).filter { it.isNotBlank() }.joinToString(" · ")
            val styled = android.text.SpannableString(combined)
            if (lesson.type.isNotBlank()) styled.setSpan(android.text.style.ForegroundColorSpan(typeColor), 0, lesson.type.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            details.addView(label("", 12f, muted).apply { text = styled })
        } else {
            details.addView(label(lesson.type, 13f, typeColor, true))
            if (lesson.groups.isNotBlank()) {
                details.gap(3)
                details.addView(label(lesson.groups, 13f, muted))
            }
        }
        if (teacher(context) < 0 && lesson.teacherName.isNotBlank()) {
            details.gap(3)
            details.addView(label(lesson.teacherName, 13f, muted))
        }
        if (lesson.note.isNotBlank()) {
            details.gap(4)
            details.addView(label(lesson.note, 12f, muted))
        }
        card.addView(details, weight())
        // Keep completed lessons readable; status is indicated without fading text.
        if (past) {
            details.addView(label("Завершено", 11f, muted))
        }
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
            textSize = if (compactLayout) 13f else 15f
            isAllCaps = false
            isSelected = active
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (active) palette.onSelected else accent)
            val glyph = context.getDrawable(icon)?.mutate()
            glyph?.setTint(if (active) palette.onSelected else accent)
            glyph?.setBounds(0, 0, dp(20), dp(20))
            if (!compactLayout) setCompoundDrawablesRelative(glyph, null, null, null)
            compoundDrawablePadding = dp(6)
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(dp(if (compactLayout) 6 else 12), dp(if (compactLayout) 4 else 10), dp(if (compactLayout) 6 else 12), dp(if (compactLayout) 4 else 10))
            stateListAnimator = null
            backgroundTintList = null
            val shape = rounded(if (active) palette.selected else surface, palette.radius).apply {
                setStroke(dp(1), if (active) accent else palette.border)
            }
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(palette.ripple),
                shape,
                null
            )
            setOnClickListener { action() }
        }

    private fun ripple(color: Int, radius: Int, stroke: Int? = null) =
        RippleDrawable(ColorStateList.valueOf(palette.ripple), rounded(color, radius).apply {
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
    private fun label(value: String, size: Float, color: Int = palette.text, bold: Boolean = false) =
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

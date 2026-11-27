package ru.tltsu.schedule.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.Gravity
import android.widget.*
import ru.tltsu.schedule.ui.AppPalette
import java.util.Locale

/** Draft changes are isolated until Apply; invalid contrast cannot be saved. */
internal class PaletteEditor(
    private val activity: Activity,
    initial: AppPalette,
    private val onApply: (AppPalette) -> Unit
) {
    private var draft = initial
    private lateinit var root: LinearLayout
    private lateinit var preview: LinearLayout
    private lateinit var warning: TextView
    private lateinit var controls: LinearLayout
    private lateinit var dialog: AlertDialog
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    private fun hex(c: Int) = String.format(Locale.ROOT, "#%06X", c and 0xFFFFFF)
    private fun col() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float = 14f) = TextView(activity).apply { text = value; textSize = size }
    private fun shape(color: Int, radius: Int, border: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (border != null) setStroke(dp(1), border)
    }

    fun show() {
        root = col().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
        preview = col()
        warning = text("")
        controls = col()
        root.addView(preview)
        root.addView(warning)
        root.addView(controls)
        dialog = AlertDialog.Builder(activity).setTitle("Оформление")
            .setView(ScrollView(activity).apply { isVerticalScrollBarEnabled = false; addView(root) })
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Сброс", null)
            .setPositiveButton("Применить", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                draft = AppPalette.defaults()
                buildControls()
                refresh()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (draft.minContrast() >= 4.5) {
                    dialog.dismiss()
                    onApply(draft)
                }
            }
            refresh()
        }
        buildControls()
        dialog.show()
    }

    private fun buildControls() {
        controls.removeAllViews()
        for (i in AppPalette.colorNames.indices) {
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(0, dp(5), 0, dp(5))
            }
            val swatch = android.view.View(activity).apply { background = shape(draft.colors()[i], 8, 0xFF888888.toInt()) }
            val title = text(AppPalette.colorNames[i] + "\n" + hex(draft.colors()[i]))
            line.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(swatch, LinearLayout.LayoutParams(dp(42), dp(32)))
            line.setOnClickListener {
                editColor(i) { c ->
                    draft = draft.withColor(i, c)
                    swatch.background = shape(c, 8, 0xFF888888.toInt())
                    title.text = AppPalette.colorNames[i] + "\n" + hex(c)
                    refresh()
                }
            }
            controls.addView(line)
        }
        addSlider("Непрозрачность карточек", draft.cardOpacity, 100, "%") { draft = draft.copy(cardOpacity = it) }
        addSlider("Непрозрачность текущей пары", draft.activeOpacity, 100, "%") { draft = draft.copy(activeOpacity = it) }
        addSlider("Непрозрачность активных кнопок", draft.buttonOpacity, 100, "%") { draft = draft.copy(buttonOpacity = it) }
        addSlider("Скругление карточек и кнопок", draft.radius, 28, " dp") { draft = draft.copy(radius = it) }
        controls.addView(Switch(activity).apply {
            text = "Рамки карточек и календаря"
            isChecked = draft.showBorders
            setOnCheckedChangeListener { _, checked -> draft = draft.copy(showBorders = checked); refresh() }
        })
    }

    private fun addSlider(label: String, initial: Int, maximum: Int, unit: String, change: (Int) -> Unit) {
        val caption = text("$label: $initial$unit")
        controls.addView(caption)
        controls.addView(SeekBar(activity).apply {
            max = maximum; progress = initial; minimumHeight = dp(48); contentDescription = label
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    caption.text = "$label: $value$unit"
                    change(value); refresh()
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        })
    }

    private fun refresh() {
        preview.removeAllViews()
        preview.setPadding(dp(12), dp(10), dp(12), dp(10))
        preview.background = shape(draft.bg, draft.radius)
        preview.addView(text("Расписание ТГУ", 20f).apply { setTextColor(draft.text); setTypeface(null, Typeface.BOLD) })
        preview.addView(text("Преподаватель ▾", 13f).apply { setTextColor(draft.accent) })
        for (active in listOf(false, true)) {
            val card = col().apply {
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = shape(if (active) draft.highlight else draft.surface, draft.radius,
                    if (active) draft.accent else if (draft.showBorders) draft.border else null)
                addView(text(if (active) "2-я пара · Сейчас" else "1-я пара · 08:30–10:00", 12f).apply { setTextColor(draft.muted) })
                addView(text("Средства программной разработки", 16f).apply { setTextColor(draft.text); setTypeface(null, Typeface.BOLD) })
                addView(text("УЛК-203", 14f).apply { setTextColor(draft.text) })
                addView(text(if (active) "Практика" else "Лекция", 12f).apply { setTextColor(if (active) draft.practice else draft.lecture) })
                if (!active) addView(text("Лабораторная", 12f).apply { setTextColor(draft.lab) })
            }
            preview.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        preview.addView(text("Сегодня", 14f).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)); setTextColor(draft.onSelected)
            background = shape(draft.selected, draft.radius)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        val contrast = draft.minContrast()
        warning.text = String.format(Locale.ROOT, "Контрастность %.2f:1 · %s", contrast,
            if (contrast >= 4.5) "можно сохранить" else "ниже 4,5:1 — измените цвета или прозрачность")
        if (::dialog.isInitialized) dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = contrast >= 4.5
    }

    private fun editColor(index: Int, save: (Int) -> Unit) {
        val original = draft.colors()[index]
        val panel = col().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val swatch = android.view.View(activity)
        panel.addView(swatch, LinearLayout.LayoutParams(-1, dp(48)))
        val input = EditText(activity).apply {
            setSingleLine(true); hint = "#RRGGBB"
            filters = arrayOf(InputFilter.LengthFilter(7))
            setText(hex(original))
        }
        panel.addView(input)
        val channels = intArrayOf((original ushr 16) and 255, (original ushr 8) and 255, original and 255)
        val sliders = mutableListOf<SeekBar>()
        val labels = mutableListOf<TextView>()
        var updating = false
        fun color() = Color.rgb(channels[0], channels[1], channels[2])
        fun update() {
            swatch.background = shape(color(), 8, 0xFF888888.toInt())
            labels.forEachIndexed { i, label -> label.text = "${listOf("R", "G", "B")[i]}: ${channels[i]}" }
        }
        for (i in 0..2) {
            val label = text("")
            labels.add(label); panel.addView(label)
            val slider = SeekBar(activity).apply {
                max = 255; progress = channels[i]; minimumHeight = dp(48)
                contentDescription = listOf("Красный", "Зелёный", "Синий")[i]
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                        if (updating) return
                        channels[i] = value; update()
                        updating = true
                        input.setText(hex(color())); input.error = null
                        updating = false
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) {}
                    override fun onStopTrackingTouch(bar: SeekBar) {}
                })
            }
            sliders.add(slider); panel.addView(slider)
        }
        fun parse(): Int? {
            val raw = input.text.toString().trim().removePrefix("#")
            if (!raw.matches(Regex("[0-9a-fA-F]{6}"))) return null
            return raw.toInt(16) or AppPalette.BLACK
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (updating) return
                val c = parse() ?: return
                updating = true
                for (i in 0..2) { channels[i] = (c ushr (16 - 8*i)) and 255; sliders[i].progress = channels[i] }
                update(); updating = false
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        update()
        val picker = AlertDialog.Builder(activity).setTitle(AppPalette.colorNames[index])
            .setView(ScrollView(activity).apply { addView(panel) })
            .setNegativeButton("Отмена", null).setPositiveButton("Выбрать", null).create()
        picker.setOnShowListener {
            picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val c = parse()
                if (c == null) input.error = "Введите 6 HEX-символов"
                else { save(c); picker.dismiss() }
            }
        }
        picker.show()
    }
}

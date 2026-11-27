package ru.tltsu.schedule.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.view.Gravity
import android.widget.*
import ru.tltsu.schedule.MainActivity
import ru.tltsu.schedule.R
import ru.tltsu.schedule.data.local.prefs
import ru.tltsu.schedule.ui.AppPalette
import java.util.Locale

internal object LauncherIconSettings {
    private const val ID = "schedule_custom_icon"
    private const val MODE = "launcher_shortcut_mode"
    private const val BG = "launcher_shortcut_bg"
    private const val FG = "launcher_shortcut_fg"
    private const val ALPHA = "launcher_shortcut_alpha"
    private const val DEFAULT_BG = 0xFF2455B8.toInt()

    private fun bitmap(context: Context, bg: Int, fg: Int, opacity: Int): Bitmap {
        val result = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor((bg and 0xFFFFFF) or ((opacity.coerceIn(0,100) * 255 / 100) shl 24))
        val mark = context.getDrawable(R.drawable.ic_launcher_calendar)!!.mutate()
        mark.setTint(fg)
        mark.setBounds(0, 0, 216, 216)
        mark.draw(canvas)
        return result
    }

    private fun shortcut(context: Context, mode: String, bg: Int, fg: Int, opacity: Int): ShortcutInfo {
        val p = AppPalette.current(context)
        val image = when (mode) {
            "palette" -> bitmap(context, p.selected, p.onSelected, 100)
            "custom" -> bitmap(context, bg, fg, opacity)
            else -> bitmap(context, DEFAULT_BG, Color.WHITE, 100)
        }
        return ShortcutInfo.Builder(context, ID)
            .setShortLabel("Расписание ТГУ")
            .setLongLabel("Расписание ТГУ")
            .setIcon(Icon.createWithAdaptiveBitmap(image))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
            .build()
    }

    fun refreshPaletteShortcut(context: Context) {
        val storage = prefs(context)
        if (storage.getString(MODE, "") != "palette") return
        runCatching {
            val manager = context.getSystemService(ShortcutManager::class.java)
            if (manager.pinnedShortcuts.any { it.id == ID }) {
                manager.updateShortcuts(listOf(shortcut(context, "palette", DEFAULT_BG, Color.WHITE, 100)))
            }
        }
    }

    fun show(activity: Activity) {
        val storage = prefs(activity)
        val manager = activity.getSystemService(ShortcutManager::class.java)
        val palette = AppPalette.current(activity)
        fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
        fun hex(c: Int) = String.format(Locale.ROOT, "#%06X", c and 0xFFFFFF)
        var mode = storage.getString(MODE, "palette") ?: "palette"
        if (mode !in listOf("palette","custom","default")) mode = "palette"
        var bg = storage.getInt(BG, palette.selectedColor)
        var fg = storage.getInt(FG, palette.onSelected)
        var opacity = storage.getInt(ALPHA, 100).coerceIn(0,100)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val info = TextView(activity).apply {
            text = if (Build.VERSION.SDK_INT >= 33)
                "Основная иконка поддерживает автоцвета Android. Включите тематические значки в настройках рабочего стола, если лаунчер это поддерживает."
            else "Автоматические тематические значки доступны начиная с Android 13."
        }
        body.addView(info)
        body.addView(Button(activity).apply {
            text = "Настройки рабочего стола"
            isAllCaps = false
            setOnClickListener {
                runCatching { activity.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
                    .onFailure { Toast.makeText(activity, "Откройте настройки лаунчера вручную", Toast.LENGTH_LONG).show() }
            }
        })
        body.addView(TextView(activity).apply {
            text = "Отдельный ярлык на рабочем столе"
            textSize = 17f
            setPadding(0, dp(12), 0, dp(6))
        })
        val preview = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Предпросмотр ярлыка"
        }
        body.addView(preview, LinearLayout.LayoutParams(dp(88), dp(88)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val group = RadioGroup(activity)
        val modes = listOf("palette" to "По палитре приложения", "custom" to "Собственные RGB / HEX", "default" to "Стандартный синий")
        for ((key, title) in modes) {
            group.addView(RadioButton(activity).apply {
                id = android.view.View.generateViewId()
                text = title
                tag = key
                isChecked = mode == key
            })
        }
        body.addView(group)
        val custom = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(custom)
        val bgButton = Button(activity).apply { isAllCaps = false }
        val fgButton = Button(activity).apply { isAllCaps = false }
        custom.addView(bgButton)
        custom.addView(fgButton)
        val alphaText = TextView(activity)
        custom.addView(alphaText)
        val alpha = SeekBar(activity).apply { max = 100; progress = opacity; contentDescription = "Непрозрачность фона" }
        custom.addView(alpha)
        val contrast = TextView(activity)
        custom.addView(contrast)
        fun refresh() {
            custom.visibility = if (mode == "custom") android.view.View.VISIBLE else android.view.View.GONE
            bgButton.text = "Фон: " + hex(bg)
            fgButton.text = "Символ: " + hex(fg)
            alphaText.text = "Непрозрачность фона: $opacity%"
            contrast.text = if (AppPalette.contrast(bg,fg) < 3 || opacity < 50)
                "Внимание: символ может быть плохо виден на обоях." else ""
            val image = when (mode) {
                "palette" -> bitmap(activity, palette.selected, palette.onSelected, 100)
                "custom" -> bitmap(activity, bg, fg, opacity)
                else -> bitmap(activity, DEFAULT_BG, Color.WHITE, 100)
            }
            preview.setImageBitmap(image)
        }
        group.setOnCheckedChangeListener { g, id ->
            val selected = g.findViewById<RadioButton>(id)?.tag as? String
            if (selected != null) { mode = selected; refresh() }
        }
        bgButton.setOnClickListener { chooseColor(activity, "Цвет фона", bg) { bg = it; refresh() } }
        fgButton.setOnClickListener { chooseColor(activity, "Цвет символа", fg) { fg = it; refresh() } }
        alpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { opacity = value; refresh() }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
        refresh()
        val pinned = runCatching { manager.pinnedShortcuts.any { it.id == ID } }.getOrDefault(false)
        val supported = manager.isRequestPinShortcutSupported
        if (!supported && !pinned) body.addView(TextView(activity).apply { text = "Этот лаунчер не поддерживает добавление ярлыков из приложения." })
        val dialog = AlertDialog.Builder(activity).setTitle("Иконка приложения")
            .setView(ScrollView(activity).apply { isVerticalScrollBarEnabled = false; addView(body) })
            .setNegativeButton("Закрыть", null)
            .setPositiveButton(if (pinned) "Обновить ярлык" else "Добавить ярлык", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = supported || pinned
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val result = runCatching {
                    val item = shortcut(activity, mode, bg, fg, opacity)
                    if (pinned) manager.updateShortcuts(listOf(item)) else manager.requestPinShortcut(item, null)
                }.getOrDefault(false)
                if (result) {
                    storage.edit().putString(MODE, mode).putInt(BG, bg).putInt(FG, fg).putInt(ALPHA, opacity).apply()
                    Toast.makeText(activity, if (pinned) "Запрошено обновление ярлыка" else "Подтвердите добавление в лаунчере", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                } else Toast.makeText(activity, "Лаунчер не смог применить ярлык", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun chooseColor(activity: Activity, title: String, initial: Int, apply: (Int) -> Unit) {
        fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
        fun hex(c: Int) = String.format(Locale.ROOT, "#%06X", c and 0xFFFFFF)
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val input = EditText(activity).apply { setSingleLine(true); hint = "#RRGGBB"; setText(hex(initial)); filters = arrayOf(android.text.InputFilter.LengthFilter(7)) }
        val picker = HsvPicker(activity).apply { setColor(initial) }
        val hue = SeekBar(activity).apply { max = 359; progress = picker.hue.toInt(); contentDescription = "Цветовой тон" }
        val rgb = TextView(activity)
        var updating = false
        fun parse(): Int? = input.text.toString().trim().removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toInt(16)?.or(Color.BLACK)
        fun update(c: Int, fromPicker: Boolean) {
            updating = true
            if (!fromPicker) { picker.setColor(c); hue.progress = picker.hue.toInt().coerceIn(0,359) }
            else input.setText(hex(c))
            rgb.text = "RGB: ${Color.red(c)}, ${Color.green(c)}, ${Color.blue(c)}"
            input.error = null
            updating = false
        }
        picker.onColor = { update(it,true) }
        hue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { if (!updating && fromUser) { picker.setHue(value.toFloat()); update(picker.color(),true) } }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (!updating) parse()?.let { update(it,false) } }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        panel.addView(input)
        panel.addView(picker, LinearLayout.LayoutParams(-1, dp(160)))
        panel.addView(hue)
        panel.addView(rgb)
        update(initial,false)
        val dialog = AlertDialog.Builder(activity).setTitle(title)
            .setView(ScrollView(activity).apply { addView(panel) })
            .setNegativeButton("Отмена",null).setPositiveButton("Выбрать",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val color = parse()
                if (color == null) input.error = "Введите 6 HEX-символов"
                else { apply(color); dialog.dismiss() }
            }
        }
        dialog.show()
    }
}

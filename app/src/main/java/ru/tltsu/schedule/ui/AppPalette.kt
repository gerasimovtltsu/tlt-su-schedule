package ru.tltsu.schedule.ui

import android.app.Activity
import android.content.Context
import android.view.View
import ru.tltsu.schedule.R
import ru.tltsu.schedule.data.local.prefs
import kotlin.math.pow
import kotlin.math.roundToInt

private enum class LegacyPalette(
    val title: String, val light: Boolean,
    val bg: Int, val surface: Int, val text: Int, val muted: Int,
    val accent: Int, val selected: Int, val highlight: Int, val border: Int,
    val lab: Int, val practice: Int
) {
    MIDNIGHT("Полночь", false, 0xFF111827.toInt(), 0xFF1E293B.toInt(), 0xFFFFFFFF.toInt(), 0xFFB4C1D4.toInt(), 0xFF8EB5FF.toInt(), 0xFF315DA8.toInt(), 0xFF233A5C.toInt(), 0xFF40536E.toInt(), 0xFF72D9BA.toInt(), 0xFFF2C581.toInt()),
    GRAPHITE("Графит", false, 0xFF16181D.toInt(), 0xFF252930.toInt(), 0xFFF5F7FA.toInt(), 0xFFC1C7D0.toInt(), 0xFFB5C9E9.toInt(), 0xFF43566F.toInt(), 0xFF303C4D.toInt(), 0xFF505D70.toInt(), 0xFF8EDCC0.toInt(), 0xFFF4CC8D.toInt()),
    AMOLED("AMOLED", false, 0xFF000000.toInt(), 0xFF151515.toInt(), 0xFFFFFFFF.toInt(), 0xFFC5C5C5.toInt(), 0xFFA9C6FF.toInt(), 0xFF304D83.toInt(), 0xFF172B4D.toInt(), 0xFF505B70.toInt(), 0xFF83E0BD.toInt(), 0xFFFFD18C.toInt()),
    OCEAN("Океан", false, 0xFF0D2333.toInt(), 0xFF173748.toInt(), 0xFFF0FAFF.toInt(), 0xFFBBD6E2.toInt(), 0xFF8DD9FF.toInt(), 0xFF235E7C.toInt(), 0xFF204759.toInt(), 0xFF4E7485.toInt(), 0xFF85E2BD.toInt(), 0xFFFFD18E.toInt()),
    FOREST("Лес", false, 0xFF14251F.toInt(), 0xFF23382E.toInt(), 0xFFF3FBF5.toInt(), 0xFFBED6C6.toInt(), 0xFFA2E3B8.toInt(), 0xFF326747.toInt(), 0xFF294C38.toInt(), 0xFF527760.toInt(), 0xFF9DE5DE.toInt(), 0xFFF5D095.toInt()),
    LAVENDER("Лаванда", false, 0xFF211B30.toInt(), 0xFF332A45.toInt(), 0xFFFAF5FF.toInt(), 0xFFD2C6E3.toInt(), 0xFFD5B8FF.toInt(), 0xFF68498C.toInt(), 0xFF49365F.toInt(), 0xFF77648D.toInt(), 0xFF96E2C8.toInt(), 0xFFFFD199.toInt()),
    PAPER("Бумага", true, 0xFFF5F7FA.toInt(), 0xFFFFFFFF.toInt(), 0xFF192532.toInt(), 0xFF46576B.toInt(), 0xFF174D9A.toInt(), 0xFF255AA8.toInt(), 0xFFE4ECFA.toInt(), 0xFF8193AA.toInt(), 0xFF08664B.toInt(), 0xFF854A00.toInt()),
    SAND("Песок", true, 0xFFF7F1E7.toInt(), 0xFFFFFBF4.toInt(), 0xFF30271F.toInt(), 0xFF655344.toInt(), 0xFF854410.toInt(), 0xFF8B4619.toInt(), 0xFFF1E3D0.toInt(), 0xFF9B8974.toInt(), 0xFF17634B.toInt(), 0xFF854400.toInt()),
    MINT("Мята", true, 0xFFEEF7F3.toInt(), 0xFFFFFFFF.toInt(), 0xFF19322A.toInt(), 0xFF455D53.toInt(), 0xFF075A47.toInt(), 0xFF17654F.toInt(), 0xFFDCEFE5.toInt(), 0xFF76968A.toInt(), 0xFF145F68.toInt(), 0xFF854A00.toInt()),
    ROSE("Роза", true, 0xFFFAF1F4.toInt(), 0xFFFFFFFF.toInt(), 0xFF35232D.toInt(), 0xFF654D59.toInt(), 0xFF8A2455.toInt(), 0xFF9A3264.toInt(), 0xFFF3E0E9.toInt(), 0xFFA28A98.toInt(), 0xFF12664F.toInt(), 0xFF854600.toInt()),
    LAVENDER_LIGHT("Лаванда светлая", true, 0xFFF8F5FC.toInt(), 0xFFFFFFFF.toInt(), 0xFF292238.toInt(), 0xFF5D526E.toInt(), 0xFF69439A.toInt(), 0xFF7149A8.toInt(), 0xFFEEE5FA.toInt(), 0xFF9484AF.toInt(), 0xFF14664F.toInt(), 0xFF854600.toInt());
}

internal data class AppPalette(
    val bg: Int, val cardColor: Int, val text: Int, val muted: Int,
    val accent: Int, val selectedColor: Int, val activeColor: Int, val border: Int,
    val lab: Int, val practice: Int, val lecture: Int, val onSelected: Int,
    val cardOpacity: Int = 100, val activeOpacity: Int = 100,
    val buttonOpacity: Int = 100, val radius: Int = 14, val showBorders: Boolean = false
) {
    val title get() = "Собственное оформление"
    val surface get() = blend(cardColor, bg, cardOpacity)
    val highlight get() = blend(activeColor, bg, activeOpacity)
    val selected get() = blend(selectedColor, bg, buttonOpacity)
    val light get() = contrast(BLACK, bg) >= contrast(WHITE, bg)
    val ripple get() = if (light) 0x22000000 else 0x33FFFFFF
    val style get() = if (light) R.style.Theme_PAPER else R.style.Theme_MIDNIGHT

    fun minContrast(): Double {
        val backgrounds = listOf(bg, surface, highlight)
        return (backgrounds.flatMap { back ->
            listOf(text, muted, accent, lab, practice, lecture).map { contrast(it, back) }
        } + contrast(onSelected, selected)).minOrNull() ?: 0.0
    }

    fun applyWindow(activity: Activity) {
        activity.window.statusBarColor = bg
        activity.window.navigationBarColor = bg
        val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val decor = activity.window.decorView
        decor.systemUiVisibility = (decor.systemUiVisibility and flags.inv()) or (if (light) flags else 0)
    }

    fun colors() = listOf(bg, cardColor, text, muted, accent, selectedColor, activeColor, border, lab, practice, lecture, onSelected)
    fun withColor(index: Int, color: Int): AppPalette {
        val c = color or BLACK
        return when (index) {
            0 -> copy(bg = c)
            1 -> copy(cardColor = c)
            2 -> copy(text = c)
            3 -> copy(muted = c)
            4 -> copy(accent = c)
            5 -> copy(selectedColor = c)
            6 -> copy(activeColor = c)
            7 -> copy(border = c)
            8 -> copy(lab = c)
            9 -> copy(practice = c)
            10 -> copy(lecture = c)
            11 -> copy(onSelected = c)
            else -> this
        }
    }

    companion object {
        private const val KEY = "custom_palette_v1"
        const val BLACK = -16777216
        const val WHITE = -1
        val colorNames = listOf("Фон приложения", "Фон карточек", "Основной текст", "Второстепенный текст",
            "Акцент и иконки", "Активная кнопка и день", "Текущая и ближайшая пара", "Рамки",
            "Лабораторные", "Практика", "Лекции", "Текст активной кнопки")

        private fun fromLegacy(p: LegacyPalette) = AppPalette(p.bg, p.surface, p.text, p.muted,
            p.accent, p.selected, p.highlight, p.border, p.lab, p.practice, p.accent, WHITE)

        fun defaults() = fromLegacy(LegacyPalette.MIDNIGHT)
        internal fun legacyValues() = LegacyPalette.values().map { fromLegacy(it) }

        fun current(context: Context): AppPalette {
            val storage = prefs(context)
            val previous = LegacyPalette.values().firstOrNull { it.name == storage.getString("app_palette", "MIDNIGHT") }
                ?: LegacyPalette.MIDNIGHT
            val base = fromLegacy(previous)
            val raw = storage.getString(KEY, null) ?: return base
            return runCatching { decode(raw) }.getOrNull()?.takeIf { it.minContrast() >= 4.5 } ?: base
        }

        fun save(context: Context, palette: AppPalette) {
            require(palette.minContrast() >= 4.5)
            prefs(context).edit().putString(KEY, palette.encode()).apply()
        }

        fun decode(raw: String): AppPalette {
            val v = raw.split(",").map { it.toInt() }
            require(v.size == 17)
            var p = defaults()
            for (i in 0..11) p = p.withColor(i, v[i])
            return p.copy(cardOpacity = v[12].coerceIn(0, 100), activeOpacity = v[13].coerceIn(0, 100),
                buttonOpacity = v[14].coerceIn(0, 100), radius = v[15].coerceIn(0, 28), showBorders = v[16] != 0)
        }

        fun blend(fg: Int, bg: Int, opacity: Int): Int {
            val a = opacity.coerceIn(0, 100) / 100.0
            fun channel(shift: Int) = ((((fg ushr shift) and 255) * a + ((bg ushr shift) and 255) * (1-a)).roundToInt()) shl shift
            return BLACK or channel(16) or channel(8) or channel(0)
        }

        fun contrast(a: Int, b: Int): Double {
            fun luminance(c: Int): Double {
                fun channel(shift: Int): Double {
                    val x = ((c ushr shift) and 255) / 255.0
                    return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
                }
                return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
            }
            val x = luminance(a); val y = luminance(b)
            return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
        }
    }
    fun encode() = (colors() + listOf(cardOpacity, activeOpacity, buttonOpacity, radius, if (showBorders) 1 else 0)).joinToString(",")
}

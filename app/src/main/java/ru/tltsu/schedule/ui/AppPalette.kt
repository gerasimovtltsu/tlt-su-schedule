package ru.tltsu.schedule.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import ru.tltsu.schedule.R
import ru.tltsu.schedule.data.local.prefs

internal enum class AppPalette(
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
    ROSE("Роза", true, 0xFFFAF1F4.toInt(), 0xFFFFFFFF.toInt(), 0xFF35232D.toInt(), 0xFF654D59.toInt(), 0xFF8A2455.toInt(), 0xFF9A3264.toInt(), 0xFFF3E0E9.toInt(), 0xFFA28A98.toInt(), 0xFF12664F.toInt(), 0xFF854600.toInt());
    val onSelected: Int get() = 0xFFFFFFFF.toInt()
    val ripple: Int get() = if (light) 0x22000000 else 0x33FFFFFF
    val style: Int get() = when (this) {
        MIDNIGHT -> R.style.Theme_MIDNIGHT
        GRAPHITE -> R.style.Theme_GRAPHITE
        AMOLED -> R.style.Theme_AMOLED
        OCEAN -> R.style.Theme_OCEAN
        FOREST -> R.style.Theme_FOREST
        LAVENDER -> R.style.Theme_LAVENDER
        PAPER -> R.style.Theme_PAPER
        SAND -> R.style.Theme_SAND
        MINT -> R.style.Theme_MINT
        ROSE -> R.style.Theme_ROSE
    }

    fun applyWindow(activity: Activity) {
        activity.window.statusBarColor = bg
        activity.window.navigationBarColor = bg
        val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val decor = activity.window.decorView
        decor.systemUiVisibility = (decor.systemUiVisibility and flags.inv()) or (if (light) flags else 0)
    }

    companion object {
        private const val KEY = "app_palette"
        fun current(context: Context): AppPalette =
            values().firstOrNull { it.name == prefs(context).getString(KEY, MIDNIGHT.name) } ?: MIDNIGHT
        fun save(context: Context, palette: AppPalette) {
            prefs(context).edit().putString(KEY, palette.name).apply()
        }
    }
}

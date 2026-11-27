package ru.tltsu.schedule.ui

import android.content.Context
import android.content.res.Configuration
import ru.tltsu.schedule.data.local.prefs

internal data class InterfaceSize(val percent: Int?) {
    val title: String get() = percent?.let { "$it%" } ?: "По настройкам системы"

    companion object {
        const val MIN = 70
        const val MAX = 150
        const val STEP = 5
        private const val KEY = "interface_size"

        fun current(context: Context): InterfaceSize {
            val value = prefs(context).getString(KEY, "SYSTEM")
            val percent = when (value) {
                "SYSTEM" -> null
                "COMPACT" -> 90
                "STANDARD" -> 100
                "LARGE" -> 130
                else -> value?.toIntOrNull()?.coerceIn(MIN, MAX)
            }
            return InterfaceSize(percent)
        }

        fun save(context: Context, size: InterfaceSize) {
            prefs(context).edit().putString(KEY, size.percent?.toString() ?: "SYSTEM").apply()
        }

        fun wrap(base: Context): Context {
            val scale = current(base).percent?.div(100f) ?: return base
            val config = Configuration(base.resources.configuration).apply { fontScale = scale }
            return base.createConfigurationContext(config)
        }
    }
}

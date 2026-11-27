package ru.tltsu.schedule.ui

import android.content.Context
import android.content.res.Configuration
import ru.tltsu.schedule.data.local.prefs

internal enum class InterfaceSize(val title: String, val scale: Float?) {
    SYSTEM("По настройкам системы", null),
    COMPACT("Компактный", 0.9f),
    STANDARD("Стандартный", 1f),
    LARGE("Крупный", 1.3f);

    companion object {
        private const val KEY = "interface_size"
        fun current(context: Context): InterfaceSize =
            values().firstOrNull { it.name == prefs(context).getString(KEY, SYSTEM.name) } ?: SYSTEM

        fun save(context: Context, size: InterfaceSize) {
            prefs(context).edit().putString(KEY, size.name).apply()
        }

        fun wrap(base: Context): Context {
            val scale = current(base).scale ?: return base
            val config = Configuration(base.resources.configuration).apply { fontScale = scale }
            return base.createConfigurationContext(config)
        }
    }
}

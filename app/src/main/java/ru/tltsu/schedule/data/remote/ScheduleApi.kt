package ru.tltsu.schedule.data.remote

import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import ru.tltsu.schedule.util.weekBounds

internal object ScheduleApi {
    private const val ENDPOINT = "https://its.tltsu.ru/api/schedule"

    fun load(teacherId: Int, date: LocalDate): String {
        val (from, to) = weekBounds(date)
        val profile = ru.tltsu.schedule.domain.model.ScheduleProfile.fromKey(teacherId)
        val url = URL("$ENDPOINT/${profile.role.path}?${profile.role.parameter}=${profile.id}&fromDate=$from&toDate=$to")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            check(connection.responseCode == 200) { "HTTP " + connection.responseCode }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

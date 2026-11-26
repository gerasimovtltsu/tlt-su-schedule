package ru.tltsu.schedule.domain.model

import java.time.LocalDate
import java.time.LocalTime
import ru.tltsu.schedule.util.zone

/** The API labels lesson times with Z, although they represent TGU local wall time. */
data class Lesson(
    val id: String,
    val name: String,
    val type: String,
    val number: Int,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val room: String,
    val groups: String,
    val note: String
) {
    fun begins(): Long = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
    fun ends(): Long = date.atTime(end).atZone(zone).toInstant().toEpochMilli()
    fun time(): String = "$start – $end"
}

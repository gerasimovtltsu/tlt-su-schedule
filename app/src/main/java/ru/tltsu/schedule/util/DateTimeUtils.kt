package ru.tltsu.schedule.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal val zone: ZoneId = ZoneId.of("Europe/Samara")
internal val ru = Locale("ru")
internal val dayFmt = DateTimeFormatter.ofPattern("d MMMM, EEEE", ru)
private val apiFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
    .withZone(ZoneOffset.UTC)

internal fun today(): LocalDate = LocalDate.now(zone)

internal fun week(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** API boundaries are UTC; the university week starts at midnight in Samara. */
internal fun weekBounds(date: LocalDate): Pair<String, String> {
    val monday = week(date)
    val from = monday.atStartOfDay(zone).toInstant()
    val to = monday.plusWeeks(1).atStartOfDay(zone).toInstant().minusMillis(1)
    return apiFormat.format(from) to apiFormat.format(to)
}

internal fun reminderTime(start: Long, leadMinutes: Int, now: Long): Long =
    maxOf(start - leadMinutes * 60_000L, now + 1_000L)

internal fun displayTimestamp(time: Long): String =
    Instant.ofEpochMilli(time).atZone(zone).format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))

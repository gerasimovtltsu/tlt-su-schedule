package ru.tltsu.schedule.data.remote

import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime
import ru.tltsu.schedule.domain.model.Lesson

internal object ScheduleParser {
    private fun teacherLabel(teacher: org.json.JSONObject?): String =
        if (teacher == null) "" else listOf("lastName", "name", "patronymic")
            .map { teacher.optString(it) }.filter { it.isNotBlank() && it != "null" }.joinToString(" ")

    fun parse(raw: String): List<Lesson> {
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val groups = item.optJSONArray("groupsList") ?: JSONArray()
            val classroom = item.optJSONObject("classroom")
            val type = when (val value = item.optString("type")) {
                "Лек" -> "Лекция"
                "Лаб" -> "Лабораторная"
                "ПрЗ" -> "Практика"
                else -> value
            }
            Lesson(
                id = item.getString("id"),
                name = item.optString("disciplineName"),
                type = type,
                number = item.optInt("pairNumber"),
                date = LocalDate.parse(item.getString("date").replace('.', '-')),
                start = LocalTime.parse(item.getString("fromTime").substring(11, 16)),
                end = LocalTime.parse(item.getString("toTime").substring(11, 16)),
                room = classroom?.optString("number").orEmpty()
                    .ifBlank { classroom?.optString("name").orEmpty() },
                groups = (0 until groups.length()).joinToString(", ") {
                    groups.getJSONObject(it).optString("name")
                },
                note = listOf("subgroup", "note")
                    .filter { !item.isNull(it) }
                    .map { item.optString(it).trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                teacherName = teacherLabel(item.optJSONObject("teacher"))
            )
        }.sortedWith(compareBy({ it.date }, { it.start }))
    }

    fun teacherName(raw: String, teacherId: Int): String? {
        if (teacherId < 0) {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val groups = item.optJSONArray("groupsList") ?: JSONArray()
                for (j in 0 until groups.length()) {
                    val group = groups.optJSONObject(j) ?: continue
                    if (group.optInt("id") == -teacherId) {
                        val name = group.optString("name")
                        if (name.isNotBlank() && name != "null") return name
                    }
                }
            }
            return null
        }
        val teacher = JSONArray(raw).optJSONObject(0)?.optJSONObject("teacher") ?: return null
        if (teacher.optInt("id") != teacherId) return null
        return listOf("lastName", "name", "patronymic")
            .map { teacher.optString(it) }
            .filter { it.isNotBlank() && it != "null" }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }
}

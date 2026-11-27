package ru.tltsu.schedule.domain.model

internal object ScheduleChanges {
    fun compare(before: List<Lesson>, after: List<Lesson>, now: Long): List<String> {
        val old = before.associateBy { it.id }
        val fresh = after.associateBy { it.id }
        val result = mutableListOf<String>()
        fun label(l: Lesson) = "${l.date} ${l.start} · ${l.name}"
        for ((id, lesson) in old) {
            val next = fresh[id]
            if (next == null) {
                if (lesson.begins() > now) result += "Отменено: " + label(lesson)
            } else if (lesson.begins() > now || next.begins() > now) {
                val changes = mutableListOf<String>()
                if (lesson.date != next.date || lesson.start != next.start || lesson.end != next.end)
                    changes += "время: ${lesson.date} ${lesson.time()} → ${next.date} ${next.time()}"
                if (lesson.room != next.room) changes += "аудитория: ${lesson.room} → ${next.room}"
                if (lesson.name != next.name) changes += "дисциплина: ${lesson.name} → ${next.name}"
                if (lesson.groups != next.groups) changes += "группы: ${lesson.groups} → ${next.groups}"
                if (lesson.teacherName != next.teacherName) changes += "преподаватель: ${lesson.teacherName} → ${next.teacherName}"
                if (lesson.type != next.type) changes += "тип: ${lesson.type} → ${next.type}"
                if (changes.isNotEmpty()) result += label(next) + " · " + changes.joinToString("; ")
            }
        }
        for ((id, lesson) in fresh) {
            if (id !in old && lesson.begins() > now) result += "Добавлено: " + label(lesson)
        }
        return result
    }
}

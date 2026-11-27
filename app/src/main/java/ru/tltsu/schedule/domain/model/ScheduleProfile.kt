package ru.tltsu.schedule.domain.model

internal enum class ProfileRole(val path: String, val parameter: String, val title: String) {
    TEACHER("teacher", "teacherId", "Преподаватель"),
    STUDENT("group", "groupId", "Студент")
}

internal data class ScheduleProfile(val role: ProfileRole, val id: Int) {
    init { require(id > 0) }
    val key: Int get() = if (role == ProfileRole.TEACHER) id else -id
    companion object {
        fun fromKey(key: Int): ScheduleProfile {
            require(key != 0 && key != Int.MIN_VALUE)
            return if (key > 0) ScheduleProfile(ProfileRole.TEACHER, key)
                else ScheduleProfile(ProfileRole.STUDENT, -key)
        }
        fun parse(role: ProfileRole, input: String): ScheduleProfile? {
            val raw = input.trim()
            val id = raw.toIntOrNull() ?: runCatching {
                val url = java.net.URI(raw)
                if (url.scheme != "https" && url.scheme != "http") return null
                if (!url.host.equals("rasp.tltsu.ru", ignoreCase = true)) return null
                val parts = url.path.trim('/').split('/')
                if (parts.size != 2 || parts[0] != role.path) return null
                parts[1].toIntOrNull()
            }.getOrNull()
            return id?.takeIf { it > 0 }?.let { ScheduleProfile(role, it) }
        }
    }
}

package ru.tltsu.schedule.update

/** The release tag includes the CI run number, but application updates compare actual version names. */
internal object ReleaseVersion {
    fun parts(value: String): List<Int>? {
        val match = Regex("^v?(\\d+(?:\\.\\d+)*)(?:-build\\.\\d+\\.\\d+)?$").matchEntire(value.trim())
            ?: return null
        return match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    }

    fun newer(remote: String, installed: String): Boolean {
        val a = parts(remote) ?: return false
        val b = parts(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

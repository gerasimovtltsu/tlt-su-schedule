package ru.tltsu.schedule.domain.model

import org.junit.Assert.*
import org.junit.Test

class ScheduleProfileTest {
    @Test fun groupLink() {
        val profile = ScheduleProfile.parse(ProfileRole.STUDENT, "https://rasp.tltsu.ru/group/35090")
        assertEquals(ScheduleProfile(ProfileRole.STUDENT, 35090), profile)
        assertEquals(-35090, profile?.key)
    }
    @Test fun teacherId() { assertEquals(15943, ScheduleProfile.parse(ProfileRole.TEACHER, "15943")?.key) }
    @Test fun profileIsolation() { assertNotEquals(ScheduleProfile(ProfileRole.TEACHER, 35090).key, ScheduleProfile(ProfileRole.STUDENT, 35090).key) }
    @Test fun invalidLinks() {
        assertNull(ScheduleProfile.parse(ProfileRole.STUDENT, "https://rasp.tltsu.ru/teacher/35090"))
        assertNull(ScheduleProfile.parse(ProfileRole.STUDENT, "https://other.ru/group/35090"))
        assertNull(ScheduleProfile.parse(ProfileRole.STUDENT, "-35090"))
    }
}

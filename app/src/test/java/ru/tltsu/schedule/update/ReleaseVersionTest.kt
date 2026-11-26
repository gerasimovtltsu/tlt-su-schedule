package ru.tltsu.schedule.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesNumericParts() {
        assertTrue(ReleaseVersion.newer("v0.10.0-build.12.1", "0.9.9"))
        assertTrue(ReleaseVersion.newer("v1.0.0-build.1.1", "0.9.9"))
    }
    @Test fun ignoresBuildSuffix() {
        assertFalse(ReleaseVersion.newer("v0.6.0-build.19.1", "0.6.0"))
        assertFalse(ReleaseVersion.newer("v0.6.0-build.19.1", "0.6"))
    }
    @Test fun rejectsOlderAndInvalidVersions() {
        assertFalse(ReleaseVersion.newer("v0.5.0-build.19.1", "0.6.0"))
        assertFalse(ReleaseVersion.newer("invalid", "0.6.0"))
    }
}

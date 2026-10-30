package com.danitube.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateManagerTest {

    // Must match the version code the release workflow computes: major*10000 + minor*100 + patch.
    @Test fun versionCodeMatchesTheReleaseWorkflow() {
        assertEquals(300L, UpdateManager.versionCodeOf("v0.3.0"))
        assertEquals(201L, UpdateManager.versionCodeOf("v0.2.1"))
        assertEquals(10_203L, UpdateManager.versionCodeOf("1.2.3"))
    }

    @Test fun ignoresPreReleaseSuffix() {
        assertEquals(300L, UpdateManager.versionCodeOf("v0.3.0-beta"))
    }

    @Test fun rejectsTagsThatAreNotVersions() {
        assertNull(UpdateManager.versionCodeOf("latest"))
        assertNull(UpdateManager.versionCodeOf("v1.2"))
        assertNull(UpdateManager.versionCodeOf("v1.x.3"))
    }
}

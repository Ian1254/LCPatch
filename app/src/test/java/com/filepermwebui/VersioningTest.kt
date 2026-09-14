package com.lcpatch

import org.junit.Assert.assertTrue
import org.junit.Test

class VersioningTest {
    @Test
    fun prereleaseOrderingMatchesSemVer() {
        assertTrue(Versioning.compare("1.2.0-beta.2", "1.2.0-beta.1") > 0)
        assertTrue(Versioning.compare("1.2.0-rc.1", "1.2.0-beta.9") > 0)
        assertTrue(Versioning.compare("1.2.0", "1.2.0-rc.9") > 0)
        assertTrue(Versioning.compare("1.2.1", "1.2.0") > 0)
    }

    @Test
    fun numericIdentifiersAreComparedNumerically() {
        assertTrue(Versioning.compare("1.2.0-beta.10", "1.2.0-beta.2") > 0)
    }
}

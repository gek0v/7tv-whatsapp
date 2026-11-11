package com.seventv.stickers.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun parseStandardVersions() {
        val v1 = SemVer.parse("1.2.3")
        assertNotNull(v1)
        assertEquals(1, v1!!.major)
        assertEquals(2, v1.minor)
        assertEquals(3, v1.patch)
        assertTrue(v1.preRelease.isEmpty())

        val withV = SemVer.parse("v2.0.1")
        assertNotNull(withV)
        assertEquals(2, withV!!.major)
        assertEquals(0, withV.minor)
        assertEquals(1, withV.patch)

        val capitalV = SemVer.parse("V0.5.0")
        assertNotNull(capitalV)
        assertEquals(0, capitalV!!.major)
        assertEquals(5, capitalV.minor)
        assertEquals(0, capitalV.patch)
    }

    @Test
    fun parseTwoPartVersionDefaultsPatchToZero() {
        val parsed = SemVer.parse("1.2")
        assertNotNull(parsed)
        assertEquals(1, parsed!!.major)
        assertEquals(2, parsed.minor)
        assertEquals(0, parsed.patch)
    }

    @Test
    fun parsePreReleaseAndBuildMetadata() {
        val pre = SemVer.parse("v1.2.0-beta.1")
        assertNotNull(pre)
        assertEquals(1, pre!!.major)
        assertEquals(2, pre.minor)
        assertEquals(0, pre.patch)
        assertEquals(listOf("beta", "1"), pre.preRelease)

        val withBuild = SemVer.parse("1.2.0-rc.2+sha.12345")
        assertNotNull(withBuild)
        assertEquals(listOf("rc", "2"), withBuild!!.preRelease)
    }

    @Test
    fun parseInvalidReturnsNull() {
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("    "))
        assertNull(SemVer.parse("abc"))
        assertNull(SemVer.parse("1.x.3"))
        assertNull(SemVer.parse("v-beta"))
    }

    @Test
    fun compareVersionsCorrectly() {
        val v100 = SemVer.parse("1.0.0")!!
        val v101 = SemVer.parse("1.0.1")!!
        val v110 = SemVer.parse("1.1.0")!!
        val v119 = SemVer.parse("1.1.9")!!
        val v1110 = SemVer.parse("1.1.10")!!
        val v200 = SemVer.parse("2.0.0")!!

        assertTrue(v100 < v101)
        assertTrue(v101 < v110)
        assertTrue(v119 < v1110)
        assertTrue(v1110 < v200)

        // Pre-release vs stable
        val v120Alpha = SemVer.parse("1.2.0-alpha")!!
        val v120Beta = SemVer.parse("1.2.0-beta")!!
        val v120Beta1 = SemVer.parse("1.2.0-beta.1")!!
        val v120Beta2 = SemVer.parse("1.2.0-beta.2")!!
        val v120 = SemVer.parse("1.2.0")!!

        assertTrue(v120Alpha < v120Beta)
        assertTrue(v120Beta < v120Beta1)
        assertTrue(v120Beta1 < v120Beta2)
        assertTrue(v120Beta2 < v120)
    }

    @Test
    fun isUpdateAvailableChecks() {
        assertTrue(SemVer.isUpdateAvailable("1.1.0", "v1.2.0"))
        assertTrue(SemVer.isUpdateAvailable("v1.1.0", "1.1.1"))
        assertTrue(SemVer.isUpdateAvailable("1.1.0-beta.1", "1.1.0"))
        assertFalse(SemVer.isUpdateAvailable("1.2.0", "v1.1.0"))
        assertFalse(SemVer.isUpdateAvailable("1.2.0", "1.2.0"))

        // VersionCode tie-breaker
        assertTrue(SemVer.isUpdateAvailable("1.2.0", "1.2.0", currentVersionCode = 2, remoteVersionCode = 3))
        assertFalse(SemVer.isUpdateAvailable("1.2.0", "1.2.0", currentVersionCode = 3, remoteVersionCode = 3))
        assertFalse(SemVer.isUpdateAvailable("1.2.0", "1.2.0", currentVersionCode = 4, remoteVersionCode = 3))

        // Malformed inputs return false safely
        assertFalse(SemVer.isUpdateAvailable("invalid", "v1.2.0"))
        assertFalse(SemVer.isUpdateAvailable("1.1.0", "invalid"))
    }
}
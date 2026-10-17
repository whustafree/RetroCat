package com.swordfish.lemuroid.app.mobile.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionsTest {
    @Test
    fun `reads a tag with a leading v`() {
        assertEquals(listOf(1, 19, 4), UpdateVersions.parse("v1.19.4"))
    }

    @Test
    fun `reads the version the app reports`() {
        assertEquals(listOf(1, 19, 3), UpdateVersions.parse("1.19.3-retrocat"))
    }

    @Test
    fun `reads a version without a patch number`() {
        assertEquals(listOf(1, 19), UpdateVersions.parse("1.19"))
    }

    @Test
    fun `ignores a trailing dot`() {
        assertEquals(listOf(1, 19, 3), UpdateVersions.parse("1.19.3."))
    }

    @Test
    fun `reads an uppercase V`() {
        assertEquals(listOf(2, 0, 1), UpdateVersions.parse("V2.0.1"))
    }

    @Test
    fun `does not read a tag that is not a version`() {
        assertEquals(emptyList<Int>(), UpdateVersions.parse("apk-latest"))
        assertEquals(emptyList<Int>(), UpdateVersions.parse(""))
        assertEquals(emptyList<Int>(), UpdateVersions.parse("main"))
    }

    @Test
    fun `a newer patch is newer`() {
        assertTrue(UpdateVersions.isNewer("v1.19.4", "1.19.3-retrocat"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(UpdateVersions.isNewer("v1.19.3", "1.19.3-retrocat"))
    }

    @Test
    fun `an older patch is not newer`() {
        assertFalse(UpdateVersions.isNewer("v1.19.2", "1.19.3-retrocat"))
    }

    @Test
    fun `a newer minor beats a higher patch`() {
        assertTrue(UpdateVersions.isNewer("v1.20", "1.19.9-retrocat"))
    }

    @Test
    fun `a newer major beats everything`() {
        assertTrue(UpdateVersions.isNewer("v2.0", "1.19.3-retrocat"))
        assertFalse(UpdateVersions.isNewer("v0.9", "1.19.3-retrocat"))
    }

    @Test
    fun `a missing part counts as zero`() {
        assertFalse(UpdateVersions.isNewer("v1.19", "1.19.0-retrocat"))
        assertTrue(UpdateVersions.isNewer("v1.19.1", "1.19-retrocat"))
    }

    @Test
    fun `a tag that is not a version is never newer`() {
        assertFalse(UpdateVersions.isNewer("apk-latest", "1.19.3-retrocat"))
        assertFalse(UpdateVersions.isNewer("v1.19.4", ""))
    }
}

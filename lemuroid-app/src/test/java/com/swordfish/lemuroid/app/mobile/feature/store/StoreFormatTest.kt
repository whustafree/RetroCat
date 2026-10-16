package com.swordfish.lemuroid.app.mobile.feature.store

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreFormatTest {
    @Test
    fun `bytes below a kilobyte are shown raw`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1 B", formatBytes(1))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `kilobytes and above use one decimal`() {
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("3.0 GB", formatBytes(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun `negative sizes are empty instead of nonsense`() {
        assertEquals("", formatBytes(-1))
    }
}

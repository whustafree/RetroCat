package com.swordfish.lemuroid.app.mobile.feature.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Drive listing is scraped from HTML Google can change without notice, so the parser is pinned
 * with a fixture copied from a real response (thousands of entries, escaped names included).
 */
class DriveFolderListingTest {
    private val folderHtml =
        """
        <!DOCTYPE html><html><head><title>gba</title></head><body>
        <div class="flip-entries">
        <div class="flip-entry" id="entry-1ealr8kxLWu46FAa_ebNl7FBz88SPuHZ9" tabindex="0" role="link">
          <div class="flip-entry-info">
            <a href="https://drive.google.com/drive/folders/1ealr8kxLWu46FAa_ebNl7FBz88SPuHZ9" target="_blank">
              <div class="flip-entry-title">Pack Covers</div>
            </a>
          </div>
          <div class="flip-entry-last-modified"><div>2:18 pm</div></div>
        </div>
        <div class="flip-entry" id="entry-1Hv4zjwm7s8lG9NZqiN5aWrHyVASgG_Bf" tabindex="0" role="link">
          <div class="flip-entry-info">
            <a href="https://drive.google.com/file/d/1Hv4zjwm7s8lG9NZqiN5aWrHyVASgG_Bf/view?usp=drive_web" target="_blank">
              <div class="flip-entry-title">2 in 1 - Dragon Ball Z - Buu&#39;s Fury &amp; GT.gba</div>
            </a>
          </div>
        </div>
        </div></body></html>
        """.trimIndent()

    @Test
    fun `reads ids, names and the folder flag`() {
        val entries = DriveFolderListing.parse(folderHtml)

        assertEquals(2, entries.size)

        assertEquals("1ealr8kxLWu46FAa_ebNl7FBz88SPuHZ9", entries[0].id)
        assertEquals("Pack Covers", entries[0].name)
        assertTrue(entries[0].isFolder)

        assertEquals("1Hv4zjwm7s8lG9NZqiN5aWrHyVASgG_Bf", entries[1].id)
        assertTrue(!entries[1].isFolder)
    }

    @Test
    fun `decodes html entities in names`() {
        val entries = DriveFolderListing.parse(folderHtml)

        assertEquals("2 in 1 - Dragon Ball Z - Buu's Fury & GT.gba", entries[1].name)
    }

    @Test
    fun `returns nothing for an empty or unrelated page`() {
        assertEquals(0, DriveFolderListing.parse("").size)
        assertEquals(0, DriveFolderListing.parse("<html><body>nope</body></html>").size)
    }

    @Test
    fun `extracts the folder id from the shapes a share link takes`() {
        val id = "1_nY8sC3OwNZ8KmtgoTmQ9RTpyIg7i_cP"

        assertEquals(id, DriveFolderListing.folderIdFrom("https://drive.google.com/drive/folders/$id?usp=sharing"))
        assertEquals(id, DriveFolderListing.folderIdFrom("https://drive.google.com/drive/u/0/folders/$id"))
        assertEquals(id, DriveFolderListing.folderIdFrom(id))
        assertEquals(id, DriveFolderListing.folderIdFrom("  https://drive.google.com/open?id=$id  "))
    }

    @Test
    fun `rejects text that is not a folder link`() {
        assertNull(DriveFolderListing.folderIdFrom(""))
        assertNull(DriveFolderListing.folderIdFrom("   "))
        assertNull(DriveFolderListing.folderIdFrom("hola"))
        assertNull(DriveFolderListing.folderIdFrom("https://example.com/folders/short"))
    }
}

package com.swordfish.lemuroid.app.mobile.feature.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page Drive serves instead of an oversized file is scraped, so its detection and the two shapes
 * it can take (a plain link, a hidden form) are pinned here as plain JVM tests.
 */
class DriveDownloadPageTest {
    @Test
    fun `recognises the warning page`() {
        assertTrue(
            DriveDownloadPage.isWarning("https://drive.google.com/uc?export=download&id=x", "text/html; charset=utf-8"),
        )
        assertTrue(DriveDownloadPage.isWarning("https://drive.usercontent.google.com/download?id=x", "text/html"))
    }

    @Test
    fun `does not mistake the file for the warning page`() {
        assertFalse(
            DriveDownloadPage.isWarning(
                "https://drive.google.com/uc?export=download&id=x",
                "application/octet-stream",
            ),
        )
        assertFalse(DriveDownloadPage.isWarning("https://drive.google.com/uc?export=download&id=x", null))
        assertFalse(DriveDownloadPage.isWarning("https://example.com/rom.gba", "text/html"))
    }

    @Test
    fun `reads the download link out of the warning page`() {
        val html =
            """
            <html><body>
            <p>Google Drive can't scan this file for viruses.</p>
            <a href="https://drive.usercontent.google.com/download?id=1AbC-D_ef&amp;export=download&amp;confirm=t&amp;uuid=9f8c-123">Download anyway</a>
            </body></html>
            """.trimIndent()

        assertEquals(
            "https://drive.usercontent.google.com/download?id=1AbC-D_ef&export=download&confirm=t&uuid=9f8c-123",
            DriveDownloadPage.realUrl(html),
        )
    }

    @Test
    fun `builds the download url from the hidden form fields`() {
        val html =
            """
            <html><body>
            <form id="download-form" action="https://drive.usercontent.google.com/download" method="get">
              <input type="hidden" name="id" value="1AbC">
              <input class="jfk" type="hidden" name="export" value="download">
              <input type="hidden" name="confirm" value="t">
              <input type="hidden" name="uuid" value="9f8c-123">
            </form>
            </body></html>
            """.trimIndent()

        assertEquals(
            "https://drive.usercontent.google.com/download?id=1AbC&export=download&confirm=t&uuid=9f8c-123",
            DriveDownloadPage.realUrl(html),
        )
    }

    @Test
    fun `accepts a form without a uuid`() {
        val html = """<form><input name="id" value="Zz"><input name="confirm" value="t"></form>"""

        assertEquals(
            "https://drive.usercontent.google.com/download?id=Zz&export=download&confirm=t",
            DriveDownloadPage.realUrl(html),
        )
    }

    @Test
    fun `returns nothing when the page carries no target`() {
        assertNull(DriveDownloadPage.realUrl("<html><body>nothing</body></html>"))
        assertNull(DriveDownloadPage.realUrl(""))
    }
}

package com.swordfish.lemuroid.app.mobile.feature.store

import com.swordfish.lemuroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch downloads are how the store is really used: a system, a filter, "select all", and a few
 * hundred ROMs on their way. These tests pin what the panel above the list counts, what "retry the
 * failed ones" hands back and when the warning about room is honest enough to be shown.
 */
class StoreDownloadsTest {
    private val groups =
        listOf(
            StoreGroup(
                SystemID.GBA,
                listOf(
                    game("mario", sizeBytes = 8_000_000L),
                    game("zelda", sizeBytes = 16_000_000L),
                ),
            ),
            StoreGroup(
                SystemID.SNES,
                listOf(game("chrono", sizeBytes = 4_000_000L)),
            ),
        )

    @Test
    fun `counts what is queued, running and failed`() {
        val state =
            state(
                download("mario", StoreDownloadStatus.QUEUED),
                download("zelda", StoreDownloadStatus.DOWNLOADING),
                download("chrono", StoreDownloadStatus.FAILED),
            )

        val summary = state.downloadSummary()

        assertEquals(1, summary.queued)
        assertEquals(1, summary.downloading)
        assertEquals(1, summary.failed)
        assertTrue(summary.active)
    }

    @Test
    fun `a finished download leaves the panel empty`() {
        val summary = state().downloadSummary()

        assertEquals(0, summary.total)
        assertFalse(summary.active)
    }

    @Test
    fun `the failed ones come back in catalog order, and only those`() {
        val state =
            state(
                download("mario", StoreDownloadStatus.DOWNLOADING),
                download("zelda", StoreDownloadStatus.FAILED),
                download("chrono", StoreDownloadStatus.FAILED),
            )

        assertEquals(listOf("zelda", "chrono"), state.failedDownloads().map { it.id })
    }

    @Test
    fun `with nothing failed there is nothing to retry`() {
        val state = state(download("mario", StoreDownloadStatus.QUEUED))

        assertTrue(state.failedDownloads().isEmpty())
    }

    @Test
    fun `says nothing when the catalog announced no sizes`() {
        assertNull(storeSpaceCheck(listOf(game("mario")), freeBytes = 1_000_000_000L))
    }

    @Test
    fun `says nothing when the device would not say how much room is left`() {
        assertNull(storeSpaceCheck(listOf(game("mario", sizeBytes = 8_000_000L)), freeBytes = 0L))
    }

    @Test
    fun `adds up what the games take and compares it with the room left`() {
        val check = storeSpaceCheck(listOf(game("mario", sizeBytes = 8_000_000L)), freeBytes = 100_000_000L)

        assertEquals(8_000_000L, check?.neededBytes)
        assertTrue(check!!.fits)
    }

    @Test
    fun `warns when the games are bigger than the room left`() {
        val check = storeSpaceCheck(listOf(game("mario", sizeBytes = 8_000_000L)), freeBytes = 1_000_000L)

        assertFalse(check!!.fits)
    }

    @Test
    fun `entries with no size do not make the answer a lie`() {
        // One of the two announced a size and it already does not fit, so the answer is still no.
        val check =
            storeSpaceCheck(
                listOf(game("mario", sizeBytes = 8_000_000L), game("zelda")),
                freeBytes = 1_000_000L,
            )

        assertFalse(check!!.fits)
    }

    private fun state(vararg downloads: StoreDownload) =
        StoreUiState(
            groups = groups,
            downloads = downloads.associateBy { it.gameId },
        )

    private fun download(
        id: String,
        status: StoreDownloadStatus,
    ) = StoreDownload(gameId = id, status = status)

    private fun game(
        id: String,
        sizeBytes: Long? = null,
    ) = StoreGame(
        id = id,
        title = id.replaceFirstChar { it.uppercase() },
        system = "gba",
        url = "https://example.com/$id.gba",
        sizeBytes = sizeBytes,
    )
}

package com.swordfish.lemuroid.app.mobile.feature.store

import com.swordfish.lemuroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Browsing a catalog of a thousand entries is where the store is actually used, so the search box,
 * the chips, the sort order and the layout of the list are covered here as plain JVM tests.
 */
class StoreBrowsingTest {
    private val groups =
        listOf(
            StoreGroup(
                SystemID.GBA,
                listOf(
                    game("mario", "Mario Kart - Super Circuit", sizeBytes = 8_000_000L),
                    game("zelda", "The Legend of Zelda - The Minish Cap", sizeBytes = 16_000_000L),
                    game("pokemon", "Pokémon Esmeralda", sizeBytes = 12_000_000L),
                ),
            ),
        )

    /** The catalog as the list actually receives it: already narrowed and sorted by [browse]. */
    private val sortedGroups = groups.browse()

    @Test
    fun `search ignores case and accents`() {
        val found = groups.browse(query = "POKEMON").single().games

        assertEquals(listOf("pokemon"), found.map { it.id })
    }

    @Test
    fun `search matches words in any order`() {
        val found = groups.browse(query = "zelda minish").single().games

        assertEquals(listOf("zelda"), found.map { it.id })
    }

    @Test
    fun `search drops the systems left with no games`() {
        val found = groups.browse(query = "nothing matches this")

        assertTrue(found.isEmpty())
    }

    @Test
    fun `the status chip filters installed games`() {
        val installed = groups.browse(status = StoreStatusFilter.DOWNLOADED, downloadedIds = setOf("mario"))

        assertEquals(listOf("mario"), installed.single().games.map { it.id })
    }

    @Test
    fun `the status chip filters pending games`() {
        val pending = groups.browse(status = StoreStatusFilter.NOT_DOWNLOADED, downloadedIds = setOf("mario"))

        assertEquals(listOf("pokemon", "zelda"), pending.single().games.map { it.id }.sorted())
    }

    @Test
    fun `the status chip filters what the last scan found`() {
        val new = groups.browse(status = StoreStatusFilter.NEW, newIds = setOf("zelda"))

        assertEquals(listOf("zelda"), new.single().games.map { it.id })
    }

    @Test
    fun `sorting by name ignores accents and case`() {
        val sorted = groups.browse(sort = StoreSortOrder.NAME).single().games

        assertEquals(listOf("mario", "pokemon", "zelda"), sorted.map { it.id })
    }

    @Test
    fun `sorting by newest keeps the new games first and the catalog order after them`() {
        val sorted = groups.browse(sort = StoreSortOrder.NEWEST, newIds = setOf("pokemon")).single().games

        assertEquals(listOf("pokemon", "mario", "zelda"), sorted.map { it.id })
    }

    @Test
    fun `sorting by size puts the biggest first and the unknown last`() {
        val withUnknown =
            listOf(
                StoreGroup(
                    SystemID.GBA,
                    listOf(
                        game("small", "Small", sizeBytes = 1_000L),
                        game("unknown", "Unknown"),
                        game("big", "Big", sizeBytes = 32_000L),
                    ),
                ),
            )

        val sorted = withUnknown.browse(sort = StoreSortOrder.SIZE).single().games

        assertEquals(listOf("big", "small", "unknown"), sorted.map { it.id })
    }

    @Test
    fun `rows carry system headers only when the catalog has several systems`() {
        val rows =
            buildStoreRows(sortedGroups, showSystemHeaders = true, showLetterHeaders = false, newIds = emptySet())

        assertEquals(StoreRow.SystemHeader(SystemID.GBA, 3), rows.first())
        assertEquals(3, rows.count { it is StoreRow.GameRow })
    }

    @Test
    fun `rows add a letter header whenever the initial changes`() {
        val rows =
            buildStoreRows(sortedGroups, showSystemHeaders = false, showLetterHeaders = true, newIds = emptySet())

        assertEquals(
            listOf("M", "game_mario", "P", "game_pokemon", "T", "game_zelda"),
            rows.map { if (it is StoreRow.LetterHeader) it.letter else it.key },
        )
    }

    @Test
    fun `rows flag the games the last scan found`() {
        val rows =
            buildStoreRows(sortedGroups, showSystemHeaders = false, showLetterHeaders = false, newIds = setOf("mario"))

        val flagged = rows.filterIsInstance<StoreRow.GameRow>().filter { it.isNew }.map { it.game.id }

        assertEquals(listOf("mario"), flagged)
    }

    @Test
    fun `row keys are unique`() {
        val rows =
            buildStoreRows(sortedGroups, showSystemHeaders = true, showLetterHeaders = true, newIds = emptySet())

        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun `the letter index points at the row each letter starts at`() {
        val rows =
            buildStoreRows(sortedGroups, showSystemHeaders = false, showLetterHeaders = true, newIds = emptySet())

        assertEquals(listOf("M" to 0, "P" to 2, "T" to 4), rows.letterIndex())
        assertTrue(rows[rows.letterIndex().first().second] is StoreRow.LetterHeader)
    }

    @Test
    fun `the index letter skips punctuation and folds accents`() {
        assertEquals("M", "Mario".indexLetter())
        assertEquals("P", "Pokémon".indexLetter())
        assertEquals("Z", "- Zelda".indexLetter())
        assertEquals("5", "5 in 1".indexLetter())
        assertEquals("#", "".indexLetter())
        assertEquals("#", "!!!".indexLetter())
    }

    @Test
    fun `an empty query keeps every game`() {
        val all = groups.browse(query = "   ")

        assertEquals(3, all.single().games.size)
        assertFalse(all.isEmpty())
    }

    @Test
    fun `preloads ahead and behind the viewport`() {
        assertEquals(0..40, coverPreloadRange(firstVisible = 8, lastVisible = 16, size = 1000, ahead = 24, behind = 8))
    }

    @Test
    fun `preload stops at the start of the list`() {
        val range = coverPreloadRange(firstVisible = 0, lastVisible = 5, size = 1000)

        assertEquals(0, range.first)
        assertEquals(29, range.last)
    }

    @Test
    fun `preload stops at the end of the list`() {
        val range = coverPreloadRange(firstVisible = 995, lastVisible = 999, size = 1000)

        assertEquals(987, range.first)
        assertEquals(999, range.last)
    }

    @Test
    fun `preload of an empty list is empty`() {
        assertTrue(coverPreloadRange(firstVisible = 0, lastVisible = 0, size = 0).isEmpty())
    }

    @Test
    fun `preload covers a list shorter than the window`() {
        val range = coverPreloadRange(firstVisible = 0, lastVisible = 2, size = 3)

        assertEquals(0..2, range)
    }

    private fun game(
        id: String,
        title: String,
        system: String = "gba",
        sizeBytes: Long? = null,
    ) = StoreGame(
        id = id,
        title = title,
        system = system,
        url = "https://example.com/$id.gba",
        sizeBytes = sizeBytes,
    )
}

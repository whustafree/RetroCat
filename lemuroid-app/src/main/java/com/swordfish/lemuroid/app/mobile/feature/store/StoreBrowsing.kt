package com.swordfish.lemuroid.app.mobile.feature.store

import com.swordfish.lemuroid.lib.library.SystemID
import java.text.Normalizer

/** Which slice of the catalog the status chips are showing. */
enum class StoreStatusFilter {
    ALL,
    NEW,
    NOT_DOWNLOADED,
    DOWNLOADED,
}

/** How the games are ordered inside each system. */
enum class StoreSortOrder {
    /** Alphabetical, ignoring case and accents. */
    NAME,

    /** Games found by the latest scan first, then whatever order the catalog came in. */
    NEWEST,

    /** Biggest first. Only meaningful when the catalog announced a size. */
    SIZE,
}

/**
 * Applies the search box, the status chip and the sort order to the loaded catalog.
 *
 * Kept pure on purpose: the whole browsing behaviour is unit tested off-device, and the screen can
 * recompute it only when something relevant changed instead of on every download progress tick.
 */
fun List<StoreGroup>.browse(
    query: String = "",
    status: StoreStatusFilter = StoreStatusFilter.ALL,
    sort: StoreSortOrder = StoreSortOrder.NAME,
    downloadedIds: Set<String> = emptySet(),
    newIds: Set<String> = emptySet(),
): List<StoreGroup> {
    val tokens = query.searchTokens()

    return mapNotNull { group ->
        val games =
            group.games
                .filter { it.matchesQuery(tokens) && it.matchesStatus(status, downloadedIds, newIds) }
                .sortedFor(sort, newIds)

        group.copy(games = games).takeIf { games.isNotEmpty() }
    }
}

private fun StoreGame.matchesQuery(tokens: List<String>): Boolean {
    if (tokens.isEmpty()) return true

    val title = title.folded()
    // Every word has to appear somewhere, so "zelda minish" finds "The Legend of Zelda - Minish Cap".
    return tokens.all { title.contains(it) }
}

private fun StoreGame.matchesStatus(
    status: StoreStatusFilter,
    downloadedIds: Set<String>,
    newIds: Set<String>,
): Boolean =
    when (status) {
        StoreStatusFilter.ALL -> true
        StoreStatusFilter.NEW -> id in newIds
        StoreStatusFilter.DOWNLOADED -> id in downloadedIds
        StoreStatusFilter.NOT_DOWNLOADED -> id !in downloadedIds
    }

private fun List<StoreGame>.sortedFor(
    sort: StoreSortOrder,
    newIds: Set<String>,
): List<StoreGame> =
    when (sort) {
        StoreSortOrder.NAME -> sortedWith(compareBy { it.title.folded() })
        StoreSortOrder.NEWEST -> sortedWith(compareByDescending<StoreGame> { it.id in newIds })
        StoreSortOrder.SIZE -> sortedWith(compareByDescending<StoreGame> { it.sizeBytes ?: 0L })
    }

/** One line of the vertical list, already decided. */
sealed interface StoreRow {
    val key: String

    data class SystemHeader(val system: SystemID, val count: Int) : StoreRow {
        override val key: String = "system_${system.name}"
    }

    data class LetterHeader(val system: SystemID, val letter: String) : StoreRow {
        override val key: String = "letter_${system.name}_$letter"
    }

    data class GameRow(val game: StoreGame, val isNew: Boolean) : StoreRow {
        override val key: String = "game_${game.id}"
    }
}

/**
 * Flattens the visible catalog into the lines the vertical list renders.
 *
 * System headers only earn a line when the catalog holds more than one system; with a single system
 * the letter headers are what actually helps to navigate a thousand entries.
 */
fun buildStoreRows(
    groups: List<StoreGroup>,
    showSystemHeaders: Boolean,
    showLetterHeaders: Boolean,
    newIds: Set<String>,
): List<StoreRow> {
    val rows = mutableListOf<StoreRow>()

    groups.forEach { group ->
        if (showSystemHeaders) rows += StoreRow.SystemHeader(group.system, group.games.size)

        var lastLetter: String? = null
        group.games.forEach { game ->
            if (showLetterHeaders) {
                val letter = game.title.indexLetter()
                if (letter != lastLetter) {
                    lastLetter = letter
                    rows += StoreRow.LetterHeader(group.system, letter)
                }
            }

            rows += StoreRow.GameRow(game, game.id in newIds)
        }
    }

    return rows
}

/** The letters of the visible rows and the line each one starts at, for the side index. */
fun List<StoreRow>.letterIndex(): List<Pair<String, Int>> =
    withIndex().mapNotNull { (index, row) ->
        (row as? StoreRow.LetterHeader)?.let { it.letter to index }
    }

/** How many rows of covers are fetched below the viewport. */
const val COVER_PRELOAD_AHEAD = 24

/** And how many above it, so scrolling back up does not have to go out again. */
const val COVER_PRELOAD_BEHIND = 8

/**
 * The rows whose covers are worth fetching for a viewport, clamped to the list.
 *
 * Kept here rather than in the screen because the interesting part is the arithmetic: a viewport at
 * either end of a thousand-entry catalog must not ask for rows that do not exist.
 */
fun coverPreloadRange(
    firstVisible: Int,
    lastVisible: Int,
    size: Int,
    ahead: Int = COVER_PRELOAD_AHEAD,
    behind: Int = COVER_PRELOAD_BEHIND,
): IntRange {
    if (size <= 0) return IntRange.EMPTY

    val start = (firstVisible - behind).coerceIn(0, size - 1)
    val end = (lastVisible + ahead).coerceIn(0, size - 1)

    return if (end < start) IntRange.EMPTY else start..end
}

private val DIACRITICS = Regex("\\p{Mn}+")

/** Lowercase with accents stripped, so "Pokemon" finds "Pokémon". */
internal fun String.folded(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase().trim()

/** The words of a search box, ready to be matched one by one against a title. */
internal fun String.searchTokens(): List<String> = folded().split(' ').filter { it.isNotEmpty() }

/** The letter this title files under in the side index. */
internal fun String.indexLetter(): String {
    val char = firstOrNull { it.isLetterOrDigit() } ?: return "#"
    val letter = char.toString().folded().uppercase().firstOrNull() ?: return "#"
    return if (letter.isLetterOrDigit()) letter.toString() else "#"
}

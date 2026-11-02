/*
 * GameDao.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.lib.library.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun selectById(id: Int): Game?

    @Query("SELECT * FROM games WHERE fileUri = :fileUri")
    fun selectByFileUri(fileUri: String): Game?

    @Query("SELECT * FROM games WHERE lastIndexedAt < :lastIndexedAt")
    fun selectByLastIndexedAtLessThan(lastIndexedAt: Long): List<Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY title ASC")
    fun selectFavorites(): PagingSource<Int, Game>

    @Query(
        """
        SELECT * FROM games WHERE lastPlayedAt IS NOT NULL AND isFavorite = 0 ORDER BY lastPlayedAt DESC LIMIT :limit
        """,
    )
    fun selectFirstUnfavoriteRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavoritesRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun asyncSelectFirstRecents(limit: Int): List<Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavorites(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NULL LIMIT :limit")
    fun selectFirstNotPlayed(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE systemId = :systemId ORDER BY title ASC, id DESC")
    fun selectBySystem(systemId: String): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE systemId IN (:systemIds) ORDER BY title ASC, id DESC")
    fun selectBySystems(systemIds: List<String>): PagingSource<Int, Game>

    @Query("SELECT * FROM games ORDER BY title ASC, id DESC")
    fun selectAll(): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC, title ASC, id DESC")
    fun selectAllByRecency(): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY title ASC, id DESC")
    fun selectAllFavoritesByName(): PagingSource<Int, Game>

    @Query(
        "SELECT * FROM games WHERE systemId IN (:systemIds) AND lastPlayedAt IS NOT NULL " +
            "ORDER BY lastPlayedAt DESC, title ASC, id DESC",
    )
    fun selectBySystemsByRecency(systemIds: List<String>): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE systemId IN (:systemIds) AND isFavorite = 1 ORDER BY title ASC, id DESC")
    fun selectBySystemsFavoritesByName(systemIds: List<String>): PagingSource<Int, Game>

    @Query(
        "SELECT * FROM games WHERE systemId = :systemId " +
            "AND lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC, title ASC, id DESC",
    )
    fun selectBySystemByRecency(systemId: String): PagingSource<Int, Game>

    @Query(
        "SELECT * FROM games WHERE systemId = :systemId " +
            "AND isFavorite = 1 ORDER BY title ASC, id DESC",
    )
    fun selectBySystemFavoritesByName(systemId: String): PagingSource<Int, Game>

    /**
     * Catalog search runs on the `fts_games` index instead of `LIKE '%' || :query || '%'`, which
     * had to rescan the whole table on every keystroke.
     *
     * The searches are default methods on top of a single `@RawQuery` because Room validates
     * `@Query` statements at compile time against the entities it knows about, and `fts_games`
     * is created by [GameSearchDao.MIGRATION] rather than by a Room entity. `observedEntities`
     * still makes the paging source refresh when the `games` table changes, which is when the
     * FTS triggers run too.
     *
     * `match` must be an FTS4 phrase built by `toFtsPhrase(prefixLastToken = true)`, never raw
     * user input: FTS4 rejects an empty operand, so a blank search has to be answered by the
     * unfiltered `select*` methods rather than by these ones.
     */
    @RawQuery(observedEntities = [Game::class])
    fun rawSearchByMatch(query: SupportSQLiteQuery): PagingSource<Int, Game>

    fun selectAllByQuery(match: String): PagingSource<Int, Game> =
        rawSearchByMatch(
            searchQuery(ALL_GAMES, BY_NAME, match),
        )

    fun selectAllByQueryRecency(match: String): PagingSource<Int, Game> =
        rawSearchByMatch(
            searchQuery(PLAYED, BY_RECENCY, match),
        )

    fun selectAllByQueryFavorites(match: String): PagingSource<Int, Game> =
        rawSearchByMatch(
            searchQuery(FAVORITES, BY_NAME, match),
        )

    fun selectBySystemAndQuery(
        systemId: String,
        match: String,
    ): PagingSource<Int, Game> = rawSearchByMatch(searchQuery(system(systemId), BY_NAME, match))

    fun selectBySystemAndQueryRecency(
        systemId: String,
        match: String,
    ): PagingSource<Int, Game> = rawSearchByMatch(searchQuery(system(systemId).and(PLAYED), BY_RECENCY, match))

    fun selectBySystemAndQueryFavorites(
        systemId: String,
        match: String,
    ): PagingSource<Int, Game> = rawSearchByMatch(searchQuery(system(systemId).and(FAVORITES), BY_NAME, match))

    fun selectBySystemsAndQuery(
        systemIds: List<String>,
        match: String,
    ): PagingSource<Int, Game> = rawSearchByMatch(searchQuery(systems(systemIds), BY_NAME, match))

    fun selectBySystemsAndQueryRecency(
        systemIds: List<String>,
        match: String,
    ): PagingSource<Int, Game> = rawSearchByMatch(searchQuery(systems(systemIds).and(PLAYED), BY_RECENCY, match))

    fun selectBySystemsAndQueryFavorites(
        systemIds: List<String>,
        match: String,
    ): PagingSource<Int, Game> = rawSearchByMatch(searchQuery(systems(systemIds).and(FAVORITES), BY_NAME, match))

    @Query("SELECT * FROM games WHERE id = :id")
    fun selectByIdFlow(id: Int): Flow<Game?>

    @Query(
        "SELECT * FROM games WHERE lastPlayedAt IS NOT NULL " +
            "ORDER BY lastPlayedAt DESC LIMIT :limit",
    )
    fun selectContinuePlaying(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE screenshotUrl IS NULL AND coverFrontUrl IS NOT NULL")
    suspend fun selectMissingScreenshots(): List<Game>

    @Query("SELECT * FROM games WHERE screenshotUrl IS NOT NULL")
    suspend fun selectScreenshotCandidates(): List<Game>

    @Query("SELECT DISTINCT systemId FROM games ORDER BY systemId ASC")
    suspend fun selectSystems(): List<String>

    @Query("SELECT count(*) count, systemId systemId FROM games GROUP BY systemId")
    fun selectSystemsWithCount(): Flow<List<SystemCount>>

    @Insert
    fun insert(games: List<Game>): List<Long>

    @Delete
    fun delete(games: List<Game>)

    @Update
    suspend fun update(game: Game)

    @Update
    fun update(games: List<Game>)
}

data class SystemCount(val systemId: String, val count: Int)

private const val MATCH_PREDICATE = "id IN (SELECT docid FROM fts_games WHERE fts_games MATCH ?)"
private const val BY_NAME = "ORDER BY title ASC, id DESC"
private const val BY_RECENCY = "ORDER BY lastPlayedAt DESC, title ASC, id DESC"

/**
 * A `WHERE` fragment together with the arguments it binds, used to assemble the catalog
 * searches without repeating the same SQL nine times.
 */
private class Predicate(
    val sql: String,
    val args: List<String>,
) {
    fun and(other: Predicate) = Predicate("$sql AND ${other.sql}", args + other.args)
}

private val ALL_GAMES = Predicate("1", emptyList())
private val PLAYED = Predicate("lastPlayedAt IS NOT NULL", emptyList())
private val FAVORITES = Predicate("isFavorite = 1", emptyList())

private fun system(systemId: String) = Predicate("systemId = ?", listOf(systemId))

private fun systems(systemIds: List<String>) = Predicate("systemId IN (${systemIds.joinToString { "?" }})", systemIds)

private fun searchQuery(
    predicate: Predicate,
    orderBy: String,
    match: String,
) = SimpleSQLiteQuery(
    "SELECT * FROM games WHERE ${predicate.sql} AND $MATCH_PREDICATE $orderBy",
    (predicate.args + match).toTypedArray(),
)

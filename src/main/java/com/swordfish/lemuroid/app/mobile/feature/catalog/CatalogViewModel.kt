package com.swordfish.lemuroid.app.mobile.feature.catalog

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.dao.GameDao
import com.swordfish.lemuroid.lib.library.db.dao.SystemCount
import com.swordfish.lemuroid.lib.library.db.dao.toFtsPhrase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metaSystemID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CatalogSortOrder(
    @StringRes val labelRes: Int,
) {
    NAME(R.string.catalog_sort_name),
    RECENT(R.string.catalog_sort_recent),
    FAVORITES(R.string.catalog_sort_favorites),
}

data class CatalogFilter(
    val metaSystem: MetaSystemID? = null,
    val sortOrder: CatalogSortOrder = CatalogSortOrder.NAME,
    val query: String = "",
)

data class CatalogUiState(
    val filter: CatalogFilter = CatalogFilter(),
    val gridDensity: GridDensity = GridDensity.REGULAR,
    val groupedLetters: Boolean = true,
)

class CatalogViewModel(
    private val retrogradeDb: RetrogradeDatabase,
    private val preferences: CatalogPreferences,
) : ViewModel() {
    class Factory(
        private val retrogradeDb: RetrogradeDatabase,
        private val preferences: CatalogPreferences,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CatalogViewModel(retrogradeDb, preferences) as T
        }
    }

    private val filter = MutableStateFlow(CatalogFilter())
    private val gridDensity = MutableStateFlow(preferences.gridDensity)
    private val groupedLetters = MutableStateFlow(preferences.groupedLetters)

    val state: Flow<CatalogUiState> =
        combine(filter, gridDensity, groupedLetters) { currentFilter, density, grouped ->
            CatalogUiState(
                filter = currentFilter,
                gridDensity = density,
                groupedLetters = grouped,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue =
                CatalogUiState(
                    filter = CatalogFilter(),
                    gridDensity = preferences.gridDensity,
                    groupedLetters = preferences.groupedLetters,
                ),
        )

    val systems: Flow<List<MetaSystemInfo>> =
        retrogradeDb.gameDao()
            .selectSystemsWithCount()
            .map { counts -> counts.toMetaSystemInfos() }

    /**
     * Games for the "continue playing" row. Kept out of the main paging source because it
     * is a fixed short list that must not disturb the catalog scroll position.
     */
    val continuePlaying: Flow<List<Game>> =
        retrogradeDb.gameDao()
            .selectContinuePlaying(CONTINUE_PLAYING_LIMIT)

    @OptIn(ExperimentalCoroutinesApi::class)
    val games: Flow<PagingData<Game>> =
        filter.flatMapLatest { current ->
            val source = retrogradeDb.gameDao().sourceFor(current)
            Pager(
                // prefetchDistance is required for the catalog to append the next page:
                // the screen renders grouped rows instead of one item per game, so it has
                // to signal "reached the end" by touching the last loaded index.
                config =
                    PagingConfig(
                        pageSize = PAGE_SIZE,
                        prefetchDistance = PAGE_SIZE,
                        enablePlaceholders = false,
                    ),
                pagingSourceFactory = source,
            ).flow
        }.cachedIn(viewModelScope)

    /**
     * Emits the full game entity for [id] and keeps emitting on every change, so the
     * detail dialog always reflects the current database state.
     */
    fun observeGame(id: Int): Flow<Game?> = retrogradeDb.gameDao().selectByIdFlow(id)

    fun selectSystem(metaSystem: MetaSystemID?) {
        filter.value = filter.value.copy(metaSystem = metaSystem)
    }

    fun selectSortOrder(sortOrder: CatalogSortOrder) {
        filter.value = filter.value.copy(sortOrder = sortOrder)
    }

    fun changeQuery(query: String) {
        filter.value = filter.value.copy(query = query)
    }

    fun setGridDensity(density: GridDensity) {
        gridDensity.value = density
        viewModelScope.launch { preferences.gridDensity = density }
    }

    fun setGroupedLetters(grouped: Boolean) {
        groupedLetters.value = grouped
        viewModelScope.launch { preferences.groupedLetters = grouped }
    }

    private fun List<SystemCount>.toMetaSystemInfos() =
        asSequence()
            .filter { (_, count) -> count > 0 }
            .map { (systemId, count) -> GameSystem.findById(systemId).metaSystemID() to count }
            .groupBy { (metaSystemId, _) -> metaSystemId }
            .map { (metaSystemId, counts) -> MetaSystemInfo(metaSystemId, counts.sumOf { it.second }) }
            .sortedBy { it.metaSystem.ordinal }
            .toList()

    private fun GameDao.sourceFor(filter: CatalogFilter): () -> PagingSource<Int, Game> {
        val systemIds = filter.metaSystem?.systemIDs?.map { it.dbname }
        // `null` means "no search text", which is answered by the unfiltered queries: FTS4
        // rejects an empty operand, so a blank query must never reach `MATCH`.
        val match = filter.query.toFtsPhrase(prefixLastToken = true)

        return if (systemIds == null) {
            allSystemsSource(match, filter.sortOrder)
        } else {
            systemsSource(systemIds, match, filter.sortOrder)
        }
    }

    private fun GameDao.allSystemsSource(
        match: String?,
        sortOrder: CatalogSortOrder,
    ): () -> PagingSource<Int, Game> {
        val source: () -> PagingSource<Int, Game> =
            if (match == null) {
                when (sortOrder) {
                    CatalogSortOrder.NAME ->

                        fun() = selectAll()
                    CatalogSortOrder.RECENT ->

                        fun() = selectAllByRecency()
                    CatalogSortOrder.FAVORITES ->

                        fun() = selectAllFavoritesByName()
                }
            } else {
                when (sortOrder) {
                    CatalogSortOrder.NAME ->

                        fun() = selectAllByQuery(match)
                    CatalogSortOrder.RECENT ->

                        fun() = selectAllByQueryRecency(match)
                    CatalogSortOrder.FAVORITES ->

                        fun() = selectAllByQueryFavorites(match)
                }
            }
        return source
    }

    private fun GameDao.systemsSource(
        systemIds: List<String>,
        match: String?,
        sortOrder: CatalogSortOrder,
    ): () -> PagingSource<Int, Game> {
        if (systemIds.size == 1) {
            val id = systemIds.first()
            val source: () -> PagingSource<Int, Game> =
                if (match == null) {
                    when (sortOrder) {
                        CatalogSortOrder.NAME ->

                            fun() = selectBySystem(id)
                        CatalogSortOrder.RECENT ->

                            fun() = selectBySystemByRecency(id)
                        CatalogSortOrder.FAVORITES ->

                            fun() = selectBySystemFavoritesByName(id)
                    }
                } else {
                    when (sortOrder) {
                        CatalogSortOrder.NAME ->

                            fun() = selectBySystemAndQuery(id, match)
                        CatalogSortOrder.RECENT ->

                            fun() = selectBySystemAndQueryRecency(id, match)
                        CatalogSortOrder.FAVORITES ->

                            fun() = selectBySystemAndQueryFavorites(id, match)
                    }
                }
            return source
        }

        val source: () -> PagingSource<Int, Game> =
            if (match == null) {
                when (sortOrder) {
                    CatalogSortOrder.NAME ->

                        fun() = selectBySystems(systemIds)
                    CatalogSortOrder.RECENT ->

                        fun() = selectBySystemsByRecency(systemIds)
                    CatalogSortOrder.FAVORITES ->

                        fun() = selectBySystemsFavoritesByName(systemIds)
                }
            } else {
                when (sortOrder) {
                    CatalogSortOrder.NAME ->

                        fun() = selectBySystemsAndQuery(systemIds, match)
                    CatalogSortOrder.RECENT ->

                        fun() = selectBySystemsAndQueryRecency(systemIds, match)
                    CatalogSortOrder.FAVORITES ->

                        fun() = selectBySystemsAndQueryFavorites(systemIds, match)
                }
            }
        return source
    }

    companion object {
        const val PAGE_SIZE = 40
        const val CONTINUE_PLAYING_LIMIT = 12
    }
}

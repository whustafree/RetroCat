package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.network.networkErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StoreDownloadStatus {
    /** Waiting for a slot or for the network. */
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

data class StoreDownload(
    val gameId: String,
    val status: StoreDownloadStatus,
    val bytesRead: Long = 0L,
    val totalBytes: Long? = null,
    val error: String? = null,
) {
    /** 0f..1f while downloading, null when the server did not announce a size. */
    val fraction: Float?
        get() {
            val total = totalBytes ?: return null
            if (total <= 0L) return null
            return (bytesRead.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

data class StoreUiState(
    val loading: Boolean = false,
    /** Where the catalog comes from: a published manifest or a Drive folder. */
    val source: StoreSource = StoreSource.MANIFEST,
    /** How the catalog is laid out: a vertical list or a shelf per system. */
    val viewMode: StoreViewMode = StoreViewMode.LIST,
    /** The manifest URL or the Drive folder link, depending on [source]. */
    val location: String = "",
    val configured: Boolean = false,
    val name: String = "",
    /** The whole catalog, before the search box, the chips and the sort order are applied. */
    val groups: List<StoreGroup> = emptyList(),
    val error: String? = null,
    /** Problems found in the manifest itself, so a misconfigured store can be explained. */
    val issues: List<String> = emptyList(),
    val downloads: Map<String, StoreDownload> = emptyMap(),
    val downloadedIds: Set<String> = emptySet(),
    /** Room the downloaded games take, shown under the catalog name. */
    val installedBytes: Long = 0L,
    /** The games the latest scan found that the previous one did not have. */
    val newIds: Set<String> = emptySet(),
    val query: String = "",
    val statusFilter: StoreStatusFilter = StoreStatusFilter.ALL,
    val sort: StoreSortOrder = StoreSortOrder.NAME,
    /** Games ticked for a bulk download. Empty means the catalog is being browsed, not picked. */
    val selection: Set<String> = emptySet(),
    /** True when downloads are held back until the device is on a network that is not metered. */
    val wifiOnly: Boolean = false,
    /** Room left where the ROMs land, for the warning before a bulk download. Zero when unknown. */
    val freeBytes: Long = 0L,
) {
    val isEmpty: Boolean
        get() = groups.isEmpty()

    /** True while the user is picking games to download in one go. */
    val selectionMode: Boolean
        get() = selection.isNotEmpty()

    /** How many games the catalog holds in total, whatever the filters are showing. */
    val totalGames: Int
        get() = groups.sumOf { it.games.size }
}

/**
 * Drives the store: reads the remote catalog, lists it and hands the chosen ROMs to WorkManager.
 *
 * The downloads themselves run under [StoreDownloadWork], so leaving the store or the process being
 * recreated no longer kills them. This class only queues them and mirrors what WorkManager reports
 * back into [StoreUiState], which is what the screen renders.
 */
class StoreViewModel(
    private val appContext: Context,
    private val repository: StoreRepository,
    private val preferences: StorePreferences,
    private val storeLibrary: StoreLibrary,
    private val catalogCache: StoreCatalogCache,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val repository: StoreRepository,
        private val preferences: StorePreferences,
        private val storeLibrary: StoreLibrary,
        private val catalogCache: StoreCatalogCache,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StoreViewModel(
                appContext.applicationContext,
                repository,
                preferences,
                storeLibrary,
                catalogCache,
            ) as T
        }
    }

    private val _state =
        MutableStateFlow(
            StoreUiState(
                source = preferences.source,
                viewMode = preferences.viewMode,
                location = preferences.location,
                configured = preferences.isConfigured,
                wifiOnly = preferences.wifiOnly,
            ),
        )
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    init {
        if (preferences.isConfigured) refresh()
        observeDownloads()
    }

    /** Points the store at a published manifest (or clears it when blank) and reloads it. */
    fun saveManifestUrl(url: String) = saveLocation(StoreSource.MANIFEST, url)

    /** Points the store at a public Drive folder, whose contents become the catalog. */
    fun saveDriveFolderUrl(url: String) = saveLocation(StoreSource.DRIVE_FOLDER, url)

    /** Points the store at a new origin, drops the filters that no longer apply and reloads it. */
    fun saveLocation(
        source: StoreSource,
        url: String,
    ) {
        preferences.source = source
        when (source) {
            StoreSource.MANIFEST -> preferences.manifestUrl = url
            StoreSource.DRIVE_FOLDER -> preferences.driveFolderUrl = url
        }

        _state.update {
            it.copy(
                source = source,
                location = url.trim(),
                configured = preferences.isConfigured,
                query = "",
                statusFilter = StoreStatusFilter.ALL,
                selection = emptySet(),
            )
        }

        refresh()
    }

    /** Flips the catalog between the vertical list and the shelves, remembering the choice. */
    fun toggleViewMode() {
        val next =
            when (_state.value.viewMode) {
                StoreViewMode.LIST -> StoreViewMode.SHELVES
                StoreViewMode.SHELVES -> StoreViewMode.LIST
            }
        preferences.viewMode = next
        _state.update { it.copy(viewMode = next) }
    }

    fun changeQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun selectStatusFilter(statusFilter: StoreStatusFilter) {
        _state.update { it.copy(statusFilter = statusFilter) }
    }

    fun selectSort(sort: StoreSortOrder) {
        _state.update { it.copy(sort = sort) }
    }

    /** Remembers whether downloads are allowed to use a metered connection. */
    fun setWifiOnly(wifiOnly: Boolean) {
        preferences.wifiOnly = wifiOnly
        _state.update { it.copy(wifiOnly = wifiOnly) }
    }

    /** Drops the search box and the chips, which is what "no results" offers to do. */
    fun clearFilters() {
        _state.update { it.copy(query = "", statusFilter = StoreStatusFilter.ALL) }
    }

    /** Queues [game] for download. WorkManager reports the progress back through [state]. */
    fun download(game: StoreGame) = download(listOf(game))

    /**
     * Queues a batch the user asked for, in one go.
     *
     * Each game keeps its own piece of work, so one failure does not take the rest down with it.
     */
    fun download(games: List<StoreGame>) {
        if (games.isEmpty()) return
        StoreDownloadScheduler.enqueueAll(appContext, games, _state.value.wifiOnly)
    }

    /** Queues [game] again after a failure, replacing the failed attempt. */
    fun retryDownload(game: StoreGame) {
        StoreDownloadScheduler.retry(appContext, game, _state.value.wifiOnly)
    }

    /** Queues again everything that gave up, so a bad connection is one tap to recover from. */
    fun retryFailedDownloads() {
        StoreDownloadScheduler.retryAll(appContext, _state.value.failedDownloads(), _state.value.wifiOnly)
    }

    fun cancelDownload(game: StoreGame) {
        StoreDownloadScheduler.cancel(appContext, game.id)
    }

    /** Stops everything queued or running, for the mistake of picking a thousand games at once. */
    fun cancelAllDownloads() {
        StoreDownloadScheduler.cancelAll(appContext)
    }

    /**
     * Deletes an installed game from the device.
     *
     * The catalog entry stays, so the game can simply be downloaded again, and the library is
     * re-scanned right away so it disappears from the shelf instead of lingering as a broken entry.
     */
    fun delete(game: StoreGame) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { storeLibrary.delete(game) }

            if (!deleted) {
                _state.update { it.copy(error = appContext.getString(R.string.store_delete_failed)) }
                return@launch
            }

            _state.update {
                it.copy(
                    downloadedIds = it.downloadedIds - game.id,
                    downloads = it.downloads - game.id,
                    selection = it.selection - game.id,
                )
            }

            refreshStorage()
            LibraryIndexScheduler.scheduleLibrarySync(appContext)
        }
    }

    /** Re-reads the room the downloads take and the room that is left, off the main thread. */
    private fun refreshStorage() {
        viewModelScope.launch {
            val (installed, free) =
                withContext(Dispatchers.IO) {
                    storeLibrary.installedSizeBytes() to storeLibrary.freeSpaceBytes()
                }
            _state.update { it.copy(installedBytes = installed, freeBytes = free) }
        }
    }

    /** Turns a long press into the start of a multi selection. */
    fun startSelection(game: StoreGame) {
        _state.update { it.copy(selection = it.selection + game.id) }
    }

    fun toggleSelection(game: StoreGame) {
        _state.update {
            val selection = if (game.id in it.selection) it.selection - game.id else it.selection + game.id
            it.copy(selection = selection)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    /**
     * Ticks every game the filters are showing.
     *
     * With a thousand entries the interesting selection is "this system" or "what I have not
     * installed yet", so selecting all has to mean all of what is on screen, not all of the store.
     * The system name comes from the same helper the list uses, or the two would disagree about
     * what "gba" found.
     */
    fun selectAll() {
        val ids =
            _state.value
                .visibleGames { systemId -> storeSystemName(appContext, systemId) }
                .map { game -> game.id }
                .toSet()
        _state.update { it.copy(selection = ids) }
    }

    /**
     * The ticked games that are not installed yet, in catalog order.
     *
     * This is what the selection bar is about to queue, so the screen can check it fits before
     * handing it over.
     */
    fun selectedDownloads(): List<StoreGame> {
        val byId = _state.value.groups.flatMap { it.games }.associateBy { it.id }
        return _state.value.selection.mapNotNull { byId[it] }.filter { it.id !in _state.value.downloadedIds }
    }

    fun refresh() {
        val location = preferences.location
        if (location.isBlank()) {
            _state.update {
                it.copy(
                    loading = false,
                    configured = false,
                    groups = emptyList(),
                    issues = emptyList(),
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            // Show the catalog the user saw last time while the network catches up: the store is
            // readable the moment it opens, and it still works with no connection.
            if (_state.value.groups.isEmpty()) {
                catalogCache.read(location)?.let { cached ->
                    applyCatalog(cached, newIds = emptySet(), loading = true)
                }
            }

            try {
                val manifest =
                    when (preferences.source) {
                        StoreSource.MANIFEST -> repository.fetchManifest(location)
                        StoreSource.DRIVE_FOLDER -> repository.loadDriveCatalog(location)
                    }

                val fresh = freshIds(manifest)
                preferences.knownIds = manifest.games.map { it.id }.toSet()
                catalogCache.write(location, manifest)

                applyCatalog(manifest, newIds = fresh, loading = false)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = appContext.networkErrorMessage(t)) }
            }
        }
    }

    /**
     * Mirrors what WorkManager knows about the store downloads into the state the screen renders.
     *
     * A game can have more than one piece of work recorded (a failed attempt plus a newer one), so
     * the most advanced state wins rather than whichever one WorkManager happened to list last.
     */
    private fun observeDownloads() {
        viewModelScope.launch {
            StoreDownloadScheduler.workInfosFlow(appContext).collect { infos -> applyWorkInfos(infos) }
        }
    }

    private fun applyWorkInfos(infos: List<WorkInfo>) {
        val latest = mutableMapOf<String, WorkInfo>()
        infos.forEach { info ->
            val gameId = StoreDownloadScheduler.gameIdOf(info) ?: return@forEach
            val current = latest[gameId]
            if (current == null || info.state.rank() > current.state.rank()) latest[gameId] = info
        }

        val downloads = mutableMapOf<String, StoreDownload>()
        val previousInstalledCount = _state.value.downloadedIds.size
        var installed = _state.value.downloadedIds

        latest.forEach { (gameId, info) ->
            when (info.state) {
                WorkInfo.State.RUNNING ->
                    downloads[gameId] =
                        StoreDownload(
                            gameId = gameId,
                            status = StoreDownloadStatus.DOWNLOADING,
                            bytesRead = info.progress.getLong(StoreDownloadScheduler.KEY_BYTES, 0L),
                            totalBytes = info.progress.getLong(StoreDownloadScheduler.KEY_TOTAL, 0L).takeIf { it > 0L },
                        )

                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                    downloads[gameId] = StoreDownload(gameId, StoreDownloadStatus.QUEUED)

                WorkInfo.State.FAILED ->
                    downloads[gameId] =
                        StoreDownload(
                            gameId = gameId,
                            status = StoreDownloadStatus.FAILED,
                            error = info.outputData.getString(StoreDownloadScheduler.KEY_ERROR),
                        )

                WorkInfo.State.SUCCEEDED -> installed = installed + gameId

                WorkInfo.State.CANCELLED -> Unit
            }
        }

        _state.update { it.copy(downloads = downloads, downloadedIds = installed) }

        if (installed.size > previousInstalledCount) refreshStorage()
    }

    /**
     * The games this scan brought in.
     *
     * The very first scan marks nothing: with no previous list every entry would look new, which
     * would make the badge meaningless the first time the store is set up.
     */
    private fun freshIds(manifest: StoreManifest): Set<String> {
        val known = preferences.knownIds
        if (known.isEmpty()) return emptySet()

        return manifest.games.map { it.id }.toSet() - known
    }

    private suspend fun applyCatalog(
        manifest: StoreManifest,
        newIds: Set<String>,
        loading: Boolean,
    ) {
        val (downloadedIds, installedBytes, freeBytes) =
            withContext(Dispatchers.IO) {
                Triple(
                    storeLibrary.downloadedIds(manifest.games),
                    storeLibrary.installedSizeBytes(),
                    storeLibrary.freeSpaceBytes(),
                )
            }

        _state.update {
            it.copy(
                loading = loading,
                configured = true,
                error = null,
                name = manifest.name,
                groups = manifest.groups(),
                issues = manifest.validate().map { issue -> formatIssue(issue) },
                downloadedIds = downloadedIds,
                installedBytes = installedBytes,
                freeBytes = freeBytes,
                newIds = newIds,
            )
        }
    }

    private fun formatIssue(issue: StoreManifestIssue): String =
        listOfNotNull(issue.gameId, issue.message).joinToString(": ")

    /** How "advanced" a state is, used to pick the one worth showing for a game. */
    private fun WorkInfo.State.rank(): Int =
        when (this) {
            WorkInfo.State.RUNNING -> 5
            WorkInfo.State.ENQUEUED -> 4
            WorkInfo.State.BLOCKED -> 4
            WorkInfo.State.FAILED -> 3
            WorkInfo.State.SUCCEEDED -> 2
            WorkInfo.State.CANCELLED -> 1
        }
}

package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StoreDownloadStatus { DOWNLOADING, DOWNLOADED, FAILED }

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
    /** The games the latest scan found that the previous one did not have. */
    val newIds: Set<String> = emptySet(),
    val query: String = "",
    val statusFilter: StoreStatusFilter = StoreStatusFilter.ALL,
    val sort: StoreSortOrder = StoreSortOrder.NAME,
) {
    val isEmpty: Boolean
        get() = groups.isEmpty()

    /** How many games the catalog holds in total, whatever the filters are showing. */
    val totalGames: Int
        get() = groups.sumOf { it.games.size }
}

/**
 * Drives the store: reads the remote catalog, lists it and downloads the chosen ROMs into the
 * folder the library scans.
 *
 * Kept at the activity level by the screen so a download keeps running while the user moves around
 * the rest of the app. It is not a WorkManager job yet, so a download dies with the process; that is
 * the next step for very large ROMs.
 */
class StoreViewModel(
    private val appContext: Context,
    private val repository: StoreRepository,
    private val downloader: StoreDownloader,
    private val preferences: StorePreferences,
    private val storeLibrary: StoreLibrary,
    private val catalogCache: StoreCatalogCache,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val repository: StoreRepository,
        private val downloader: StoreDownloader,
        private val preferences: StorePreferences,
        private val storeLibrary: StoreLibrary,
        private val catalogCache: StoreCatalogCache,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StoreViewModel(
                appContext.applicationContext,
                repository,
                downloader,
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
            ),
        )
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    init {
        if (preferences.isConfigured) refresh()
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

    /** Drops the search box and the chips, which is what "no results" offers to do. */
    fun clearFilters() {
        _state.update { it.copy(query = "", statusFilter = StoreStatusFilter.ALL) }
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
                _state.update { it.copy(loading = false, error = t.message ?: t.javaClass.simpleName) }
            }
        }
    }

    fun download(game: StoreGame) {
        if (_state.value.downloads[game.id]?.status == StoreDownloadStatus.DOWNLOADING) return

        viewModelScope.launch {
            val destination = storeLibrary.destinationFor(game)
            setDownload(StoreDownload(game.id, StoreDownloadStatus.DOWNLOADING))

            try {
                downloader.download(game, destination) { progress ->
                    _state.update { current ->
                        val download = current.downloads[game.id] ?: return@update current
                        val updated =
                            download.copy(
                                bytesRead = progress.bytesRead,
                                totalBytes = progress.totalBytes,
                            )
                        current.copy(downloads = current.downloads + (game.id to updated))
                    }
                }

                _state.update {
                    it.copy(
                        downloads = it.downloads + (game.id to StoreDownload(game.id, StoreDownloadStatus.DOWNLOADED)),
                        downloadedIds = it.downloadedIds + game.id,
                    )
                }

                // Make the new ROM show up in the library without asking the user to rescan.
                LibraryIndexScheduler.scheduleLibrarySync(appContext)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                setDownload(
                    StoreDownload(
                        gameId = game.id,
                        status = StoreDownloadStatus.FAILED,
                        error = t.message ?: t.javaClass.simpleName,
                    ),
                )
            }
        }
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
        val downloadedIds = withContext(Dispatchers.IO) { storeLibrary.downloadedIds(manifest.games) }

        _state.update {
            it.copy(
                loading = loading,
                configured = true,
                error = null,
                name = manifest.name,
                groups = manifest.groups(),
                issues = manifest.validate().map { issue -> formatIssue(issue) },
                downloadedIds = downloadedIds,
                newIds = newIds,
            )
        }
    }

    private fun setDownload(download: StoreDownload) {
        _state.update { it.copy(downloads = it.downloads + (download.gameId to download)) }
    }

    private fun formatIssue(issue: StoreManifestIssue): String =
        listOfNotNull(issue.gameId, issue.message).joinToString(": ")
}

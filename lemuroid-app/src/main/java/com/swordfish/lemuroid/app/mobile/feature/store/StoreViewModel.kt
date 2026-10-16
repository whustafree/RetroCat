package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** The address in use for [source] (the manifest URL or the Drive folder link). */
    val location: String = "",
    val configured: Boolean = false,
    val name: String = "",
    val groups: List<StoreGroup> = emptyList(),
    val error: String? = null,
    /** Problems found in the manifest itself, so a misconfigured store can be explained. */
    val issues: List<String> = emptyList(),
    val downloads: Map<String, StoreDownload> = emptyMap(),
    val downloadedIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = groups.isEmpty()
}

/**
 * Drives the store: reads the remote manifest, lists it as shelves and downloads the chosen ROMs
 * into the folder the library scans.
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
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val repository: StoreRepository,
        private val downloader: StoreDownloader,
        private val preferences: StorePreferences,
        private val storeLibrary: StoreLibrary,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StoreViewModel(appContext.applicationContext, repository, downloader, preferences, storeLibrary) as T
        }
    }

    private val _state =
        MutableStateFlow(
            StoreUiState(
                source = preferences.source,
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

    /** Sets both the origin and its address, then reloads. Used by the setup screen. */
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
                error = null,
            )
        }
        refresh()
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
            try {
                val manifest =
                    when (preferences.source) {
                        StoreSource.MANIFEST -> repository.fetchManifest(location)
                        StoreSource.DRIVE_FOLDER -> repository.loadDriveCatalog(location)
                    }
                _state.update {
                    it.copy(
                        loading = false,
                        configured = true,
                        error = null,
                        name = manifest.name,
                        groups = manifest.groups(),
                        issues = manifest.validate().map { issue -> formatIssue(issue) },
                        downloadedIds = storeLibrary.downloadedIds(manifest.games),
                    )
                }
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

    private fun setDownload(download: StoreDownload) {
        _state.update { it.copy(downloads = it.downloads + (download.gameId to download)) }
    }

    private fun formatIssue(issue: StoreManifestIssue): String =
        listOfNotNull(issue.gameId, issue.message).joinToString(": ")
}

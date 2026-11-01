package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.update.UpdateInstaller
import com.swordfish.lemuroid.app.mobile.feature.update.UpdateRelease
import com.swordfish.lemuroid.app.mobile.feature.update.UpdateRepository
import com.swordfish.lemuroid.app.mobile.feature.update.UpdateVersions
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.network.NetworkFailure
import com.swordfish.lemuroid.app.shared.network.asNetworkFailure
import com.swordfish.lemuroid.app.shared.network.networkErrorMessage
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context,
    private val settingsInteractor: SettingsInteractor,
    saveSyncManager: SaveSyncManager,
    sharedPreferences: FlowSharedPreferences,
    private val updateRepository: UpdateRepository,
    private val updateInstaller: UpdateInstaller,
) : ViewModel() {
    class Factory(
        private val context: Context,
        private val settingsInteractor: SettingsInteractor,
        private val saveSyncManager: SaveSyncManager,
        private val sharedPreferences: FlowSharedPreferences,
        private val updateRepository: UpdateRepository,
        private val updateInstaller: UpdateInstaller,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                context,
                settingsInteractor,
                saveSyncManager,
                sharedPreferences,
                updateRepository,
                updateInstaller,
            ) as T
        }
    }

    data class State(
        val currentDirectory: String = "",
        val isSaveSyncSupported: Boolean = false,
    )

    /** What the update entry in the settings is currently saying. */
    sealed interface UpdateState {
        /** Nothing asked yet, or the answer was dismissed. */
        object Idle : UpdateState

        object Checking : UpdateState

        /** The newest published build is this one. */
        object UpToDate : UpdateState

        data class Available(val release: UpdateRelease) : UpdateState

        data class Downloading(val release: UpdateRelease) : UpdateState

        data class Failed(val message: String) : UpdateState
    }

    val indexingInProgress = PendingOperationsMonitor(context).anyLibraryOperationInProgress()

    val directoryScanInProgress = PendingOperationsMonitor(context).isDirectoryScanInProgress()

    /** The version this build reports, shown next to the update entry. */
    val currentVersion: String = BuildConfig.VERSION_NAME

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val uiState =
        sharedPreferences.getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder))
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")
            .map { State(it, saveSyncManager.isSupported()) }

    fun changeLocalStorageFolder() {
        settingsInteractor.changeLocalStorageFolder()
    }

    /**
     * Asks GitHub for the newest published build.
     *
     * Being unable to ask is not the same as being up to date, so the two are told apart: the entry
     * says what actually went wrong instead of quietly pretending there is nothing newer.
     */
    fun checkForUpdates() {
        if (_updateState.value is UpdateState.Checking) return

        _updateState.value = UpdateState.Checking

        viewModelScope.launch {
            _updateState.value =
                runCatching { updateRepository.latest() }.fold(
                    onSuccess = { release ->
                        when {
                            release == null -> UpdateState.UpToDate
                            UpdateVersions.isNewer(release.version, currentVersion) -> UpdateState.Available(release)
                            else -> UpdateState.UpToDate
                        }
                    },
                    onFailure = { failure -> UpdateState.Failed(context.networkErrorMessage(failure)) },
                )
        }
    }

    /** Downloads the APK and lets the system installer take over. */
    fun installUpdate(release: UpdateRelease) {
        _updateState.value = UpdateState.Downloading(release)

        viewModelScope.launch {
            updateInstaller.downloadAndInstall(release).fold(
                onSuccess = { _updateState.value = UpdateState.Idle },
                onFailure = { failure -> _updateState.value = UpdateState.Failed(downloadFailureMessage(failure)) },
            )
            // On success the system installer is on screen; the entry waits for the user to come back.
        }
    }

    /**
     * What to say when the update could not be installed.
     *
     * A network failure has a sentence of its own; anything else (no room for the APK, nothing on
     * the device able to install it) keeps the generic one instead of blaming the connection.
     */
    private fun downloadFailureMessage(failure: Throwable): String =
        if (failure.asNetworkFailure() == NetworkFailure.UNKNOWN) {
            context.getString(R.string.update_download_failed)
        } else {
            context.networkErrorMessage(failure)
        }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }
}

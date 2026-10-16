package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/** How the remote catalog is obtained. */
enum class StoreSource {
    /** A `manifest.json` the user published on any static host. */
    MANIFEST,

    /** A public Google Drive folder the catalog is generated from. */
    DRIVE_FOLDER,
}

/**
 * Where the remote catalog lives.
 *
 * The store is generic on purpose: the app never points at a hardcoded catalog. The user either
 * configures the URL of a manifest they published, or pastes a public Google Drive folder and the
 * app generates the catalog from its contents. Either way an empty value means the store has not
 * been set up yet, which the screen shows as an onboarding empty state.
 */
class StorePreferences(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context)

    /** Which of the two origins is active. Defaults to the plain manifest. */
    var source: StoreSource
        get() =
            StoreSource.values().firstOrNull { it.name == preferences.getString(KEY_SOURCE, null) }
                ?: StoreSource.MANIFEST
        set(value) {
            preferences.edit().putString(KEY_SOURCE, value.name).apply()
        }

    var manifestUrl: String
        get() = preferences.getString(KEY_MANIFEST_URL, null).orEmpty()
        set(value) {
            preferences.edit().putString(KEY_MANIFEST_URL, value.trim()).apply()
        }

    var driveFolderUrl: String
        get() = preferences.getString(KEY_DRIVE_FOLDER_URL, null).orEmpty()
        set(value) {
            preferences.edit().putString(KEY_DRIVE_FOLDER_URL, value.trim()).apply()
        }

    /** The address of the active [source], empty when that origin was never configured. */
    val location: String
        get() =
            when (source) {
                StoreSource.MANIFEST -> manifestUrl
                StoreSource.DRIVE_FOLDER -> driveFolderUrl
            }

    val isConfigured: Boolean
        get() = location.isNotBlank()

    companion object {
        private const val KEY_SOURCE = "store_source"
        private const val KEY_MANIFEST_URL = "store_manifest_url"
        private const val KEY_DRIVE_FOLDER_URL = "store_drive_folder_url"
    }
}

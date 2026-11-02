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

/** How the catalog is laid out once it is loaded. */
enum class StoreViewMode {
    /**
     * One row per game under a header per system. Browsing a catalog of thousands of entries relies
     * on seeing many at a time, which a horizontal shelf cannot do.
     */
    LIST,

    /** The classic horizontal shelf per system, which shows fewer games but bigger covers. */
    SHELVES,
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

    /** The layout the catalog is shown in. Defaults to the list, which reads better when large. */
    var viewMode: StoreViewMode
        get() =
            StoreViewMode.values().firstOrNull { it.name == preferences.getString(KEY_VIEW_MODE, null) }
                ?: StoreViewMode.LIST
        set(value) {
            preferences.edit().putString(KEY_VIEW_MODE, value.name).apply()
        }

    /**
     * Whether downloads wait for a network that is not metered.
     *
     * Off by default, which is how the store always behaved: the ROMs are big enough that a
     * catalog of a thousand of them can empty a data plan, but holding downloads hostage without
     * saying so would be worse.
     */
    var wifiOnly: Boolean
        get() = preferences.getBoolean(KEY_WIFI_ONLY, false)
        set(value) {
            preferences.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        }

    /**
     * Ids of every game the last successful scan returned.
     *
     * The difference between this and the next scan is what the store marks as "new", which is how
     * adding a few ROMs to the Drive folder and pressing refresh becomes visible.
     */
    var knownIds: Set<String>
        get() = preferences.getStringSet(KEY_KNOWN_IDS, null).orEmpty().toSet()
        set(value) {
            preferences.edit().putStringSet(KEY_KNOWN_IDS, value).apply()
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
        private const val KEY_VIEW_MODE = "store_view_mode"
        private const val KEY_WIFI_ONLY = "store_wifi_only"
        private const val KEY_KNOWN_IDS = "store_known_ids"
        private const val KEY_MANIFEST_URL = "store_manifest_url"
        private const val KEY_DRIVE_FOLDER_URL = "store_drive_folder_url"
    }
}

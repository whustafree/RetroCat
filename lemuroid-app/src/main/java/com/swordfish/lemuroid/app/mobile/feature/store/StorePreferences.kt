package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Where the remote catalog lives.
 *
 * The store is generic on purpose: the app never points at a hardcoded catalog, the user configures
 * the URL of the manifest they published. An empty value means the store has not been set up yet,
 * which the screen shows as an onboarding empty state.
 */
class StorePreferences(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context)

    var manifestUrl: String
        get() = preferences.getString(KEY_MANIFEST_URL, null).orEmpty()
        set(value) {
            preferences.edit().putString(KEY_MANIFEST_URL, value.trim()).apply()
        }

    val isConfigured: Boolean
        get() = manifestUrl.isNotBlank()

    companion object {
        private const val KEY_MANIFEST_URL = "store_manifest_url"
    }
}

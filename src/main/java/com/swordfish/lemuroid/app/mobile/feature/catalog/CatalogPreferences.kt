package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.content.Context
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Persists the catalog presentation settings: how many tiles fit per row and whether
 * games are grouped under letter headers.
 */
class CatalogPreferences(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context)

    var gridDensity: GridDensity
        get() =
            runCatching {
                GridDensity.valueOf(preferences.getString(KEY_GRID_DENSITY, null).orEmpty())
            }.getOrDefault(GridDensity.REGULAR)
        set(value) {
            preferences.edit().putString(KEY_GRID_DENSITY, value.name).apply()
        }

    var groupedLetters: Boolean
        get() = preferences.getBoolean(KEY_GROUPED_LETTERS, true)
        set(value) {
            preferences.edit().putBoolean(KEY_GROUPED_LETTERS, value).apply()
        }

    companion object {
        private const val KEY_GRID_DENSITY = "catalog_grid_density"
        private const val KEY_GROUPED_LETTERS = "catalog_grouped_letters"
    }
}

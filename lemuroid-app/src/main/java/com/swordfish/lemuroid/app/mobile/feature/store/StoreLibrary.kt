package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import java.io.File

/**
 * Where downloaded store games are written.
 *
 * The download has to land inside a folder the library scans, or the game is invisible after it
 * finishes. The local provider scans the user's legacy external folder when one is set and the app
 * roms folder otherwise, so the store mirrors that exact choice instead of inventing a third
 * location the scanner would never look at.
 */
class StoreLibrary(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    /** Root of the store's own folder, kept separate from the user's own ROMs. */
    fun downloadsRoot(): File = File(scannedRoot(), STORE_FOLDER)

    /** Absolute path the ROM for [game] is stored at, whether or not it exists yet. */
    fun destinationFor(game: StoreGame): File {
        val system = game.resolveSystemId()?.dbname ?: UNKNOWN_SYSTEM
        return File(File(downloadsRoot(), system), game.resolvedFileName())
    }

    fun isDownloaded(game: StoreGame): Boolean = destinationFor(game).isFile

    /** Ids of the games already sitting on disk, so the store can show them as installed. */
    fun downloadedIds(games: List<StoreGame>): Set<String> = games.filter { isDownloaded(it) }.map { it.id }.toSet()

    private fun scannedRoot(): File {
        val preferenceKey = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)
        val legacyFolder = SharedPreferencesHelper.getLegacySharedPreferences(context).getString(preferenceKey, null)
        return legacyFolder?.takeIf { it.isNotBlank() }?.let { File(it) } ?: directoriesManager.getInternalRomsDirectory()
    }

    companion object {
        const val STORE_FOLDER = "retrocat-store"
        private const val UNKNOWN_SYSTEM = "misc"
    }
}

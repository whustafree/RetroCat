package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Derives thumbnail URLs for the libretro thumbnail repository.
 *
 * Kept in `retrograde-app-shared` so both the metadata provider and the library
 * backfill can use the same rules without depending on each other.
 */
object ScreenshotUrls {
    const val BOXARTS = "Named_Boxarts"
    const val TITLES = "Named_Titles"

    private const val BASE_URL = "https://thumbnails.libretro.com"

    private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|]")

    /**
     * Percent encodes a value for use as a single URL path segment. Spaces and accented
     * characters are common in game names and 404 if they are left unencoded.
     */
    fun encodeSegment(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun build(
        systemName: String,
        imageType: String,
        gameName: String,
    ): String {
        val cleanName = gameName.replace(THUMB_REPLACE, "_")

        return "$BASE_URL/${encodeSegment(systemName)}/${encodeSegment(imageType)}/${encodeSegment(cleanName)}.png"
    }

    /**
     * Builds the screenshot URL for a game, or null when there is no thumbnail to derive
     * it from.
     */
    fun derive(game: Game): String? {
        val coverUrl = game.coverFrontUrl ?: return null
        if (!coverUrl.contains(BOXARTS)) return null

        return coverUrl.replace(BOXARTS, TITLES)
    }
}

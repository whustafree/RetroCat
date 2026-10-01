package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Checks whether derived screenshot URLs actually resolve, and clears the ones that 404.
 *
 * The libretro thumbnail repository has no complete `Named_Titles` coverage, so a derived
 * URL is a guess. Without this the hero shows a broken image instead of a placeholder.
 */
class ScreenshotValidator(
    private val retrogradedb: RetrogradeDatabase,
    private val okHttpClient: OkHttpClient = defaultClient,
) {
    /**
     * Validates at most [maxChecks] candidates, so a large library is cleaned up over
     * several background runs instead of hammering the thumbnail server in one go.
     *
     * @return the number of URLs cleared as invalid.
     */
    suspend fun validateAll(
        batchSize: Int = DEFAULT_BATCH_SIZE,
        maxChecks: Int = DEFAULT_MAX_CHECKS,
    ): Int {
        val candidates = retrogradedb.gameDao().selectScreenshotCandidates().take(maxChecks)
        if (candidates.isEmpty()) return 0

        val invalid = mutableListOf<Game>()

        withContext(Dispatchers.IO) {
            candidates.chunked(batchSize).forEach { batch ->
                batch.forEach { game ->
                    val url = game.screenshotUrl
                    if (url == null || !exists(url)) {
                        invalid += game
                    }
                }
            }
        }

        if (invalid.isNotEmpty()) {
            retrogradedb.gameDao().update(invalid.map { it.copy(screenshotUrl = null) })
        }

        Timber.i("Screenshot validation cleared ${invalid.size}/${candidates.size} invalid urls")

        return invalid.size
    }

    private fun exists(url: String): Boolean {
        val request =
            Request.Builder()
                .url(url)
                .head()
                .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrElse {
            Timber.w("Failed to validate screenshot $url: $it")
            // Treat network errors as "unknown" and keep the URL, so a temporary
            // connectivity problem does not wipe every hero image.
            true
        }
    }

    companion object {
        private const val DEFAULT_BATCH_SIZE = 50
        private const val DEFAULT_MAX_CHECKS = 60

        private val defaultClient: OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
    }
}

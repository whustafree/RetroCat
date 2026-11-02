package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit

/**
 * Queues store downloads and reports how they are going.
 *
 * Each game gets its own named piece of work, so asking twice for the same ROM does not queue it
 * twice and one download can be cancelled without touching the rest. WorkManager owns the retry
 * policy, the network constraint and the foreground notification, which is what makes a download
 * survive the user leaving the store.
 */
object StoreDownloadScheduler {
    const val TAG = "store-download"

    const val KEY_BYTES = "bytes"
    const val KEY_TOTAL = "total"
    const val KEY_ERROR = "error"

    /** How many times a download that looks transient is attempted before giving up. */
    const val MAX_ATTEMPTS = 3

    private const val KEY_GAME = "game"
    private const val NAME_PREFIX = "$TAG-"

    /** Enough room for every download running at once to keep its own notification. */
    private const val NOTIFICATION_BASE = 0x5300
    private const val NOTIFICATION_MASK = 0x3FF

    /** Queues [game] unless it is already queued or running. */
    fun enqueue(
        context: Context,
        game: StoreGame,
        wifiOnly: Boolean,
    ) = enqueueUnique(context, game, ExistingWorkPolicy.KEEP, wifiOnly)

    /** Queues [game] again, replacing an attempt that failed. */
    fun retry(
        context: Context,
        game: StoreGame,
        wifiOnly: Boolean,
    ) = enqueueUnique(context, game, ExistingWorkPolicy.REPLACE, wifiOnly)

    /**
     * Queues a whole batch.
     *
     * Each game keeps its own piece of work, so WorkManager decides how many run at once and one
     * failure does not take the rest of the batch down with it.
     */
    fun enqueueAll(
        context: Context,
        games: List<StoreGame>,
        wifiOnly: Boolean,
    ) {
        games.forEach { game -> enqueue(context, game, wifiOnly) }
    }

    /** Queues again everything that gave up, replacing each failed attempt. */
    fun retryAll(
        context: Context,
        games: List<StoreGame>,
        wifiOnly: Boolean,
    ) {
        games.forEach { game -> retry(context, game, wifiOnly) }
    }

    fun cancel(
        context: Context,
        gameId: String,
    ) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(gameId))
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    /** Every store download WorkManager knows about, updated as its state changes. */
    fun workInfosFlow(context: Context): Flow<List<WorkInfo>> =
        callbackFlow {
            val liveData = WorkManager.getInstance(context).getWorkInfosByTagLiveData(TAG)
            val observer = Observer<List<WorkInfo>> { infos -> trySend(infos) }

            liveData.observeForever(observer)
            awaitClose { liveData.removeObserver(observer) }
        }

    fun uniqueName(gameId: String): String = "$NAME_PREFIX$gameId"

    /** The game a piece of work belongs to, read back from the tag it was queued with. */
    fun gameIdOf(workInfo: WorkInfo): String? =
        workInfo.tags.firstOrNull { it.startsWith(NAME_PREFIX) }?.removePrefix(NAME_PREFIX)

    /** A notification id of its own per game, so two downloads do not fight over one. */
    fun notificationId(gameId: String): Int = NOTIFICATION_BASE + (gameId.hashCode() and NOTIFICATION_MASK)

    fun progressData(progress: StoreDownloader.Progress): Data =
        workDataOf(
            KEY_BYTES to progress.bytesRead,
            KEY_TOTAL to (progress.totalBytes ?: 0L),
        )

    fun errorData(message: String): Data = workDataOf(KEY_ERROR to message)

    fun readGame(inputData: Data): StoreGame? =
        inputData.getString(KEY_GAME)?.let { text -> runCatching { StoreManifestCodec.decodeGame(text) }.getOrNull() }

    private fun enqueueUnique(
        context: Context,
        game: StoreGame,
        policy: ExistingWorkPolicy,
        wifiOnly: Boolean,
    ) {
        val request =
            OneTimeWorkRequestBuilder<StoreDownloadWork>()
                .setConstraints(
                    Constraints
                        .Builder()
                        // A catalog of a thousand ROMs can empty a data plan in one sitting, so the
                        // user can ask for a network that does not charge by the byte.
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_GAME to StoreManifestCodec.encodeGame(game.forTransport())))
                .addTag(TAG)
                .addTag(uniqueName(game.id))
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(game.id), policy, request)
    }

    /**
     * The part of the game the worker actually needs.
     *
     * WorkManager caps input data at 10 KB, and a well documented catalog entry with screenshots
     * can grow past that for no reason: the description and the images are only used by the detail
     * dialog, never by the download.
     */
    private fun StoreGame.forTransport(): StoreGame =
        copy(
            description = null,
            coverUrl = null,
            screenshots = emptyList(),
        )
}

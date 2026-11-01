package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.network.asNetworkFailure
import com.swordfish.lemuroid.app.shared.network.networkErrorMessage
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Downloads one store ROM, in the background and with a progress notification.
 *
 * Running under WorkManager instead of inside the screen means the download no longer dies when the
 * user leaves the store tab or the process is recreated: WorkManager owns the retry policy and the
 * foreground notification, and the ROM is streamed to the same folder the library scans, so a
 * finished download shows up in the library without any extra step.
 *
 * The whole [StoreGame] travels in the input data (minus the fields the download does not need) so
 * the worker never has to re-read the catalog, which may have been refreshed in the meantime.
 */
class StoreDownloadWork(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var downloader: StoreDownloader

    @Inject
    lateinit var storeLibrary: StoreLibrary

    /** Last time the notification was refreshed, in milliseconds since boot. */
    private var lastNotificationAt = 0L

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)

        val game =
            StoreDownloadScheduler.readGame(inputData)
                ?: return Result.failure(
                    StoreDownloadScheduler.errorData(
                        applicationContext.networkErrorMessage(IllegalStateException("The request carried no game")),
                    ),
                )

        val destination = storeLibrary.destinationFor(game)
        if (destination.isFile) {
            LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
            return Result.success()
        }

        val notifications = NotificationsManager(applicationContext)
        val notificationId = StoreDownloadScheduler.notificationId(game.id)

        // A background start on Android 12+ cannot go foreground, and a download without a
        // notification is still better than no download at all.
        runCatching {
            setForeground(
                createSyncForegroundInfo(
                    notificationId,
                    notifications.storeDownloadNotification(game.title, null),
                ),
            )
        }.onFailure { Timber.w(it, "The store download could not go foreground") }

        return try {
            downloader.download(game, destination) { progress ->
                refreshProgress(progress, notifications, notificationId, game.title)
            }

            notifications.cancelStoreDownload(notificationId)
            LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
            Result.success()
        } catch (cancellation: CancellationException) {
            notifications.cancelStoreDownload(notificationId)
            throw cancellation
        } catch (t: Throwable) {
            Timber.w(t, "Downloading ${game.id} failed")
            notifications.cancelStoreDownload(notificationId)

            // Trying again is only worth it for a failure that could go away, and only while there
            // are attempts left; the user reads the reason in their language either way.
            if (t.asNetworkFailure().retryable && runAttemptCount + 1 < StoreDownloadScheduler.MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(StoreDownloadScheduler.errorData(applicationContext.networkErrorMessage(t)))
            }
        }
    }

    /** Progress arrives once per 64 KB buffer; a notification per chunk would drown the system. */
    private fun refreshProgress(
        progress: StoreDownloader.Progress,
        notifications: NotificationsManager,
        notificationId: Int,
        title: String,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationAt < PROGRESS_INTERVAL_MS) return
        lastNotificationAt = now

        setProgressAsync(StoreDownloadScheduler.progressData(progress))
        notifications.notifyStoreDownload(notificationId, title, fractionOf(progress))
    }

    companion object {
        /** How often the notification and the reported progress are refreshed. */
        private const val PROGRESS_INTERVAL_MS = 500L

        private fun fractionOf(progress: StoreDownloader.Progress): Float? {
            val total = progress.totalBytes ?: return null
            if (total <= 0L) return null
            return (progress.bytesRead.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(StoreDownloadWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<StoreDownloadWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<StoreDownloadWork>()
    }
}

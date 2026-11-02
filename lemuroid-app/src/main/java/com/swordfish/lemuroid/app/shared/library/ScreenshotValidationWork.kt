package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.library.ScreenshotValidator
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import timber.log.Timber
import javax.inject.Inject

/**
 * Clears derived screenshot URLs that do not actually resolve, so the game detail shows a
 * placeholder instead of a broken hero image.
 */
class ScreenshotValidationWork(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var screenshotValidator: ScreenshotValidator

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)

        return try {
            val cleared = screenshotValidator.validateAll()
            Timber.i("Screenshot validation cleared $cleared urls")
            Result.success()
        } catch (e: Throwable) {
            // A failed validation is not fatal: the URLs stay as they are and the next
            // library sync retries.
            Timber.e(e, "Screenshot validation terminated with an exception")
            Result.success()
        }
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(ScreenshotValidationWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<ScreenshotValidationWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<ScreenshotValidationWork>()
    }
}

package com.swordfish.lemuroid.app.mobile.feature.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.swordfish.lemuroid.app.shared.network.HttpFailure
import com.swordfish.lemuroid.app.shared.network.NetworkFailure
import com.swordfish.lemuroid.app.shared.network.NetworkFailureException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads a published APK and hands it to the system installer.
 *
 * The app does not install anything itself: it can only open the package with the installer, which
 * is the only part of Android allowed to ask the user for permission and to actually replace the
 * app. The APK is fetched into the cache, wrapped in a `FileProvider` URI (a plain `file://` would
 * be refused) and handed over with a one-off read grant.
 */
class UpdateInstaller(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    /**
     * Downloads [release] and opens the system installer.
     *
     * The download happens off the main thread; the install intent is fired afterwards, back on
     * whichever dispatcher called this.
     */
    suspend fun downloadAndInstall(release: UpdateRelease): Result<Unit> =
        runCatching {
            val apk = withContext(Dispatchers.IO) { download(release) }
            install(apk)
        }

    private fun download(release: UpdateRelease): File {
        // Old APKs are only useful until they are installed, so the folder is cleared first rather
        // than filled with one file per attempt.
        val folder = File(context.cacheDir, UPDATE_FOLDER)
        folder.deleteRecursively()
        folder.mkdirs()

        val target = File(folder, "RetroCat-${release.version}.apk")
        val partial = File(folder, target.name + PARTIAL_SUFFIX)

        val request = Request.Builder().url(release.apkUrl).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpFailure(response.code)

            val body = response.body
            if (body == null) {
                throw NetworkFailureException(NetworkFailure.INVALID_CONTENT, "The update returned an empty body")
            }

            partial.outputStream().use { output -> body.byteStream().copyTo(output) }
        }

        check(partial.renameTo(target)) { "The downloaded update could not be moved into place" }

        return target
    }

    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)

        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    companion object {
        private const val UPDATE_FOLDER = "update"
        private const val PARTIAL_SUFFIX = ".part"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

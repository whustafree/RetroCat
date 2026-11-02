package com.swordfish.lemuroid.app.mobile.feature.store

import androidx.annotation.StringRes
import com.swordfish.lemuroid.app.shared.network.HttpFailure
import com.swordfish.lemuroid.app.shared.network.NetworkFailure
import com.swordfish.lemuroid.app.shared.network.NetworkFailureException
import com.swordfish.lemuroid.app.shared.network.withNetworkRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Raised when the remote store cannot be reached or returns something unusable.
 *
 * It carries [NetworkFailure] so whatever shows the error can say "no connection" instead of the
 * stack trace's opinion, and [messageRes] for the few cases with an explanation of their own.
 */
class StoreException(
    message: String,
    cause: Throwable? = null,
    failure: NetworkFailure = NetworkFailure.UNKNOWN,
    @StringRes messageRes: Int? = null,
) : NetworkFailureException(failure, message, cause, messageRes)

/**
 * Reads the remote catalog over plain HTTP.
 *
 * The manifest is a single small JSON document, so a streaming converter is not needed: the body
 * is read whole and handed to [StoreManifestCodec]. Only the catalog is fetched here; the ROM
 * downloads keep their own progress aware path.
 *
 * A request that fails for a reason that may go away is retried a couple of times before the
 * failure is reported: a catalog that opens on a flaky connection is worth the extra second.
 *
 * A published manifest is not the only origin: [loadDriveCatalog] hands a public Drive folder to
 * [DriveCatalogLoader], which builds the same [StoreManifest] from the folder's contents.
 */
class StoreRepository(
    private val okHttpClient: OkHttpClient,
) {
    private val driveCatalogLoader = DriveCatalogLoader(okHttpClient)

    suspend fun fetchManifest(url: String): StoreManifest =
        withContext(Dispatchers.IO) {
            withNetworkRetry { fetchManifestOnce(url) }
        }

    private fun fetchManifestOnce(url: String): StoreManifest {
        val request =
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw StoreException("The store answered HTTP ${response.code}", HttpFailure(response.code))
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                throw StoreException("The store returned an empty manifest", failure = NetworkFailure.INVALID_CONTENT)
            }

            StoreManifestCodec.parse(body)
        }
    }

    /** Builds the catalog from a public Drive folder instead of a published manifest. */
    suspend fun loadDriveCatalog(folderUrl: String): StoreManifest = driveCatalogLoader.load(folderUrl)
}

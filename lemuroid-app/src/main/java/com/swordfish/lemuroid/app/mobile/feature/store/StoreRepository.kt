package com.swordfish.lemuroid.app.mobile.feature.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Raised when the remote store cannot be reached or returns something unusable. */
class StoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Reads the remote catalog over plain HTTP.
 *
 * The manifest is a single small JSON document, so a streaming converter is not needed: the body
 * is read whole and handed to [StoreManifestCodec]. Only the catalog is fetched here; the ROM
 * downloads keep their own progress aware path.
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
            val request =
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                when {
                    !response.isSuccessful -> throw StoreException("The store returned HTTP ${response.code}")
                    body.isNullOrBlank() -> throw StoreException("The store returned an empty manifest")
                    else -> StoreManifestCodec.parse(body)
                }
            }
        }

    /** Builds the catalog from a public Drive folder instead of a published manifest. */
    suspend fun loadDriveCatalog(folderUrl: String): StoreManifest = driveCatalogLoader.load(folderUrl)
}

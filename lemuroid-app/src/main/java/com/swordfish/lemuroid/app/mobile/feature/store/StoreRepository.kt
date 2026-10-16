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
 * is read whole and handed to [StoreManifestCodec]. Only the manifest is fetched here; the ROM
 * downloads keep their own progress aware path.
 */
class StoreRepository(
    private val okHttpClient: OkHttpClient,
) {
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
}

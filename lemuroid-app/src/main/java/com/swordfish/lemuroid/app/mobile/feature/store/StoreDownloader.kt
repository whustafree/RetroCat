package com.swordfish.lemuroid.app.mobile.feature.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Streams a store ROM to disk, reporting progress and verifying the manifest hash.
 *
 * The body is written to a `.part` sidecar and only renamed onto the final name once the download
 * finished and the checksum matched, so an interrupted or tampered download never leaves a
 * half-written file that the scanner would index as a game.
 */
class StoreDownloader(
    private val okHttpClient: OkHttpClient,
) {
    data class Progress(
        val bytesRead: Long,
        val totalBytes: Long?,
    )

    suspend fun download(
        game: StoreGame,
        destination: File,
        onProgress: (Progress) -> Unit = {},
    ): File =
        withContext(Dispatchers.IO) {
            destination.parentFile?.mkdirs()

            val partial = File(destination.parentFile, destination.name + PARTIAL_SUFFIX)
            try {
                val actualHash = fetchToFile(game.url, partial, onProgress)
                verifyChecksum(game, actualHash)
                move(partial, destination)
                destination
            } catch (t: Throwable) {
                // A cancelled or failed download must not leave its partial file behind: the
                // scanner walks this folder and would try to index it.
                partial.delete()
                throw t
            }
        }

    /** Streams the response into [partial] and returns the lowercase hex sha256 of what was read. */
    private suspend fun fetchToFile(
        url: String,
        partial: File,
        onProgress: (Progress) -> Unit,
    ): String {
        val request = Request.Builder().url(url).build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw StoreException("The download returned HTTP ${response.code}")
            }

            val body = response.body ?: throw StoreException("The download returned an empty body")
            val totalBytes = body.contentLength().takeIf { it > 0L }
            val digest = MessageDigest.getInstance(SHA256)

            body.byteStream().use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read = input.read(buffer)
                    var bytesRead = 0L

                    while (read >= 0) {
                        // Cancelling the coroutine must abort the download, not keep writing.
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesRead += read
                        onProgress(Progress(bytesRead, totalBytes))
                        read = input.read(buffer)
                    }

                    output.flush()
                }
            }

            return ByteString.of(*digest.digest()).hex()
        }
    }

    private fun verifyChecksum(
        game: StoreGame,
        actualHash: String,
    ) {
        val expected = game.sha256?.trim()?.lowercase().orEmpty()
        if (expected.isNotEmpty() && expected != actualHash) {
            throw StoreException("Checksum mismatch: expected $expected but got $actualHash")
        }
    }

    private fun move(
        partial: File,
        destination: File,
    ) {
        if (partial.renameTo(destination)) return

        // Rename only works inside one filesystem; fall back to a copy for exotic setups.
        partial.copyTo(destination, overwrite = true)
        partial.delete()
    }

    companion object {
        private const val PARTIAL_SUFFIX = ".part"
        private const val SHA256 = "SHA-256"
        private const val BUFFER_SIZE = 64 * 1024
    }
}

package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Keeps the last catalog on disk so the store opens instantly and still works with no connection.
 *
 * A manifest is a small JSON document, so it is stored as-is. There is one file per configured
 * location: pointing the store at another Drive folder must not resurrect the previous catalog, and
 * the two origins (a manifest URL and a Drive folder link) must not overwrite each other.
 */
class StoreCatalogCache(context: Context) {
    private val root = File(context.filesDir, FOLDER)

    suspend fun read(location: String): StoreManifest? =
        withContext(Dispatchers.IO) {
            val file = fileFor(location)
            if (!file.isFile) return@withContext null

            runCatching { StoreManifestCodec.parse(file.readText()) }.getOrNull()
        }

    suspend fun write(
        location: String,
        manifest: StoreManifest,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            root.mkdirs()
            fileFor(location).writeText(StoreManifestCodec.serialize(manifest))
        }
        Unit
    }

    private fun fileFor(location: String) = File(root, "${location.sha256()}.json")

    private fun String.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val FOLDER = "store-catalog"
    }
}

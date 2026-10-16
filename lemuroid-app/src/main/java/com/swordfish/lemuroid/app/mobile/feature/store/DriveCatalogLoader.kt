package com.swordfish.lemuroid.app.mobile.feature.store

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads a shared Google Drive folder and turns it into a [StoreManifest].
 *
 * Nothing but the folder link is needed. The listing comes from the same public endpoint the Drive
 * web UI uses for its embedded view, subfolders are walked (bounded in depth and never revisiting a
 * folder, so a shortcut cycle cannot loop forever) and every file is handed to
 * [DriveManifestBuilder]. Since the catalog is generated on the spot, adding a game to Drive and
 * hitting "rescan" is all it takes for it to appear.
 *
 * This works for folders shared with "anyone with the link". A private folder answers with a page
 * that carries no listing, which is reported as a readable error instead of an empty catalog.
 */
class DriveCatalogLoader(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun load(folderUrl: String): StoreManifest =
        withContext(Dispatchers.IO) {
            val folderId =
                DriveFolderListing.folderIdFrom(folderUrl)
                    ?: throw StoreException(NOT_A_FOLDER_MESSAGE)

            val rootHtml = fetchFolder(folderId)
            val rootEntries = DriveFolderListing.parse(rootHtml)
            val name = DriveFolderListing.parseFolderName(rootHtml) ?: DEFAULT_NAME

            val files = mutableListOf<DriveFile>()
            collect(rootEntries, emptyList(), 0, mutableSetOf(folderId), files)

            DriveManifestBuilder.build(name, files)
        }

    private suspend fun collect(
        entries: List<DriveEntry>,
        path: List<String>,
        depth: Int,
        visited: MutableSet<String>,
        out: MutableList<DriveFile>,
    ) {
        for (entry in entries) {
            if (!entry.isFolder) {
                out += DriveFile(id = entry.id, name = entry.name, folderPath = path)
                continue
            }
            if (depth >= MAX_DEPTH) continue
            if (!visited.add(entry.id)) continue

            val children =
                try {
                    DriveFolderListing.parse(fetchFolder(entry.id))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    // One unreadable subfolder must not cost the user the whole catalog.
                    continue
                }
            collect(children, path + entry.name, depth + 1, visited, out)
        }
    }

    private fun fetchFolder(folderId: String): String {
        val request =
            Request
                .Builder()
                .url("$EMBEDDED_FOLDER_VIEW_BASE$folderId#list")
                .header("Accept", "text/html")
                .header("User-Agent", USER_AGENT)
                .build()

        val html =
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw StoreException("Google Drive respondió HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }

        if (LISTING_MARKER !in html) throw StoreException(NOT_LISTABLE_MESSAGE)
        return html
    }

    private companion object {
        const val EMBEDDED_FOLDER_VIEW_BASE = "https://drive.google.com/embeddedfolderview?id="
        const val LISTING_MARKER = "flip-contents"
        const val DEFAULT_NAME = "Google Drive"
        const val MAX_DEPTH = 3
        const val USER_AGENT = "RetroCat/1.0 (Android)"

        const val NOT_A_FOLDER_MESSAGE = "El enlace no apunta a una carpeta de Google Drive"
        const val NOT_LISTABLE_MESSAGE =
            "No se pudo leer la carpeta. Compártela como \"cualquier persona con el enlace\"."
    }
}

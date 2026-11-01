package com.swordfish.lemuroid.app.mobile.feature.store

/**
 * One entry of a public Google Drive folder listing.
 *
 * [id] is the Drive file or folder id. It is stable across rescans (unlike the name), which is what
 * lets the store remember a downloaded game and lets a rescan tell "already there" from "new".
 */
data class DriveEntry(
    val id: String,
    val name: String,
    val isFolder: Boolean,
)

/**
 * A file found somewhere under a Drive folder, together with the chain of folders containing it.
 *
 * The [folderPath] is kept because some extensions are ambiguous (`iso` is PlayStation, PSP or
 * Dreamcast) and the folder name is the only hint available to decide the system.
 */
data class DriveFile(
    val id: String,
    val name: String,
    /** Folder names from the catalog root down to the file's own folder. Empty at the root. */
    val folderPath: List<String>,
)

/**
 * Parser for the server rendered listing that Google Drive returns for
 * `https://drive.google.com/embeddedfolderview?id=<folder>#list`.
 *
 * Google exposes no unauthenticated JSON API to list a folder shared with "anyone with the link",
 * but that endpoint renders the whole listing (a folder with a thousand files included) as plain
 * HTML. Parsing it is what lets the store turn a plain folder link into a catalog with no OAuth
 * client, no API key and no hand written manifest.
 *
 * The markup is read defensively so a layout change degrades instead of crashing: entries are
 * located by their `flip-entry` block and each field is matched on its own, so an added attribute or
 * an unknown block does not take down the rest of the listing.
 */
object DriveFolderListing {
    fun parse(html: String): List<DriveEntry> {
        if (html.isBlank()) return emptyList()

        val entries = mutableListOf<DriveEntry>()
        // The first chunk is everything before the first entry: head, styles and opening tags.
        for (chunk in html.split(ENTRY_START).drop(1)) {
            val id = ENTRY_ID.find(chunk)?.groupValues?.get(1)?.trim().orEmpty()
            if (id.isEmpty()) continue

            val rawName = ENTRY_TITLE.find(chunk)?.groupValues?.get(1) ?: continue
            val name = decodeHtmlEntities(rawName).trim()
            if (name.isEmpty()) continue

            entries += DriveEntry(id = id, name = name, isFolder = FOLDER_LINK in chunk)
        }
        return entries
    }

    /**
     * The folder name, taken from the page title (`gba` for a folder called `gba`).
     *
     * Only used as a fallback: the URL is what identifies the folder.
     */
    fun parseFolderName(html: String): String? =
        FOLDER_TITLE
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.let(::decodeHtmlEntities)
            ?.trim()
            ?.ifEmpty { null }

    /**
     * Extracts the folder id from any of the shapes a Drive folder link can take.
     *
     * Accepts `.../drive/folders/<id>`, the query form `...?id=<id>` and a bare id, so the user can
     * paste whatever the "share" dialog copied without trimming it by hand.
     */
    fun folderIdFrom(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null

        FOLDER_ID_IN_PATH.find(trimmed)?.let { return it.groupValues[1] }
        FOLDER_ID_IN_QUERY.find(trimmed)?.let { return it.groupValues[1] }

        val looksLikeBareId = trimmed.none { it == '/' || it == '?' || it.isWhitespace() }
        if (looksLikeBareId && trimmed.length in MIN_BARE_ID..MAX_BARE_ID) return trimmed

        return null
    }

    private const val ENTRY_START = "<div class=\"flip-entry\" "
    private const val FOLDER_LINK = "drive.google.com/drive/folders/"

    private val ENTRY_ID = Regex("id=\"entry-([^\"]+)\"")
    private val ENTRY_TITLE = Regex("<div class=\"flip-entry-title\">([^<]*)</div>")
    private val FOLDER_TITLE = Regex("<title>([^<]*)</title>", RegexOption.IGNORE_CASE)
    private val FOLDER_ID_IN_PATH = Regex("/folders/([A-Za-z0-9_-]{10,})")
    private val FOLDER_ID_IN_QUERY = Regex("[?&]id=([A-Za-z0-9_-]{10,})")

    private const val MIN_BARE_ID = 10
    private const val MAX_BARE_ID = 200

    private const val MAX_ENTITY_LENGTH = 12
    private const val MIN_CODE_POINT = 0x20
    private const val MAX_CODE_POINT = 0x10FFFF

    private val NAMED_ENTITIES =
        mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
        )

    /** Minimal HTML entity decoder: file names carry quotes and ampersands, which Drive escapes. */
    private fun decodeHtmlEntities(text: String): String {
        if ('&' !in text) return text

        val output = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val current = text[index]
            if (current != '&') {
                output.append(current)
                index++
                continue
            }

            val end = text.indexOf(';', index + 1)
            if (end < 0 || end - index > MAX_ENTITY_LENGTH) {
                output.append(current)
                index++
                continue
            }

            val decoded = decodeEntity(text.substring(index + 1, end))
            if (decoded == null) {
                output.append(current)
                index++
            } else {
                output.append(decoded)
                index = end + 1
            }
        }
        return output.toString()
    }

    private fun decodeEntity(entity: String): String? {
        if (entity.isEmpty()) return null

        if (entity.startsWith("#x") || entity.startsWith("#X")) {
            return entity.substring(2).toIntOrNull(16)?.let(::codePointToString)
        }
        if (entity.startsWith("#")) {
            return entity.substring(1).toIntOrNull()?.let(::codePointToString)
        }
        return NAMED_ENTITIES[entity]
    }

    private fun codePointToString(codePoint: Int): String? =
        if (codePoint in MIN_CODE_POINT..MAX_CODE_POINT) {
            String(Character.toChars(codePoint))
        } else {
            null
        }
}

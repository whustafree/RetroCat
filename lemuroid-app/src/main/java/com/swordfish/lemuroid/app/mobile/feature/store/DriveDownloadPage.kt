package com.swordfish.lemuroid.app.mobile.feature.store

/**
 * The page Google Drive serves instead of a file it refuses to hand over directly (a big ROM, an
 * ISO) when asking for a confirmation token.
 *
 * It is plain HTML, so it is parsed here as a unit rather than inside the download loop, which keeps
 * the downloader about streaming and makes the fragile part covered by tests.
 */
internal object DriveDownloadPage {
    private const val DRIVE_HOST = "drive.google.com"
    private const val DRIVE_USERCONTENT_HOST = "drive.usercontent.google.com"
    private const val HTML_CONTENT_TYPE = "text/html"
    private const val DOWNLOAD_ENDPOINT = "https://drive.usercontent.google.com/download"

    private val LINK = Regex("""https://drive\.usercontent\.google\.com/download\?[^"'<>\s]+""")
    private val INPUT_TAG = Regex("<input[^>]*>", RegexOption.IGNORE_CASE)
    private val NAME_ATTRIBUTE = Regex("""name="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val VALUE_ATTRIBUTE = Regex("""value="([^"]*)"""", RegexOption.IGNORE_CASE)

    /** Whether this response is the warning page rather than the file itself. */
    fun isWarning(
        url: String,
        contentType: String?,
    ): Boolean =
        (url.contains(DRIVE_HOST) || url.contains(DRIVE_USERCONTENT_HOST)) &&
            contentType?.contains(HTML_CONTENT_TYPE) == true

    /** The URL of the real file, read out of the warning page, or `null` when it cannot be found. */
    fun realUrl(html: String): String? {
        LINK.find(html)?.let { return it.value.unescapeHtml() }

        val fields =
            INPUT_TAG
                .findAll(html)
                .mapNotNull { tag ->
                    val name = NAME_ATTRIBUTE.find(tag.value)?.groupValues?.get(1) ?: return@mapNotNull null
                    val value = VALUE_ATTRIBUTE.find(tag.value)?.groupValues?.get(1) ?: return@mapNotNull null
                    name to value.unescapeHtml()
                }.toMap()

        val id = fields["id"] ?: return null
        val parameters =
            buildList {
                add("id=$id")
                add("export=download")
                fields["confirm"]?.let { add("confirm=$it") }
                fields["uuid"]?.let { add("uuid=$it") }
            }
        return "$DOWNLOAD_ENDPOINT?${parameters.joinToString("&")}"
    }

    private fun String.unescapeHtml(): String =
        replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "\"")
}

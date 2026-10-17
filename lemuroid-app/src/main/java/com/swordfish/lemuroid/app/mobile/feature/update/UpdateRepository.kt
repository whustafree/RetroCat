package com.swordfish.lemuroid.app.mobile.feature.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** A published build the app can install over itself. */
data class UpdateRelease(
    /** The tag, as written by the release, e.g. `v1.19.4`. */
    val version: String,
    /** The release page, for anyone who would rather read it in a browser. */
    val pageUrl: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/** One entry of the GitHub releases list, with only the fields that matter here. */
@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
) {
    /** Null unless the tag looks like a version and the release carries an APK. */
    fun toRelease(): UpdateRelease? {
        if (UpdateVersions.parse(tagName).isEmpty()) return null

        val apk = assets.firstOrNull { it.name.endsWith(APK_SUFFIX, ignoreCase = true) } ?: return null

        return UpdateRelease(
            version = tagName,
            pageUrl = htmlUrl,
            apkUrl = apk.downloadUrl,
            sizeBytes = apk.size,
        )
    }

    companion object {
        private const val APK_SUFFIX = ".apk"
    }
}

/** One file attached to a release. */
@Serializable
internal data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L,
)

/**
 * Looks for a newer build of this fork on GitHub, which is where the APKs are published.
 *
 * The list of releases is read rather than `releases/latest`, because the workflow also keeps a
 * moving `apk-latest` release for people who want the current APK without a version in the name.
 * That tag is not a version, so it is skipped here: what counts is the newest tag that parses as
 * one and carries an APK.
 */
class UpdateRepository(
    private val okHttpClient: OkHttpClient,
) {
    /** The newest published release that has an APK, or null when GitHub cannot be read. */
    suspend fun latest(): UpdateRelease? = withContext(Dispatchers.IO) { fetchLatest() }

    private fun fetchLatest(): UpdateRelease? {
        val request =
            Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

        val body =
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string() ?: return null
            }

        val releases =
            runCatching { json.decodeFromString(ListSerializer(GithubRelease.serializer()), body) }
                .getOrNull()
                ?: return null

        return releases.asSequence()
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { it.toRelease() }
            .maxByOrNull { versionKey(it.version) }
    }

    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/whustafree/RetroCat/releases"

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        /** `v1.19.4` sorts above `v1.19.3`, with up to four parts, which is plenty. */
        private fun versionKey(version: String): Long =
            UpdateVersions.parse(version).take(4).fold(0L) { total, part -> total * 1000 + part }
    }
}

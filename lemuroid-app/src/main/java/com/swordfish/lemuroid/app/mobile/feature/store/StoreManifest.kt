package com.swordfish.lemuroid.app.mobile.feature.store

import com.swordfish.lemuroid.lib.library.SystemID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Remote catalog for the store.
 *
 * The manifest is a plain JSON document the user publishes once (Drive, GitHub, any static
 * host). RetroCat only needs to read it: it does not walk the remote folder, so the catalog can
 * carry metadata the files themselves do not (title, cover, screenshots, size, hash).
 *
 * Every field except the id/title/system/url quartet is optional so a hand written manifest stays
 * usable, and unknown keys are ignored so the format can grow without breaking older clients.
 */
@Serializable
data class StoreManifest(
    val version: Int = 1,
    val name: String = "",
    val games: List<StoreGame> = emptyList(),
)

@Serializable
data class StoreGame(
    /** Stable identifier, used to tell whether a game is already downloaded. */
    val id: String,
    val title: String,
    /** [SystemID.dbname] of the target system, for example `snes` or `psx`. */
    val system: String,
    /** Direct download URL of the ROM. */
    val url: String,
    /** File name to store the downloaded ROM as. Strongly recommended, see [resolvedFileName]. */
    val fileName: String? = null,
    val developer: String? = null,
    val releaseYear: Int? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val sizeBytes: Long? = null,
    /** Lowercase hex sha256 of the ROM, verified after the download when present. */
    val sha256: String? = null,
)

/** A store shelf: every game of one system, ready to be rendered as a row of covers. */
data class StoreGroup(
    val system: SystemID,
    val games: List<StoreGame>,
)

private val SYSTEMS_BY_DB_NAME: Map<String, SystemID> = SystemID.values().associateBy { it.dbname }

/** Characters that are unsafe in a file name on every platform the app runs on. */
private val UNSAFE_FILE_CHARS = Regex("[\\\\/:*?\"<>|]")

/**
 * The system this entry targets, or `null` when the manifest names a system this build does not
 * know. Matching ignores case and surrounding spaces because manifests are hand written.
 */
fun StoreGame.resolveSystemId(): SystemID? = SYSTEMS_BY_DB_NAME[system.trim().lowercase()]

/**
 * File name the ROM should be saved as.
 *
 * A correct extension is what makes the scanner recognise the system, so the manifest should always
 * carry [fileName]. The fallbacks exist only to keep a sloppy manifest usable: an explicit name
 * wins, then a file-looking last path segment of the URL, then the sanitised title.
 */
fun StoreGame.resolvedFileName(): String {
    val explicit = fileName?.trim().orEmpty()
    if (explicit.isNotEmpty()) return explicit

    val fromUrl = url.substringAfterLast('/').substringBefore('?').substringBefore('#').trim()
    if (fromUrl.isNotEmpty() && fromUrl.contains('.')) return fromUrl

    val base = title.trim().ifEmpty { id }.replace(UNSAFE_FILE_CHARS, "_")
    return base.ifEmpty { id }
}

/** Games this build can actually run, in manifest order. */
fun StoreManifest.knownGames(): List<StoreGame> = games.filter { it.resolveSystemId() != null }

/**
 * Games grouped by system for the shelf layout. Unknown systems are dropped rather than shown as
 * an unplayable row, and shelves are ordered like the systems screen (by [SystemID] ordinal).
 */
fun StoreManifest.groups(): List<StoreGroup> =
    games.asSequence()
        .mapNotNull { game -> game.resolveSystemId()?.let { system -> system to game } }
        .groupBy({ it.first }, { it.second })
        .map { (system, systemGames) -> StoreGroup(system, systemGames.sortedBy { it.title.lowercase() }) }
        .sortedBy { it.system.ordinal }
        .toList()

/** A problem found in a manifest, surfaced so a misconfigured store can be explained to the user. */
data class StoreManifestIssue(
    val gameId: String?,
    val message: String,
)

/**
 * Sanity check for a manifest, used before showing a catalog and by the manifest authoring tests.
 * Returns an empty list when the manifest is usable.
 */
fun StoreManifest.validate(): List<StoreManifestIssue> {
    val issues = mutableListOf<StoreManifestIssue>()
    val seenIds = mutableSetOf<String>()

    games.forEachIndexed { index, game ->
        val label = game.id.ifBlank { "#$index" }

        if (game.id.isBlank()) {
            issues += StoreManifestIssue(null, "Game #$index has no id")
        } else if (!seenIds.add(game.id)) {
            issues += StoreManifestIssue(game.id, "Duplicate id \"$label\"")
        }
        if (game.title.isBlank()) issues += StoreManifestIssue(label, "Missing title")
        if (game.url.isBlank()) issues += StoreManifestIssue(label, "Missing download url")
        if (game.resolveSystemId() == null) {
            issues += StoreManifestIssue(label, "Unknown system \"${game.system}\"")
        }
        if (game.sizeBytes != null && game.sizeBytes < 0) {
            issues += StoreManifestIssue(label, "Negative size")
        }
        val hash = game.sha256?.trim().orEmpty()
        if (hash.isNotEmpty() && (hash.length != SHA256_LENGTH || hash.any { it !in HEX_DIGITS })) {
            issues += StoreManifestIssue(label, "Malformed sha256")
        }
    }

    return issues
}

private const val SHA256_LENGTH = 64
private const val HEX_DIGITS = "0123456789abcdef"

/** Small wrapper around the JSON codec so the parsing rules live in one place. */
object StoreManifestCodec {
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun parse(text: String): StoreManifest = json.decodeFromString(StoreManifest.serializer(), text)

    fun serialize(manifest: StoreManifest): String = json.encodeToString(StoreManifest.serializer(), manifest)
}

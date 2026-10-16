package com.swordfish.lemuroid.app.mobile.feature.store

import com.swordfish.lemuroid.lib.library.GameSystem
import java.util.Locale

/**
 * Builds a [StoreManifest] out of a plain Google Drive folder.
 *
 * A folder shared with "anyone with the link" becomes a catalog with no manifest to write and no
 * client id to register: the ROMs and their covers are recognised by extension and paired by name,
 * and the system is inferred from the extension first and from the folder names second (a folder
 * called `gba` marks its files as Game Boy Advance), which is what rescues formats that are
 * ambiguous on their own such as `iso` or `zip`.
 *
 * The result is rebuilt in memory on every rescan, so dropping a file into Drive is all it takes for
 * it to show up in the store.
 *
 * The extension lists are taken from the app's own [GameSystem] table instead of being hardcoded, so
 * the store can never offer a file the emulator would refuse to open.
 */
object DriveManifestBuilder {
    fun build(
        name: String,
        files: List<DriveFile>,
    ): StoreManifest {
        val covers =
            files
                .filter { it.extension() in IMAGE_EXTENSIONS }
                .associateBy { it.normalizedKey() }

        val games =
            files
                .filter { it.extension() in romExtensions }
                .mapNotNull { rom ->
                    val system = rom.resolveSystem() ?: return@mapNotNull null
                    StoreGame(
                        id = rom.id,
                        title = rom.title(),
                        system = system,
                        url = downloadUrl(rom.id),
                        fileName = rom.name,
                        coverUrl = covers[rom.normalizedKey()]?.let { coverUrl(it.id) },
                    )
                }
                .sortedBy { it.title.lowercase(Locale.US) }

        return StoreManifest(version = MANIFEST_VERSION, name = name, games = games)
    }

    private const val MANIFEST_VERSION = 1

    private fun downloadUrl(id: String): String = "https://drive.google.com/uc?export=download&id=$id"

    /** Google's thumbnail endpoint, which serves a resized copy of a public image. */
    private fun coverUrl(id: String): String = "https://drive.google.com/thumbnail?id=$id&sz=w$COVER_WIDTH"

    private const val COVER_WIDTH = 512
}

/** `zip` is a standalone arcade romset, but `cue`/`m3u` only point at files the store cannot fetch. */
private val EXCLUDED_ROM_EXTENSIONS = setOf("cue", "m3u")

/** Every extension the app can load, straight from its own system table. */
private val romExtensions: Set<String> by lazy {
    GameSystem
        .getSupportedExtensions()
        .map { it.lowercase(Locale.US) }
        .filterNot { it in EXCLUDED_ROM_EXTENSIONS }
        .toSet()
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

/** `extension -> SystemID.dbname`, derived from [GameSystem] so it can never drift from the app. */
private val EXTENSION_SYSTEMS: Map<String, String> by lazy {
    buildMap {
        for (system in GameSystem.all()) {
            for (extension in system.uniqueExtensions) {
                putIfAbsent(extension.lowercase(Locale.US), system.id.dbname)
            }
        }
    }
}

/** Folder names that name a system more clearly than its dbname does. */
private val EXTRA_FOLDER_HINTS =
    mapOf(
        "gameboyadvance" to "gba",
        "gameboycolor" to "gbc",
        "supernintendo" to "snes",
        "megadrive" to "md",
        "genesis" to "md",
        "mastersystem" to "sms",
        "gamegear" to "gg",
        "nintendo64" to "n64",
        "nintendods" to "nds",
        "nintendo3ds" to "3ds",
        "pcengine" to "pce",
        "playstation" to "psx",
        "ps1" to "psx",
        "wonderswan" to "ws",
        "wonderswancolor" to "wsc",
    )

private val FOLDER_SYSTEM_HINTS: Map<String, String> by lazy {
    val hints = mutableMapOf<String, String>()
    for (system in GameSystem.all()) {
        hints.putIfAbsent(system.id.dbname.lowercase(Locale.US), system.id.dbname)
    }
    hints.putAll(EXTRA_FOLDER_HINTS)
    hints
}

private fun DriveFile.extension(): String = name.substringAfterLast('.', "").lowercase(Locale.US)

private fun DriveFile.title(): String = name.substringBeforeLast('.').ifBlank { name }

/**
 * Name without extension, without the leading `- ` that cover files in most packs carry, and
 * without any separator, so `- 007 - Everything or Nothing (U) (M3).png` matches the ROM.
 */
private fun DriveFile.normalizedKey(): String =
    name
        .substringBeforeLast('.')
        .removePrefix("-")
        .lowercase(Locale.US)
        .filter { it.isLetterOrDigit() }

private fun DriveFile.resolveSystem(): String? {
    EXTENSION_SYSTEMS[extension()]?.let { return it }

    // Ambiguous extension: trust the deepest folder name that names a system.
    for (folder in folderPath.asReversed()) {
        FOLDER_SYSTEM_HINTS[folder.trim().lowercase(Locale.US)]?.let { return it }
    }
    return null
}

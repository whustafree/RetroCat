package com.swordfish.lemuroid.app.mobile.feature.store

/**
 * The queue of downloads, counted for the panel that sits above the catalog.
 *
 * A bulk download of a whole system is the store's normal use, so "what is going on" has to be
 * answerable at a glance instead of by scrolling a thousand rows looking for the ones with a bar.
 */
data class StoreDownloadSummary(
    val queued: Int,
    val downloading: Int,
    val failed: Int,
) {
    val total: Int
        get() = queued + downloading + failed

    /** True while there is something worth a panel: running, waiting or given up on. */
    val active: Boolean
        get() = total > 0
}

fun StoreUiState.downloadSummary(): StoreDownloadSummary {
    var queued = 0
    var downloading = 0
    var failed = 0

    downloads.values.forEach { download ->
        when (download.status) {
            StoreDownloadStatus.QUEUED -> queued++
            StoreDownloadStatus.DOWNLOADING -> downloading++
            StoreDownloadStatus.FAILED -> failed++
            StoreDownloadStatus.DOWNLOADED -> Unit
        }
    }

    return StoreDownloadSummary(queued, downloading, failed)
}

/**
 * The games that gave up, in catalog order.
 *
 * This is what "retry the failed ones" hands back to WorkManager: a hundred downloads on a bad
 * connection should not mean a hundred taps.
 */
fun StoreUiState.failedDownloads(): List<StoreGame> {
    val failed = downloads.filterValues { it.status == StoreDownloadStatus.FAILED }.keys
    if (failed.isEmpty()) return emptyList()

    return groups.flatMap { it.games }.filter { it.id in failed }
}

/**
 * Whether the games about to be queued fit on the device.
 *
 * Only the sizes the catalog announced are counted, so the answer is a lower bound rather than a
 * guess. With no sizes at all, or with no idea of the free space, there is nothing honest to say
 * and the whole check is skipped instead of warning about a number that means nothing.
 */
data class StoreSpaceCheck(
    val neededBytes: Long,
    val freeBytes: Long,
) {
    val fits: Boolean
        get() = neededBytes <= freeBytes
}

fun storeSpaceCheck(
    games: List<StoreGame>,
    freeBytes: Long,
): StoreSpaceCheck? {
    val needed = games.sumOf { it.sizeBytes ?: 0L }
    if (needed <= 0L || freeBytes <= 0L) return null

    return StoreSpaceCheck(needed, freeBytes)
}

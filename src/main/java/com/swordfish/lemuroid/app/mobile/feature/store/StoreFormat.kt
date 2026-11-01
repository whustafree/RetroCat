package com.swordfish.lemuroid.app.mobile.feature.store

import java.util.Locale

/**
 * Human readable byte size for the store, e.g. `1.5 MB`.
 *
 * Kept as a pure function so the rounding (which is easy to get wrong at the unit boundaries) is
 * covered by a JVM test rather than by eyeballing the UI.
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return ""
    if (bytes < KILOBYTE) return "$bytes B"

    var value = bytes.toDouble() / KILOBYTE
    var unitIndex = 0
    while (value >= KILOBYTE && unitIndex < UNITS.lastIndex) {
        value /= KILOBYTE
        unitIndex++
    }

    return String.format(Locale.US, "%.1f %s", value, UNITS[unitIndex])
}

private const val KILOBYTE = 1024.0
private val UNITS = listOf("KB", "MB", "GB", "TB")

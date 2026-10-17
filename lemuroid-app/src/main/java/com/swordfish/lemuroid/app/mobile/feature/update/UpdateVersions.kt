package com.swordfish.lemuroid.app.mobile.feature.update

/**
 * Version numbers as this fork and GitHub write them, in a form that can be compared.
 *
 * The app calls itself `1.19.3-retrocat` and a release is tagged `v1.19.3`, so the string is not
 * used as-is: everything from the first character that is not a digit or a dot is dropped. That
 * leaves `1.19.3`, which is what actually gets compared, part by part, with missing parts read as
 * zero.
 */
object UpdateVersions {
    /** `"v1.19.4"`, `"1.19.4-retrocat"` and `"1.19"` become `[1, 19, 4]` and `[1, 19]`. */
    fun parse(text: String): List<Int> {
        val digits =
            text.trim()
                .removePrefix("v")
                .removePrefix("V")
                .takeWhile { it.isDigit() || it == '.' }
                .trim('.')

        if (digits.isEmpty()) return emptyList()

        return digits.split('.').map { part -> part.toIntOrNull() ?: return emptyList() }
    }

    /**
     * Whether [remote] is newer than [local].
     *
     * A tag that is not a version at all (a branch name, an empty string) is never newer: it is
     * better to say "you are up to date" than to offer an install that makes no sense.
     */
    fun isNewer(
        remote: String,
        local: String,
    ): Boolean {
        val remoteParts = parse(remote)
        val localParts = parse(local)
        if (remoteParts.isEmpty() || localParts.isEmpty()) return false

        val size = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until size) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val localPart = localParts.getOrElse(index) { 0 }
            if (remotePart != localPart) return remotePart > localPart
        }

        return false
    }
}

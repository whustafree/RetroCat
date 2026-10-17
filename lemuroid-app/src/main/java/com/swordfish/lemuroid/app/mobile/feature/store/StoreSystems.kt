package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID

/**
 * How a system is named on screen and in the search box, e.g. "GBA".
 *
 * The short name, because that is what the rows show and what a person types: with a catalog of a
 * thousand entries, "gba" is how one asks for that shelf without scrolling to it.
 *
 * The list and "select all" both build this name, so they have to build it the same way or the
 * search would show games that selecting everything then ignores.
 */
fun storeSystemName(
    context: Context,
    system: SystemID,
): String =
    runCatching {
        context.getString(GameSystem.findById(system.dbname).shortTitleResId)
    }.getOrDefault(system.dbname)

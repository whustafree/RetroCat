package com.swordfish.lemuroid.app.mobile.feature.catalog

import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import kotlin.math.floor

/**
 * How large a catalog tile should be.
 *
 * [minTileWidth] is the width a single tile is allowed to shrink to before the catalog drops a
 * column, so the column count is derived from the space actually available. Deriving it this way
 * instead of hardcoding a column count is what keeps the 3:4 covers inside the viewport when the
 * device is rotated.
 */
enum class GridDensity(
    val minTileWidth: Dp,
    @StringRes val labelRes: Int,
) {
    COMPACT(104.dp, R.string.catalog_density_compact),
    REGULAR(150.dp, R.string.catalog_density_regular),
    COMFORTABLE(200.dp, R.string.catalog_density_comfortable),
}

/** Gap and outer padding of the grid. */
internal val TILE_SPACING = 10.dp
internal val CATALOG_HORIZONTAL_PADDING = 24.dp
internal val CAROUSEL_DETAILS_HEIGHT = 64.dp

/**
 * Leading gap of the landscape carousel.
 *
 * The row is anchored to the start, not centred, so this is the only thing standing between the
 * first cover and the edge of the screen. It is deliberately smaller than a centred row's total
 * inset: a centred row spends half a screen of padding on the trailing side, which is what used to
 * leave the shelf looking like it started two slots in.
 */
internal val CAROUSEL_START_PADDING = 20.dp

/**
 * Vertical band reserved at the top of the carousel for the "continue playing" shelf.
 *
 * The shelf is a 168x108 card plus its caption, so it needs a fixed reservation rather than an
 * overlay: the covers are sized from the height that is left, and without the reservation the
 * shelf drew on top of the first row of artwork.
 */
internal val CONTINUE_PLAYING_BAND_HEIGHT = 140.dp

private val CAROUSEL_BOTTOM_MARGIN = 16.dp
private const val COVER_ASPECT = 3f / 4f
private const val MAX_COLUMNS = 12
private const val HERO_PANEL_FRACTION = 0.34f
private val MIN_HERO_PANEL_WIDTH = 200.dp
private val MAX_HERO_PANEL_WIDTH = 420.dp

/**
 * How many columns fit in [availableWidth] at [minTileWidth].
 *
 * This is the single source of truth for the grid layout, extracted so it can be asserted on the
 * JVM. The bug it guards against was a fixed column count that overflowed the viewport once the
 * device was rotated, which pushed the bottom of the covers off screen.
 */
internal fun catalogColumnCount(
    availableWidth: Dp,
    minTileWidth: Dp,
): Int {
    val usable = (availableWidth - CATALOG_HORIZONTAL_PADDING).coerceAtLeast(minTileWidth)

    return floor((usable + TILE_SPACING) / (minTileWidth + TILE_SPACING))
        .toInt()
        .coerceIn(1, MAX_COLUMNS)
}

/**
 * Cover size for the landscape carousel: fitted to the height left over by the details strip
 * instead of being derived from the width, so a short landscape screen never crops the artwork.
 */
internal fun carouselCoverSize(availableHeight: Dp): Pair<Dp, Dp> {
    val coverHeight =
        (availableHeight - CAROUSEL_DETAILS_HEIGHT - CAROUSEL_BOTTOM_MARGIN).coerceAtLeast(96.dp)
    // A 3:4 portrait cover: the width is the height multiplied by 0.75. Dividing by the ratio
    // instead would flip the artwork to 4:3 landscape. The lower bound only matters on a viewport
    // so short that the cover would otherwise become unreadably small.
    val coverWidth = (coverHeight * COVER_ASPECT).coerceAtLeast(96.dp)

    return coverWidth to coverHeight
}

/**
 * How much of the landscape width the hero panel claims for the focused game's title, metadata and
 * play button.
 *
 * The artwork is the hero, so the panel takes what is left after the cover plus room for two
 * neighbouring covers, and is capped so a tablet does not stretch it into a wall of text.
 */
internal fun heroPanelWidth(availableWidth: Dp): Dp =
    (availableWidth * HERO_PANEL_FRACTION)
        .coerceIn(MIN_HERO_PANEL_WIDTH, MAX_HERO_PANEL_WIDTH)

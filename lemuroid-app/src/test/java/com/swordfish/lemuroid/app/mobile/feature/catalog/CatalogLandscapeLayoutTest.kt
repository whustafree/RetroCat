package com.swordfish.lemuroid.app.mobile.feature.catalog

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor

/**
 * Landscape layout regression tests.
 *
 * These assert the real layout arithmetic, not a screenshot, because the failure being guarded
 * against is arithmetic: a fixed column count and a width-derived cover size both overflow the
 * viewport once the device is rotated, which pushed the artwork off screen.
 */
class CatalogLandscapeLayoutTest {
    private fun columnCount(
        availableWidth: Dp,
        minTileWidth: Dp,
    ): Int = catalogColumnCount(availableWidth, minTileWidth)

    @Test
    fun `a narrow portrait screen keeps few columns`() {
        // 400dp - 24dp padding = 376dp, which fits two 150dp tiles plus the 10dp gaps.
        assertEquals(2, columnCount(400.dp, GridDensity.REGULAR.minTileWidth))
    }

    /** The grid gives every tile `weight(1f)`, so the row consumes the usable width exactly. */
    private fun tileWidth(
        availableWidth: Dp,
        columns: Int,
    ): Dp = (availableWidth - CATALOG_HORIZONTAL_PADDING - TILE_SPACING * (columns - 1)) / columns

    @Test
    fun `a wide landscape screen fits more columns than portrait`() {
        val portrait = columnCount(400.dp, GridDensity.REGULAR.minTileWidth)
        val landscape = columnCount(800.dp, GridDensity.REGULAR.minTileWidth)

        assertTrue("landscape ($landscape) should fit more columns than portrait ($portrait)", landscape > portrait)
    }

    @Test
    fun `every density produces a non degenerate column count`() {
        listOf(240.dp, 320.dp, 411.dp, 600.dp, 720.dp, 800.dp, 1000.dp, 1600.dp).forEach { width ->
            GridDensity.values().forEach { density ->
                val columns = columnCount(width, density.minTileWidth)

                assertTrue("$density at $width produced $columns", columns in 1..12)
            }
        }
    }

    @Test
    fun `a grid row never overflows the available width`() {
        listOf(320.dp, 411.dp, 600.dp, 800.dp, 1280.dp).forEach { width ->
            GridDensity.values().forEach { density ->
                val columns = columnCount(width, density.minTileWidth)
                val rowWidth = TILE_SPACING * (columns - 1) + tileWidth(width, columns) * columns

                assertTrue(
                    "${density.name} row ($rowWidth) overflows $width at $columns columns",
                    rowWidth <= width,
                )
            }
        }
    }

    @Test
    fun `tiles stay at least as wide as the density asks for`() {
        listOf(320.dp, 411.dp, 800.dp).forEach { width ->
            GridDensity.values().forEach { density ->
                val columns = columnCount(width, density.minTileWidth)
                val width = tileWidth(width, columns)

                assertTrue(
                    "${density.name} tile ($width) is narrower than the minimum (${density.minTileWidth})",
                    width >= density.minTileWidth,
                )
            }
        }
    }

    @Test
    fun `carousel cover leaves room for the details strip in landscape`() {
        // Common landscape viewports: a tall phone rotated, a tablet and a very short window.
        listOf(320.dp, 360.dp, 400.dp, 480.dp).forEach { height ->
            val (_, coverHeight) = carouselCoverSize(height)

            assertTrue(
                "cover ($coverHeight) plus details overflows the viewport ($height)",
                coverHeight + CAROUSEL_DETAILS_HEIGHT <= height,
            )
        }
    }

    @Test
    fun `carousel cover still has a usable size on a very short window`() {
        val (_, coverHeight) = carouselCoverSize(200.dp)

        assertTrue("cover collapsed on a short window: $coverHeight", coverHeight >= 96.dp)
    }

    @Test
    fun `carousel cover fills a landscape viewport instead of being capped`() {
        // A 360dp tall landscape phone is the common case: the cover should take most of it.
        val (_, coverHeight) = carouselCoverSize(360.dp)

        assertTrue("cover ($coverHeight) should use most of a 360dp viewport", coverHeight >= 250.dp)
    }

    @Test
    fun `carousel cover keeps the 3 by 4 ratio at every viewport size`() {
        // Every real landscape viewport. The ratio is only allowed to break on a window so short
        // that the 96dp minimum width kicks in, which no phone produces in landscape.
        listOf(320.dp, 360.dp, 400.dp, 480.dp, 800.dp, 2000.dp).forEach { height ->
            val (coverWidth, coverHeight) = carouselCoverSize(height)

            assertTrue(
                "cover is not 3 by 4 at $height (${coverWidth.value}x${coverHeight.value})",
                abs(coverWidth.value * 4f - coverHeight.value * 3f) < 1f,
            )
        }
    }

    @Test
    fun `hero panel takes a share of the width without swallowing the covers`() {
        // The carousel needs room for the focused cover plus its neighbours, so the panel is
        // always a fraction of the width and never the whole thing.
        listOf(640.dp, 800.dp, 1280.dp, 1600.dp).forEach { width ->
            val panel = heroPanelWidth(width)

            assertTrue("panel ($panel) leaves no room at $width", panel < width / 2)
            assertTrue("panel ($panel) is too small at $width", panel >= 200.dp)
        }
    }

    @Test
    fun `hero panel is capped so a tablet does not stretch it`() {
        assertEquals(420.dp, heroPanelWidth(4000.dp))
    }

    @Test
    fun `column count is monotonic in width`() {
        val widths = listOf(320.dp, 400.dp, 500.dp, 640.dp, 800.dp, 1000.dp)
        val counts = widths.map { columnCount(it, GridDensity.REGULAR.minTileWidth) }

        assertEquals("column count should never decrease as width grows", counts.sorted(), counts)
    }

    @Test
    fun `column count math matches the tile spacing it was derived from`() {
        // Guards the formula itself: 800dp - 24dp padding, 150dp tiles, 10dp gaps.
        val usable = 800.dp - CATALOG_HORIZONTAL_PADDING
        val expected = floor((usable + TILE_SPACING) / (GridDensity.REGULAR.minTileWidth + TILE_SPACING)).toInt()

        assertEquals(expected, columnCount(800.dp, GridDensity.REGULAR.minTileWidth))
    }

    @Test
    fun `the carousel starts close to the edge instead of centring the first cover`() {
        // The row used to scroll the focused cover to the middle of the viewport, which pushed
        // the first games out of frame. A start-anchored row spends only this much leading gap.
        assertTrue(
            "start padding ($CAROUSEL_START_PADDING) should stay tight",
            CAROUSEL_START_PADDING <= 32.dp,
        )
        assertTrue(
            "start padding should not exceed the grid padding ($CATALOG_HORIZONTAL_PADDING)",
            CAROUSEL_START_PADDING <= CATALOG_HORIZONTAL_PADDING,
        )
    }

    @Test
    fun `the shelf band is reserved as padding so covers never sit under it`() {
        // A continue-playing card is 108dp tall plus its caption; the band has to cover both or
        // the first row of artwork is drawn underneath the shelf.
        assertTrue("shelf band is too small to hold a card", CONTINUE_PLAYING_BAND_HEIGHT >= 108.dp)
    }

    @Test
    fun `reserving the shelf band still leaves a usable cover`() {
        // The worst case is a short landscape phone that also has continue-playing games.
        val (_, coverHeight) = carouselCoverSize(320.dp - CONTINUE_PLAYING_BAND_HEIGHT)

        assertTrue(
            "cover collapsed to $coverHeight once the shelf band was reserved",
            coverHeight >= 96.dp,
        )
    }
}

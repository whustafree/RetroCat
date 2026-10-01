package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.lib.library.db.entity.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenshotUrlsTest {
    @Test
    fun `encodes spaces in the game name`() {
        val url = ScreenshotUrls.build("Nintendo - Nintendo Entertainment System", "Named_Titles", "Super Mario Bros.")

        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/" +
                "Named_Titles/Super%20Mario%20Bros..png",
            url,
        )
    }

    @Test
    fun `encodes non ascii characters`() {
        val url = ScreenshotUrls.build("Nintendo - Game Boy", "Named_Titles", "Pokémon Red")

        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy/Named_Titles/Pok%C3%A9mon%20Red.png",
            url,
        )
    }

    @Test
    fun `replaces characters that are invalid in a path`() {
        val url = ScreenshotUrls.build("Nintendo - NES", "Named_Boxarts", "Ninja/Tencho: Quest")

        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20NES/Named_Boxarts/Ninja_Tencho_%20Quest.png",
            url,
        )
    }

    @Test
    fun `derives screenshot url by swapping the boxarts segment`() {
        val derived =
            ScreenshotUrls.derive(
                game(cover = "https://thumbnails.libretro.com/Sega%20-%20Mega%20Drive/Named_Boxarts/Sonic.png"),
            )

        assertEquals(
            "https://thumbnails.libretro.com/Sega%20-%20Mega%20Drive/Named_Titles/Sonic.png",
            derived,
        )
    }

    @Test
    fun `derivation returns null without a cover url`() {
        assertNull(ScreenshotUrls.derive(game(cover = null)))
    }

    @Test
    fun `derivation returns null when the cover is not a boxart url`() {
        val derived =
            ScreenshotUrls.derive(
                game(cover = "https://example.com/some/local/cover.png"),
            )

        assertNull(derived)
    }

    private fun game(cover: String?) =
        Game(
            fileName = "Sonic.md",
            fileUri = "content://games/1",
            title = "Sonic the Hedgehog",
            systemId = "megadrive",
            developer = null,
            coverFrontUrl = cover,
            screenshotUrl = null,
            lastIndexedAt = 0L,
        )
}

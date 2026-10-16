package com.swordfish.lemuroid.app.mobile.feature.store

import com.swordfish.lemuroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest is the only part of the store the app cannot verify at runtime (the user writes it),
 * so its parsing, system resolution and validation are covered here as plain JVM tests.
 */
class StoreManifestTest {
    private val completeManifest =
        """
        {
          "version": 1,
          "name": "Mi tienda",
          "futureField": { "nested": true },
          "games": [
            {
              "id": "snes-mario",
              "title": "Super Mario World",
              "system": "snes",
              "url": "https://example.com/mario.sfc",
              "fileName": "Super Mario World (USA).sfc",
              "developer": "Nintendo",
              "releaseYear": 1990,
              "description": "A platformer",
              "coverUrl": "https://example.com/mario.png",
              "screenshots": ["https://example.com/mario-1.png"],
              "sizeBytes": 524288,
              "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `parses a complete manifest`() {
        val manifest = StoreManifestCodec.parse(completeManifest)

        assertEquals(1, manifest.version)
        assertEquals("Mi tienda", manifest.name)

        val game = manifest.games.single()
        assertEquals("snes-mario", game.id)
        assertEquals("Super Mario World", game.title)
        assertEquals("snes", game.system)
        assertEquals("Nintendo", game.developer)
        assertEquals(1990, game.releaseYear)
        assertEquals(1, game.screenshots.size)
        assertEquals(524288L, game.sizeBytes)
        assertEquals(SystemID.SNES, game.resolveSystemId())
    }

    @Test
    fun `ignores unknown keys and default optional fields`() {
        val manifest =
            StoreManifestCodec.parse(
                """
                {
                  "unexpected": 42,
                  "games": [
                    { "id": "nes-a", "title": "A", "system": "nes", "url": "https://example.com/a.nes" }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(1, manifest.version)
        assertEquals("", manifest.name)

        val game = manifest.games.single()
        assertNull(game.fileName)
        assertNull(game.developer)
        assertNull(game.coverUrl)
        assertNull(game.sha256)
        assertTrue(game.screenshots.isEmpty())
    }

    @Test
    fun `resolves system ids ignoring case and spaces`() {
        val game = storeGame(system = "  SNES ")

        assertEquals(SystemID.SNES, game.resolveSystemId())
    }

    @Test
    fun `unknown systems resolve to null`() {
        assertNull(storeGame(system = "dreamcast").resolveSystemId())
    }

    @Test
    fun `file name prefers the explicit name`() {
        val game = storeGame(fileName = "  Mario (USA).sfc ", url = "https://example.com/download?id=1")

        assertEquals("Mario (USA).sfc", game.resolvedFileName())
    }

    @Test
    fun `file name falls back to a file-like url segment`() {
        val game = storeGame(fileName = null, url = "https://example.com/roms/mario.sfc?raw=1")

        assertEquals("mario.sfc", game.resolvedFileName())
    }

    @Test
    fun `file name falls back to the sanitised title when the url has no extension`() {
        val game =
            storeGame(
                title = "Zelda: A Link/To the Past",
                fileName = null,
                url = "https://drive.google.com/uc?export=download&id=ABC",
            )

        assertEquals("Zelda_ A Link_To the Past", game.resolvedFileName())
    }

    @Test
    fun `groups shelves by system ordered like the systems screen`() {
        val manifest =
            manifestOf(
                storeGame(id = "snes-b", title = "b game", system = "snes"),
                storeGame(id = "nes-a", title = "a game", system = "nes"),
                storeGame(id = "snes-a", title = "a game", system = "snes"),
                storeGame(id = "dc", title = "unknown", system = "dreamcast"),
            )

        val groups = manifest.groups()

        assertEquals(listOf(SystemID.NES, SystemID.SNES), groups.map { it.system })
        assertEquals(listOf("snes-a", "snes-b"), groups[1].games.map { it.id })
    }

    @Test
    fun `known games drops entries with an unknown system`() {
        val manifest =
            manifestOf(
                storeGame(id = "good", system = "gba"),
                storeGame(id = "bad", system = "nope"),
            )

        assertEquals(listOf("good"), manifest.knownGames().map { it.id })
    }

    @Test
    fun `a valid manifest produces no issues`() {
        val manifest = StoreManifestCodec.parse(completeManifest)

        assertTrue(manifest.validate().isEmpty())
    }

    @Test
    fun `validation reports the problems a hand written manifest makes`() {
        val manifest =
            manifestOf(
                storeGame(id = "dup", title = "ok", system = "nes", url = "https://example.com/a.nes"),
                storeGame(id = "dup", title = "ok", system = "nes", url = "https://example.com/b.nes"),
                storeGame(id = "no-url", title = "ok", system = "nes", url = ""),
                storeGame(id = "bad-system", title = "ok", system = "dreamcast", url = "https://example.com/c"),
                storeGame(id = "bad-hash", title = "ok", system = "nes", url = "https://example.com/d", sha256 = "zz"),
                storeGame(id = "no-title", title = "", system = "nes", url = "https://example.com/e.nes"),
            )

        val issues = manifest.validate()
        val messages = issues.joinToString("\n") { "${it.gameId}: ${it.message}" }

        assertTrue(messages, issues.any { it.gameId == "dup" && it.message.contains("Duplicate") })
        assertTrue(messages, issues.any { it.gameId == "no-url" && it.message.contains("url") })
        assertTrue(messages, issues.any { it.gameId == "bad-system" && it.message.contains("Unknown system") })
        assertTrue(messages, issues.any { it.gameId == "bad-hash" && it.message.contains("sha256") })
        assertTrue(messages, issues.any { it.gameId == "no-title" && it.message.contains("title") })
    }

    @Test
    fun `serialising and parsing a manifest keeps it identical`() {
        val manifest = StoreManifestCodec.parse(completeManifest)

        val reparsed = StoreManifestCodec.parse(StoreManifestCodec.serialize(manifest))

        assertEquals(manifest, reparsed)
    }

    private fun manifestOf(vararg games: StoreGame) = StoreManifest(games = games.toList())

    private fun storeGame(
        id: String = "id",
        title: String = "Title",
        system: String = "nes",
        url: String = "https://example.com/game.nes",
        fileName: String? = null,
        sha256: String? = null,
    ) = StoreGame(
        id = id,
        title = title,
        system = system,
        url = url,
        fileName = fileName,
        sha256 = sha256,
    )
}

package com.swordfish.lemuroid.app.mobile.feature.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Drive catalog is generated, not written by hand, so these tests pin the rules that decide what
 * ends up in the store: which files are games, which system they belong to and which cover is theirs.
 */
class DriveManifestBuilderTest {
    @Test
    fun `turns gba files into games and pairs the covers`() {
        val files =
            listOf(
                DriveFile(id = "rom-1", name = "007 - Everything or Nothing (U) (M3).gba", folderPath = listOf("Roms")),
                DriveFile(id = "cover-1", name = "- 007 - Everything or Nothing (U) (M3).png", folderPath = listOf("Pack Covers")),
                DriveFile(id = "rom-2", name = "Zooo (E) (M5).gba", folderPath = listOf("Roms")),
            )

        val manifest = DriveManifestBuilder.build("gba", files)

        assertEquals("gba", manifest.name)
        assertEquals(listOf("007 - Everything or Nothing (U) (M3)", "Zooo (E) (M5)"), manifest.games.map { it.title })

        val first = manifest.games.first()
        assertEquals("rom-1", first.id)
        assertEquals("gba", first.system)
        assertEquals("007 - Everything or Nothing (U) (M3).gba", first.fileName)
        assertEquals("https://drive.google.com/uc?export=download&id=rom-1", first.url)
        assertEquals("https://drive.google.com/thumbnail?id=cover-1&sz=w512", first.coverUrl)

        assertNull(manifest.games[1].coverUrl)
    }

    @Test
    fun `is case and separator insensitive when pairing a cover`() {
        val files =
            listOf(
                DriveFile(id = "r", name = "Advance Wars (Europe).gba", folderPath = listOf("Roms")),
                DriveFile(id = "c", name = "advancewars (europe).PNG", folderPath = listOf("Covers")),
            )

        val game = DriveManifestBuilder.build("pack", files).games.single()

        assertEquals("https://drive.google.com/thumbnail?id=c&sz=w512", game.coverUrl)
    }

    @Test
    fun `resolves an ambiguous extension from the folder name`() {
        val files =
            listOf(
                DriveFile(id = "psx-1", name = "Crash Bandicoot.iso", folderPath = listOf("Roms", "psx")),
                DriveFile(id = "gb-1", name = "Tetris.gb", folderPath = listOf("Roms")),
                DriveFile(id = "orphan", name = "Mystery.iso", folderPath = listOf("Roms")),
            )

        val manifest = DriveManifestBuilder.build("pack", files)

        assertEquals(listOf("gb-1", "psx-1"), manifest.games.map { it.id }.sorted())
        assertEquals("psx", manifest.games.first { it.id == "psx-1" }.system)
        assertEquals("gb", manifest.games.first { it.id == "gb-1" }.system)
    }

    @Test
    fun `ignores files the emulator could not open and files with no system`() {
        val files =
            listOf(
                DriveFile(id = "rom", name = "Game.gba", folderPath = listOf("Roms")),
                DriveFile(id = "readme", name = "readme.txt", folderPath = listOf("Roms")),
                DriveFile(id = "img", name = "screenshot.png", folderPath = listOf("Roms")),
                DriveFile(id = "iso", name = "Mystery.iso", folderPath = listOf("Roms")),
            )

        val manifest = DriveManifestBuilder.build("pack", files)

        assertEquals(listOf("rom"), manifest.games.map { it.id })
    }

    @Test
    fun `an empty folder produces an empty catalog`() {
        val manifest = DriveManifestBuilder.build("vacio", emptyList())

        assertTrue(manifest.games.isEmpty())
        assertTrue(manifest.validate().isEmpty())
    }
}

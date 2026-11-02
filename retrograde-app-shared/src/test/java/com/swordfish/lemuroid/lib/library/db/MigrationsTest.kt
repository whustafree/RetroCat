package com.swordfish.lemuroid.lib.library.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.swordfish.lemuroid.lib.library.db.dao.Migrations
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verifies the v9 to v10 migration adds `screenshotUrl` without losing existing data, and
 * that the generated Room schema matches what the entity expects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "migration-test.db"
    private var database: RetrogradeDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `migrates existing games keeping their data and adding screenshotUrl`() =
        runTest {
            createLegacyV9Database()

            val migrated =
                Room.databaseBuilder(context, RetrogradeDatabase::class.java, databaseName)
                    .addMigrations(Migrations.VERSION_9_10, Migrations.VERSION_10_11)
                    .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
                    .allowMainThreadQueries()
                    .build()

            migrated.openHelper.writableDatabase
            database = migrated

            val game = migrated.gameDao().selectById(1)

            assertNotNull(game)
            assertEquals("Sonic the Hedgehog", game?.title)
            assertEquals("Sonic Team", game?.developer)
            assertEquals(200L, game?.lastPlayedAt)
            assertTrue(game?.isFavorite == true)
            // New column exists and defaults to null for pre-existing rows.
            assertNull(game?.screenshotUrl)
        }

    @Test
    fun `screenshotUrl round trips through insert and update`() =
        runTest {
            val db =
                Room.inMemoryDatabaseBuilder(context, RetrogradeDatabase::class.java)
                    .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
                    .allowMainThreadQueries()
                    .build()

            database = db

            val id =
                db.gameDao().insert(
                    listOf(
                        Game(
                            fileName = "Sonic.md",
                            fileUri = "content://games/1",
                            title = "Sonic the Hedgehog",
                            systemId = "megadrive",
                            developer = "Sonic Team",
                            coverFrontUrl = "https://thumbs/Named_Boxarts/Sonic.png",
                            screenshotUrl = "https://thumbs/Named_Titles/Sonic.png",
                            lastIndexedAt = 1L,
                        ),
                    ),
                ).first()

            val stored = db.gameDao().selectById(id.toInt())

            assertEquals("https://thumbs/Named_Titles/Sonic.png", stored?.screenshotUrl)
        }

    /**
     * Builds a database matching the v9 schema by hand, so the test does not depend on a
     * generated legacy class that would need to be kept in sync with old releases.
     */
    private fun createLegacyV9Database() {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()

        val db = SQLiteDatabase.openOrCreateDatabase(file, null)

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `games`(
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `fileName` TEXT NOT NULL,
                `fileUri` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `systemId` TEXT NOT NULL,
                `developer` TEXT,
                `coverFrontUrl` TEXT,
                `lastIndexedAt` INTEGER NOT NULL,
                `lastPlayedAt` INTEGER,
                `isFavorite` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_games_id` ON `games` (`id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_games_fileUri` ON `games` (`fileUri`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_title` ON `games` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_systemId` ON `games` (`systemId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_lastIndexedAt` ON `games` (`lastIndexedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_lastPlayedAt` ON `games` (`lastPlayedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_isFavorite` ON `games` (`isFavorite`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `datafiles`(
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `gameId` INTEGER NOT NULL,
                `fileName` TEXT NOT NULL,
                `fileUri` TEXT NOT NULL,
                `lastIndexedAt` INTEGER NOT NULL,
                `path` TEXT,
                FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_datafiles_id` ON `datafiles` (`id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_datafiles_fileUri` ON `datafiles` (`fileUri`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_datafiles_gameId` ON `datafiles` (`gameId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_datafiles_lastIndexedAt` ON `datafiles` (`lastIndexedAt`)")

        db.execSQL(
            """
            INSERT INTO games (id, fileName, fileUri, title, systemId, developer, coverFrontUrl, lastIndexedAt, lastPlayedAt, isFavorite)
            VALUES (1, 'Sonic.md', 'content://games/1', 'Sonic the Hedgehog', 'megadrive', 'Sonic Team',
                    'https://thumbnails.libretro.com/Sega/Named_Boxarts/Sonic.png', 100, 200, 1)
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO datafiles (id, gameId, fileName, fileUri, lastIndexedAt, path)
            VALUES (1, 1, 'Sonic.md', 'content://games/1', 100, NULL)
            """.trimIndent(),
        )

        db.version = 9
        db.close()
    }

    @Test
    fun `exported schema for version 10 contains the screenshotUrl column`() {
        val schemaDir = File("../retrograde-app-shared/schemas")
        val schema = File(schemaDir, "com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase/10.json")

        assertTrue("Expected exported schema 10.json at ${schema.absolutePath}", schema.exists())

        val contents = schema.readText()

        assertTrue(contents.contains("\"version\": 10"))
        assertTrue(contents.contains("screenshotUrl"))
    }

    @Test
    fun `migrates v10 dropping the redundant primary key indexes`() =
        runTest {
            createLegacyV10Database()

            val migrated =
                Room.databaseBuilder(context, RetrogradeDatabase::class.java, databaseName)
                    .addMigrations(Migrations.VERSION_10_11)
                    .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
                    .allowMainThreadQueries()
                    .build()

            // Opening the database runs the migration and the Room schema validation. A
            // mismatch here fails loudly instead of silently dropping the user's library.
            migrated.openHelper.writableDatabase
            database = migrated

            val game = migrated.gameDao().selectById(1)
            assertNotNull(game)
            assertEquals("Sonic the Hedgehog", game?.title)
            assertEquals("https://thumbnails.libretro.com/Sega/Named_Titles/Sonic.png", game?.screenshotUrl)

            val indices =
                migrated.openHelper.readableDatabase.query("PRAGMA index_list(`games`)").use { c ->
                    buildList {
                        while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name")))
                    }
                }

            assertFalse(
                "index_games_id should have been dropped, found $indices",
                indices.contains("index_games_id"),
            )
            assertTrue("index_games_fileUri should survive, found $indices", indices.contains("index_games_fileUri"))
        }

    @Test
    fun `migrates v10 dropping the redundant datafiles primary key index`() =
        runTest {
            createLegacyV10Database()

            val migrated =
                Room.databaseBuilder(context, RetrogradeDatabase::class.java, databaseName)
                    .addMigrations(Migrations.VERSION_10_11)
                    .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
                    .allowMainThreadQueries()
                    .build()

            migrated.openHelper.writableDatabase
            database = migrated

            val indices =
                migrated.openHelper.readableDatabase.query("PRAGMA index_list(`datafiles`)").use { c ->
                    buildList {
                        while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name")))
                    }
                }

            assertFalse(
                "index_datafiles_id should have been dropped, found $indices",
                indices.contains("index_datafiles_id"),
            )
            assertTrue(
                "index_datafiles_gameId should survive, found $indices",
                indices.contains("index_datafiles_gameId"),
            )
        }

    /**
     * Builds a database matching the v10 schema, which is v9 plus the `screenshotUrl` column.
     */
    private fun createLegacyV10Database() {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()

        val db = SQLiteDatabase.openOrCreateDatabase(file, null)

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `games`(
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `fileName` TEXT NOT NULL,
                `fileUri` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `systemId` TEXT NOT NULL,
                `developer` TEXT,
                `coverFrontUrl` TEXT,
                `screenshotUrl` TEXT,
                `lastIndexedAt` INTEGER NOT NULL,
                `lastPlayedAt` INTEGER,
                `isFavorite` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_games_id` ON `games` (`id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_games_fileUri` ON `games` (`fileUri`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_title` ON `games` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_systemId` ON `games` (`systemId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_lastIndexedAt` ON `games` (`lastIndexedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_lastPlayedAt` ON `games` (`lastPlayedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_isFavorite` ON `games` (`isFavorite`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `datafiles`(
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `gameId` INTEGER NOT NULL,
                `fileName` TEXT NOT NULL,
                `fileUri` TEXT NOT NULL,
                `lastIndexedAt` INTEGER NOT NULL,
                `path` TEXT,
                FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_datafiles_id` ON `datafiles` (`id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_datafiles_fileUri` ON `datafiles` (`fileUri`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_datafiles_gameId` ON `datafiles` (`gameId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_datafiles_lastIndexedAt` ON `datafiles` (`lastIndexedAt`)")

        db.execSQL(
            """
            INSERT INTO games (id, fileName, fileUri, title, systemId, developer, coverFrontUrl,
                               screenshotUrl, lastIndexedAt, lastPlayedAt, isFavorite)
            VALUES (1, 'Sonic.md', 'content://games/1', 'Sonic the Hedgehog', 'megadrive', 'Sonic Team',
                    'https://thumbnails.libretro.com/Sega/Named_Boxarts/Sonic.png',
                    'https://thumbnails.libretro.com/Sega/Named_Titles/Sonic.png', 100, 200, 1)
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO datafiles (id, gameId, fileName, fileUri, lastIndexedAt, path)
            VALUES (1, 1, 'Sonic.md', 'content://games/1', 100, NULL)
            """.trimIndent(),
        )

        db.version = 10
        db.close()
    }
}

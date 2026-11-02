package com.swordfish.lemuroid.lib.library.db

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.swordfish.lemuroid.lib.library.db.dao.GameSearchDao
import com.swordfish.lemuroid.lib.library.db.dao.toFtsPhrase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the FTS backed search, in particular that user input is treated as literal text.
 * Every case here used to throw a `SQLiteException` out of the FTS parser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameSearchDaoTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: RetrogradeDatabase

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(context, RetrogradeDatabase::class.java)
                .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
                // The fts_games virtual table and its triggers are created by this callback,
                // exactly as LemuroidApplicationModule registers it.
                .addCallback(GameSearchDao.CALLBACK)
                .allowMainThreadQueries()
                .build()

        database.gameDao().insert(
            listOf(
                game(id = 1, title = "Aladdin", systemId = "megadrive"),
                game(id = 2, title = "Alien", systemId = "megadrive"),
                game(id = 3, title = "Chrono Trigger", systemId = "snes"),
                game(id = 4, title = "Mario & Luigi", systemId = "snes"),
                game(id = 5, title = "Need for Speed (Special)", systemId = "nes"),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun search(query: String): List<String> {
        val source: PagingSource<Int, Game> = database.gameSearchDao().search(query)
        val result =
            source.load(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = 20,
                    placeholdersEnabled = false,
                ),
            )
        return (result as PagingSource.LoadResult.Page).data.map { it.title }
    }

    @Test
    fun `plain text still matches case insensitively`() =
        runTest {
            assertEquals(listOf("Aladdin"), search("Aladdin"))
            assertEquals(listOf("Alien"), search("ALIEN"))
            assertEquals(listOf("Alien"), search("alien"))
        }

    @Test
    fun `matching is token based, not substring based`() =
        runTest {
            // FTS matches whole tokens. `LIKE '%al%'` used to match these, so anything moving
            // from the catalog LIKE queries to FTS has to add prefix matching explicitly.
            assertEquals(emptyList<String>(), search("al"))
            assertEquals(emptyList<String>(), search("ala"))
            assertEquals(emptyList<String>(), search("ladd"))
        }

    @Test
    fun `blank query matches nothing instead of raising`() =
        runTest {
            assertEquals(emptyList<String>(), search(""))
            assertEquals(emptyList<String>(), search("   "))
        }

    @Test
    fun `fts operators are treated as literal text`() =
        runTest {
            // Each of these is FTS4 query syntax; none of them may reach the parser as syntax.
            val hostile =
                listOf(
                    "*",
                    "**",
                    "\"",
                    "\"\"",
                    "a\"b",
                    "AND",
                    "OR",
                    "NOT",
                    "NEAR",
                    "NEAR/2",
                    "(",
                    ")",
                    "()",
                    "^",
                    "-",
                    "+",
                    ":",
                    "a:b",
                    "NEAR(a b)",
                    "a OR b",
                    "\"unterminated",
                    "*:*",
                    "a*",
                    "((",
                    "^a",
                    "%",
                    "_",
                    "\\",
                    ";",
                )

            for (query in hostile) {
                // The assertion is that load() completes instead of throwing.
                val result = search(query)
                assertNotNull("Unexpected null for query <$query>", result)
            }
        }

    @Test
    fun `hostile input does not match unrelated titles`() =
        runTest {
            // A bare `*` used to be a prefix match on everything; it must return nothing.
            assertEquals(emptyList<String>(), search("*"))
            assertEquals(emptyList<String>(), search("NEAR"))
        }

    @Test
    fun `non alphanumeric characters are still searchable`() =
        runTest {
            assertEquals(listOf("Need for Speed (Special)"), search("Need for Speed"))
            assertTrue(search("Mario").contains("Mario & Luigi"))
        }

    @Test
    fun `phrase escaping doubles embedded quotes`() {
        assertEquals("\"\"\"\"", "\"".toFtsPhrase())
        assertEquals("\"a\"\"b\"", "a\"b".toFtsPhrase())
        assertEquals("\"sonic\"", "sonic".toFtsPhrase())
        assertEquals("\"sonic\"", "  sonic  ".toFtsPhrase())
        assertNull("".toFtsPhrase())
        assertNull("   ".toFtsPhrase())
    }

    private fun game(
        id: Int,
        title: String,
        systemId: String,
    ) = Game(
        id = id,
        fileName = "$title.rom",
        fileUri = "content://games/$id",
        title = title,
        systemId = systemId,
        developer = "Dev",
        coverFrontUrl = null,
        screenshotUrl = null,
        lastIndexedAt = 1L,
    )
}

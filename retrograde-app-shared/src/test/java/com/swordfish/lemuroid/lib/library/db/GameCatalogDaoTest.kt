package com.swordfish.lemuroid.lib.library.db

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.swordfish.lemuroid.lib.library.db.dao.GameDao
import com.swordfish.lemuroid.lib.library.db.dao.GameSearchDao
import com.swordfish.lemuroid.lib.library.db.dao.toFtsPhrase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the catalog queries, especially the sort-order and search combinations that are
 * easy to get wrong and hard to notice in the UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameCatalogDaoTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: RetrogradeDatabase

    private val dao: GameDao
        get() = database.gameDao()

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(context, RetrogradeDatabase::class.java)
                .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
                // Creates the fts_games table and the triggers that keep it in sync with games.
                .addCallback(GameSearchDao.CALLBACK)
                .allowMainThreadQueries()
                .build()

        dao.insert(
            listOf(
                game(id = 1, title = "Aladdin", systemId = "megadrive", favorite = true, playedAt = 300),
                game(id = 2, title = "Alien", systemId = "megadrive", favorite = false, playedAt = null),
                game(id = 3, title = "Bubsy", systemId = "snes", favorite = false, playedAt = 100),
                game(id = 4, title = "Chrono Trigger", systemId = "snes", favorite = true, playedAt = 500),
                game(id = 5, title = "Donkey Kong", systemId = "nes", favorite = false, playedAt = null),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `empty search falls back to the unfiltered queries`() =
        runTest {
            assertEquals(
                listOf("Aladdin", "Alien", "Bubsy", "Chrono Trigger", "Donkey Kong"),
                titles(dao.selectAll()),
            )
            assertEquals(
                listOf("Chrono Trigger", "Aladdin", "Bubsy"),
                titles(dao.selectAllByRecency()),
            )
            assertEquals(listOf("Aladdin", "Chrono Trigger"), titles(dao.selectAllFavoritesByName()))
            assertEquals(listOf("Aladdin", "Alien"), titles(dao.selectBySystem("megadrive")))
        }

    @Test
    fun `search filters by title prefix and ignores case`() =
        runTest {
            // FTS4 matches whole tokens, so the catalog searches by prefix rather than by
            // arbitrary substring. "ala" still finds the game while the user is typing.
            assertEquals(listOf("Aladdin"), titles(dao.selectAllByQuery(match("ala"))))
            assertEquals(listOf("Alien"), titles(dao.selectAllByQuery(match("ALIEN"))))
            assertEquals(listOf("Chrono Trigger"), titles(dao.selectAllByQuery(match("chrono"))))
        }

    @Test
    fun `search matches whole tokens only, never a fragment in the middle of a word`() =
        runTest {
            // This is the behaviour change coming from FTS4: "key" sits inside "Donkey" and is
            // no longer reachable, while a prefix of the word still is.
            assertEquals(emptyList<String>(), titles(dao.selectAllByQuery(match("key"))))
            assertEquals(listOf("Donkey Kong"), titles(dao.selectAllByQuery(match("don"))))
            assertEquals(emptyList<String>(), titles(dao.selectAllByQuery(match("zzz"))))
        }

    @Test
    fun `search matches multi word titles as a phrase`() =
        runTest {
            assertEquals(listOf("Chrono Trigger"), titles(dao.selectAllByQuery(match("chrono trig"))))
            assertEquals(listOf("Chrono Trigger"), titles(dao.selectAllByQuery(match("chrono t"))))
            // Order matters, exactly as it did with the previous substring match.
            assertEquals(emptyList<String>(), titles(dao.selectAllByQuery(match("trigger chrono"))))
        }

    @Test
    fun `search tolerates FTS operators typed by the user`() =
        runTest {
            // These used to abort the query with a SQLiteException or change its meaning.
            for (hostile in listOf("\"", "*", "a AND b", "a OR b", "NEAR(a b)", "^a", "a:b", "()", "a*")) {
                titles(dao.selectAllByQuery(match(hostile)))
            }
        }

    @Test
    fun `search keeps working after a title is renamed`() =
        runTest {
            assertEquals(listOf("Bubsy"), titles(dao.selectAllByQuery(match("bubs"))))

            dao.update(dao.selectById(3)!!.copy(title = "Bubsy in Monster World"))

            assertEquals(listOf("Bubsy in Monster World"), titles(dao.selectAllByQuery(match("bubs"))))
            assertEquals(listOf("Bubsy in Monster World"), titles(dao.selectAllByQuery(match("monster"))))
        }

    @Test
    fun `recency query returns played games most recent first`() =
        runTest {
            assertEquals(
                listOf("Chrono Trigger", "Aladdin", "Bubsy"),
                titles(dao.selectAllByRecency()),
            )
            // "ala" only finds Aladdin, which is the only game of the two that has been played.
            assertEquals(
                listOf("Aladdin"),
                titles(dao.selectAllByQueryRecency(match("ala"))),
            )
        }

    @Test
    fun `favorites query only returns favorites`() =
        runTest {
            assertEquals(listOf("Aladdin", "Chrono Trigger"), titles(dao.selectAllFavoritesByName()))
            // "ala" finds Aladdin and Alien, but Alien is not a favorite.
            assertEquals(listOf("Aladdin"), titles(dao.selectAllByQueryFavorites(match("ala"))))
        }

    @Test
    fun `system query narrows to a single system`() =
        runTest {
            assertEquals(listOf("Aladdin", "Alien"), titles(dao.selectBySystem("megadrive")))
            assertEquals(listOf("Aladdin"), titles(dao.selectBySystemAndQuery("megadrive", match("ala"))))
            assertEquals(listOf("Bubsy"), titles(dao.selectBySystemAndQuery("snes", match("bub"))))
        }

    @Test
    fun `single system recency respects the sort order`() =
        runTest {
            // Only Aladdin has been played in the Mega Drive library.
            assertEquals(listOf("Aladdin"), titles(dao.selectBySystemByRecency("megadrive")))
            // In SNES both games are played, so they come back most recent first.
            assertEquals(listOf("Chrono Trigger", "Bubsy"), titles(dao.selectBySystemByRecency("snes")))
        }

    @Test
    fun `single system favorites respects the sort order`() =
        runTest {
            assertEquals(listOf("Aladdin"), titles(dao.selectBySystemFavoritesByName("megadrive")))
            assertEquals(listOf("Chrono Trigger"), titles(dao.selectBySystemFavoritesByName("snes")))
        }

    @Test
    fun `multi system query combines systems and honours search`() =
        runTest {
            assertEquals(
                listOf("Aladdin", "Alien", "Bubsy", "Chrono Trigger"),
                titles(dao.selectBySystems(listOf("megadrive", "snes"))),
            )
            // "bub" only matches Bubsy, while "ala" only matches Aladdin.
            assertEquals(
                listOf("Bubsy"),
                titles(dao.selectBySystemsAndQuery(listOf("megadrive", "snes"), match("bub"))),
            )
            assertEquals(
                listOf("Aladdin"),
                titles(dao.selectBySystemsAndQuery(listOf("megadrive", "snes"), match("ala"))),
            )
        }

    @Test
    fun `multi system recency and favorites are filtered by search too`() =
        runTest {
            assertEquals(
                listOf("Chrono Trigger", "Aladdin", "Bubsy"),
                titles(dao.selectBySystemsByRecency(listOf("megadrive", "snes"))),
            )
            // "b" is a prefix of the token "Bubsy" only; "Trigger" is not reachable from "b".
            assertEquals(
                listOf("Bubsy"),
                titles(dao.selectBySystemsAndQueryRecency(listOf("megadrive", "snes"), match("b"))),
            )
            assertEquals(
                listOf("Aladdin"),
                titles(dao.selectBySystemsAndQueryFavorites(listOf("megadrive", "snes"), match("ala"))),
            )
        }

    @Test
    fun `continue playing returns most recently played first`() =
        runTest {
            assertEquals(listOf("Chrono Trigger", "Aladdin"), dao.selectContinuePlaying(2).first().map { it.title })
        }

    @Test
    fun `missing screenshots are the ones without a screenshot but with a cover`() =
        runTest {
            dao.update(dao.selectById(1)!!.copy(screenshotUrl = "https://thumbs/Named_Titles/Aladdin.png"))

            assertEquals(listOf(2, 3, 4, 5), dao.selectMissingScreenshots().map { it.id })
        }

    @Test
    fun `game by id flow reflects updates`() =
        runTest {
            val flow = dao.selectByIdFlow(2)
            assertEquals(false, flow.first()!!.isFavorite)

            dao.update(dao.selectById(2)!!.copy(isFavorite = true))

            assertEquals(true, flow.first()!!.isFavorite)
        }

    /**
     * Builds the FTS4 phrase expected by the `select*ByQuery` methods, which only accept
     * non blank search text.
     */
    private fun match(query: String): String = requireNotNull(query.toFtsPhrase(prefixLastToken = true))

    /**
     * Loads a paging source directly instead of going through the paging-testing helpers,
     * which only exist for a newer paging version than this project depends on.
     */
    private fun titles(source: PagingSource<Int, Game>): List<String> =
        runBlocking {
            val page =
                source.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 50,
                        placeholdersEnabled = false,
                    ),
                ) as PagingSource.LoadResult.Page

            page.data.map { it.title }
        }

    private fun game(
        id: Int,
        title: String,
        systemId: String,
        favorite: Boolean,
        playedAt: Long?,
    ) = Game(
        id = id,
        fileName = "$title.rom",
        fileUri = "content://games/$id",
        title = title,
        systemId = systemId,
        developer = null,
        coverFrontUrl = "https://thumbs/Named_Boxarts/$title.png",
        screenshotUrl = null,
        lastIndexedAt = 0L,
        lastPlayedAt = playedAt,
        isFavorite = favorite,
    )
}

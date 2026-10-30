package fr.roadly.core.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteDaoTest {
    private lateinit var db: RoadlyDatabase
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RoadlyDatabase::class.java).build()
    }
    @After fun tearDown() = db.close()

    private fun fav(id: String) = FavoritePlaceEntity(id, "n$id", null, 48.0, 2.0, "ADDRESS", "test", 1L)

    @Test fun setHome_keeps_single_home() = runTest {
        db.favoriteDao().setHome(fav("a")); db.favoriteDao().setHome(fav("b"))
        val all = db.favoriteDao().observeAll().first()
        assertEquals(1, all.count { it.isHome })
        assertEquals("b", all.first { it.isHome }.id)
    }

    @Test fun recents_capped_at_50() = runTest {
        repeat(60) { i -> db.recentSearchDao().record(RecentSearchEntity(placeId = "p$i", placeJson = "{}", searchedAt = i.toLong())) }
        assertEquals(50, db.recentSearchDao().observe().first().size)
    }
}

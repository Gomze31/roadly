package fr.roadly.core.data.db

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.roadlyPrefs: DataStore<Preferences> by preferencesDataStore(name = "roadly_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): RoadlyDatabase =
        Room.databaseBuilder(ctx, RoadlyDatabase::class.java, "roadly.db").build()

    @Provides fun favoriteDao(db: RoadlyDatabase) = db.favoriteDao()
    @Provides fun recentDao(db: RoadlyDatabase) = db.recentSearchDao()
    @Provides fun vehicleDao(db: RoadlyDatabase) = db.vehicleProfileDao()
    @Provides @Singleton fun dataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.roadlyPrefs
}

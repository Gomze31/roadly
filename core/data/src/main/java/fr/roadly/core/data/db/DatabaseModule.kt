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
        Room.databaseBuilder(ctx, RoadlyDatabase::class.java, "roadly.db").addMigrations(RoadlyDatabase.MIGRATION_1_2, RoadlyDatabase.MIGRATION_2_3, RoadlyDatabase.MIGRATION_3_4, RoadlyDatabase.MIGRATION_4_5, RoadlyDatabase.MIGRATION_5_6, RoadlyDatabase.MIGRATION_6_7).build()

    @Provides fun favoriteDao(db: RoadlyDatabase) = db.favoriteDao()
    @Provides fun vigilanceDao(db: RoadlyDatabase) = db.vigilanceZoneDao()
    @Provides fun tripDao(db: RoadlyDatabase) = db.tripDao()
    @Provides fun recentDao(db: RoadlyDatabase) = db.recentSearchDao()
    @Provides fun vehicleDao(db: RoadlyDatabase) = db.vehicleProfileDao()
    @Provides fun scooterProfileDao(db: RoadlyDatabase) = db.scooterProfileDao()
    @Provides @Singleton fun dataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.roadlyPrefs
}

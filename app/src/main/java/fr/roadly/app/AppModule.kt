package fr.roadly.app

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.roadly.core.common.update.AppVersion

/** Version installée (comparée au `versionCode` des releases GitHub). */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides fun appVersion(): AppVersion = AppVersion(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
}

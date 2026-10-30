package fr.roadly.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.roadly.core.common.DefaultDispatcherProvider
import fr.roadly.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides @Singleton fun dispatchers(): DispatcherProvider = DefaultDispatcherProvider()
    @Provides @Singleton @ApplicationScope
    fun appScope(dispatchers: DispatcherProvider): CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.default)
}

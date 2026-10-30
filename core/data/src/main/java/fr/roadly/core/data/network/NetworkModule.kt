package fr.roadly.core.data.network

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.roadly.core.data.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Provides @Singleton
    fun okHttp(@ApplicationContext ctx: Context): OkHttpClient {
        val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "dev"
        return OkHttpClient.Builder()
            .cache(Cache(File(ctx.cacheDir, "http"), 10L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor("Roadly/$version (+https://roadly.example)"))
            .apply { if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)) }
            .build()
    }
}

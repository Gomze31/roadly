package fr.roadly.core.data.update

import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.roadly.core.common.update.AppVersion
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

/** Liaisons des mises à jour ; [AppVersion] est fourni par le module `app` (BuildConfig). */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds abstract fun settings(impl: DataStoreUpdateSettings): UpdateSettings
    @Binds abstract fun installer(impl: PackageApkInstaller): ApkInstaller

    companion object {
        @Provides @Singleton
        fun source(client: OkHttpClient): ReleaseSource = GitHubReleaseSource(client)

        @Provides @Singleton
        fun controller(source: ReleaseSource, settings: UpdateSettings, installer: ApkInstaller, version: AppVersion, @ApplicationContext ctx: Context): AppUpdateController =
            AppUpdateController(source, settings, installer, version, Build.VERSION.SDK_INT, File(ctx.cacheDir, "updates"))
    }
}

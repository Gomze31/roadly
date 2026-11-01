package fr.roadly.core.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installation par session PackageInstaller (aucun FileProvider). Android affiche sa confirmation ; à partir
 * d'Android 12, si Roadly a lui-même installé la version en place, les mises à jour suivantes peuvent se faire
 * sans confirmation (permission UPDATE_PACKAGES_WITHOUT_USER_ACTION déclarée).
 */
@Singleton
class PackageApkInstaller @Inject constructor(@ApplicationContext private val context: Context) : ApkInstaller {
    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override suspend fun install(file: File): Outcome<Unit> = withContext(Dispatchers.IO) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("roadly.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java).putExtra(UpdateInstallReceiver.EXTRA_FILE, file.absolutePath)
                // FLAG_MUTABLE : le système ajoute le statut de l'installation dans l'intent (explicite vers notre récepteur).
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            }
            Outcome.Success(Unit)
        } catch (e: Exception) {
            Outcome.Failure(AppError.Unknown(e.message))
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateEntryPoint {
    fun controller(): AppUpdateController
}

/** Statut de la session d'installation : confirmation utilisateur à afficher, succès ou échec. */
class UpdateInstallReceiver : BroadcastReceiver() {
    companion object { const val EXTRA_FILE = "fr.roadly.update.FILE" }

    override fun onReceive(context: Context, intent: Intent) {
        val controller = EntryPointAccessors.fromApplication(context.applicationContext, UpdateEntryPoint::class.java).controller()
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else controller.onInstallResult(false, message)
            }
            PackageInstaller.STATUS_SUCCESS -> controller.onInstallResult(true, null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> controller.onInstallCancelled(intent.getStringExtra(EXTRA_FILE)?.let(::File))
            else -> controller.onInstallResult(false, message)
        }
    }
}

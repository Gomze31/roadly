package fr.roadly.core.data.update

import fr.roadly.core.common.Outcome
import fr.roadly.core.common.update.AppUpdates
import fr.roadly.core.common.update.AppVersion
import fr.roadly.core.common.update.AvailableUpdate
import fr.roadly.core.common.update.GitHubRelease
import fr.roadly.core.common.update.ReleaseAsset
import fr.roadly.core.common.update.UpdateDecision
import fr.roadly.core.common.update.UpdateManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Source des releases (GitHub). [token] null = accès anonyme (dépôt public seulement). */
interface ReleaseSource {
    suspend fun latest(owner: String, repo: String, token: String?): Outcome<GitHubRelease>
    suspend fun text(asset: ReleaseAsset, token: String?): Outcome<String>
    suspend fun download(asset: ReleaseAsset, token: String?, dest: File, onProgress: (Long, Long) -> Unit): Outcome<File>
}

/** Réglages des mises à jour (dépôt, jeton chiffré, vérification automatique). */
interface UpdateSettings {
    val repository: Flow<String>
    suspend fun setRepository(value: String)
    /** Jeton GitHub déchiffré ; null = aucun. */
    val token: Flow<String?>
    suspend fun setToken(value: String?)
    val autoCheck: Flow<Boolean>
    suspend fun setAutoCheck(value: Boolean)
    val lastCheckAtMs: Flow<Long?>
    suspend fun setLastCheckAtMs(value: Long)
    /** Version ignorée par « Plus tard » : plus proposée automatiquement (la vérification manuelle la montre). */
    val skippedVersionCode: Flow<Int?>
    suspend fun setSkippedVersionCode(value: Int?)
}

/** Installation d'un APK téléchargé (Android PackageInstaller). */
interface ApkInstaller {
    /** « Installer des applications inconnues » accordé à Roadly. */
    fun canInstall(): Boolean
    /** Lance la session ; le résultat arrive par [AppUpdateController.onInstallResult]. */
    suspend fun install(file: File): Outcome<Unit>
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAtMs: Long) : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Incompatible(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val update: AvailableUpdate, val doneBytes: Long, val totalBytes: Long) : UpdateState
    data class ReadyToInstall(val update: AvailableUpdate, val file: File) : UpdateState
    /** Autorisation « sources inconnues » à accorder dans les réglages Android avant d'installer. */
    data class NeedsPermission(val update: AvailableUpdate, val file: File) : UpdateState
    data class Installing(val update: AvailableUpdate) : UpdateState
    data class Failed(val message: String, val update: AvailableUpdate? = null) : UpdateState
}

/**
 * Mise à jour de Roadly depuis les releases GitHub : dernière release → `update.json` → comparaison du
 * `versionCode` → téléchargement de l'APK → vérification SHA-256 → installation (confirmation Android).
 * Android refuse lui-même un APK signé avec une autre clé que l'application installée.
 */
class AppUpdateController(
    private val source: ReleaseSource,
    private val settings: UpdateSettings,
    private val installer: ApkInstaller,
    private val current: AppVersion,
    private val deviceSdk: Int,
    private val downloadDir: File,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** Chaque push sur main publie une release : plusieurs par jour. 30 min évite de solliciter GitHub à chaque retour dans l'appli. */
        const val AUTO_CHECK_INTERVAL_MS = 30 * 60 * 1000L
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val mutex = Mutex()

    /**
     * Vérification automatique à chaque retour au premier plan : si activée, si la dernière date de plus de 30 min, et jamais
     * pendant un téléchargement ou une installation (l'état en cours serait écrasé).
     */
    suspend fun checkIfDue() {
        if (!settings.autoCheck.first()) return
        when (_state.value) {
            is UpdateState.Checking, is UpdateState.Downloading, is UpdateState.ReadyToInstall,
            is UpdateState.NeedsPermission, is UpdateState.Installing -> return
            else -> Unit
        }
        val last = settings.lastCheckAtMs.first()
        if (last != null && nowMs() - last < AUTO_CHECK_INTERVAL_MS) return
        check()
    }

    /** Version proposée automatiquement (non ignorée) ; null sinon. */
    suspend fun promptable(): AvailableUpdate? {
        val s = _state.value as? UpdateState.Available ?: return null
        return s.update.takeIf { it.manifest.versionCode != settings.skippedVersionCode.first() }
    }

    suspend fun skip(update: AvailableUpdate) = settings.setSkippedVersionCode(update.manifest.versionCode)

    suspend fun check(): UpdateState = mutex.withLock {
        _state.value = UpdateState.Checking
        val result = try { doCheck() } catch (e: CancellationException) { throw e } catch (e: Exception) { UpdateState.Failed(e.message ?: "Erreur inattendue") }
        if (result !is UpdateState.Failed) settings.setLastCheckAtMs(nowMs())
        _state.value = result
        result
    }

    private suspend fun doCheck(): UpdateState {
        val (owner, repo) = AppUpdates.parseRepository(settings.repository.first())
            ?: return UpdateState.Failed("Dépôt invalide : saisissez « propriétaire/dépôt »")
        val token = settings.token.first()
        val release = when (val r = source.latest(owner, repo, token)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return UpdateState.Failed(GitHubErrors.message(r.error, token != null))
        }
        val manifestAsset = AppUpdates.manifestAsset(release)
        val manifest = manifestAsset?.let { a ->
            when (val t = source.text(a, token)) {
                is Outcome.Success -> AppUpdates.parseManifest(t.value)
                is Outcome.Failure -> return UpdateState.Failed(GitHubErrors.message(t.error, token != null))
            }
        }
        return when (val d = AppUpdates.decide(release, manifest, current, deviceSdk)) {
            is UpdateDecision.Available -> UpdateState.Available(d.update)
            UpdateDecision.UpToDate -> UpdateState.UpToDate(nowMs())
            is UpdateDecision.Incompatible -> UpdateState.Incompatible(d.manifest)
            is UpdateDecision.Malformed -> UpdateState.Failed(d.reason)
        }
    }

    /** Télécharge l'APK de [update], vérifie son empreinte, puis enchaîne sur l'installation. */
    suspend fun downloadAndInstall(update: AvailableUpdate) {
        mutex.withLock {
            downloadDir.mkdirs()
            downloadDir.listFiles()?.forEach { it.delete() }
            val dest = File(downloadDir, "roadly-${update.manifest.versionCode}.apk")
            _state.value = UpdateState.Downloading(update, 0, update.apk.size)
            val token = settings.token.first()
            val file = when (val r = source.download(update.apk, token, dest) { done, total -> _state.value = UpdateState.Downloading(update, done, total) }) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> { _state.value = UpdateState.Failed(GitHubErrors.message(r.error, token != null), update); return }
            }
            val actual = AppUpdates.sha256(file)
            if (!actual.equals(update.manifest.sha256, ignoreCase = true)) {
                file.delete()
                _state.value = UpdateState.Failed("Fichier corrompu (empreinte SHA-256 différente) : réessayez", update)
                return
            }
            _state.value = UpdateState.ReadyToInstall(update, file)
        }
        install()
    }

    /** Installe l'APK prêt ; demande d'abord l'autorisation « sources inconnues » si nécessaire. */
    suspend fun install() {
        val (update, file) = when (val s = _state.value) {
            is UpdateState.ReadyToInstall -> s.update to s.file
            is UpdateState.NeedsPermission -> s.update to s.file
            else -> return
        }
        if (!installer.canInstall()) { _state.value = UpdateState.NeedsPermission(update, file); return }
        _state.value = UpdateState.Installing(update)
        when (val r = installer.install(file)) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> _state.value = UpdateState.Failed("Installation impossible : ${r.error}", update)
        }
    }

    /** Résultat de la session PackageInstaller. En cas de succès, Android redémarre l'application. */
    fun onInstallResult(success: Boolean, message: String?) {
        val update = (_state.value as? UpdateState.Installing)?.update
        if (!success) {
            val hint = if (message?.contains("signature", ignoreCase = true) == true || message?.contains("conflict", ignoreCase = true) == true)
                " — l'APK n'est pas signé avec la même clé que l'application installée" else ""
            _state.value = UpdateState.Failed("Installation refusée${message?.let { " : $it" } ?: ""}$hint", update)
        }
    }

    /** L'utilisateur a annulé la confirmation Android : l'APK reste prêt. */
    fun onInstallCancelled(file: File?) {
        val update = (_state.value as? UpdateState.Installing)?.update ?: return
        _state.value = if (file != null && file.exists()) UpdateState.ReadyToInstall(update, file) else UpdateState.Available(update)
    }
}

/** Messages d'erreur GitHub compréhensibles. */
object GitHubErrors {
    fun message(e: fr.roadly.core.common.AppError, hasToken: Boolean): String = when {
        e is fr.roadly.core.common.AppError.Http && e.code == 404 ->
            if (hasToken) "Aucune release publiée, ou le jeton n'a pas accès à ce dépôt" else "Aucune release trouvée : dépôt privé ? Ajoutez un jeton GitHub (lecture seule)"
        e is fr.roadly.core.common.AppError.Http && e.code == 401 -> "Jeton GitHub refusé (expiré ou invalide)"
        e is fr.roadly.core.common.AppError.Http && e.code == 403 -> "Accès refusé ou limite de requêtes GitHub atteinte : réessayez plus tard"
        e == fr.roadly.core.common.AppError.RateLimited -> "Limite de requêtes GitHub atteinte : réessayez plus tard"
        e == fr.roadly.core.common.AppError.Network -> "Pas de connexion"
        e == fr.roadly.core.common.AppError.Timeout -> "GitHub ne répond pas"
        else -> "Erreur GitHub ($e)"
    }
}

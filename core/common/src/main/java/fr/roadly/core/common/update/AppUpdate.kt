package fr.roadly.core.common.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Version installée (BuildConfig de l'application). */
data class AppVersion(val code: Int, val name: String)

/** Fichier joint à une release GitHub. [apiUrl] = `assets[].url` (téléchargement authentifié possible). */
@Serializable
data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long = 0,
    @SerialName("url") val apiUrl: String,
    @SerialName("browser_download_url") val browserUrl: String = "",
)

/** Réponse `GET /repos/{owner}/{repo}/releases/latest` (champs utiles). */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

/** `update.json` publié avec chaque release par le workflow `.github/workflows/release.yml`. */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    /** Nom du fichier APK joint à la même release. */
    val apk: String,
    /** Empreinte SHA-256 (hexadécimal) de l'APK, vérifiée après téléchargement. */
    val sha256: String,
    val minSdk: Int = 1,
)

/** Mise à jour proposée : release, manifeste et fichier APK correspondant. */
data class AvailableUpdate(val release: GitHubRelease, val manifest: UpdateManifest, val apk: ReleaseAsset) {
    val notes: String get() = release.body?.trim().orEmpty()
}

sealed interface UpdateDecision {
    data class Available(val update: AvailableUpdate) : UpdateDecision
    data object UpToDate : UpdateDecision
    /** Version plus récente, mais Android trop ancien pour elle. */
    data class Incompatible(val manifest: UpdateManifest) : UpdateDecision
    /** Release sans `update.json` ou sans l'APK annoncé : publiée hors du workflow. */
    data class Malformed(val reason: String) : UpdateDecision
}

object AppUpdates {
    const val MANIFEST_ASSET = "update.json"
    const val DEFAULT_REPOSITORY = "Gomze31/roadly"

    private val json = Json { ignoreUnknownKeys = true }

    fun parseRelease(body: String): GitHubRelease? = runCatching { json.decodeFromString(GitHubRelease.serializer(), body) }.getOrNull()
    fun parseManifest(body: String): UpdateManifest? = runCatching { json.decodeFromString(UpdateManifest.serializer(), body) }.getOrNull()
    fun encodeManifest(m: UpdateManifest): String = json.encodeToString(UpdateManifest.serializer(), m)

    fun manifestAsset(release: GitHubRelease): ReleaseAsset? = release.assets.firstOrNull { it.name == MANIFEST_ASSET }

    /** Compare la release au code de version installé ; seul un `versionCode` strictement supérieur est proposé. */
    fun decide(release: GitHubRelease, manifest: UpdateManifest?, current: AppVersion, deviceSdk: Int): UpdateDecision {
        if (manifest == null) return UpdateDecision.Malformed("update.json absent ou illisible dans la release ${release.tag}")
        if (manifest.versionCode <= current.code) return UpdateDecision.UpToDate
        if (manifest.minSdk > deviceSdk) return UpdateDecision.Incompatible(manifest)
        val apk = release.assets.firstOrNull { it.name == manifest.apk }
            ?: return UpdateDecision.Malformed("APK ${manifest.apk} absent de la release ${release.tag}")
        if (!manifest.sha256.matches(Regex("^[0-9a-fA-F]{64}$"))) return UpdateDecision.Malformed("Empreinte SHA-256 invalide")
        return UpdateDecision.Available(AvailableUpdate(release, manifest, apk))
    }

    /** « propriétaire/dépôt » valide (caractères GitHub autorisés), sinon null. */
    fun parseRepository(value: String): Pair<String, String>? {
        val m = Regex("^\\s*([A-Za-z0-9-]{1,39})/([A-Za-z0-9._-]{1,100})\\s*$").find(value) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }
}

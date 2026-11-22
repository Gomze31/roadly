package fr.roadly.core.data.update

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.update.AppUpdates
import fr.roadly.core.common.update.GitHubRelease
import fr.roadly.core.common.update.ReleaseAsset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Serveurs auxquels le module de mise à jour accepte de parler, en HTTPS seulement : l'API GitHub, et ceux vers lesquels elle
 * redirige le téléchargement d'un fichier de release (`release-assets.githubusercontent.com` constaté le 2026-11-22 ;
 * `objects.githubusercontent.com` auparavant ; `github.com` pour les liens `browser_download_url`).
 */
object GitHubHosts {
    val ALLOWED: Set<String> = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    fun isAllowed(url: HttpUrl): Boolean = url.isHttps && url.host.lowercase() in ALLOWED
}

/**
 * API REST GitHub (https://docs.github.com/rest/releases) : `releases/latest` puis téléchargement des fichiers
 * par `assets/{id}` avec `Accept: application/octet-stream` (redirection vers un lien signé ; OkHttp ne renvoie
 * pas l'en-tête Authorization vers un autre hôte). Le jeton n'est jamais journalisé ni placé dans une URL.
 */
class GitHubReleaseSource(
    baseClient: OkHttpClient,
    private val apiBase: String = "https://api.github.com",
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Adresses acceptées, redirections comprises (SEC-6). Les tests y mettent leur serveur local. */
    private val allowed: (HttpUrl) -> Boolean = GitHubHosts::isAllowed,
    private val maxTextBytes: Long = MAX_TEXT_BYTES,
    private val maxApkBytes: Long = MAX_APK_BYTES,
) : ReleaseSource {
    companion object {
        /** Taille maximale d'une réponse texte (release GitHub, `update.json`) : 1 Mio. */
        const val MAX_TEXT_BYTES = 1L * 1024 * 1024
        /** Taille maximale d'un APK téléchargé : 200 Mio (l'APK de Roadly en fait quelques dizaines). */
        const val MAX_APK_BYTES = 200L * 1024 * 1024
        /** Corps d'une réponse d'erreur : seuls les premiers caractères servent au message. */
        private const val MAX_ERROR_BYTES = 16L * 1024

        /** Adresse (ou redirection) hors des serveurs GitHub : rien n'est téléchargé. */
        val UNTRUSTED_HOST: AppError = AppError.Parse("hôte de téléchargement non autorisé")
        /** Réponse ou fichier au-delà de la taille maximale. */
        val TOO_LARGE: AppError = AppError.Parse("téléchargement trop volumineux")
    }

    private class UntrustedHostException(url: HttpUrl) : IOException("hôte non autorisé : ${url.host}")

    // Sans cache HTTP (réponse toujours fraîche) et avec des délais adaptés au téléchargement d'un APK. L'intercepteur réseau
    // voit chaque requête réellement émise, y compris celles nées d'une redirection : aucune ne part vers un hôte refusé.
    private val client = baseClient.newBuilder().cache(null).readTimeout(60, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            if (!allowed(url)) throw UntrustedHostException(url)
            chain.proceed(chain.request())
        }
        .build()

    private val api = apiBase.toHttpUrlOrNull()

    private fun trusted(url: String): Boolean = url.toHttpUrlOrNull()?.let(allowed) == true

    /** Corps lu en mémoire dans la limite de [max] octets ; null au-delà. */
    private fun ResponseBody.boundedString(max: Long): String? {
        if (contentLength() > max) return null
        val source = source()
        source.request(max + 1)
        return if (source.buffer.size > max) null else source.buffer.readUtf8()
    }

    /**
     * Le jeton n'accompagne que les appels à l'API GitHub elle-même : une adresse de fichier venue de la réponse (`assets[].url`)
     * qui pointerait ailleurs part sans lui.
     */
    private fun request(url: String, token: String?, accept: String) = Request.Builder().url(url)
        .header("Accept", accept)
        .header("X-GitHub-Api-Version", "2022-11-28")
        .apply {
            val sameHost = api != null && url.toHttpUrlOrNull()?.let { it.host == api.host && it.scheme == api.scheme && it.port == api.port } == true
            if (!token.isNullOrBlank() && sameHost) header("Authorization", "Bearer ${token.trim()}")
        }
        .build()

    override suspend fun latest(owner: String, repo: String, token: String?): Outcome<GitHubRelease> {
        val url = "${apiBase.trimEnd('/')}/repos/$owner/$repo/releases/latest".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable("github"))
        if (!allowed(url)) return Outcome.Failure(UNTRUSTED_HOST)
        return when (val r = fetchText(request(url.toString(), token, "application/vnd.github+json"))) {
            is Outcome.Success -> AppUpdates.parseRelease(r.value)?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Parse("release GitHub"))
            is Outcome.Failure -> r
        }
    }

    override suspend fun text(asset: ReleaseAsset, token: String?): Outcome<String> {
        if (!trusted(asset.apiUrl)) return Outcome.Failure(UNTRUSTED_HOST)
        return fetchText(request(asset.apiUrl, token, "application/octet-stream"))
    }

    override suspend fun download(asset: ReleaseAsset, token: String?, dest: File, onProgress: (Long, Long) -> Unit): Outcome<File> = withContext(io) {
        if (!trusted(asset.apiUrl)) return@withContext Outcome.Failure(UNTRUSTED_HOST)
        if (asset.size > maxApkBytes) return@withContext Outcome.Failure(TOO_LARGE)
        try {
            client.newCall(request(asset.apiUrl, token, "application/octet-stream")).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Outcome.Failure(httpError(resp.code, resp.body?.boundedString(MAX_ERROR_BYTES).orEmpty()))
                val body = resp.body ?: return@withContext Outcome.Failure(AppError.Parse("réponse vide"))
                if (body.contentLength() > maxApkBytes) return@withContext Outcome.Failure(TOO_LARGE)
                val total = body.contentLength().takeIf { it > 0 } ?: asset.size
                val tmp = File(dest.parentFile, dest.name + ".part")
                var tooLarge = false
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            // Taille annoncée absente ou fausse : la limite s'applique aux octets réellement reçus.
                            if (done + n > maxApkBytes) { tooLarge = true; break }
                            out.write(buf, 0, n)
                            done += n
                            if (done - lastReport >= 256 * 1024 || done == total) { onProgress(done, total); lastReport = done }
                        }
                    }
                }
                if (tooLarge) { tmp.delete(); return@withContext Outcome.Failure(TOO_LARGE) }
                if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
                Outcome.Success(dest)
            }
        } catch (e: UntrustedHostException) {
            Outcome.Failure(UNTRUSTED_HOST)
        } catch (e: SocketTimeoutException) {
            Outcome.Failure(AppError.Timeout)
        } catch (e: IOException) {
            Outcome.Failure(AppError.Network)
        }
    }

    private suspend fun fetchText(req: Request): Outcome<String> = withContext(io) {
        try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body
                if (!resp.isSuccessful) return@withContext Outcome.Failure(httpError(resp.code, body?.boundedString(MAX_ERROR_BYTES).orEmpty()))
                if (body == null) return@withContext Outcome.Success("")
                body.boundedString(maxTextBytes)?.let { Outcome.Success(it) } ?: Outcome.Failure(TOO_LARGE)
            }
        } catch (e: UntrustedHostException) {
            Outcome.Failure(UNTRUSTED_HOST)
        } catch (e: SocketTimeoutException) {
            Outcome.Failure(AppError.Timeout)
        } catch (e: IOException) {
            Outcome.Failure(AppError.Network)
        }
    }

    private fun httpError(code: Int, body: String): AppError = when (code) {
        429 -> AppError.RateLimited
        in 500..599 -> AppError.ProviderUnavailable("github", code)
        else -> AppError.Http(code, body.take(300))
    }
}

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * API REST GitHub (https://docs.github.com/rest/releases) : `releases/latest` puis téléchargement des fichiers
 * par `assets/{id}` avec `Accept: application/octet-stream` (redirection vers un lien signé ; OkHttp ne renvoie
 * pas l'en-tête Authorization vers un autre hôte). Le jeton n'est jamais journalisé ni placé dans une URL.
 */
class GitHubReleaseSource(
    baseClient: OkHttpClient,
    private val apiBase: String = "https://api.github.com",
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ReleaseSource {
    // Sans cache HTTP (réponse toujours fraîche) et avec des délais adaptés au téléchargement d'un APK.
    private val client = baseClient.newBuilder().cache(null).readTimeout(60, TimeUnit.SECONDS).build()

    private fun request(url: String, token: String?, accept: String) = Request.Builder().url(url)
        .header("Accept", accept)
        .header("X-GitHub-Api-Version", "2022-11-28")
        .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer ${token.trim()}") }
        .build()

    override suspend fun latest(owner: String, repo: String, token: String?): Outcome<GitHubRelease> {
        val url = "${apiBase.trimEnd('/')}/repos/$owner/$repo/releases/latest".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable("github"))
        return when (val r = fetchText(request(url.toString(), token, "application/vnd.github+json"))) {
            is Outcome.Success -> AppUpdates.parseRelease(r.value)?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Parse("release GitHub"))
            is Outcome.Failure -> r
        }
    }

    override suspend fun text(asset: ReleaseAsset, token: String?): Outcome<String> =
        fetchText(request(asset.apiUrl, token, "application/octet-stream"))

    override suspend fun download(asset: ReleaseAsset, token: String?, dest: File, onProgress: (Long, Long) -> Unit): Outcome<File> = withContext(io) {
        try {
            client.newCall(request(asset.apiUrl, token, "application/octet-stream")).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Outcome.Failure(httpError(resp.code, resp.body?.string().orEmpty()))
                val body = resp.body ?: return@withContext Outcome.Failure(AppError.Parse("réponse vide"))
                val total = body.contentLength().takeIf { it > 0 } ?: asset.size
                val tmp = File(dest.parentFile, dest.name + ".part")
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - lastReport >= 256 * 1024 || done == total) { onProgress(done, total); lastReport = done }
                        }
                    }
                }
                if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
                Outcome.Success(dest)
            }
        } catch (e: SocketTimeoutException) {
            Outcome.Failure(AppError.Timeout)
        } catch (e: IOException) {
            Outcome.Failure(AppError.Network)
        }
    }

    private suspend fun fetchText(req: Request): Outcome<String> = withContext(io) {
        try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) Outcome.Success(text) else Outcome.Failure(httpError(resp.code, text))
            }
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

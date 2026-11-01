package fr.roadly.core.data.network

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownServiceException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class ApiClient @Inject constructor(
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) {
    /** Pauses avant chaque nouvelle tentative après un HTTP 429 (spec §8) ; surchargeable par les tests. */
    internal var retryDelaysMs: List<Long> = listOf(2_000L, 8_000L)

    suspend fun get(url: HttpUrl): Outcome<String> = execute(Request.Builder().url(url).get().build())

    /** [headers] : en-têtes supplémentaires (clé d'API d'un service configuré par l'utilisateur, par exemple). */
    suspend fun postJson(url: HttpUrl, body: String, headers: Map<String, String> = emptyMap()): Outcome<String> = execute(
        Request.Builder().url(url).post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { headers.forEach { (k, v) -> header(k, v) } }.build(),
    )

    /** Formulaire `application/x-www-form-urlencoded` (Overpass : champ `data`). */
    suspend fun postForm(url: HttpUrl, fields: Map<String, String>): Outcome<String> = execute(
        Request.Builder().url(url).post(okhttp3.FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()).build(),
    )

    private suspend fun execute(request: Request): Outcome<String> {
        var attempt = 0
        while (true) {
            val outcome = executeOnce(request)
            if (outcome is Outcome.Failure && outcome.error == AppError.RateLimited && attempt < retryDelaysMs.size) {
                delay(retryDelaysMs[attempt++])
                continue
            }
            return outcome
        }
    }

    private suspend fun executeOnce(request: Request): Outcome<String> = withContext(dispatchers.io) {
        try {
            client.newCall(request).await().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> Outcome.Success(text)
                    resp.code == 429 -> Outcome.Failure(AppError.RateLimited)
                    resp.code in 500..599 -> Outcome.Failure(AppError.ProviderUnavailable(request.url.host, resp.code))
                    else -> Outcome.Failure(AppError.Http(resp.code, text))
                }
            }
        } catch (e: SocketTimeoutException) {
            Outcome.Failure(AppError.Timeout)
        } catch (e: UnknownServiceException) {
            // Trafic http:// refusé par la politique cleartext d'Android : le fournisseur est inutilisable tel quel.
            Outcome.Failure(AppError.ProviderUnavailable(request.url.host))
        } catch (e: IOException) {
            Outcome.Failure(AppError.Network)
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) { cont.resume(response) { _, _, _ -> response.close() } }
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
    })
    cont.invokeOnCancellation { cancel() }
}

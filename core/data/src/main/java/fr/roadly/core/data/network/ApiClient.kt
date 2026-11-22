package fr.roadly.core.data.network

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.UnknownServiceException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Point de passage unique des appels HTTP des fournisseurs. Toute requête vers une instance publique FOSSGIS
 * (`*.openstreetmap.de`, dont Valhalla) passe par le [ValhallaRateLimiter] : aucun appelant ne peut le contourner.
 */
@Singleton
class ApiClient @Inject constructor(
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
    private val limiter: ValhallaRateLimiter = ValhallaRateLimiter(),
) {
    /** Pauses avant chaque nouvelle tentative après un HTTP 429 (spec §8) ; surchargeable par les tests. */
    internal var retryDelaysMs: List<Long> = listOf(2_000L, 8_000L)

    private companion object {
        /** Durée maximale (s) d'un appel vers un hôte limité, connexion et lecture comprises. */
        const val THROTTLED_CALL_TIMEOUT_S = 15L
    }

    /** [retryRateLimited] faux : un HTTP 429 est rendu tout de suite (appelant qui tient son propre retrait, voir [postForm]). */
    suspend fun get(url: HttpUrl, retryRateLimited: Boolean = true): Outcome<String> =
        execute(Request.Builder().url(url).get().build(), ApiPriority.BACKGROUND, retryRateLimited)

    /**
     * [headers] : en-têtes supplémentaires (clé d'API d'un service configuré par l'utilisateur, par exemple).
     * [priority] : rang dans la file du limiteur de débit (sans effet sur un hôte non limité).
     */
    suspend fun postJson(
        url: HttpUrl,
        body: String,
        headers: Map<String, String> = emptyMap(),
        priority: ApiPriority = ApiPriority.BACKGROUND,
    ): Outcome<String> = execute(
        Request.Builder().url(url).post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { headers.forEach { (k, v) -> header(k, v) } }.build(),
        priority,
    )

    /**
     * Formulaire `application/x-www-form-urlencoded` (Overpass : champ `data`).
     * [retryRateLimited] faux : un HTTP 429 est rendu tout de suite, sans les nouvelles tentatives de ce client — pour un
     * appelant qui tient son propre retrait (ralentisseurs : un 429 coûtait trois requêtes avant que le retrait ne commence).
     */
    suspend fun postForm(url: HttpUrl, fields: Map<String, String>, retryRateLimited: Boolean = true): Outcome<String> = execute(
        Request.Builder().url(url).post(okhttp3.FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()).build(),
        ApiPriority.BACKGROUND,
        retryRateLimited,
    )

    private suspend fun execute(request: Request, priority: ApiPriority, retryRateLimited: Boolean = true): Outcome<String> {
        val throttled = limiter.appliesTo(request.url.host)
        var attempt = 0
        while (true) {
            val outcome = if (!throttled) executeOnce(request, throttled = false) else {
                when (val permit = limiter.withPermit(priority) { executeOnce(request, throttled = true) }) {
                    is ValhallaRateLimiter.Permit.Ran -> permit.value
                    // Longue pause demandée par le serveur : échec immédiat, sans requête ni nouvelle tentative.
                    is ValhallaRateLimiter.Permit.Refused -> return Outcome.Failure(
                        if (permit.httpCode == 429) AppError.RateLimited else AppError.ProviderUnavailable(request.url.host, permit.httpCode),
                    )
                }
            }
            if (retryRateLimited && outcome is Outcome.Failure && outcome.error == AppError.RateLimited && attempt < retryDelaysMs.size) {
                delay(retryDelaysMs[attempt++])
                continue
            }
            return outcome
        }
    }

    private suspend fun executeOnce(request: Request, throttled: Boolean): Outcome<String> = withContext(dispatchers.io) {
        try {
            // Instance publique : durée totale bornée. Sans cela, une réponse qui s'étire (20 s de lecture par bloc reçu) gardait
            // l'unique créneau du limiteur et un recalcul abandonnait à 10 s sans jamais être parti.
            client.newCall(request).also { if (throttled) it.timeout().timeout(THROTTLED_CALL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS) }.await().use { resp ->
                val text = resp.body?.string().orEmpty()
                // Instance publique saturée ou indisponible : tout le processus se met en retrait, pas seulement cet appel.
                if (throttled && (resp.code == 429 || resp.code == 503)) {
                    limiter.pause(retryAfterMs(resp.headers, System.currentTimeMillis()) ?: ValhallaRateLimiter.DEFAULT_BACKOFF_MS, resp.code)
                }
                when {
                    resp.isSuccessful -> Outcome.Success(text)
                    resp.code == 429 -> Outcome.Failure(AppError.RateLimited)
                    resp.code in 500..599 -> Outcome.Failure(AppError.ProviderUnavailable(request.url.host, resp.code))
                    else -> Outcome.Failure(AppError.Http(resp.code, text))
                }
            }
        } catch (e: java.io.InterruptedIOException) {
            // Délai de connexion, de lecture (SocketTimeoutException) ou de l'appel entier.
            Outcome.Failure(AppError.Timeout)
        } catch (e: UnknownServiceException) {
            // Trafic http:// refusé par la politique cleartext d'Android : le fournisseur est inutilisable tel quel.
            Outcome.Failure(AppError.ProviderUnavailable(request.url.host))
        } catch (e: IOException) {
            Outcome.Failure(AppError.Network)
        }
    }
}

/** `Retry-After` en millisecondes : nombre de secondes ou date HTTP (RFC 9110 §10.2.3) ; null si absent ou illisible. */
internal fun retryAfterMs(headers: Headers, nowEpochMs: Long): Long? {
    val raw = headers["Retry-After"]?.trim().orEmpty()
    if (raw.isEmpty()) return null
    raw.toLongOrNull()?.let { return if (it >= 0) it.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L else null }
    return headers.getDate("Retry-After")?.let { (it.time - nowEpochMs).coerceAtLeast(0L) }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) { cont.resume(response) { _, _, _ -> response.close() } }
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
    })
    cont.invokeOnCancellation { cancel() }
}
